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

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import net.oukranos.oreadv1.interfaces.AbstractController;
import net.oukranos.oreadv1.interfaces.HttpEncodableData;
import net.oukranos.oreadv1.types.ControllerState;
import net.oukranos.oreadv1.types.ControllerStatus;
import net.oukranos.oreadv1.types.DataStore;
import net.oukranos.oreadv1.types.DataStoreObject;
import net.oukranos.oreadv1.types.MainControllerInfo;
import net.oukranos.oreadv1.types.SendableData;
import net.oukranos.oreadv1.types.Status;
import net.oukranos.oreadv1.util.OLog;

public class NetworkController extends AbstractController {
	private static final int MAX_QUEUE_CAPACITY = 20;
	private static final long MAX_DESTROY_THREAD_TIMEOUT = 5000;
	private static final int HTTP_ERROR_CODE_THRESHOLD = 300;

	private Lock _queueLock = null;
	private Queue<SendableData> _sendTaskQueue = null;
	private SendThreadTask _sendTaskLoop = null;
	private Thread _sendTaskLoopThread = null;
	private boolean _sendThreadRunning = false;
	private Context _parentContext = null;
	private MainControllerInfo _mainInfo = null;

	private static NetworkController _networkController = null;
	private static final HttpClient _httpClient = new DefaultHttpClient();

	private NetworkController(MainControllerInfo mainInfo) {

		/* Set the controller identifiers */
		this.setType("comm");
		this.setName("network");

		/* Set the controller state */
		this.setState(ControllerState.UNKNOWN);

		
		/* TODO Will need to separate this from the implementation later
		 * since it is actually part of the android platform. Goal is to
		 * be able to run the SubControllers independently of Android */
		this._parentContext = (Context) mainInfo.getContext();
		
		/* Set the main info reference */
		this._mainInfo = mainInfo;
		
		return;
	}

	public static NetworkController getInstance(MainControllerInfo mainInfo) {
		if (_networkController == null) {
			if (mainInfo == null) {
				return null;
			}
			
			/* TODO Will need to separate this from the implementation later
			 * since it is actually part of the android platform. Goal is to
			 * be able to run the SubControllers independently of Android */
			if (mainInfo.getContext() == null) {
				return null;
			}

			_networkController = new NetworkController(mainInfo);
		}

		return _networkController;
	}

	@Override
	public Status initialize() {
		_sendTaskQueue = new LinkedList<SendableData>();
		_queueLock = new ReentrantLock();

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
			this.writeErr("Invalid input params");
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

		int statusCode = httpResp.getStatusLine().getStatusCode();
		String statusMsg = httpResp.getStatusLine().getReasonPhrase();
		if (statusCode >= HTTP_ERROR_CODE_THRESHOLD) {
			OLog.err("HttpResponse Error: " + statusCode + " - " + statusMsg);
			return Status.FAILED;
		}

		OLog.info("Sent data to " + url);

		return Status.OK;
	}

	private Status checkInternetConnectivity() {
		if (_parentContext == null) {
			OLog.err("Not attached to an Android activity");
			return Status.FAILED;
		}

		ConnectivityManager cm = (ConnectivityManager) _parentContext
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
		boolean isConnected = (activeNetwork != null && activeNetwork
				.isConnected());

		if (isConnected == false) {
			OLog.warn("No internet connectivity");
			return Status.FAILED;
		}

		return Status.OK;
	}

	private class SendThreadTask implements Runnable {

		@Override
		public void run() {
			OLog.info("Network Controller run task started");
			while (getState() == ControllerState.READY
					&& _sendThreadRunning == true) {
				OLog.info("Network Controller run task loop start");
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
				}
			}

			OLog.err("Network Controller run task finished");
			return;
		}
	}
}
