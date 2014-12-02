package net.oukranos.oreadv1.controller;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.oukranos.oreadv1.interfaces.AbstractController;
import net.oukranos.oreadv1.interfaces.BluetoothEventHandler;
import net.oukranos.oreadv1.types.ControllerState;
import net.oukranos.oreadv1.types.Status;
import net.oukranos.oreadv1.util.OLog;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

public class BluetoothController extends AbstractController {
	private static BluetoothController _bluetoothController = null;
	
	private static final int BLUETOOTH_ENABLE_REQUEST = 1;
	protected String NAME_SECURE = "BluetoothSecure";
	protected UUID MY_UUID_SECURE = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
	protected String NAME_INSECURE = "BluetoothInsecure";
	protected UUID MY_UUID_INSECURE = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
	
	private BluetoothEventHandler _btEventHandler = null;
	private BluetoothAdapter _btAdapter = null;
	private BluetoothListener _btListener = null;
	private ConnectThread _btConnectThread = null;
	private Activity _parentActivity = null;
	private BroadcastReceiver _broadcastReceiver = null;
	
	private HashMap<String, String> _pairedDevices;
	private HashMap<String, String> _discoveredDevices;
	private HashMap<String, BluetoothConnection> _currentConnections;
	
	private BluetoothController(Activity parent, BluetoothEventHandler btEventHandler) {
		_btAdapter = BluetoothAdapter.getDefaultAdapter();
		_parentActivity = parent;
		
		if (_btAdapter == null) {
			this.setState(ControllerState.UNKNOWN);
			this.setLastCmdStatus(Status.FAILED);
			this.setLogData("[E] No Bluetooth Adapter!");
			return;
		}
		
		if (_btAdapter.isEnabled() == false) {
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			_parentActivity.startActivityForResult(enableBtIntent, BLUETOOTH_ENABLE_REQUEST);
		}
		
		_pairedDevices = new HashMap<String, String>();
		_discoveredDevices = new HashMap<String, String>();
		_currentConnections = new HashMap<String, BluetoothConnection>();
		
		this.setState(ControllerState.INACTIVE);
		this.setName("BluetoothController");
		this._btEventHandler = btEventHandler;
		
		return;
	}
	
	public static BluetoothController getInstance(Activity parent, BluetoothEventHandler btEventHandler) {
		if (_bluetoothController == null) {
			_bluetoothController = new BluetoothController(parent, btEventHandler);
		}
		
		return _bluetoothController;
	}

	@Override
	public Status initialize() {
		return (this.start());
	}

	public Status connectToDeviceByName(String deviceName) {
		if (this.getState() != ControllerState.READY) {
			OLog.err("BluetoothController has not been started: " + this.getState().toString());
			return Status.FAILED;
		}
		
		String address = "";
		if (_pairedDevices.containsKey(deviceName)) {
			address = _pairedDevices.get(deviceName);
		} else if (_discoveredDevices.containsKey(deviceName)) {
			address = _discoveredDevices.get(deviceName);
		}
		
		if (address.length() > 0 && _currentConnections.containsKey(address)) {
			return Status.OK;
		}
		
		OLog.info("Found device address: " + address);

		return connectDevice(address);
	}

	public void broadcast(byte[] data) {
		if (this.getState() != ControllerState.ACTIVE) {
			OLog.err("BluetoothController has not been connected");
			return;
		}
		
		for (Map.Entry<String, BluetoothConnection> device : _currentConnections.entrySet()) {
			device.getValue().write(data);
		}
		
		return;
	}

	@Override
	public Status destroy() {
		return (this.stop());
	}
	
	/* Public Utility Functions */
	public ArrayList<String> getDiscoveredDeviceNames() {
		ArrayList<String> devices = new ArrayList<String>();

		for (String key : _discoveredDevices.keySet()) {
			if (key != null) {
				devices.add(key);// key + "->" + discoveredDevices.get(key) +
			}					// "\n";
		}
		return devices;
	}
	
	public ArrayList<String> getPairedDeviceNames() {
		ArrayList<String> devices = new ArrayList<String>();

		_pairedDevices.clear();
		Set<BluetoothDevice> bondedDevices = _btAdapter.getBondedDevices();
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
	
	public void setEventHandler(BluetoothEventHandler btHandler) {
		this._btEventHandler = btHandler;
		return;
	}
	
	/* Private functions */
	private Status start() {
		if ( this.getState() == ControllerState.UNKNOWN ) {
			OLog.warn("BluetoothController has not been initialized");
			return Status.FAILED;
		} else if ( this.getState() != ControllerState.INACTIVE ) {
			OLog.info("BluetoothController already started");
			return Status.ALREADY_STARTED;
		}
		
		// start or re-start
		if (_btListener != null) {
			this.stop();
			this.setState(ControllerState.UNKNOWN);
		}

		_btListener = new BluetoothListener(this, true);
		_btListener.start();
		this.setState(ControllerState.READY);
		
		if (_broadcastReceiver == null) {
			_broadcastReceiver = new BroadcastReceiver() {
				public void onReceive(Context context, Intent intent) {
					String action = intent.getAction();
					if (BluetoothDevice.ACTION_FOUND.equals(action)) {
						BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
						if (device != null) {
							_discoveredDevices.put(device.getName(), device.getAddress());
							System.out.println("New Device Discovered: " + device.getName());
						}
					}
				}
			};
		}
		
		_parentActivity.registerReceiver(_broadcastReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
		_parentActivity.registerReceiver(_broadcastReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));

		return Status.OK;
	}

	private Status stop() {
		if (_btListener != null) {
			OLog.info("Terminating Bluetooth Listener Thread...");
			_btListener.cancel();
			try {
				_btListener.join(1000);
			} catch (InterruptedException e) {
				OLog.warn("_btListener join() interrupted");
			}
		}

		if (_btConnectThread != null) {
			OLog.info("Terminating Bluetooth Connect Thread...");
			_btConnectThread.cancel();
			try {
				_btConnectThread.join(1000);
			} catch (InterruptedException e) {
				OLog.warn("_btConnectThread join() interrupted");
			}
		}
		
		if ( (this.getState() != ControllerState.INACTIVE) ||
				(this.getState() != ControllerState.UNKNOWN) ){
			if (_broadcastReceiver != null) {
				_parentActivity.unregisterReceiver(_broadcastReceiver);
			}
		}

		for (String key : _currentConnections.keySet()) {
			_currentConnections.get(key).cancel();
			try {
				_currentConnections.get(key).join(1000);
			} catch (InterruptedException e) {
				OLog.warn("connection thread join() interrupted");
			}
		}
		
		_currentConnections.clear();	/* Clear active connections */
		_btListener = null;				/* Started by start() */
		_btConnectThread = null; 		/* Started by connectDevice() */
		_broadcastReceiver = null;		/* Initialized by start() */
		
		this.setState(ControllerState.INACTIVE);
		
		return Status.OK;
	}
	
	private BluetoothAdapter getBluetoothAdapter() {
		return _btAdapter;
	}

	private Status connectDevice(String hwAddr) {
		BluetoothDevice device;
		
		boolean isSecure = true; // Defaulting to true

		if ( BluetoothAdapter.checkBluetoothAddress(hwAddr) == false ) {
			OLog.err("Invalid bluetooth hardware address: " + hwAddr);
			return Status.FAILED;
		}
		device = _btAdapter.getRemoteDevice(hwAddr);

		if (_btConnectThread == null) {
			_btConnectThread = new ConnectThread(device, isSecure);
			_btConnectThread.start();
		} else if (_btConnectThread._btDevice.getAddress() != hwAddr) {
			_btConnectThread.cancel();
			_btConnectThread = new ConnectThread(device, isSecure);
			_btConnectThread.start();
		}
		
		return Status.OK;
	}
	
	private Status connectDevice(BluetoothSocket deviceSocket) {
		if (this.getState() != ControllerState.READY) {
			OLog.err("BluetoothController has not been started: " + this.getState().toString());
			return Status.FAILED;
		}

		BluetoothConnection tmp = new BluetoothConnection(this, deviceSocket, _btEventHandler);
		
		if (tmp.isConnected())
			tmp.start();
		else {
			System.out.println("[E] Error trying to connect to "
					+ deviceSocket.getRemoteDevice().getName() + " ("
					+ deviceSocket.getRemoteDevice().getAddress() + ")");
			_btConnectThread = null;
			return Status.FAILED;
		}

		if (tmp != null) {
			if (!_currentConnections.containsKey(deviceSocket.getRemoteDevice().getAddress())) {
				_currentConnections.put(deviceSocket.getRemoteDevice().getAddress(), tmp);
			}
		}
		
		if (_btConnectThread != null) {
			_btConnectThread.cancel();
			_btConnectThread = null;
		}
		
		this.setState(ControllerState.ACTIVE);
		
		return Status.OK;
	}
	
	private Status removeConnection(BluetoothConnection conn){
		if (_currentConnections.containsKey(conn.getAddress())) {
			conn.cancel();
			try {
				conn.join(1000);
			} catch (InterruptedException e) {
				System.out.println("[E] connection join() interrupted. ");
			}
			
			_currentConnections.remove(conn.getAddress());
		}
		return Status.OK;
	}
	
	/* Inner Classes */
	private class BluetoothListener extends Thread {
		private BluetoothController _btController = null;
		private BluetoothAdapter _btAdapter = null;
		private BluetoothServerSocket _btServerSocket = null;
		private boolean _useSecureRFComm = false;
		private boolean _isRunning = false;
		
		@SuppressLint("NewApi")
		public BluetoothListener(BluetoothController controller, boolean useSecureRFComm) {
			BluetoothServerSocket socket = null;
			
			_useSecureRFComm = useSecureRFComm;
			_btController = controller;
			_btAdapter = _btController.getBluetoothAdapter();

			// Create a new listening server socket
			try {
				if (_useSecureRFComm) {
					socket = _btAdapter.listenUsingRfcommWithServiceRecord(
							_btController.NAME_SECURE, _btController.MY_UUID_SECURE);
				} else {
					socket = _btAdapter.listenUsingInsecureRfcommWithServiceRecord(
							_btController.NAME_INSECURE, _btController.MY_UUID_INSECURE);
				}
			} catch (IOException e) {
				System.out.println("[E] Socket Type: " 
									+ (_useSecureRFComm ? "Secure" : "Insecure")
									+ "listen() failed"
									+ e);
			}
			_btServerSocket = socket;
		}
		
		public void run() {
//			System.out.println("Socket Type: " + mSocketType + "BEGIN mAcceptThread"
//					+ this);
//			System.out.println("AcceptThread" + mSocketType);

			BluetoothSocket socket = null;
			if (_btServerSocket == null) {
				return;
			}
			
			_isRunning = true;
			
			while (_isRunning) {
				try {
					socket = _btServerSocket.accept();
					if (socket != null) {
						synchronized (this) {
//							System.out.println("Incoming connection from: "
//									+ socket.getRemoteDevice().getName());
							_btController.connectDevice(socket);
						}
					}
				} catch (IOException e) {
					System.out.println("[E] Socket Type: " 
							+ (_useSecureRFComm ? "Secure" : "Insecure")
							+ "accept() failed"
							+ e);
				}
			}
//			System.out.println("END mAcceptThread, socket Type: " + mSocketType);
		}
		
		public void cancel() {
//			System.out.println("Socket Type" + mSocketType + "cancel " + this);
			_isRunning = false;
			try {
				if (_btServerSocket != null)
					_btServerSocket.close();
			} catch (IOException e) {
				System.out.println("[E] Socket Type: " 
						+ (_useSecureRFComm ? "Secure" : "Insecure")
						+ "close() failed"
						+ e);
			}
		}
	}
	
	private class BluetoothConnection extends Thread {
		
		private BluetoothController _btController = null;
		private BluetoothSocket _btSocket = null;
		private BluetoothEventHandler _btEventHandler = null;
		private InputStream _cxInput = null;
		private OutputStream _cxOutput = null;
		
		private String _remoteAddr = "";
		private boolean _isConnected = false;
		
		public BluetoothConnection(BluetoothController btController, 
				BluetoothSocket socket, BluetoothEventHandler btEventHandler) {
			
			if (socket == null) {
				System.out.println("[E] Invalid socket!");
				return;
			}
			
			System.out.println("create Connection thread to " + socket.getRemoteDevice().getName());
			
			_btController = btController;
			_btSocket = socket;
			
			InputStream tmpIn = null;
			OutputStream tmpOut = null;
			_remoteAddr = socket.getRemoteDevice().getAddress();

			try {
				// socket.connect();
				tmpIn = socket.getInputStream();
				tmpOut = socket.getOutputStream();
			} catch (IOException e) {
				System.out.println("temp sockets not created: " + e.getMessage());
			}

			_cxInput = tmpIn;
			_cxOutput = tmpOut;
			
			_btEventHandler = btEventHandler;
			
			_isConnected = true;
		}

		public String getAddress() {
			return _remoteAddr;
		}

		public String getDeviceName() {
			if (_btSocket == null)
				return "";
			return _btSocket.getRemoteDevice().getName();
		}

		/* (non-Javadoc)
		 * @see java.lang.Thread#run()
		 */
		@SuppressLint("NewApi")
		public void run() {
//			System.out.println("BEGIN mConnectedThread to " + address);
			int bytesInStream = 0;

			// Keep listening to the InputStream while connected
			while (true) {
				try {
					
					bytesInStream = _cxInput.available();
					/* Check if there are bytes available for reading in our input stream */
					if (bytesInStream > 0)
					{
						byte[] buffer = new byte[bytesInStream];
						
						// System.out.println("[KBluetoothConnection] INFO: Data available!");
						
						// Read from the InputStream
						_cxInput.read(buffer);
						
						/* Relay the buffer's contents back upstairs */
						if (_btEventHandler != null) {
							_btEventHandler.onDataReceived(buffer);
						}
					} else {
					// // Send the obtained bytes to the UI Activity
					// mHandler.obtainMessage(BluetoothChat.MESSAGE_READ, bytes, -1,
					// buffer).sendToTarget();
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
		
						}
					}
				} catch (IOException e) {
					_btController.removeConnection(this);
					System.out.println("[E] " + getAddress() + " disconnected" + e.getMessage());
					// notify manager that we've gone belly up
					// connectionLost();
					_isConnected = false;
					break;
				}
			}
		}

		/**
		 * Checks if we are connected.
		 *
		 * @return true, if is connected
		 */
		public boolean isConnected() {
			return _isConnected;
		}

		/**
		 * Write data to the connection
		 *
		 * @param buffer the buffer
		 */
		public void write(byte[] buffer) {
			try {
				// System.out.println("KBTConnection thread writing " + buffer.length
				// + " bytes to " + address);

				_cxOutput.write(buffer);
			} catch (IOException e) {
				System.out.println("[E] " + getAddress() + ": Exception during write"
						+ e.getMessage());
				_btController.removeConnection(this);
			}
		}

		/**
		 * Cancel, close out the resource
		 */
		public void cancel() {
			try {
				_btSocket.close();
			} catch (IOException e) {
				System.out.println("[E] close() of connect socket failed" + e.getMessage());
			}
		}
	}
	
	

	/**
	 * The Class ConnectThread.
	 */
	private class ConnectThread extends Thread {
		
		private BluetoothSocket _btSocket = null;
		private BluetoothDevice _btDevice = null;
		
		private boolean _useSecureRFComm = false;

		/**
		 * Instantiates a new connect thread.
		 *
		 * @param device the device
		 * @param secure the secure
		 */
		@SuppressLint("NewApi")
		public ConnectThread(BluetoothDevice device, boolean secure) {
			BluetoothSocket socket = null;
			
			_btDevice = device;
			_useSecureRFComm = secure;

			// Get a BluetoothSocket for a connection with the
			// given BluetoothDevice
			try {
				if (secure) {
					socket = _btDevice
							.createRfcommSocketToServiceRecord(MY_UUID_SECURE);
				} else {
					socket = _btDevice
							.createInsecureRfcommSocketToServiceRecord(MY_UUID_INSECURE);
				}
			} catch (IOException e) {
				System.out.println("[E] Socket Type: "
						+ (_useSecureRFComm ? "Secure" : "Insecure")
						+ "create() failed" + e);
			}
			_btSocket = socket;
		}

		/* (non-Javadoc)
		 * @see java.lang.Thread#run()
		 */
		public void run() {
			while (_btSocket == null) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
			}
			
			// Always cancel discovery because it will slow down a connection
			_btAdapter.cancelDiscovery();

			// Make a connection to the BluetoothSocket
			try {
				// This is a blocking call and will only return on a
				// successful connection or an exception
				if (_btSocket != null)
					_btSocket.connect();
//				System.out.println("KBTConnect thread connected!");
			} catch (IOException e) {
				// Close the socket
				try {
					_btSocket.close();
				} catch (IOException e2) {
					System.out.println("[E] unable to close() "
							+ (_useSecureRFComm ? "Secure" : "Insecure")
							+ " socket during connection failure" + e2);
				}
				_btConnectThread = null;
				return;
			}

			// Start the connected thread
			connectDevice(_btSocket);// , mmDevice, mSocketType);
		}

		/**
		 * Cancel.
		 */
		public void cancel() {
//			 try {
//				 _btSocket.close();
//			 } catch (IOException e) {
//				 System.out.println("close() of connect " 
//						 + (_useSecureRFComm ? "Secure" : "Insecure") 
//						 + " socket failed" + e);
//			 }
		}

	}
}
