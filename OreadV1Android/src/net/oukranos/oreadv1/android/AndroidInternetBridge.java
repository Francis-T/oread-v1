package net.oukranos.oreadv1.android;

import java.io.IOException;

import net.oukranos.oreadv1.interfaces.ConnectivityBridgeIntf;
import net.oukranos.oreadv1.interfaces.InternetBridgeIntf;
import net.oukranos.oreadv1.types.SendableData;
import net.oukranos.oreadv1.types.Status;
import net.oukranos.oreadv1.util.OreadLogger;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import android.content.Context;

public class AndroidInternetBridge implements InternetBridgeIntf {
	/* Get an instance of the OreadLogger class to handle logging */
	private static final OreadLogger OLog = OreadLogger.getInstance();
	
	private static final int HTTP_ERROR_CODE_THRESHOLD = 300;
	
	private static AndroidInternetBridge _androidInternetBridge = null;
	private boolean _sendThreadRunning = false;
	
	private Context _context = null;
	private HttpResponse _lastHttpResponse = null;
	private static final HttpClient _httpClient = new DefaultHttpClient();

	private AndroidInternetBridge() {
		return;
	}
	
	public static AndroidInternetBridge getInstance() {
		if (_androidInternetBridge == null) {
			_androidInternetBridge = new AndroidInternetBridge();
		}
		return _androidInternetBridge;
	}
	
	@Override
	public Status initialize(Object initObject) {
//		if (_httpClient == null) {
//			_httpClient = new DefaultHttpClient();
//		}
		
		if (_context == null) {
			_context = (Context) initObject;
		}
		
		return null;
	}

	private SendThreadTask _sendTask = null;
	private Thread _sendTaskThread = null;
	
	@Override
	public Status send(SendableData sendableData) {
		if (_context == null) {
			OLog.err("Not attached to an Android activity");
			return Status.FAILED;
		}
		
		if (_httpClient == null) {
			OLog.err("HttpClient is null");
			return Status.FAILED;
		}

		if (sendableData == null) {
			OLog.err("SendableData is NULL");
			return Status.FAILED;
		}
		

		if (_sendTask == null) {
			_sendTask = new SendThreadTask(sendableData);
		}

		if (_sendTaskThread == null) {
			_sendTaskThread = new Thread(_sendTask);
		}
		
		/* Start the send task thread and wait for the results */
		_sendTaskThread.start();
		_sendThreadRunning = true;
		
		try {
			_sendTaskThread.join(30000);
		} catch (InterruptedException e) {
			OLog.warn("Send thread timed out");
		}

		_sendThreadRunning = false;
		_sendTask = null;
		_sendTaskThread = null;

		return Status.OK;
	}
	
	@Override
	public String getResponse() {
		if (_context == null) {
			OLog.err("Not attached to an Android activity");
			return "";
		}
		
		if (_httpClient == null) {
			OLog.err("HttpClient is null");
			return "";
		}
		
		if (this._lastHttpResponse == null) {
			return "";
		}
		
		String response = ""; 

		try {
			response = EntityUtils.toString(this._lastHttpResponse.getEntity());
		} catch (Exception e) {
			OLog.err("Failed to parse response: " + e.getMessage());
			response = "";
		}
		
		return response;
	}

	@Override
	public Status destroy() {
//		_httpClient = null;
		_context = null;
		
		return null;
	}
	/** Private Methods **/
	private Status sendData(SendableData sendableData) {
		if (_context == null) {
			OLog.err("Not attached to an Android activity");
			return Status.FAILED;
		}
		
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
		
		OLog.info("Sending data to " + url);

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
		
		/* Save this as the last httpResponse */
		_lastHttpResponse = httpResp;

//		String response = "";
//		try {
//			response = EntityUtils.toString(this._lastHttpResponse.getEntity());
//		} catch (Exception e) {
//			OLog.err("Failed to parse response: " + e.getMessage());
//			response = "";
//		}
//		
//		OLog.info("Response: " + response);

		int statusCode = httpResp.getStatusLine().getStatusCode();
		String statusMsg = httpResp.getStatusLine().getReasonPhrase();
		if (statusCode >= HTTP_ERROR_CODE_THRESHOLD) {
			OLog.err("HttpResponse Error: " + statusCode + " - " + statusMsg);
			return Status.FAILED;
		}

		OLog.info("Sent data to " + url);

		return Status.OK;
	}
	
	/** Inner Classes **/
	private class SendThreadTask implements Runnable {
		private SendableData _data = null;
		
		public SendThreadTask(SendableData data) {
			_data = data;
			return;
		}

		@Override
		public void run() {
			OLog.info("Send task started");

			/* Check our connectivity */
			ConnectivityBridgeIntf connBridge = null;
			connBridge = AndroidConnectivityBridge.getInstance();
			connBridge.initialize((Object)_context);
			
			if (connBridge.isConnected() == false) {
				OLog.err("Not connected");
				
				/* Discard the data */
				_data = null;
				return;
			}
			
			/* Send the data */
			sendData(_data);

			OLog.err("Send task finished");
			return;
		}
	}
}
