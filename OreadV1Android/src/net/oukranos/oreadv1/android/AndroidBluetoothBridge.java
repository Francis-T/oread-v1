package net.oukranos.oreadv1.android;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

import net.oukranos.oreadv1.interfaces.BluetoothEventHandler;
import net.oukranos.oreadv1.interfaces.bridge.IBluetoothBridge;
import net.oukranos.oreadv1.types.Status;

public class AndroidBluetoothBridge extends AndroidBridgeImpl implements IBluetoothBridge {
	private final String NAME_SECURE = "BluetoothSecure";
	private final UUID MY_UUID_SECURE = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
	private final String NAME_INSECURE = "BluetoothInsecure";
	private final UUID MY_UUID_INSECURE = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
	
	private static AndroidBluetoothBridge _androidBluetoothBridge = null;
	
	private BluetoothAdapter _bluetoothAdapter = null;
	private BroadcastReceiver _broadcastReceiver = null;

	/* Device list maps */
	private HashMap<String, String> _pairedDevices = null;
	private HashMap<String, String> _discoveredDevices = null;
	private HashMap<String, BluetoothConnection> _currentConnections = null;
	
	private BluetoothEventHandler _eventHandler = null;
	private BluetoothConnectThread _bluetoothConnectThread = null;
	private BluetoothListenerThread _bluetoothListener = null;

	private Lock _connThreadLock = null;
	private Lock _listenThreadLock = null;
	
	private static final long MAX_BLUETOOTH_SOCKET_WAIT = 5000;
	
	private AndroidBluetoothBridge() {
		return;
	}
	
	public static AndroidBluetoothBridge getInstance() {
		if (_androidBluetoothBridge == null) {
			_androidBluetoothBridge =  new AndroidBluetoothBridge();
		}
		
		return _androidBluetoothBridge;
	}

	@Override
	public String getId() {
		return "bluetooth";
	}

	@Override
	public String getPlatform() {
		return "android";
	}
	
	@Override
	public Status initialize(Object initObject) {
		/* Attempt to load the initializer object */
		/*  Note: This method is in AndroidBridgeImpl */
		if (loadInitializer(initObject) != Status.OK) {
			OLog.err("Failed to initialize " + getPlatform() + "." + getId());
			return Status.FAILED;
		}
		
		_bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		
		if ( _bluetoothAdapter == null ) {
			OLog.err("Could not obtain a Bluetooth adapter");
			return Status.FAILED;
		}
		
		/* TODO */
		if ( _bluetoothAdapter.isEnabled() == false ) {
//			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//			_parentContext.startActivity(enableBtIntent);
//			(enableBtIntent, BLUETOOTH_ENABLE_REQUEST); //TODO consider removing?
			return Status.FAILED;
		}

		_connThreadLock = new ReentrantLock();
		_listenThreadLock = new ReentrantLock();
		
		setupDeviceLists();
		
		if (start() != Status.OK) {
			destroy();
			return Status.FAILED;
		}
		
		return Status.OK;
	}

	@Override
	public Status connectDeviceByAddress(String address) {
		if ( BluetoothAdapter.checkBluetoothAddress(address) == false ) {
			OLog.err("Invalid bluetooth hardware address: " + address);
			return Status.FAILED;
		}
		
		if (_bluetoothAdapter == null) {
			OLog.err("Bluetooth adapter unavailable");
			return Status.FAILED;
		}
		
		BluetoothDevice device = _bluetoothAdapter.getRemoteDevice(address);
		boolean isSecure = true; // Defaulting to true
		
		if (device == null) {
			OLog.err("Invalid Bluetooth device address");
			return Status.FAILED;
		}
		
		/* Prevent other threads from manipulating the conn thread 
		 * 	while we are still connecting */
		_connThreadLock.lock();
		
		if (_bluetoothConnectThread != null) {
			if (_bluetoothConnectThread.getDeviceAddress() != address) {
				_bluetoothConnectThread.cancel();
			}
		}

		BluetoothConnectThread connThread = this.getNewConnectThread(address, isSecure);
		if (connThread == null) {
			OLog.err("Failed to obtain a connect thread");
			return Status.FAILED;
		}
		
		_bluetoothConnectThread = connThread;
		_bluetoothConnectThread.start();
		
		/* Block until the connection is successful */
		try {
			_bluetoothConnectThread.join(5000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		_connThreadLock.unlock();
		
		/* Check if the address being connected to is already in the
		 *  current connections list. If not, the connect operation
		 *  was unsuccessful. */
		if (_currentConnections.containsKey(address) == false) {
			if (_bluetoothConnectThread != null) {
				_bluetoothConnectThread.cancel();
			}
			OLog.err("Connect unsuccessful");
			return Status.FAILED;
		}
		
		return Status.OK;
	}

	@Override
	public Status connectDeviceByName(String name) {
		if (_pairedDevices == null) {
			OLog.err("Paired devices list unavailable");
			return Status.FAILED;
		}
		
		if (_discoveredDevices == null) {
			OLog.err("Discovered devices list unavailable");
			return Status.FAILED;
		}
		
		if (_currentConnections == null) {
			OLog.err("Current connection list unavailable");
			return Status.FAILED;
		}
		
		String address = "";
		if (_pairedDevices.containsKey(name)) {
			address = _pairedDevices.get(name);
		} else if (_discoveredDevices.containsKey(name)) {
			address = _discoveredDevices.get(name);
		}
		
		if (address.length() > 0 && _currentConnections.containsKey(address)) {
			return Status.OK;
		}
		
		OLog.info("Found device address: " + address);

		return (this.connectDeviceByAddress(address));
	}

	@Override
	public Status broadcast(byte[] data) {
		if (_currentConnections == null) {
			return Status.FAILED;
		}
		
		if (_currentConnections.isEmpty()) {
			return Status.FAILED;
		}
		
		for (Map.Entry<String, BluetoothConnection> device : _currentConnections.entrySet()) {
			device.getValue().write(data);
		}
		
		return Status.OK;
	}

	@Override
	public Status destroy() {
		if (stop() != Status.OK)
		{
			OLog.err("Failed to destroy BluetoothBridge");
		}
		
		unloadInitializers();
		
		return Status.OK;
	}

	@Override
	public Status setEventHandler(BluetoothEventHandler eventHandler) {
		this._eventHandler = eventHandler;
		return Status.OK;
	}

	/*********************/
	/** Private Methods **/
	/*********************/
	private Status start() {
		/* Prevent other threads from manipulating the listen thread 
		 * 	while we are still setting it up */
		_listenThreadLock.lock();
		
		// start or re-start
		if (_bluetoothListener != null) {
			this.stop();
		}
	
		_bluetoothListener = new BluetoothListenerThread(true);
		_bluetoothListener.start();
		
		/* Release the lock on the listen thread */
		_listenThreadLock.unlock();
		
		if (_broadcastReceiver == null) {
			_broadcastReceiver = new BluetoothBroadcastReceiver();
		}
		
		_context.registerReceiver(_broadcastReceiver, 
				new IntentFilter(BluetoothDevice.ACTION_FOUND));
		_context.registerReceiver(_broadcastReceiver, 
				new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));
		
		this.getPairedDeviceNames();
		
		return Status.OK;
	}
	
	private Status stop() {
		if (_broadcastReceiver != null) {
			_context.unregisterReceiver(_broadcastReceiver);
			_broadcastReceiver = null;
		}

		/* Prevent other threads from manipulating the listen thread 
		 * 	while we are closing it */
		_listenThreadLock.lock();
		if (_bluetoothListener != null) {
			OLog.info("Terminating Bluetooth Listener Thread...");
			_bluetoothListener.cancel();
			try {
				_bluetoothListener.join(1000);
			} catch (InterruptedException e) {
				OLog.warn("_bluetoothListener join() interrupted");
			}
		}
		/* Release the lock on the listen thread */
		_listenThreadLock.unlock();

		/* Prevent other threads from manipulating the conn thread 
		 * 	while we are closing it */
		_connThreadLock.lock();
		if (_bluetoothConnectThread != null) {
			OLog.info("Terminating Bluetooth Connect Thread...");
			_bluetoothConnectThread.cancel();
			try {
				_bluetoothConnectThread.join(1000);
			} catch (InterruptedException e) {
				OLog.warn("_btConnectThread join() interrupted");
			}
		}
		/* Release the lock on the listen thread */
		_connThreadLock.unlock();
	
		if (_currentConnections != null) {
			for (String key : _currentConnections.keySet()) {
				_currentConnections.get(key).cancel();
				if (_currentConnections.get(key) != null) {
					try {
						_currentConnections.get(key).join(1000);
					} catch (InterruptedException e) {
						OLog.warn("connection thread join() interrupted");
					}
				}
			}
			
			_currentConnections.clear();	/* Clear active connections */
		}
		
		_bluetoothListener = null;		/* Started by start() */
		_bluetoothConnectThread = null; /* Started by connectDevice() */
		_broadcastReceiver = null;		/* Initialized by start() */
		_eventHandler = null;			/* Initialized by setEventHandler() */
		
		clearDeviceLists();
		
		return Status.OK;
	}
	
	private BluetoothConnectThread getNewConnectThread(String deviceAddr, boolean useSecureRfComm) {
		/* If a connection thread already exists */
		if (_bluetoothConnectThread != null) {
			OLog.err("BluetoothConnectThread already exists");
			return null;
		}
		
		if ( BluetoothAdapter.checkBluetoothAddress(deviceAddr) == false ) {
			OLog.err("Invalid bluetooth hardware address: " + deviceAddr);
			return null;
		}
		
		BluetoothDevice device = _bluetoothAdapter.getRemoteDevice(deviceAddr);
		if (device == null){
			OLog.err("Invalid remote device address: " + deviceAddr);
			return null;
		}
		
		return new BluetoothConnectThread(device, useSecureRfComm);
	}
	
	private Status connectDevice(BluetoothSocket socket) {
		BluetoothConnection bluetoothConn = new BluetoothConnection(socket);
		
		if ( bluetoothConn.isConnected() ) {
			bluetoothConn.start();
		} else {
			OLog.err("Error trying to connect to "
					+ socket.getRemoteDevice().getName() + "/"
					+ socket.getRemoteDevice().getAddress() );
			_bluetoothConnectThread = null;
			return Status.FAILED;
		}

		if (bluetoothConn != null) {
			BluetoothDevice device = socket.getRemoteDevice();
			String deviceAddr = device.getAddress();
			if (!_currentConnections.containsKey(deviceAddr)) {
				_currentConnections.put(deviceAddr, bluetoothConn);
			}
		}
		
		/* Cancel the existing connect thread */
		if (_bluetoothConnectThread != null) {
			_bluetoothConnectThread.cancel();
			_bluetoothConnectThread = null;
		}
		
		OLog.info("Bluetooth connected!");
		return Status.OK;
	}
	
	private Status removeConnection(BluetoothConnection conn){
		if (conn == null) {
			OLog.err("Invalid input parameter/s" +
					" in AndroidBluetoothBridge.removeConnection()");
			return Status.FAILED;
		}
		
		if (_currentConnections == null) {
			return Status.FAILED;
		}
		
		if (_currentConnections.isEmpty()) {
			return Status.FAILED;
		}
		
		if (_currentConnections.containsKey(conn.getDeviceAddress())) {
//			conn.cancel();
//			try {
//				conn.join(1000);
//			} catch (InterruptedException e) {
//				System.out.println("[E] connection join() interrupted. ");
//			}
			
			_currentConnections.remove(conn.getDeviceAddress());
		}
		
		return Status.OK;
	}

	/********************************************************/
	/** Private Utility Methods for Accessing Device Lists **/
	/********************************************************/
	public ArrayList<String> getDiscoveredDeviceNames() {
		ArrayList<String> devices = new ArrayList<String>();

		for (String key : _discoveredDevices.keySet()) {
			if (key != null) {
				devices.add(key);
			}
		}
		return devices;
	}
	
	public ArrayList<String> getPairedDeviceNames() {
		ArrayList<String> devices = new ArrayList<String>();

		_pairedDevices.clear();
		Set<BluetoothDevice> bondedDevices = _bluetoothAdapter.getBondedDevices();
		if (bondedDevices.size() > 0) {
			for (BluetoothDevice device : bondedDevices) {
				_pairedDevices.put(device.getName(), device.getAddress());
				devices.add(device.getName());
			}
		}
		return devices;
	}

	public ArrayList<String> getConnectedDeviceNames() {
		ArrayList<String> devices = new ArrayList<String>();
		Set<String> connectedDevices = _currentConnections.keySet();

		if (connectedDevices.size() > 0) {
			for (String device : connectedDevices) {
				BluetoothConnection c = _currentConnections.get(device);
				devices.add(c.getDeviceName() + "(" + device + ")");
			}
		}
		return devices;
	}
	
	private void clearDeviceLists() {
		
		if (_pairedDevices != null) {
			if (_pairedDevices.isEmpty() == false) {
				_pairedDevices.clear();
			}
			_pairedDevices = null;
		}
		
		if (_discoveredDevices != null) {
			if (_discoveredDevices.isEmpty() == false) {
				_discoveredDevices.clear();
			}
			_discoveredDevices = null;
		}
		
		if (_currentConnections != null) {
			if (_currentConnections.isEmpty() == false) {
				_currentConnections.clear();
			}
			_currentConnections = null;
		}
		
		return;
	}
	
	private void setupDeviceLists() {
		_pairedDevices = new HashMap<String, String>();
		_discoveredDevices = new HashMap<String, String>();
		_currentConnections = new HashMap<String, BluetoothConnection>();
		
		return;
	}
	/***************************/
	/** Private Inner Classes **/
	/***************************/
	private class BluetoothBroadcastReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (BluetoothDevice.ACTION_FOUND.equals(action)) {
				BluetoothDevice device = intent
						.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				if (device != null) {
					_discoveredDevices.put(device.getName(), 
							device.getAddress());
					OLog.info("New Device Discovered: " + 
							device.getName());
				}
			}
		}
		
	}
	
	private class BluetoothConnection extends Thread {
		private BluetoothSocket _socket = null;
		private InputStream _inputStream = null;
		private OutputStream _outputStream = null;

		private String _name = "";
		private String _remoteAddress = "";
		private boolean _isConnected = false;
		
		public BluetoothConnection(BluetoothSocket socket) {
			if (socket == null) {
				OLog.err("Invalid bluetooth connection socket");
				return;
			}

			_socket = socket;
			_name = socket.getRemoteDevice().getName();
			_remoteAddress = socket.getRemoteDevice().getAddress();
			
			try {
				_inputStream = socket.getInputStream();
			} catch (IOException e) {
				_inputStream = null;
				OLog.err("Could not get bluetooth conn socket input stream");
			}

			try {
				_outputStream = socket.getOutputStream();
			} catch (IOException e) {
				_outputStream = null;
				OLog.err("Could not get bluetooth conn socket output stream");
			}
			
			_isConnected = true;
			
			return;
		}
		
		public String getDeviceAddress() {
			return this._remoteAddress;
		}
		
		public String getDeviceName() {
			return this._name;
		}
		
		public boolean isConnected() {
			return this._isConnected;
		}
		
		public void run() {
			OLog.info("Connection to " 
						+ _name + "/" 
						+ _remoteAddress + " "
						+ "started.");
			try {
				performCyclicTask();
			} catch (Exception e) {
				OLog.err("Failed to broadcast data to " 
						+ _name + "/" 
						+ _remoteAddress + "!");
				OLog.err("Exception encountered: " + e.getMessage());
				OLog.stackTrace(e);
			}

			OLog.info("Connection to " 
						+ _name + "/" 
						+ _remoteAddress + " "
						+ "ended.");
			return;
		}
		
		public void write(byte[] buffer) {
			try {
				if (performWrite(buffer) != Status.OK) {
					OLog.err("Failed to broadcast data to " 
								+ _name + "/" 
								+ _remoteAddress + "!");
				}
			} catch (Exception e) {
				OLog.err("Failed to broadcast data to " 
						+ _name + "/" 
						+ _remoteAddress + "!");
				OLog.err("Exception encountered: " + e.getMessage());
				OLog.stackTrace(e);
			}
			
			return;
		}
		
		public void cancel() {
			try {
				if (performCancel() != Status.OK) {
					OLog.err("Failed to cancel connection to " 
							+ _name + "/" 
							+ _remoteAddress + "!");
				}
			} catch (Exception e) {
				OLog.err("Failed to cancel connection to " 
						+ _name + "/" 
						+ _remoteAddress + "!");
				OLog.err("Exception encountered: " + e.getMessage());
				OLog.stackTrace(e);
			}
			
			return;
		}
		
		private void performCyclicTask() {
			int readableBytes = 0;
			if ( (_inputStream == null) || (_outputStream == null) ) {
				OLog.err("No streams available for this BluetoothConnection");
				return;
			}
			
			while (_isConnected) {
				try {
					readableBytes = _inputStream.available();
				} catch (IOException e) {
					OLog.err("Encountered an IOEXCEPTION upon checking stream");
					OLog.stackTrace(e);
					break;
				}
				
				if (readableBytes > 0) {
					byte[] buffer = new byte[readableBytes];
					
					try {
						_inputStream.read(buffer);
					} catch (IOException e) {
						OLog.err("Encountered an IOEXCEPTION upon reading from stream");
						OLog.stackTrace(e);
					}
					
					/* Notify the BluetoothController here */
					if (_eventHandler != null) {
						OLog.info("Notifying handlers from AndroidBluetoothBridge...");
						_eventHandler.onDataReceived(buffer);
					} else {
						OLog.warn("AndroidBluetoothBridge event handler not set");
					}
					
				} else {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						OLog.info("BluetoothConnection thread interrupted");
					}
				}
			}
			
			_isConnected = false;
			detachConnection();
			OLog.info("Disconnected from " + this.getDeviceName() + "/" +this.getDeviceAddress());
			
			return;
		}
		
		private Status performWrite(byte[] buffer) {
			if (buffer == null) {
				OLog.err("Invalid parameters");
				return Status.FAILED;
			}
			
			if (_outputStream == null) {
				OLog.err("Output stream unavailable");
				return Status.FAILED;
			}
			
			try {
				_outputStream.write(buffer);
			} catch (IOException e) {
				OLog.err("Encountered an IOEXCEPTION upon writing to stream");
				detachConnection();
			}
			
			return Status.OK;
		}
		
		private Status performCancel() {
			if (_socket == null) {
				OLog.err("Invalid BluetoothConnection socket");
				_isConnected = false;
				detachConnection();
				return Status.FAILED;
			}
			
			try {
				_socket.close();
			} catch (IOException e) {
				OLog.err("Failed to close BluetoothConnection socket");
				OLog.stackTrace(e);
			}
			
			_isConnected = false;
			detachConnection();
			
			OLog.info("Bluetooth Connection canceled");
			
			return Status.OK;
		}
		
		private void detachConnection() {
			removeConnection(this);
			return;
		}
		
	}
	
	private class BluetoothListenerThread extends Thread {
		private BluetoothServerSocket _bluetoothServerSocket = null;
		private boolean _useSecureRfComm = false;
		private boolean _isRunning = false;
		
		public BluetoothListenerThread(boolean useSecureRfComm) {
			if (_bluetoothAdapter == null) {
				return;
			}
			
			_bluetoothServerSocket = getServerSocket(useSecureRfComm);
			if (_bluetoothServerSocket == null) {
				return;
			}
				
			_useSecureRfComm = useSecureRfComm;
			
			return;
		}

		public void run() {
			OLog.info("BluetoothListenerThread started.");

			try {
				performTask();
			} catch (Exception e) {
				OLog.err("Exception occurred: " + e.getMessage());
				OLog.stackTrace(e);
			}
			
			OLog.info("BluetoothListenerThread finished.");
			return;
		}

		public void cancel() {
			OLog.info("Closing BluetoothListenerThread...");
			_isRunning = false;
			
			try {
				if (_bluetoothServerSocket != null) {
					_bluetoothServerSocket.close();
				}
				_bluetoothServerSocket = null;
			} catch (IOException e) {
				OLog.err("Failed to close the Bluetooth server socket: " +
						"type=" + (_useSecureRfComm ? "Secure" : "Insecure"));
			}
			
			OLog.info("BluetoothListenerThread closed.");
			return;
		}
		
		/** Private Methods **/
		private void performTask() {
			BluetoothSocket connSocket = null;
			_isRunning = false;
			
			while (_isRunning) {
				if (_bluetoothServerSocket == null) {
					OLog.err("No server socket found");
					this.cancel();
					return;
				}
				
				try {
					connSocket = _bluetoothServerSocket.accept();
					if (connSocket != null) {
						synchronized (this) {
							OLog.info("Incoming connection from: " + 
										connSocket.getRemoteDevice().getName());
							
							/* Attempt to accept the incoming connection */
							connectDevice(connSocket);
						}
					}
				} catch (IOException e) {
					OLog.err("Failed to accept an incoming Bluetooth socket connection: " +
							"type=" + (_useSecureRfComm ? "Secure" : "Insecure"));
					this.cancel();
				}
			}
			
			return;
		}
		
		private BluetoothServerSocket getServerSocket(boolean useSecureRfComm) {
			BluetoothServerSocket tempSocket = null;
			
			// Create a new listening server socket
			try {
				if (useSecureRfComm) {
					tempSocket = _bluetoothAdapter.listenUsingRfcommWithServiceRecord(
							NAME_SECURE, MY_UUID_SECURE);
				} else {
					tempSocket = _bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(
							NAME_INSECURE, MY_UUID_INSECURE);
				}
			} catch (IOException e) {
				OLog.err("Failed to create Bluetooth Listening Socket: " +
						"type=" + (useSecureRfComm ? "Secure" : "Insecure"));
			}
			
			return tempSocket;
		}
	}
	
	private class BluetoothConnectThread extends Thread {
		private BluetoothSocket _bluetoothSocket = null;
		private BluetoothDevice _bluetoothDevice = null;
		private String _deviceAddress = "";
		private boolean _useSecureRFComm = false;
		
		public BluetoothConnectThread(BluetoothDevice device, boolean secure) {
			BluetoothSocket socket = null;
			
			_bluetoothDevice = device;
			_useSecureRFComm = secure;

			// Get a BluetoothSocket for a connection with the
			// given BluetoothDevice
			try {
				if (secure) {
					socket = _bluetoothDevice
							.createRfcommSocketToServiceRecord(MY_UUID_SECURE);
				} else {
					socket = _bluetoothDevice
							.createInsecureRfcommSocketToServiceRecord(MY_UUID_INSECURE);
				}
			} catch (IOException e) {
				OLog.err("Failed to create Bluetooth Connect Thread: " +
						"type=" + (_useSecureRFComm ? "Secure" : "Insecure"));
			}
			_bluetoothSocket = socket;
			_deviceAddress = device.getAddress();
			
			OLog.info("ConnectThread created");
			return;
		}
		
		public void run() {
			OLog.info("ConnectThread Started.");
			
			try {
				performTask();
			} catch (Exception e) {
				OLog.err("Exception occurred: " + e.getMessage());
				OLog.stackTrace(e);
			}
			
			OLog.info("ConnectThread finished.");
		}
		
		public void cancel() {
			/* TODO Do not close the socket here yet since you will still use it! */
//			if (_bluetoothSocket == null) {
//				return;
//			}
//			
//			try {
//				_bluetoothSocket.close();
//			} catch (IOException e) {
//				OLog.err("Failed to close Bluetooth Connect Thread: " +
//						"type=" + (_useSecureRFComm ? "Secure" : "Insecure"));
//			}
		}
		
		private void performTask() {
			waitForBluetoothSocket(MAX_BLUETOOTH_SOCKET_WAIT);
			
			// Always cancel discovery because it will slow down a connection
			_bluetoothAdapter.cancelDiscovery();
	
			// Make a connection to the BluetoothSocket
			try {
				// This is a blocking call and will only return on a
				// successful connection or an exception
				if (_bluetoothSocket != null) {
					_bluetoothSocket.connect();
				}
			} catch (IOException connIOException) {
				// Close the socket
				try {
					if (_bluetoothSocket != null) {
						_bluetoothSocket.close();
					}
				} catch (IOException closeIOException) {
					OLog.err("Failed to close Bluetooth Connect Thread " +
							"due to an IO Exception: " + 
							closeIOException.getMessage());
					OLog.stackTrace(closeIOException);
				}
				
				OLog.warn("Failed to connect to Bluetooth socket due to an IOException");
				OLog.stackTrace(connIOException);
				
				_bluetoothConnectThread = null;
				return;
			}
	
			// Start the connected thread
			connectDevice(_bluetoothSocket);
			
			return;
		}
		
		private String getDeviceAddress() {
			return _deviceAddress;
		}
		
		private void waitForBluetoothSocket(long timeout) {
			long waitStart = System.currentTimeMillis();
			long elapsedTime = 0;
			
			while ( (_bluetoothSocket == null) &&
					(elapsedTime < timeout) ) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					OLog.warn("Wait for BluetoothSocket interrupted: ");
				}
				
				elapsedTime = (System.currentTimeMillis() - waitStart);
				if (elapsedTime > timeout) {
					break;
				}
			}
			
			if (_bluetoothSocket == null) {
				OLog.err("BluetoothSocket unavailable");
			}
			
			return;
		}
		
	}
}
