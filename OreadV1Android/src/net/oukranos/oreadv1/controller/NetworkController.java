package net.oukranos.oreadv1.controller;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import net.oukranos.oreadv1.interfaces.AbstractController;
import net.oukranos.oreadv1.interfaces.HttpEncodableData;
import net.oukranos.oreadv1.interfaces.bridge.IConnectivityBridge;
import net.oukranos.oreadv1.types.ControllerState;
import net.oukranos.oreadv1.types.ControllerStatus;
import net.oukranos.oreadv1.types.DataStore;
import net.oukranos.oreadv1.types.DataStoreObject;
import net.oukranos.oreadv1.types.MainControllerInfo;
import net.oukranos.oreadv1.types.NetworkResponse;
import net.oukranos.oreadv1.types.SendableData;
import net.oukranos.oreadv1.types.Status;

public class NetworkController extends AbstractController {
	private static final int MAX_QUEUE_CAPACITY = 20;
	private static final long MAX_DESTROY_THREAD_TIMEOUT = 5000;
	private static final long DEFAULT_AWAIT_RESPONSE_TIMEOUT = 30000;
	
	private static final int HTTP_ERROR_CODE_THRESHOLD = 300;
	public  static final int HTTP_RESPONSE_CODE_OK = 200;

	private Lock _queueLock = null;
	private Lock _waitThreadLock = null;
	private Queue<SendableData> _sendTaskQueue = null;
	private SendThreadTask _sendTaskLoop = null;
	private Thread _sendTaskLoopThread = null;
	private Thread _awaitResponseThread = null;
	private boolean _sendThreadRunning = false;
	private NetworkResponse _lastResponse = null;

	private static NetworkController _networkController = null;
	private static final HttpClient _httpClient = new DefaultHttpClient();

	private NetworkController(MainControllerInfo mainInfo) {

		/* Set the controller identifiers */
		this.setType("comm");
		this.setName("network");

		/* Set the controller state */
		this.setState(ControllerState.UNKNOWN);
		
		return;
	}

	public static NetworkController getInstance(MainControllerInfo mainInfo) {
		if (mainInfo == null) {
			OLog.err("Invalid input parameter/s" +
					" in NetworkController.getInstance()");
			return null;
		}
		
		if (_networkController == null) {
			_networkController = new NetworkController(mainInfo);
		}
		
		_networkController._mainInfo = mainInfo;

		return _networkController;
	}

	@Override
	public Status initialize(Object initializer) {
		_sendTaskQueue = new LinkedList<SendableData>();
		_queueLock = new ReentrantLock();
		_waitThreadLock = new ReentrantLock();

		this.setState(ControllerState.INACTIVE);

		OLog.info("Network Controller Initialized");

		return Status.OK;
	}

	@Override
	public Status destroy() {
		this.setState(ControllerState.UNKNOWN);

		this.destroySendTaskLoop();

		/* Destroy the lock */
		if (_queueLock != null) {
			if (_queueLock.tryLock()) {
				try {
					_queueLock.unlock();
				} catch (Exception e) {
					OLog.err("Unlock failed");
				}
			}
		}

		/* Destroy the await response lock */
		if (_waitThreadLock != null) {
			if (_waitThreadLock.tryLock()) {
				try {
					_waitThreadLock.unlock();
				} catch (Exception e) {
					OLog.err("Unlock failed");
				}
			}
		}
		
		/* Destroy the Task Queue */
		if (_sendTaskQueue != null) {
			_sendTaskQueue.clear();
			_sendTaskQueue = null;
		}

		OLog.info("Network Controller Destroyed");

		return Status.OK;
	}

	@Override
	public ControllerStatus performCommand(String cmdStr, String paramStr) {
		/* Check that inputs are valid */
		if (cmdStr == null) {
			this.writeErr("Invalid input parameter/s" +
							" in NetworkController.performCommand()");
			return this.getControllerStatus();
		}
		
		String sigStr = this.getType() + "." + this.getName();
		/* Check if the given command matches this controller's signature */
		if ( cmdStr.startsWith(sigStr) == false ) {
			this.writeErr("Signature does not match");
			return this.getControllerStatus();
		}
		
		/* Get the command portion of the string */
		int shortCmdStartIdx = cmdStr.lastIndexOf('.') + 1;
		/* Check if the last index is sane */
		if (shortCmdStartIdx < 0) {
			this.writeErr("Could not extract command");
			return this.getControllerStatus();
		}
		String shortCmdStr = cmdStr.substring(shortCmdStartIdx);
		if (shortCmdStr.isEmpty()) {
			this.writeErr("Malformed command string");
			return this.getControllerStatus();
		}
		
		/* Check which command to perform */
		if (shortCmdStr.equals("start") == true) {
			this.start();
		} else if (shortCmdStr.equals("stop") == true) {
			this.stop();
		} else if (shortCmdStr.equals("uploadData") == true) {
			/* Separate the param string into two elements via ',' */
			String paramArr[] = paramStr.split(",");
			if (paramArr.length != 2) {
				this.writeErr("Invalid number of params");
				return this.getControllerStatus();
			}

			/* Get a DataStore reference and retrieve the req'd objects */
			DataStore ds = _mainInfo.getDataStore();
			if (ds == null) {
				this.writeErr("DataStore uninitialized or unavailable");
				return this.getControllerStatus();
			}
			
			DataStoreObject httpData = ds.retrieve(paramArr[0]);
			DataStoreObject encData = ds.retrieve(paramArr[1]);
			
			if ((httpData == null) || (encData == null)) {
				this.writeErr("Could not retrieve data object");
				return this.getControllerStatus();
			}
			
			/* TODO Verify the data type here as well */
			
			String httpStr = (String) httpData.getObject(); 
			HttpEncodableData encodedData = (HttpEncodableData) encData.getObject();
			
			/* Send the data */
			this.send(httpStr, encodedData);
		} else if (shortCmdStr.equals("uploadDataNew") == true) {
			/* Separate the param string into two elements via ',' */
			String paramArr[] = paramStr.split(",");
			if (paramArr.length != 2) {
				this.writeErr("Invalid number of params");
				return this.getControllerStatus();
			}

			/* Get a DataStore reference and retrieve the req'd objects */
			DataStore ds = _mainInfo.getDataStore();
			if (ds == null) {
				this.writeErr("DataStore uninitialized or unavailable");
				return this.getControllerStatus();
			}
			
			DataStoreObject httpData = ds.retrieve(paramArr[0]);
			DataStoreObject encData = ds.retrieve(paramArr[1]);
			
			if ((httpData == null) || (encData == null)) {
				this.writeErr("Could not retrieve data object");
				return this.getControllerStatus();
			}
			
			/* TODO Verify the data type here as well */
			
			String httpStr = (String) httpData.getObject(); 
			HttpEncodableData encodedData = (HttpEncodableData) encData.getObject();
			
			/* Send the data */
			this.send(httpStr, encodedData);
		} else {
			this.writeErr("Unknown or invalid command: " + shortCmdStr);
		}
		
		return this.getControllerStatus();
	}

	public Status start() {
		OLog.info("NetController Start");
		if (this.getState() != ControllerState.INACTIVE) {
			return this.writeInfo("Invalid State: " + this.getState());
		}

		this.initalizeSendTaskLoop();

		this.setState(ControllerState.READY);

		/* Run the send task loop */
		_sendThreadRunning = true;
		_sendTaskLoopThread.start();

		return this.writeInfo("Network Controller Started");
	}

	public Status stop() {
		if (_sendTaskLoop == null && _sendTaskLoopThread == null) {
			return this.writeInfo("Already started");
		}

		this.destroySendTaskLoop();

		this.setState(ControllerState.INACTIVE);

		return this.writeInfo("Network controller stopped");
	}

	public Status send(String url, HttpEncodableData data) {
		if ((this.getState() != ControllerState.READY)
				&& (this.getState() != ControllerState.BUSY)) {
			/* We can send during busy state too because of the queue */
			return this.writeErr("Invalid state: " + this.getState());
		}

		if (data == null) {
			return this.writeErr("Invalid data parameter");
		}

		if (url == null) {
			return this.writeErr("Invalid url parameter");
		}

		if (url.isEmpty()) {
			return this.writeErr("Invalid url parameter");

		}

		_queueLock.lock();
		/* Check if we can still handle stuff in the send queue */
		if (_sendTaskQueue.size() >= MAX_QUEUE_CAPACITY) {
			_queueLock.unlock();
			return this.writeErr("Queue full");
		}

		/* Add incoming data to the send task queue */
		_sendTaskQueue.add(new SendableData(url, "POST", data));
		_queueLock.unlock();

		/* Notify the send thread */
		if (_sendTaskLoopThread != null) {
			_sendTaskLoopThread.interrupt();
		}

		OLog.info("Network Controller Send Task Added");

		return Status.OK;
	}
	
	public Status waitForResponse() {
		Status retStatus = Status.FAILED;
		
		if (checkInternetConnectivity() != Status.OK) {
			return Status.FAILED;
		}
		
		_waitThreadLock.lock();
		_awaitResponseThread = Thread.currentThread();
		_waitThreadLock.unlock();
		
		try {
			Thread.sleep(DEFAULT_AWAIT_RESPONSE_TIMEOUT);
		} catch (InterruptedException e) {
			retStatus = Status.OK;
		} catch (Exception e) {
			OLog.err("Exception Occurred: " + e.getMessage());
			retStatus = Status.FAILED;
		}

		_waitThreadLock.lock();
		_awaitResponseThread = null;
		_waitThreadLock.unlock();
		
		return retStatus;
	}
	
	public int getLastResponseCode() {
		if (_lastResponse != null) {
			return _lastResponse.getStatusCode();
		}
		
		return 0;
	}
	
	public String getLastResponseMessage() {
		if (_lastResponse != null) {
			return _lastResponse.getStatusMessage();
		}
		
		return "";
	}
	
	public byte[] getLastResponseData() {
		if (_lastResponse != null) {
			return _lastResponse.getData();
		}
		
		return null;
	}
	
	public boolean isLastResponseAvailable() {
		return (_lastResponse != null ? true : false);
	}
	
	public void clearLastResponse() {
		_lastResponse = null;
		return;
	}

	/*********************/
	/** Private Methods **/
	/*********************/
	private void initalizeSendTaskLoop() {
		if (_sendTaskLoop == null) {
			_sendTaskLoop = new SendThreadTask();
		}

		if (_sendTaskLoopThread == null) {
			_sendTaskLoopThread = new Thread(_sendTaskLoop);
		}

		return;
	}

	private Status destroySendTaskLoop() {
		/* Interrupt and Destroy the Send Thread */
		if (_sendTaskLoopThread != null) {
			_sendThreadRunning = false;
			_sendTaskLoopThread.interrupt();

			try {
				_sendTaskLoopThread.join(MAX_DESTROY_THREAD_TIMEOUT);
			} catch (InterruptedException e) {
				;
			}
			_sendTaskLoopThread = null;
		}

		/* Destroy the Send Task Runnable */
		if (_sendTaskLoop != null) {
			_sendTaskLoop = null;
		}

		return Status.OK;
	}

	private Status sendData(SendableData sendableData) {
		if (_httpClient == null) {
			OLog.err("HttpClient is null");
			return Status.FAILED;
		}

		if (sendableData == null) {
			OLog.err("SendableData is NULL");
			return Status.FAILED;
		}

		String url = sendableData.getUrl();
		if (url == null || url.isEmpty()) {
			OLog.err("Invalid URL string");
			return Status.FAILED;
		}

		HttpUriRequest httpRequest = null;
		if (sendableData.getMethod().equals("GET")) {
			httpRequest = new HttpGet(url);
		} else {
			httpRequest = new HttpPost(url);
			((HttpPost)httpRequest).setEntity(sendableData.getData().encodeDataToHttpEntity());
		}
		
		HttpResponse httpResp = null;
		try {
			httpResp = _httpClient.execute(httpRequest);
		} catch (ClientProtocolException e) {
			OLog.warn("Empty HttpResponse");
		} catch (IOException e) {
			OLog.err("HttpPost execution failed");
			OLog.err("Msg: " + e.getMessage());
			return Status.FAILED;
		}
		if (httpResp == null) {
			OLog.err("Failed to perform HttpPost");
			return Status.FAILED;
		}
		
		/* Initialize the last response object */
		if (_lastResponse == null) {
			_lastResponse = new NetworkResponse();
		}

		/* Extract the status code and message */
		int statusCode = httpResp.getStatusLine().getStatusCode();
		String statusMsg = httpResp.getStatusLine().getReasonPhrase();
		_lastResponse.setStatusCode(statusCode);
		_lastResponse.setStatusMsg(statusMsg);
		
		/* Check if the HTTP Response Code has reached the error threshold */
		if (statusCode >= HTTP_ERROR_CODE_THRESHOLD) {
			OLog.err("HttpResponse Error: " + statusCode + " - " + statusMsg);
			return Status.FAILED;
		}

		/* Check if the HTTP Response Code is an unexpected response code (i.e. not HTTP_OK) */
		if (statusCode != HTTP_RESPONSE_CODE_OK) {
			OLog.warn("HttpResponse Unexpected: " + statusCode + " - " + statusMsg);
			return Status.OK;
		}
		
		/* Save the last response's data */
		try {
			_lastResponse.setData(EntityUtils.toByteArray(httpResp.getEntity()));
		} catch (Exception e) {
			_lastResponse.setData(null);
		}

		OLog.info("Sent data to " + url);
		return Status.OK;
	}

	private IConnectivityBridge getConnectivityBridge() {
		IConnectivityBridge connBridge 
			= (IConnectivityBridge) _mainInfo
				.getFeature("connectivity");
		if (connBridge == null) {
			return connBridge;
		}
		
		if ( connBridge.isReady() == false ) {
			if (connBridge.initialize(_mainInfo) != Status.OK) {
				connBridge = null;
			}
		}
		
		return connBridge;
	}

	private Status checkInternetConnectivity() {
		IConnectivityBridge connBridge = getConnectivityBridge();
		if (connBridge == null) {
			OLog.err("Connectivity Bridge unavailable");
			return Status.FAILED;
		}

		if (connBridge.isConnected() == false) {
			OLog.warn("No internet connectivity");
			return Status.FAILED;
		}

		return Status.OK;
	}

	private class SendThreadTask implements Runnable {

		@Override
		public void run() {
//			OLog.info("Network Controller run task started");
			while (getState() == ControllerState.READY
					&& _sendThreadRunning == true) {
//				OLog.info("Network Controller run task loop start");
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					; /* Do nothing */
				}

				/* Exit thread if we are not in ready state */
				/*
				 * In theory, we should never be asleep while on busy state, so
				 * exit from that too since that might indicate an error
				 */
				if (getState() != ControllerState.READY
						|| _sendThreadRunning == false) {
					break;
				}

				_queueLock.lock();
				int queueSize = _sendTaskQueue.size();
				_queueLock.unlock();

				while (queueSize > 0) {
					if (checkInternetConnectivity() != Status.OK) {
						return;
					}

					/* Send the data and remove it from the queue */
					_queueLock.lock();
					sendData(_sendTaskQueue.remove()); // TODO
					_queueLock.unlock();

					/* Re-evaluate the size of the queue */
					_queueLock.lock();
					queueSize = _sendTaskQueue.size();
					_queueLock.unlock();
					
					/* Unblock the await response thread with an
					 *  interrupt if it exists */
					_waitThreadLock.lock();
					if (_awaitResponseThread != null) {
						_awaitResponseThread.interrupt();
					}
					_waitThreadLock.unlock();
				}
			}

//			OLog.info("Network Controller run task finished");
			return;
		}
	}
}
