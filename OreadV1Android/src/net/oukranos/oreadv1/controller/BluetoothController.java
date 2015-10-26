package net.oukranos.oreadv1.controller;

import java.util.ArrayList;
import java.util.List;

import net.oukranos.oreadv1.interfaces.AbstractController;
import net.oukranos.oreadv1.interfaces.BluetoothEventHandler;
import net.oukranos.oreadv1.interfaces.bridge.IBluetoothBridge;
import net.oukranos.oreadv1.types.ControllerState;
import net.oukranos.oreadv1.types.ControllerStatus;
import net.oukranos.oreadv1.types.MainControllerInfo;
import net.oukranos.oreadv1.types.Status;

public class BluetoothController extends AbstractController implements BluetoothEventHandler {
	private static BluetoothController _bluetoothController = null;

	// private static final int BLUETOOTH_ENABLE_REQUEST = 1; // TODO consider
	// removing?
	private IBluetoothBridge _btBridge = null;
	private List<BluetoothEventHandler> _btEventHandlers = null;

	/*************************/
	/** Initializer Methods **/
	/*************************/
	private BluetoothController() {
		setState(ControllerState.UNKNOWN);
		setType("comm");
		setName("bluetooth");
		_btEventHandlers = new ArrayList<BluetoothEventHandler>();

		return;
	}

	public static BluetoothController getInstance(MainControllerInfo mainInfo) {
		if (mainInfo == null) {
			OLog.err("Invalid input parameter/s" +
					" in BluetoothController.getInstance()");
			return null;
		}

		if (mainInfo.getContext() == null) {
			OLog.err("Context object uninitialized or unavailable");
			return null;
		}

		if (_bluetoothController == null) {
			_bluetoothController = new BluetoothController();
		}
		
		_bluetoothController._mainInfo = mainInfo;

		return _bluetoothController;
	}

	/********************************/
	/** AbstractController Methods **/
	/********************************/
	@Override
	public Status initialize(Object initializer) {
		
		/* Check the controller state prior to initialize */
		ControllerState state = getState();
		if (state != ControllerState.UNKNOWN) {
			return writeErr(toString() 
					+ " state is invalid for initialize(): " 
					+ state.toString());
		}
		
		/* Initialize bluetooth bridge */
		_btBridge = getBluetoothBridge();
		if (_btBridge == null) {
			return Status.FAILED;
		}

		if (_btBridge.setEventHandler(this) != Status.OK) {
			writeErr("Failed to set Bluetooth bridge event handler");
			return Status.FAILED;
		}
		
		/* Set state to INACTIVE */
		setState(ControllerState.INACTIVE);

		return writeInfo("BluetoothController initialized");
	}

	@Override
	public Status start() {
		
		/* Check the controller state prior to start */
		ControllerState state = getState();
		if ((state == ControllerState.ACTIVE) || 
				(state == ControllerState.READY)) {
			return writeInfo(toString() 
					+ " has already been started: " 
					+ state.toString());
		}
		
		
		if (state != ControllerState.INACTIVE) {
			return writeErr(toString() 
					+ " state is invalid for start(): " 
					+ state.toString());
		}
		
		/* Set ControllerState to READY */
		setState(ControllerState.READY);
		
		return writeInfo("BluetoothController started");
	}

	@Override
	public ControllerStatus performCommand(String cmdStr, String paramStr) {
		/* Check the command string */
		if (verifyCommand(cmdStr) != Status.OK) {
			return getControllerStatus();
		}

		/* Extract the command only */
		String shortCmdStr = extractCommand(cmdStr);
		if (shortCmdStr == null) {
			return getControllerStatus();
		}

		/* Check which command to perform */
		if (shortCmdStr.equals("connectByName") == true) {
			if (paramStr == null) {
				writeErr("Invalid parameter string");
				return getControllerStatus();
			}
			connectToDeviceByName(paramStr);
			
		} else if (shortCmdStr.equals("connectByAddr") == true) {
			if (paramStr == null) {
				writeErr("Invalid parameter string");
				return getControllerStatus();
			}
			
			connectToDeviceByAddr(paramStr);
		} else if (shortCmdStr.equals("send") == true) {
			if (paramStr == null) {
				writeErr("Invalid parameter string");
				return getControllerStatus();
			}
			broadcast(paramStr.getBytes());
			
		} else if (shortCmdStr.equals("start") == true) {
			start();
			
		} else if (shortCmdStr.equals("stop") == true) {
			stop();
			
		} else {
			writeErr("Unknown or invalid command: " + shortCmdStr);
		}

		return getControllerStatus();
	}

	@Override
	public Status stop() {

		/* Check the controller state prior to start */
		ControllerState state = getState();
		if ((state != ControllerState.READY) &&
				(state != ControllerState.ACTIVE) &&
				(state != ControllerState.BUSY) ){
			return writeErr(toString() 
					+ " state is invalid for stop(): " 
					+ state.toString());
		}
		
		setState(ControllerState.INACTIVE);
		return writeInfo("BluetoothController stopped");
	}

	@Override
	public Status destroy() {
		if (_btBridge == null) {
			setState(ControllerState.UNKNOWN);
			return Status.OK;
		}

		/* Destroy the bluetooth bridge */
		if (_btBridge.destroy() != Status.OK) {
			writeErr("Failed to destroy Bluetooth bridge");
		}

		setState(ControllerState.UNKNOWN);

		return writeInfo("BluetoothController destroyed");
	}

	/********************/
	/** Public METHODS **/
	/********************/
	public Status connectToDeviceByName(String deviceName) {
		/* Check the controller state prior to start */
		ControllerState state = getState();
		if ((state == ControllerState.ACTIVE) ||
				(state == ControllerState.BUSY) ){
			return writeInfo(toString() 
					+ " is already connected to a device");
		}
		
		if (state != ControllerState.READY) {
			return writeErr(toString() 
								+ " state is invalid for" 
								+ " connecting to a device by name: " 
								+ state.toString());
		}
		
		if (_btBridge == null) {
			return writeErr("Bluetooth bridge unavailable."
								+ "Cannot connect to a device by name.");
		}

		try {
			if (_btBridge.connectDeviceByName(deviceName) != Status.OK) {
				return writeErr("Failed to connect to device: " + deviceName);
			}
		} catch (Exception e) {
			return writeErr("Failed to connect to device: " + deviceName + " (exception occurred: " + e.getMessage() + ")");
		}

		setState(ControllerState.ACTIVE);
		
		return writeInfo("Device connected by name: " + deviceName);
	}

	public Status connectToDeviceByAddr(String deviceAddr) {
		/* Check the controller state prior to start */
		ControllerState state = getState();
		if ((state == ControllerState.ACTIVE) ||
				(state == ControllerState.BUSY) ){
			return writeInfo(toString() 
					+ " is already connected to a device");
		}
		
		if (state != ControllerState.READY) {
			return writeErr(toString() 
					+ " state is invalid for" 
					+ " connecting to a device by addr: " 
					+ state.toString());
		}

		if (_btBridge == null) {
			return writeErr("Bluetooth bridge unavailable"
					+ "Cannot connect to a device by name.");
		}

		if (_btBridge.connectDeviceByAddress(deviceAddr) != Status.OK) {
			return writeErr("Failed to connect to address: " + deviceAddr);
		}

		setState(ControllerState.ACTIVE);
		
		return writeInfo("Device connected by address: " + deviceAddr);
	}

	public Status broadcast(byte[] data) {
		/* Check the controller state prior to start */
		ControllerState state = getState();
		if (state != ControllerState.ACTIVE) {
			return writeErr(toString() 
					+ " state is invalid for broadcast: " 
					+ state.toString());
		}

		if (_btBridge == null) {
			return writeErr("Bluetooth bridge unavailable"
					+ "Cannot connect to a device by addr.");
		}

		setState(ControllerState.BUSY);
		if (_btBridge.broadcast(data) != Status.OK) {
			setState(ControllerState.ACTIVE);
			return writeErr("Bluetooth broadcast failed");
		}
		setState(ControllerState.ACTIVE);
		
		return writeInfo("Bluetooth broadcast successful: " 
				+ new String(data));
	}

	/******************************/
	/** Public Utility Functions **/
	/******************************/
	public void registerEventHandler(BluetoothEventHandler btHandler) {
		if (_btEventHandlers.contains(btHandler)) {
			OLog.warn("Handler already registered");
			return;
		}

		OLog.info("Registered event handler: " + btHandler.getClass());
		_btEventHandlers.add(btHandler);

		return;
	}

	public void unregisterEventHandler(BluetoothEventHandler btHandler) {
		if (!_btEventHandlers.contains(btHandler)) {
			OLog.warn("Handler not yet registered");
			return;
		}

		OLog.info("Unregistered event handler: " + btHandler.getClass());
		_btEventHandlers.remove(btHandler);

		return;
	}

	@Override
	public void onDataReceived(byte[] data) {
		if (_btEventHandlers == null) {
			return;
		}

		if (_btEventHandlers.isEmpty()) {
			return;
		}

		OLog.info("Notifying handlers from BluetoothController...");
		for (BluetoothEventHandler handler : _btEventHandlers) {
			handler.onDataReceived(data);
		}

		return;
	}

	/*********************/
	/** Private METHODS **/
	/*********************/
	private IBluetoothBridge getBluetoothBridge() {
		IBluetoothBridge bluetoothBridge 
			= (IBluetoothBridge) _mainInfo
				.getFeature("bluetooth");
		if (bluetoothBridge == null) {
			return bluetoothBridge;
		}
		
		if ( bluetoothBridge.isReady() == false ) {
			if (bluetoothBridge.initialize(_mainInfo) != Status.OK) {
				bluetoothBridge = null;
			}
		}
		
		return bluetoothBridge;
	}
}
