package net.oukranos.oreadv1.controller;

import android.app.Activity;
import net.oukranos.oreadv1.interfaces.AbstractController;
import net.oukranos.oreadv1.interfaces.MainControllerEventHandler;
import net.oukranos.oreadv1.types.ChemicalPresenceData;
import net.oukranos.oreadv1.types.ControllerState;
import net.oukranos.oreadv1.types.HttpEncChemicalPresenceData;
import net.oukranos.oreadv1.types.HttpEncWaterQualityData;
import net.oukranos.oreadv1.types.Status;
import net.oukranos.oreadv1.types.WaterQualityData;
import net.oukranos.oreadv1.util.OLog;

public class MainController extends AbstractController {
	private static final String DEFAULT_DATA_SERVER_URL = "http://miningsensors.ateneo.edu:8080/uploadData";
	private static final String DEFAULT_IMAGE_SERVER_URL = "http://miningsensors.ateneo.edu:8080/uploadImage";
	
	private static MainController _mainControllerInstance = null;
	private Thread _controllerRunThread = null;
	private Runnable _controllerRunTask = null;

	private WaterQualityData _waterQualityData = null;
	private boolean _waterQualityDataAvailable = false;
	private ChemicalPresenceData _chemPresenceData = null;
	private boolean _chemPresenceDataAvailable = false;
	
	private BluetoothController _bluetoothController = null;
	private SensorArrayController _sensorArrayController = null;
	private CameraController _cameraController = null;
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
			OLog.info("Controller Run Thread Interrupted");
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
		
		return returnStatus;
	}
	
	public void setEventHandler(MainControllerEventHandler handler) {
		_eventHandler = handler;
	}
	
	public WaterQualityData getData() {
		if ( _waterQualityData == null ) {
			OLog.err("Data source is null");
			return null;
		}
		
		return new WaterQualityData(_waterQualityData);
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
		/* Initialize data buffers */
		_chemPresenceData = new ChemicalPresenceData(1);
		_waterQualityData = new WaterQualityData(1);
		
		/* Initialize all sub-controllers here */
		_bluetoothController = BluetoothController.getInstance(parent, null);
		_networkController = NetworkController.getInstance(parent);
		_cameraController = CameraController.getInstance(parent, _chemPresenceData);
		_sensorArrayController = SensorArrayController.getInstance(_bluetoothController, _waterQualityData);
		
		return Status.OK;
	}
	
	private Status unloadSubControllers(Activity parent) {
		/* Cleanup sub-controllers */
		_bluetoothController.destroy();
		_cameraController.destroy();
		_networkController.destroy();
		_sensorArrayController.destroy();

		/* Cleanup data buffers */
		_waterQualityData = null;
		_chemPresenceData = null;
		
		return Status.OK;
	}
	
	private Status startBluetoothController() {
		OLog.info("Starting BluetoothController...");
		
		if (_bluetoothController == null) {
			OLog.err("BluetoothController is NULL");
			return Status.FAILED;
		}
		
		_bluetoothController.initialize();

		OLog.info("Getting paired device names...");
		if ( _bluetoothController.getPairedDeviceNames() == null ) {
			OLog.err("Failed to get paired device names");
			return Status.FAILED;
		}

		OLog.info("Connecting to device...");
		if ( _bluetoothController.connectToDeviceByName("HC-05") == Status.FAILED ) {
			OLog.err("Failed to connect to device");
			return Status.FAILED;
		}

		OLog.info("BluetoothController started successfully");
		return Status.OK;
	}
	
	private Status startNetworkController() {
		Status retStatus = Status.OK;
		OLog.info("Starting NetworkController...");
		switch (_networkController.getState()) {
			case UNKNOWN:
				retStatus = _networkController.initialize();
				if ((retStatus == Status.FAILED) || 
					(retStatus == Status.UNKNOWN)) {
					OLog.err("Failed to initialize NetworkController");
					_networkController.destroy();
					return retStatus;
				}
				/* Allow fall-through since our goal is to get the Controller 
				 * to the READY state */
			case INACTIVE:
				retStatus = _networkController.start();
				if ((retStatus == Status.FAILED) || 
					(retStatus == Status.UNKNOWN)) {
					OLog.err("Failed to start NetworkController");
					_networkController.destroy();
					return retStatus;
				}
				break;
			default:
				OLog.info("NetworkController already started");
				break;
		}
		OLog.info("NetworkController started successfully");
		return retStatus;
	}
	
	/* Inner Classes */
	private class ControllerRunTask implements Runnable {
		
		@Override
		public void run() {
			OLog.info("Run Task started");
			
			for (int i = 0; i < 20; i++) {
				/* Start the network controller */
				if (startNetworkController() != Status.OK) {
					OLog.err("Failed to start network controller");
				}

				/* Start the Bluetooh controller */
				if (startBluetoothController() != Status.OK) {
					OLog.err("Failed to start Bluetooth controller");
					break;
				}

				/* Start the sensor array controller */
				if (_sensorArrayController.initialize() == Status.FAILED) {
					OLog.err("Failed to start sensor controller");
					break;
				}

				/* Start the camera controller */
				if (_cameraController.initialize() == Status.FAILED) {
					OLog.err("Failed to start the camera controller"); 
					break;
				}

				try {
					Thread.sleep(1750);
				} catch (InterruptedException e) {
					/* This allows the running thread to be interrupted whenever
					 * MainController.stop() is invoked by the UI */
					OLog.info("Controller Run Thread Interrupted");
				}
				
				/* Pull data from water quality sensors */
				OLog.info("Reading Sensor Data...");
				if ( _sensorArrayController.readSensorData() == Status.OK ) {
					_waterQualityDataAvailable = true;
					_eventHandler.onDataAvailable();
				}

				/* Upload sensor data to server */
				if ( _waterQualityDataAvailable == true ) {
					if ( _networkController.getState() == ControllerState.READY ) {
						_networkController.send(DEFAULT_DATA_SERVER_URL,
								new HttpEncWaterQualityData(_waterQualityData));	
					}
					_waterQualityDataAvailable = false;
				}

				/* Pull data from the phone's camera */
				OLog.info("Capturing Chem Strip Image...");
				if ( _cameraController.captureImage() == Status.OK ) {
					_chemPresenceDataAvailable = true;
				}

				try {
					Thread.sleep(1750);
				} catch (InterruptedException e) {
					/* This allows the running thread to be interrupted whenever
					 * MainController.stop() is invoked by the UI */
					OLog.info("Controller Run Thread Interrupted");
				}
				
				/* Upload the chemical presence data to server */
				if ( _chemPresenceDataAvailable == true ) {
					if ( _networkController.getState() == ControllerState.READY ) {
						_networkController.send( DEFAULT_IMAGE_SERVER_URL,
								new HttpEncChemicalPresenceData(_chemPresenceData) );	
					}
					_chemPresenceDataAvailable = false;
				}
				
				try {
					Thread.sleep(7500);
				} catch (InterruptedException e) {
					/* This allows the running thread to be interrupted whenever
					 * MainController.stop() is invoked by the UI */
					OLog.info("Controller Run Thread Interrupted");
					break;
				}
			}
			
			unloadSubControllers(_parentActivity);
			
			OLog.info("Run Task finished");
		}
	}
}
