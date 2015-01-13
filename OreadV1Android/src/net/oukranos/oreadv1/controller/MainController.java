package net.oukranos.oreadv1.controller;

import android.app.Activity;
import net.oukranos.oreadv1.interfaces.AbstractController;
import net.oukranos.oreadv1.interfaces.MainControllerEventHandler;
import net.oukranos.oreadv1.types.ControllerState;
import net.oukranos.oreadv1.types.HttpEncWaterQualityData;
import net.oukranos.oreadv1.types.Status;
import net.oukranos.oreadv1.types.WaterQualityData;
import net.oukranos.oreadv1.util.OLog;

public class MainController extends AbstractController {
	private static MainController _mainControllerInstance = null;
	private Thread _controllerRunThread = null;
	private Runnable _controllerRunTask = null;

	private WaterQualityData _waterQualityData = null;
	
	private BluetoothController _bluetoothController = null;
	private SensorArrayController _sensorArrayController = null;
	private NetworkController _networkController = null;
	
	private MainControllerEventHandler _eventHandler = null;
	private Activity _parentActivity = null;

	private MainController(Activity parent) {
		this._parentActivity = parent;
		
		return;
	}
	
	public static MainController getInstance(Activity parent) {
		if (_mainControllerInstance == null) {
			_mainControllerInstance = new MainController(parent);
		}
		
		return _mainControllerInstance;
	}
	
	@Override
	public Status initialize() {
		this.setState(ControllerState.UNKNOWN);
		
		if (this.initializeSubControllers(_parentActivity) != Status.OK) {
			OLog.err("Failed to initialize subcontrollers");
			return Status.FAILED;
		}
		
		return Status.OK;
	}
	
	public Status start() {
		OLog.info("MainController start()");
		if ( this.getState() == ControllerState.READY ) {
			return Status.ALREADY_STARTED;
		}
		
		this.setState(ControllerState.READY);

		this.initializeRunTaskLoop();
		
		if ( this.startRunTaskLoop() == Status.FAILED ) {
			this.setState(ControllerState.UNKNOWN);
			OLog.err("Failed to start run task loop");
			return Status.FAILED;
		}
		
		return Status.OK;
	}
	
	public Status stop() {
		OLog.info("MainController stop()");
		if ( this.getState() == ControllerState.UNKNOWN ) {
			OLog.info("MainController already stopped");
			return Status.OK;
		}
		
		if ( _controllerRunThread == null ) {
			OLog.err("MainController run thread unavailable");
			return Status.FAILED;
		}
	
		if ( _controllerRunThread.isAlive() == false ) {
			OLog.info("MainController run thread already stopped");
			return Status.OK;
		}
		
		this.setState(ControllerState.UNKNOWN);	

		_controllerRunThread.interrupt();
		
		try {
			_controllerRunThread.join();
		} catch (InterruptedException e) {
			; /* No need to handle interrupts */
		}
		
		_controllerRunThread = null;
		
		return Status.OK;
	}
	
	@Override
	public Status destroy() {
		Status returnStatus = Status.FAILED;
		returnStatus = this.stop();
		if ( returnStatus != Status.OK ) {
			OLog.err("Failed to stop MainController");
		}
		
		unloadSubControllers(_parentActivity);
		if ( returnStatus != Status.OK ) {
			OLog.err("Failed to unload subcontrollers");
		}
		return returnStatus;
	}
	
	public void setEventHandler(MainControllerEventHandler handler) {
		_eventHandler = handler;
	}
	
	public Status getData(WaterQualityData container) {
		if ( container == null ) {
			OLog.err("Data container is null");
			return Status.FAILED;
		}
		
		if ( _waterQualityData == null ) {
			OLog.err("Data source is null");
			return Status.FAILED;
		}
		
		container = new WaterQualityData(_waterQualityData);
		
		return Status.OK;
	}
	
	
	private void initializeRunTaskLoop() {
		if (_controllerRunTask == null) {
			_controllerRunTask = new ControllerRunTask();
		}
		
		if (_controllerRunThread == null) {
			_controllerRunThread = new Thread(_controllerRunTask);
		}
		
		return;
	}
	
	private Status startRunTaskLoop() {
		if (_controllerRunThread == null) {
			return Status.FAILED;
		}
		
		if (_controllerRunThread.isAlive() == true) {
			return Status.ALREADY_STARTED;
		}
		
		_controllerRunThread.start();
		
		return Status.OK;
	}
	
	private Status initializeSubControllers(Activity parent) {
		/* Initialize all sub-controllers here */
		_bluetoothController = BluetoothController.getInstance(parent, null);
		_networkController = NetworkController.getInstance(parent);
		
		_waterQualityData = new WaterQualityData(0);
		_sensorArrayController = SensorArrayController.getInstance(_bluetoothController, _waterQualityData);
		
		
		_sensorArrayController.initialize();
		_networkController.initialize();
		_bluetoothController.initialize();
		
		return Status.OK;
	}
	
	private Status unloadSubControllers(Activity parent) {
		_sensorArrayController.destroy();
		_waterQualityData = null;
		_bluetoothController.destroy();
		
		return Status.OK;
	}
	
	private Status startBluetoothController() {
		OLog.info("Getting paired device names...");
		if (_bluetoothController == null) {
			return Status.FAILED;
		}
		
		_bluetoothController.initialize();
		
		if ( _bluetoothController.getPairedDeviceNames() == null ) {
			OLog.err("Failed to get paired device names");
			return Status.FAILED;
		}

		OLog.info("Connecting to device...");
		if ( _bluetoothController.connectToDeviceByName("HC-05") == Status.FAILED ) {
			OLog.err("Failed to connect to device");
			return Status.FAILED;
		}
		
		return Status.OK;
	}
	
	/* Inner Classes */
	private class ControllerRunTask implements Runnable {
		
		@Override
		public void run() {
			OLog.info("Run Task started");
			while ( getState() == ControllerState.READY ) {
				/* TODO Check state of BluetoothController */
				/* TODO Check state of SensorController */
				/* Check state of NetworkController */
				if (_networkController.getState() != ControllerState.READY) {
					_networkController.start();
				}
				
				
				/* TODO CLEANUP LOGIC IN THIS PART */
				if ( _bluetoothController.getState() == ControllerState.ACTIVE ) {
					/* Pull data from water quality sensors */
					OLog.info("Reading Sensor Data...");
					if ( _sensorArrayController.readSensorData() == Status.OK ) {
						_eventHandler.onDataAvailable();
					}
				} else {
					startBluetoothController();
				}
				
				/* TODO Upload sensor data to server */
				if (_networkController.getState() == ControllerState.READY) {
					_networkController.send("http://www.dummyurl.net/",
							new HttpEncWaterQualityData(_waterQualityData));
				}
				
				/* TODO Check state of CameraController */
				/* TODO Pull data from the camera */
				/* TODO Upload image data to server */
				
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					/* This allows the running thread to be interrupted whenever
					 * MainController.stop() is invoked by the UI */
					OLog.info("Controller Run Thread Interrupted");
					continue;
				}
			}
			_networkController.stop();
			_networkController.destroy();
			_bluetoothController.destroy();
			OLog.info("Run Task finished");
		}
	}
}
