package net.oukranos.oreadv1.controller;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import net.oukranos.oreadv1.interfaces.AbstractController;
import net.oukranos.oreadv1.interfaces.HttpEncodableData;
import net.oukranos.oreadv1.types.ControllerState;
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
	private Activity _parentActivity = null;
	
	private static NetworkController _networkController = null;
	private static final HttpClient _httpClient = new DefaultHttpClient(); 
	
	private NetworkController(Activity parent) {
		this._parentActivity = parent;
	}
	
	public static NetworkController getInstance(Activity parent) {
		if (_networkController == null) {
			_networkController = new NetworkController(parent);
		}
		
		return _networkController;
	}
	
	@Override
	public Status initialize() {
		_sendTaskQueue = new LinkedList<SendableData>();
		_queueLock = new ReentrantLock();
		
		this.setState(ControllerState.READY);
		
		return Status.OK;
	}
	
	public Status start() {
		if ( this.getState() != ControllerState.READY ) {
			OLog.err("Invalid State: " + this.getState());
			return Status.FAILED;
		}

		this.initalizeSendTaskLoop();
		
		/* Run the send task loop */
		_sendTaskLoopThread.run();
		_sendThreadRunning = true;
		
		return Status.OK;
	}
	
	public Status stop() {
		if ( _sendTaskLoop == null && _sendTaskLoopThread == null ) {
			OLog.info("Already stopped");
			return Status.OK;
		}

		this.destroySendTaskLoop();
		
		return Status.OK;
	}

	@Override
	public Status destroy() {
		this.setState(ControllerState.INACTIVE);
		
		this.destroySendTaskLoop();
		
		/* Destroy the lock */
		if (_queueLock != null) {
			_queueLock.unlock();
		}
		
		/* Destroy the Task Queue */
		_sendTaskQueue.clear();
		_sendTaskQueue = null;
		
		return null;
	}
	
	public Status send(String url, HttpEncodableData data) {
		if ((this.getState() != ControllerState.READY) ||
				(this.getState() != ControllerState.BUSY)) {
			/* We can send during busy state too because of the queue */
			OLog.err("Invalid state: " + this.getState());
			return Status.FAILED;
		}
		
		if ( data == null ) {
			OLog.err("Invalid data parameter");
			return Status.FAILED;
		}
		
		if ( url == null || url.isEmpty() ) {
			OLog.err("Invalid url parameter");
			return Status.FAILED;
		}
		
		_queueLock.lock();
		/* Check if we can still handle stuff in the send queue */ 
		if (_sendTaskQueue.size() >= MAX_QUEUE_CAPACITY) {
			_queueLock.unlock();
			return Status.FAILED;
		}
		
		/* Add incoming data to the send task queue */
		_sendTaskQueue.add(new SendableData(url, data));
		_queueLock.unlock();
		
		/* Notify the send thread */
		if ( _sendTaskLoopThread != null ) {
			_sendTaskLoopThread.interrupt();
		}
		
		return Status.OK;
	}
	
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
			return Status.FAILED;
		}
		
		String url = sendableData.getUrl();
		if (url == null || url.isEmpty()) {
			OLog.err("Invalid URL string");
			return Status.FAILED;
		}
		
		HttpPost httpPost = new HttpPost(url);
		httpPost.setEntity(sendableData.getData().encodeDataToHttpEntity());
		HttpResponse httpResp = null;
		try {
			httpResp = _httpClient.execute(httpPost);
		} catch (ClientProtocolException e) {
			OLog.warn("Empty HttpResponse");
		} catch (IOException e) {
			OLog.err("HttpPost execution failed");
			return Status.FAILED;
		}
		if ( httpResp == null) {
			OLog.err("Failed to perform HttpPost");
			return Status.FAILED;
		}
		
		int statusCode = httpResp.getStatusLine().getStatusCode();
		String statusMsg = httpResp.getStatusLine().getReasonPhrase();
		if (statusCode >= HTTP_ERROR_CODE_THRESHOLD) {
			OLog.err("HttpResponse Error: " + statusCode + " - " + statusMsg);
			return Status.FAILED;
		}
		
		return Status.OK;
	}
	
	private Status checkInternetConnectivity() {
		if (_parentActivity == null) {
			OLog.err("Not attached to an Android activity");
			return Status.FAILED;
		}
		
		ConnectivityManager cm = (ConnectivityManager) _parentActivity.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
		boolean isConnected = (activeNetwork != null && activeNetwork.isConnected());
		
		if ( isConnected == false ) {
			OLog.warn("No internet connectivity");
			return Status.FAILED;
		}
		
		return Status.OK;
	}
	
	
	private class SendThreadTask implements Runnable {

		@Override
		public void run() {
			while ( getState() == ControllerState.READY || 
					_sendThreadRunning == true ) {
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					; /* Do nothing */
				}
				
				/* Exit thread if we are not in ready state */
				/*  In theory, we should never be asleep while on busy state,
				 * 	so exit from that too since that might indicate an error */
				if ( getState() != ControllerState.READY || 
						_sendThreadRunning == false ) {
					return;
				}
				
				_queueLock.lock();
				int queueSize = _sendTaskQueue.size();
				_queueLock.unlock();
				
				while ( queueSize > 0 ) {
					if ( checkInternetConnectivity() != Status.OK ) {
						return;
					}
					
					/* Send the data and remove it from the queue */
					_queueLock.lock();
					sendData(_sendTaskQueue.remove());
					_queueLock.unlock();

					/* Re-evaluate the size of the queue */
					_queueLock.lock();
					queueSize = _sendTaskQueue.size();
					_queueLock.unlock();
				}
			}
		}
	}
	
	private class SendableData {
		private String url = "";
		private HttpEncodableData data = null;
		
		public SendableData(String url, HttpEncodableData data) {
			this.url = url;
			this.data = data;
		}
		
		public String getUrl() {
			return this.url;
		}
		
		public HttpEncodableData getData() {
			return this.data;
		}
	}
}
