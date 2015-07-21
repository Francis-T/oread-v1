package net.oukranos.oreadv1.controller;

import java.util.Arrays;

import android.net.NetworkInfo.State;

import net.oukranos.oreadv1.android.AndroidStoredDataBridge;
import net.oukranos.oreadv1.interfaces.AbstractController;
import net.oukranos.oreadv1.interfaces.PersistentDataStoreIntf;
import net.oukranos.oreadv1.interfaces.SensorEventHandler;
import net.oukranos.oreadv1.types.ControlMechanism;
import net.oukranos.oreadv1.types.ControlMechanism.ReceiveStatus;
import net.oukranos.oreadv1.types.ControllerState;
import net.oukranos.oreadv1.types.ControllerStatus;
import net.oukranos.oreadv1.types.MainControllerInfo;
import net.oukranos.oreadv1.types.Status;
import net.oukranos.oreadv1.util.OreadLogger;

public class AutomationController extends AbstractController implements
		SensorEventHandler {
	/* Get an instance of the OreadLogger class to handle logging */
	private static final OreadLogger OLog = OreadLogger.getInstance();
	
	private static AutomationController _automationController = null;
	
	private BluetoothController _bluetoothController = null;
	private ControlMechanism _activeMechanism = null;
	private Thread _automationControllerThread = null;
	private PersistentDataStoreIntf _pDataStore = null;
	
	private DrainValve _drainValve = null;
	private SubmersiblePump _submersiblePump = null;
	private PeristalticPump _peristalticPump = null;
	private Autosampler _autosampler = null;
	private CuZnAutosampler _cuZnAutosampler = null;

	private byte[] _tempDataBuffer = new byte[512];
	private boolean _isUninterruptible = false;
	/*************************/
	/** Initializer Methods **/
	/*************************/
	private AutomationController(MainControllerInfo mainInfo, BluetoothController bluetooth) {
		_bluetoothController = bluetooth;
		_pDataStore = AndroidStoredDataBridge.getInstance(mainInfo.getContext());
		
		this.setType("device");
		this.setName("fd_control");
		return;
	}

	public static AutomationController getInstance(MainControllerInfo mainInfo) {
		if (mainInfo == null) {
			OLog.err("Main controller info uninitialized or unavailable");
			return null;
		}
		
		BluetoothController bluetooth = (BluetoothController) mainInfo
				.getSubController("bluetooth", "comm");
		if (bluetooth == null) {
			OLog.err("No bluetooth controller available");
			return null;
		}
		
		if (_automationController == null) {
			_automationController = new AutomationController(mainInfo, bluetooth);
		}
		
		return _automationController;
	}

	/********************************/
	/** AbstractController Methods **/
	/********************************/
	@Override
	public Status initialize(Object initializer) {
		/* Register our event handlers */
		_bluetoothController.registerEventHandler(this);

		/* Initialize the control devices */
		if (_drainValve == null) {
			_drainValve = new DrainValve(_bluetoothController);
		}

		if (_submersiblePump == null) {
			_submersiblePump = new SubmersiblePump(_bluetoothController);
		}

		if (_peristalticPump == null) {
			_peristalticPump = new PeristalticPump(_bluetoothController);
		}
		
		if (_autosampler == null) {
			_autosampler = new Autosampler(_bluetoothController);
		}
		
		if (_cuZnAutosampler == null) {
			_cuZnAutosampler = new CuZnAutosampler(_bluetoothController);
		}

		this.setState(ControllerState.READY);
		return Status.OK;
	}

	@Override
	public ControllerStatus performCommand(String cmdStr, String paramStr) {
		/* Check the command string*/
		if ( verifyCommand(cmdStr) != Status.OK ) {
			return this.getControllerStatus();
		}
		
		/* Extract the command only */
		String shortCmdStr = extractCommand(cmdStr);
		if (shortCmdStr == null) {
			return this.getControllerStatus();
		}
		
		/* Check which command to perform */
		if (shortCmdStr.equals("openValve") == true) {
			this.performDeviceActivate(_drainValve, paramStr);
		} else if (shortCmdStr.equals("closeValve") == true) {
			this.performDeviceDeactivate(_drainValve, paramStr);
		} else if (shortCmdStr.equals("startPump") == true) {
			this.performDeviceActivate(_submersiblePump, paramStr);
		} else if (shortCmdStr.equals("stopPump") == true) {
			this.performDeviceDeactivate(_submersiblePump, paramStr);
		} else if (shortCmdStr.equals("startSolutionDispense") == true) {
			this.performDeviceActivate(_peristalticPump, paramStr);
		} else if (shortCmdStr.equals("stopSolutionDispense") == true) {
			this.performDeviceDeactivate(_peristalticPump, paramStr);
		} else if (shortCmdStr.equals("startAutosampler") == true) {
			this.performDeviceActivate(_autosampler, paramStr);
		} else if (shortCmdStr.equals("stopAutosampler") == true) {
			this.performDeviceDeactivate(_autosampler, paramStr);
		} else if (shortCmdStr.equals("readFromCuZnAutosampler") == true) {
			this.performDeviceActivate(_cuZnAutosampler, paramStr);
		} else if (shortCmdStr.equals("stopCuZnAutosampler") == true) {
			this.performDeviceDeactivate(_cuZnAutosampler, paramStr);
		} else if (shortCmdStr.equals("start") == true) {
			this.writeInfo("Started");
		} else {
			this.writeErr("Unknown or invalid command: " + shortCmdStr);
		}
		
		return this.getControllerStatus();
	}

	@Override
	public Status destroy() {
		OLog.info("AutomationController destroy() invoked.");
		this.setState(ControllerState.UNKNOWN);
		_bluetoothController.unregisterEventHandler(this);
		
		/* Interrupt the waiting automation controller thread */
		if ((_automationControllerThread != null)
				&& (_automationControllerThread.isAlive())) {
			_automationControllerThread.interrupt();
		}

		return Status.OK;
	}

	/********************/
	/** Public METHODS **/
	/********************/
	@Override
	public void onDataReceived(byte[] data) {
		OLog.info("Received data in automation");
		
		if (data == null) {
			OLog.err("Received data is null");
			return;
		}

		if (_activeMechanism == null) {
			OLog.err("No active mechanisms!");
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
	private Status performDeviceActivate(ControlMechanism device, String params) {
		_automationControllerThread = Thread.currentThread();
		_activeMechanism = device;
		
		if (device == null) {
			OLog.err("Device is null");
			return Status.FAILED;
		}

		if (device.initialize() != Status.OK) {
			OLog.err("Failed to initialize " + device.getName());
			return Status.FAILED;
		}

		if (device.activate(params) != Status.OK) {
			OLog.err("Failed to activate " + device.getName());
			device.destroy();
			return Status.FAILED;
		}

		if (device.isPollable()) {
			OLog.info("Polling " + device.getName() + "...");
			performDevicePoll(device);
		} else if (device.isBlocking()) {
			/* If this is a blocking device, wait until a response is received */
			long sleepTime = device.getTimeoutDuration();
			try {
				Thread.sleep(sleepTime);
			} catch (InterruptedException e) {
				OLog.info("Interrupted");
			}
		}

		device.destroy();
		
		_activeMechanism = null;
		_automationControllerThread = null;

		return Status.OK;
	}
	
	private Status performDevicePoll(ControlMechanism device) {
		long startTime = System.currentTimeMillis();
		long stopTime = startTime + device.getTimeoutDuration();
		long sleepTime = device.getPollDuration();
		long cycles = device.getTimeoutDuration() / sleepTime;
		
		device.clearReceivedData();
		
		for ( int i = 0; i < (int) cycles; i++) {
			OLog.info("Cycle started");
			try {
				Thread.sleep(sleepTime);
			} catch (InterruptedException e) {
				OLog.info("Poll Interrupted");
				OLog.info("    State: " + this.getState());
			}
			
			if (this.getState() == ControllerState.UNKNOWN) {
				OLog.warn("Terminating poll");
				break;
			}
			
			if (device.shouldContinuePolling()) {
				OLog.info("Getting poll status");
				_isUninterruptible = true;
				try {
					Thread.sleep(sleepTime);
				} catch (InterruptedException e) {
					OLog.info("Poll Interrupted");
					OLog.info("    State: " + this.getState());
				}
				_isUninterruptible = false;
				/* If so, send out the polling command */
				device.pollStatus();
			} else {
				OLog.info("Stopping poll");
				/* Otherwise, break the loop and exit */
				break;
			}
			OLog.info("Cycle stopped");
		}
		
		return Status.OK;
	}

	private Status performDeviceDeactivate(ControlMechanism device, String params) {
		_automationControllerThread = Thread.currentThread();
		_activeMechanism = device;
		
		if (device == null) {
			OLog.err("Device is null");
			return Status.FAILED;
		}

		if (device.initialize() != Status.OK) {
			OLog.err("Failed to initialize " + device.getName());
			return Status.FAILED;
		}

		if (device.deactivate(params) != Status.OK) {
			OLog.err("Failed to deactivate " + device.getName());
			device.destroy();
			return Status.FAILED;
		}

		/* If this is a blocking device, wait until a response is received */
		if (device.isBlocking()) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				OLog.info("Interrupted");
			}
		}

		device.destroy();
		
		_activeMechanism = null;
		_automationControllerThread = null;

		return Status.OK;
	}

	/*******************/
	/** Inner Classes **/
	/*******************/
	private class SubmersiblePump extends ControlMechanism {
		private static final String ACTV_CMD_STR = "PUMP START";
		private static final String DEACT_CMD_STR = "PUMP STOP";

		public SubmersiblePump(BluetoothController bluetooth) {
			super(bluetooth);
			this.setName("Submersible Pump");
			this.setBlocking(true);
			this.setTimeoutDuration(120000);
			return;
		}

		@Override
		public Status activate() {
			OLog.info(this.getName() + " activated.");
			return send(ACTV_CMD_STR.getBytes());
		}

		@Override
		public Status deactivate() {
			OLog.info(this.getName() + " deactivated.");
			return send(DEACT_CMD_STR.getBytes());
		}

		@Override
		public Status activate(String params) {
			return this.activate();
		}

		@Override
		public Status deactivate(String params) {
			return this.deactivate();
		}

		@Override
		public Status pollStatus() {
			// TODO Auto-generated method stub
			return Status.OK;
		}
	}
	
	private class PeristalticPump extends ControlMechanism {
		private static final String ACTV_CMD_STR = "FILL START";
		private static final String DEACT_CMD_STR = "FILL STOP";

		public PeristalticPump(BluetoothController bluetooth) {
			super(bluetooth);
			this.setName("Peristaltic Pump");
			this.setBlocking(true);
			this.setTimeoutDuration(10000);
			return;
		}

		@Override
		public Status activate() {
			OLog.info(this.getName() + " activated.");
			return send(ACTV_CMD_STR.getBytes());
		}

		@Override
		public Status deactivate() {
			OLog.info(this.getName() + " deactivated.");
			return send(DEACT_CMD_STR.getBytes());
		}

		@Override
		public Status activate(String params) {
			return this.activate();
		}

		@Override
		public Status deactivate(String params) {
			return this.deactivate();
		}

		@Override
		public Status pollStatus() {
			// TODO Auto-generated method stub
			return Status.OK;
		}
	}
	
	private class DrainValve extends ControlMechanism {
		private static final String ACTV_CMD_STR = "DRAIN START";
		private static final String DEACT_CMD_STR = "DRAIN STOP";

		public DrainValve(BluetoothController bluetooth) {
			super(bluetooth);
			this.setName("Drain Valve");
			this.setBlocking(true);
			this.setTimeoutDuration(20000);
		}

		@Override
		public Status activate() {
			return send(ACTV_CMD_STR.getBytes());
		}

		@Override
		public Status deactivate() {
			return send(DEACT_CMD_STR.getBytes());
		}

		@Override
		public Status activate(String params) {
			return this.activate();
		}

		@Override
		public Status deactivate(String params) {
			return this.deactivate();
		}

		@Override
		public Status pollStatus() {
			// TODO Auto-generated method stub
			return Status.OK;
		}
	}

	private class Autosampler extends ControlMechanism {
		private static final String ACTV_CMD_STR = "I2C 4 n x";
		private static final String DEACT_CMD_STR = "I2C 4 n y";
		private static final String STATE_CMD_STR = "I2C 4 y @";

		public Autosampler(BluetoothController bluetooth) {
			super(bluetooth);
			this.setName("Autosampler");
			this.setBlocking(true);
//			this.setTimeoutDuration(40000);
//			this.setPollable(true);
//			this.setPollDuration(5000);
			this.setTimeoutDuration(600000);
			this.setPollable(true);
			this.setPollDuration(30000);
		}

		@Override
		public Status activate() {
//			int dataLen = ACTV_CMD_STR.getBytes().length;
//			byte data[] = new byte[dataLen+2];
//			
//			/* TODO DEBUG EXCEPTION */
//			try {
//				System.arraycopy(ACTV_CMD_STR.getBytes(), 0, 
//								 data, 0, dataLen+1);
//				data[dataLen] = 0;
//				data[dataLen+1] = '\r';
//			} catch (Exception e) {
//				OLog.err(e.getMessage());
//				data = ACTV_CMD_STR.getBytes();
//			}
//			
//			return send(data);
			return this.activate("2");
		}

		@Override
		public Status deactivate() {
			return send(DEACT_CMD_STR.getBytes());
		}

		@Override
		public Status activate(String params) {
			int dataLen = ACTV_CMD_STR.getBytes().length;
			byte data[] = new byte[dataLen+2];
			
			String posStr = "";
			if (params.equals("@")) {
				posStr = _pDataStore.get("ASHG_CUV_NUM");
				if (posStr.equals("")) {
					OLog.info("Empty");
					posStr = "2";
				}
			} else {
				posStr = "2";
			}
			
			data = new String(ACTV_CMD_STR + " " + posStr).getBytes();
			int pos = Integer.decode(posStr) + 1;
			if (pos > 30) {
				pos = 2;
			}
			_pDataStore.put("ASHG_CUV_NUM", ("" + pos));
			OLog.info("Pos updated: " + _pDataStore.get("ASHG_CUV_NUM"));
			OLog.info("Sending data: " + new String(data));
			
			return send(data);
		}

		@Override
		public Status deactivate(String params) {
			return this.deactivate();
		}

		@Override
		public Status pollStatus() {
			return send(STATE_CMD_STR.getBytes());
		}

		@Override
		public boolean shouldContinuePolling() {
			byte data[] = this.getReceivedData();
			
			if (data == null) {
				OLog.warn("Received data is empty");
				return true;
			}
			
			String response = new String(data);
			if (response.startsWith("State: 3")) {
				OLog.info("Found Response: " + response);
				return false;
			}

			OLog.info("Found Response: " + response);
			return true;
		}
	}

	private class CuZnAutosampler extends ControlMechanism {
		private static final String ACTV_CMD_STR = "I2C 4 n x";
		private static final String DEACT_CMD_STR = "I2C 4 n y";
		private static final String STATE_CMD_STR = "I2C 4 y @";

		public CuZnAutosampler(BluetoothController bluetooth) {
			super(bluetooth);
			this.setName("CuZn Autosampler");
			this.setBlocking(true);
//			this.setTimeoutDuration(40000);
//			this.setPollable(true);
//			this.setPollDuration(5000);
			this.setTimeoutDuration(600000);
			this.setPollable(true);
			this.setPollDuration(30000);
		}

		@Override
		public Status activate() {
//			int dataLen = ACTV_CMD_STR.getBytes().length;
//			byte data[] = new byte[dataLen+2];
//			
//			/* TODO DEBUG EXCEPTION */
//			try {
//				System.arraycopy(ACTV_CMD_STR.getBytes(), 0, 
//								 data, 0, dataLen+1);
//				data[dataLen] = 0;
//				data[dataLen+1] = '\r';
//			} catch (Exception e) {
//				OLog.err(e.getMessage());
//				data = ACTV_CMD_STR.getBytes();
//			}
//			
//			return send(data);
			return this.activate("2");
		}

		@Override
		public Status deactivate() {
			return send(DEACT_CMD_STR.getBytes());
		}

		@Override
		public Status activate(String params) {
			int dataLen = ACTV_CMD_STR.getBytes().length;
			byte data[] = new byte[dataLen+2];
			
			/* TODO DEBUG EXCEPTION */
			try {
				System.arraycopy(ACTV_CMD_STR.getBytes(), 0, 
								 data, 0, dataLen);
				try {
					data[dataLen] = Byte.decode(params);
				} catch(NumberFormatException ne) {
					data[dataLen] = 0;  
					OLog.err("Could not decode activate param");
				}
				data[dataLen+1] = '\r';
			} catch (Exception e) {
				OLog.err(e.getMessage());
				data = ACTV_CMD_STR.getBytes();
			}
			
			return send(data);
		}

		@Override
		public Status deactivate(String params) {
			return this.deactivate();
		}

		@Override
		public Status pollStatus() {
			return send(STATE_CMD_STR.getBytes());
		}

		@Override
		public boolean shouldContinuePolling() {
			byte data[] = this.getReceivedData();
			
			if (data == null) {
				OLog.warn("Received data is empty");
				return true;
			}
			
			String response = new String(data);
			if ((response.startsWith("Cu:")) || (response.startsWith("Zn:"))) {
				return false;
			}
			
			return true;
		}
	}
}
