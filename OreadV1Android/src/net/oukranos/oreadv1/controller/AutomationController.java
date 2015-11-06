package net.oukranos.oreadv1.controller;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.oukranos.oreadv1.devices.control.AsHgAutosampler;
import net.oukranos.oreadv1.devices.control.CleanWaterPump;
import net.oukranos.oreadv1.devices.control.CuZnAutosampler;
import net.oukranos.oreadv1.devices.control.DrainValve;
import net.oukranos.oreadv1.devices.control.HighPointCalibSolutionPump;
import net.oukranos.oreadv1.devices.control.LowPointCalibSolutionPump;
import net.oukranos.oreadv1.devices.control.SubmersiblePump;
import net.oukranos.oreadv1.interfaces.AbstractController;
import net.oukranos.oreadv1.interfaces.IPersistentDataBridge;
import net.oukranos.oreadv1.interfaces.SensorEventHandler;
import net.oukranos.oreadv1.types.ControlMechanism;
import net.oukranos.oreadv1.types.ControlMechanism.ReceiveStatus;
import net.oukranos.oreadv1.types.ControllerState;
import net.oukranos.oreadv1.types.ControllerStatus;
import net.oukranos.oreadv1.types.MainControllerInfo;
import net.oukranos.oreadv1.types.Status;

public class AutomationController extends AbstractController implements SensorEventHandler {
	private static AutomationController _automationController = null;
	
	private BluetoothController _bluetoothController = null;
	private ControlMechanism _activeMechanism = null;
	private Thread _automationControllerThread = null;
	private List<ControlMechanism> _controlDevices = null;
	
	private Class<?> _controlDeviceClasses[] = {
			DrainValve.class,
			SubmersiblePump.class,
			CleanWaterPump.class,
			LowPointCalibSolutionPump.class,
			HighPointCalibSolutionPump.class,
			AsHgAutosampler.class,
			CuZnAutosampler.class
	};

	private byte[] _tempDataBuffer = new byte[512];
	private boolean _isUninterruptible = false;
	
	/*************************/
	/** Initializer Methods **/
	/*************************/
	private AutomationController() {
		setType("device");
		setName("fd_control");
		
		return;
	}

	public static AutomationController getInstance(MainControllerInfo mainInfo) {
		if (mainInfo == null) {
			OLog.err("Invalid input parameter/s" +
					" in AutomationController.getInstance()");
			return null;
		}
		
		BluetoothController btController = (BluetoothController) mainInfo
				.getSubController("bluetooth", "comm");
		if (btController == null) {
			OLog.err("No bluetooth controller available");
			return null;
		}

		/* Instantiate the AutomationController if it hasn't been done yet */
		if (_automationController == null) {
			_automationController = new AutomationController();
		}
		
		_automationController._mainInfo = mainInfo;
		_automationController._bluetoothController = btController;
		
		return _automationController;
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
		
		/* Instantiate the control mechanisms */
		Status retStatus = instantiateControlDevices();
		if (retStatus != Status.OK) {
			destroyControlDevices();
			return retStatus;
		}
		
		/* Set the controller state to INACTIVE */
		setState(ControllerState.INACTIVE);

		return writeInfo("AutomationController initialized");
	}

	@Override
	public Status start() {
		/* Check the controller state prior to start */
		ControllerState state = getState();
		if (state != ControllerState.INACTIVE) {
			return writeErr(toString() 
					+ " state is invalid for start(): " 
					+ state.toString());
		}
		
		if (_bluetoothController == null) {
			return writeErr("No BluetoothController assigned for " 
					+ toString());
		}
		
		/* Register as a BluetoothController event handler */
		_bluetoothController.registerEventHandler(this);
		
		/* Set the bluetooth controller for the control devices */
		setBluetoothControllerForDevices(_bluetoothController);

		/* Set the persistent data bridge for the control devices */
		IPersistentDataBridge pDataStore = getPersistentDataBridge();
		if (pDataStore != null) {
			setPersistentDataBridgeForDevices(pDataStore);
		}
		
		/* Initialize the control devices */
		Status retStatus = initializeControlDevices();
		if (retStatus != Status.OK) {
			destroyControlDevices();
			return retStatus;
		}
		
		setState(ControllerState.READY);

		return writeInfo("AutomationController started");
	}

	@Override
	public ControllerStatus performCommand(String cmdStr, String paramStr) {
		/* Check the command string*/
		if ( verifyCommand(cmdStr) != Status.OK ) {
			return getControllerStatus();
		}
		
		/* Extract the command only */
		String shortCmdStr = extractCommand(cmdStr);
		if (shortCmdStr == null) {
			return getControllerStatus();
		}
		
		/* Check which command to perform */
		if (shortCmdStr.equals("openValve") == true) {
			activateDevice(
					getDevice("Drain Valve"), 
					paramStr 
			);
			
		} else if (shortCmdStr.equals("closeValve") == true) {
			deactivateDevice(
					getDevice("Drain Valve"), 
					paramStr
			);
			
		} else if (shortCmdStr.equals("startPump") == true) {
			activateDevice(
					getDevice("Submersible Pump"), 
					paramStr
			);
			
		} else if (shortCmdStr.equals("stopPump") == true) {
			deactivateDevice(
					getDevice("Submersible Pump"), 
					paramStr
			);
			
		} else if (shortCmdStr.equals("startCleanWaterDispense") == true) {
			activateDevice(
					getDevice("Clean Water Pump"), 
					paramStr
			);
			
		} else if (shortCmdStr.equals("stopCleanWaterDispense") == true) {
			deactivateDevice(
					getDevice("Clean Water Pump"), 
					paramStr
			);
			
		} else if (shortCmdStr.equals("startHighPointSolutionDispense") == true) {
			activateDevice(
					getDevice("High-Point Calib Solution Pump"), 
					paramStr
			);
			
		} else if (shortCmdStr.equals("stopHighPointSolutionDispense") == true) {
			deactivateDevice(
					getDevice("High-Point Calib Solution Pump"), 
					paramStr
			);
			
		} else if (shortCmdStr.equals("startLowPointSolutionDispense") == true) {
			activateDevice(
					getDevice("Low-Point Calib Solution Pump"), 
					paramStr
			);
			
		} else if (shortCmdStr.equals("stopLowPointSolutionDispense") == true) {
			deactivateDevice(
					getDevice("Low-Point Calib Solution Pump"), 
					paramStr
			);
			
		} else if (shortCmdStr.equals("startAutosampler") == true) {
			activateDevice(
					getDevice("AsHg Autosampler"),
					paramStr
			);
			
		} else if (shortCmdStr.equals("pollAutosampler") == true) {
			pollDevice(
					getDevice("AsHg Autosampler")
			);
			
		} else if (shortCmdStr.equals("stopAutosampler") == true) {
			deactivateDevice(
					getDevice("AsHg Autosampler"), 
					paramStr
			);
			
		} else if (shortCmdStr.equals("readFromCuZnAutosampler") == true) {
			activateDevice(
					getDevice("CuZn Autosampler"), 
					paramStr
			);
			
		} else if (shortCmdStr.equals("stopCuZnAutosampler") == true) {
			deactivateDevice(
					getDevice("CuZn Autosampler"), 
					paramStr
			);
			
		} else if (shortCmdStr.equals("start") == true) {
			if (start() == Status.OK) {
				writeInfo("Started");
			}
			
		} else if (shortCmdStr.equals("stop") == true) {
			if (stop() == Status.OK) {
				writeInfo("Stopped");
			}
			
		} else {
			writeErr("Unknown or invalid command: " + shortCmdStr);
		}
		
		return getControllerStatus();
	}
	
	@Override
	public Status stop() {
		/* Check the controller state prior to stop */
		ControllerState state = getState();
		if ((state != ControllerState.READY) &&
				(state != ControllerState.ACTIVE) &&
				(state != ControllerState.BUSY) ) {
			return writeErr(toString() 
					+ " state is invalid for stop(): " 
					+ state.toString());
		}
		
		/* Interrupt the waiting automation controller thread */
		if ((_automationControllerThread != null)
				&& (_automationControllerThread.isAlive())) {
			_automationControllerThread.interrupt();
		}
		
		/* Unset the bluetooth controller for the control devices */
		setBluetoothControllerForDevices(null);

		/* Unset the persistent data bridge for the control devices */
		setPersistentDataBridgeForDevices(null);
		
		/* Unregister as a BluetoothController event handler */
		_bluetoothController.unregisterEventHandler(this);

		setState(ControllerState.INACTIVE);

		return writeInfo("AutomationController stopped");
	}

	@Override
	public Status destroy() {
		/* If the AutomationController is already running, execute stop() */
		ControllerState state = getState();
		if ( (state!= ControllerState.INACTIVE) ||
				(state != ControllerState.UNKNOWN) ) {
			if (stop() != Status.OK) {
				OLog.warn("Failed to stop AutomationController");
			}
		}
		
		/* Unload the control mechanisms */
		destroyControlDevices();
		
		setState(ControllerState.UNKNOWN);

		return writeInfo("AutomationController destroyed");
	}

	/********************/
	/** Public METHODS **/
	/********************/
	@Override
	public void onDataReceived(byte[] data) {
		OLog.info("Received data in automation");
		
		if (data == null) {
			OLog.warn("Received data is null");
			return;
		}

		if (_activeMechanism == null) {
			OLog.warn("No active mechanisms!");
			return;
		}
		

		final int maxLen = _tempDataBuffer.length;
		int dataLength = 0;

		/* Check if the buffer still has space to receive the data */
		if (data.length >= maxLen) {
			return;
		}

		/* Copy data to temp buffer */
		if (data.length < maxLen) {
			System.arraycopy(data, 0, _tempDataBuffer, 0, data.length);
			dataLength = data.length;
		} else {
			System.arraycopy(data, 0, _tempDataBuffer, 0, maxLen);
			dataLength = maxLen;
			OLog.warn("Received data exceeds temp buffer size. Data might have been lost. ");
		}
		
		/* Receive the data by calling on receiveData() method 
		 * 	for the active device */
		ReceiveStatus status = _activeMechanism
				.receiveData(_tempDataBuffer, dataLength);
		
		/* Clear the temp data buffer */
		Arrays.fill(_tempDataBuffer, (byte) (0));

		/* Break the loop if the data is complete or we failed */
		if ((status == ReceiveStatus.COMPLETE)
				|| (status == ReceiveStatus.FAILED)) {
			if (status == ReceiveStatus.FAILED) {
				/* Log an error */
				OLog.err("Failed to receive data");
			}
			/* Interrupt the waiting automation controller thread */
			if ( (_isUninterruptible == false)
					&& (_automationControllerThread != null)
					&& (_automationControllerThread.isAlive())) {
				_automationControllerThread.interrupt();
			} else {
				OLog.warn("Original automation controller thread does not exist");
			}
			
			return;
		}
		
		/* For partial receives, wait for the next part */
        return;
	}

	/*********************/
	/** Private Methods **/
	/*********************/
	private Status activateDevice(ControlMechanism device, String params) {
		/* Check the controller state prior to device activation */
		ControllerState state = getState();
		if (state != ControllerState.READY) {
			return writeErr(toString() 
					+ " state is invalid for device activation: " 
					+ state.toString());
		}
		
		setState(ControllerState.BUSY);
		
		_automationControllerThread = Thread.currentThread();
		_activeMechanism = device;
		
		if (device == null) {
			setState(ControllerState.READY);
			return writeErr("Device not found. " 
					+ "Cannot perform device activation.");
		}

		if (device.activate(params) != Status.OK) {
			setState(ControllerState.READY);
			return writeErr("Failed to activate " + device.getName());
		}
		
		_activeMechanism = null;
		_automationControllerThread = null;
		
		setState(ControllerState.READY);

		return writeInfo("Device activated: " + device.getName());
	}
	
	private Status pollDevice(ControlMechanism device) {
		/* Check the controller state prior to device poll */
		ControllerState state = getState();
		if (state != ControllerState.READY) {
			return writeErr(toString() 
					+ " state is invalid for device poll: " 
					+ state.toString());
		}
		
		setState(ControllerState.BUSY);
		
		_automationControllerThread = Thread.currentThread();
		_activeMechanism = device;
		
		if (device == null) {
			setState(ControllerState.READY);
			return writeErr("Device not found. " 
					+ "Cannot perform device activation.");
		}
		
		if (device.clearReceivedData() != Status.OK) {
			setState(ControllerState.READY);
			return writeErr("Failed to clear received data for device: " 
					+ device.getName() + " "
					+ "Cannot perform device poll.");
		}
		
		if (device.pollStatus() != Status.OK) {
			setState(ControllerState.READY);
			return writeErr("Failed to poll status for device: " 
					+ device.getName());
		}

		long sleepTime = device.getPollDuration();
		try {
			Thread.sleep(sleepTime);
		} catch (InterruptedException e) {
			OLog.info("Poll Interrupted");
			OLog.info("    State: " + getState());
		}
		
		if (device.shouldContinuePolling()) {
			OLog.info("Polling continues...");
		} else {
			OLog.info("Stopping poll");
		}
			
		_activeMechanism = null;
		_automationControllerThread = null;

		setState(ControllerState.READY);

		return writeInfo("Device polled: " + device.getName());
	}

	private Status deactivateDevice(ControlMechanism device, 
			String params) {
		/* Check the controller state prior to device deactivate */
		ControllerState state = getState();
		if (state != ControllerState.READY) {
			return writeErr(toString() 
					+ " state is invalid for device deactivation: " 
					+ state.toString());
		}
		
		setState(ControllerState.BUSY);
		
		_automationControllerThread = Thread.currentThread();
		_activeMechanism = device;
		
		if (device == null) {
			setState(ControllerState.READY);
			return writeErr("Device not found. " 
					+ "Cannot perform device deactivation.");
		}
		
		if (device.deactivate(params) != Status.OK) {
			setState(ControllerState.READY);
			return writeErr("Failed to deactivate device: " 
					+ device.getName());
		}

		/* If this is a blocking device, wait until a response is received */
		if (device.isBlocking()) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				OLog.info("Interrupted");
			}
		}
		
		_activeMechanism = null;
		_automationControllerThread = null;

		setState(ControllerState.READY);

		return writeInfo("Device deactivated: " + device.getName());
	}
	
	private ControlMechanism getDevice(String name) {
		if (_controlDevices == null) {
			writeErr("No control devices registered");
			return null;
		}
		
		for (ControlMechanism device : _controlDevices) {
			if (device.getName().equals(name)) {
				return device;
			}
		}

		writeErr("Control device not found");
		return null;
	}
	
	private void setPersistentDataBridgeForDevices(
			IPersistentDataBridge dataBridge) {
		if (_controlDevices == null) {
			writeErr("No control devices registered");
			return;
		}

		/* Set the PersistentDataBridge for each control device */
		for (ControlMechanism device : _controlDevices) {
			device.setPersistentDataBridge(dataBridge);
		}
		
		return;
	}
	
	private void setBluetoothControllerForDevices( 
			BluetoothController btController ) {
		if (_controlDevices == null) {
			writeErr("No control devices registered");
			return;
		}

		/* Set the BluetoothController for each control device */
		for (ControlMechanism device : _controlDevices) {
			device.setBluetoothController(btController);
		}
		
		return;
	}
	
	private Status instantiateControlDevices() {
		if (_controlDevices == null) {
			_controlDevices = new ArrayList<ControlMechanism>();
		}
		
		/* Instantiate the control devices devices */
		for (Class<?> c : _controlDeviceClasses) {
			Constructor<?>[] constructors = c.getDeclaredConstructors();
			if (constructors == null) {
				continue;
			}
			
			if (constructors.length == 0) {
				continue;
			}
			
			for (Constructor<?> constructor : constructors) {
				/* Get the constructor with no arguments */
				if (constructor.getParameterTypes().length == 0) {
					try {
						ControlMechanism device
							= (ControlMechanism) constructor.newInstance();
						if (device != null) {
							_controlDevices.add(device);
						}
					} catch (Exception e) {
						writeErr("Exception occurred trying to instantiate "
								+ c.getSimpleName() + ": "
								+ e.getMessage() );
					}
					break;
				}
			}
		}
		
		if (_controlDevices.isEmpty()) {
			writeErr("No control devices initialized");
			return Status.FAILED;
		}
		
		return writeInfo("Control mechanisms initialized");
	}

	private Status initializeControlDevices() {
		if (_controlDevices == null) {
			return writeErr("No control devices registered");
		}

		/* Setup each control device */
		for (ControlMechanism device : _controlDevices) {
			/* Initialize the control device */
			if (device.initialize() != Status.OK) {
				return writeErr("Failed to initialize device:" 
						+ device.getName() );
			}
		}
		
		return writeInfo("Control mechanisms initialized");
	}
	
	private void destroyControlDevices() {
		if (_controlDevices == null) {
			writeErr("No control devices registered");
			return;
		}

		/* Destroy the control devices */
		for (ControlMechanism device : _controlDevices) {
			if (device.destroy() != Status.OK) {
				OLog.warn("Failed to cleanup device: " 
						+ device.getName() );
			}
		}
		
		/* Unload the control devices list */
		_controlDevices.clear();
		_controlDevices = null;
		
		return;
	}
}
