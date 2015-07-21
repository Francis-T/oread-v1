package net.oukranos.oreadv1.controller;

import java.util.ArrayList;
import java.util.List;

import net.oukranos.oreadv1.android.AndroidBluetoothBridge;
import net.oukranos.oreadv1.interfaces.AbstractController;
import net.oukranos.oreadv1.interfaces.BluetoothBridgeIntf;
import net.oukranos.oreadv1.interfaces.BluetoothEventHandler;
import net.oukranos.oreadv1.types.ControllerState;
import net.oukranos.oreadv1.types.ControllerStatus;
import net.oukranos.oreadv1.types.MainControllerInfo;
import net.oukranos.oreadv1.types.Status;
import net.oukranos.oreadv1.util.OreadLogger;

public class BluetoothController extends AbstractController implements
		BluetoothEventHandler {
	/* Get an instance of the OreadLogger class to handle logging */
	private static final OreadLogger OLog = OreadLogger.getInstance();
	
	private static BluetoothController _bluetoothController = null;

	private MainControllerInfo _mainInfo = null;

	// private static final int BLUETOOTH_ENABLE_REQUEST = 1; // TODO consider
	// removing?
	private BluetoothBridgeIntf _btBridge = null;
	private List<BluetoothEventHandler> _btEventHandlers = null;

	/*************************/
	/** Initializer Methods **/
	/*************************/
	private BluetoothController(MainControllerInfo mainInfo) {
		this.setState(ControllerState.INACTIVE);
		this.setType("comm");
		this.setName("bluetooth");
		this._btEventHandlers = new ArrayList<BluetoothEventHandler>();

		/* Set the main info reference */
		this._mainInfo = mainInfo;

		return;
	}

	public static BluetoothController getInstance(MainControllerInfo mainInfo) {
		if (mainInfo == null) {
			OLog.err("Main controller info uninitialized or unavailable");
			return null;
		}

		if (mainInfo.getContext() == null) {
			OLog.err("Context object uninitialized or unavailable");
			return null;
		}

		if (_bluetoothController == null) {
			_bluetoothController = new BluetoothController(mainInfo);
		}

		return _bluetoothController;
	}

	/********************************/
	/** AbstractController Methods **/
	/********************************/
	@Override
	public Status initialize(Object initializer) {
		if (_btBridge == null) {
			_btBridge = new AndroidBluetoothBridge(); // TODO Should not be defined here
		}

		/* Initialize the Bluetooth Bridge */
		if (_btBridge.initialize(_mainInfo.getContext()) != Status.OK) {
			this.writeErr("Failed to initialize Bluetooth bridge");
			return Status.FAILED;
		}

		if (_btBridge.setEventHandler(this) != Status.OK) {
			this.writeErr("Failed to set Bluetooth bridge event handler");
			return Status.FAILED;
		}

		this.setState(ControllerState.READY);

		this.writeInfo("BluetoothController initialized");
		return Status.OK;
	}

	@Override
	public ControllerStatus performCommand(String cmdStr, String paramStr) {
		/* Check the command string */
		if (verifyCommand(cmdStr) != Status.OK) {
			return this.getControllerStatus();
		}

		/* Extract the command only */
		String shortCmdStr = extractCommand(cmdStr);
		if (shortCmdStr == null) {
			return this.getControllerStatus();
		}

		/* Check which command to perform */
		if (shortCmdStr.equals("connectByName") == true) {
			if (paramStr == null) {
				this.writeErr("Invalid parameter string");
				return this.getControllerStatus();
			}
			this.connectToDeviceByName(paramStr);
		} else if (shortCmdStr.equals("connectByAddr") == true) {
			if (paramStr == null) {
				this.writeErr("Invalid parameter string");
				return this.getControllerStatus();
			}
			this.connectToDeviceByAddr(paramStr);
		} else if (shortCmdStr.equals("send") == true) {
			if (paramStr == null) {
				this.writeErr("Invalid parameter string");
				return this.getControllerStatus();
			}
			this.broadcast(paramStr.getBytes());
		} else if (shortCmdStr.equals("start") == true) {
			this.writeInfo("Started");
			return this.getControllerStatus();
		} else {
			this.writeErr("Unknown or invalid command: " + shortCmdStr);
		}

		return this.getControllerStatus();
	}

	@Override
	public Status destroy() {
		if (_btBridge == null) {
			OLog.warn("Bluetooth bridge already unavailable");
			if (this.getState() != ControllerState.INACTIVE) {
				this.writeWarn("Bluetooth Controller state was left at "
						+ this.getState().toString());
				this.setState(ControllerState.INACTIVE);
			}
			return Status.OK;
		}

		/* Destroy the bluetooth bridge */
		if (_btBridge.destroy() != Status.OK) {
			this.writeWarn("Bluetooth bridge already unavailable");
		}

		this.setState(ControllerState.INACTIVE);

		this.writeInfo("BluetoothController destroyed");
		return Status.OK;
	}

	/********************/
	/** Public METHODS **/
	/********************/
	public Status connectToDeviceByName(String deviceName) {
		if (this.getState() == ControllerState.ACTIVE) {
			this.writeWarn("BluetoothController already connected: "
					+ this.getState().toString());
			return Status.ALREADY_STARTED;
		}

		if (this.getState() != ControllerState.READY) {
			this.writeErr("BluetoothController has not been started: "
					+ this.getState().toString());
			return Status.FAILED;
		}

		if (_btBridge == null) {
			this.writeErr("Bluetooth bridge unavailable");
			return Status.FAILED;
		}

		if (_btBridge.connectDeviceByName(deviceName) != Status.OK) {
			this.writeErr("Failed to connect to device: " + deviceName);
			return Status.FAILED;
		}

		this.setState(ControllerState.ACTIVE);
		this.writeInfo("Device connected by name: " + deviceName);
		return Status.OK;
	}

	public Status connectToDeviceByAddr(String deviceAddr) {
		if (this.getState() == ControllerState.ACTIVE) {
			this.writeWarn("BluetoothController already connected: "
					+ this.getState().toString());
			return Status.ALREADY_STARTED;
		}

		if (this.getState() != ControllerState.READY) {
			this.writeErr("BluetoothController has not been started: "
					+ this.getState().toString());
			return Status.FAILED;
		}

		if (_btBridge == null) {
			this.writeErr("Bluetooth bridge unavailable");
			return Status.FAILED;
		}

		if (_btBridge.connectDeviceByAddress(deviceAddr) != Status.OK) {
			this.writeErr("Failed to connect to address: " + deviceAddr);
			return Status.FAILED;
		}

		this.setState(ControllerState.ACTIVE);
		this.writeInfo("Device connected by address: " + deviceAddr);
		return Status.OK;
	}

	public Status broadcast(byte[] data) {
		if (this.getState() != ControllerState.ACTIVE) {
			this.writeErr("BluetoothController not active");
			return Status.ALREADY_STARTED;
		}

		if (_btBridge == null) {
			this.writeErr("Bluetooth bridge unavailable");
			return Status.FAILED;
		}

		if (_btBridge.broadcast(data) != Status.OK) {
			this.writeErr("Bluetooth broadcast failed");
			return Status.FAILED;
		}

		this.writeInfo("Bluetooth broadcast successful: " + new String(data) );
		return Status.OK;
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
}
