package net.oukranos.oreadv1.controller;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import net.oukranos.oreadv1.interfaces.AbstractController;
import net.oukranos.oreadv1.interfaces.MainControllerEventHandler;
import net.oukranos.oreadv1.types.ChemicalPresenceData;
import net.oukranos.oreadv1.types.Configuration;
import net.oukranos.oreadv1.types.ControllerState;
import net.oukranos.oreadv1.types.ControllerStatus;
import net.oukranos.oreadv1.types.DataStore;
import net.oukranos.oreadv1.types.MainControllerInfo;
import net.oukranos.oreadv1.types.Status;
import net.oukranos.oreadv1.types.Task;
import net.oukranos.oreadv1.types.WaterQualityData;
import net.oukranos.oreadv1.util.ConfigXmlParser;
import net.oukranos.oreadv1.util.OLog;

public class MainController extends AbstractController {
	@SuppressWarnings("unused")
	private static final String DEFAULT_DATA_SERVER_URL = "http://miningsensors.ateneo.edu:8080/uploadData";
	@SuppressWarnings("unused")
	private static final String DEFAULT_IMAGE_SERVER_URL = "http://miningsensors.ateneo.edu:8080/uploadImage";
	
	private static MainController _mainControllerInstance = null;
	private Thread _controllerRunThread = null;
	private Runnable _controllerRunTask = null;

	private WaterQualityData _waterQualityData = null;
	@SuppressWarnings("unused")
	private boolean _waterQualityDataAvailable = false;
	private ChemicalPresenceData _chemPresenceData = null;
	@SuppressWarnings("unused")
	private boolean _chemPresenceDataAvailable = false;
	
	private BluetoothController _bluetoothController = null;
	private SensorArrayController _sensorArrayController = null;
	private CameraController _cameraController = null;
	private NetworkController _networkController = null;

	private MainControllerInfo _mainInfo = null;
	private List<MainControllerEventHandler> _eventHandlers = null;

	/*************************/
	/** Initializer Methods **/
	/*************************/
	private MainController(Context parent, String configPath) {
		/* Set the main controller's base parameters */
		this.setName("main");
		this.setType("system");
		this.setState(ControllerState.UNKNOWN);
		
		/* Set the main controller info */
		this._mainInfo = new MainControllerInfo(new Configuration("default"), new DataStore());
		try {
			ConfigXmlParser cfgParse = new ConfigXmlParser();
			cfgParse.parseXml(configPath, this._mainInfo.getConfig());
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		/* Set the context object */
		this._mainInfo.setContext(parent);
		
		/* Initialize list of event handlers */
		this._eventHandlers = new ArrayList<MainControllerEventHandler>();
		
		return;
	}
	
	public static MainController getInstance(Context parent, String configPath) {
		if (_mainControllerInstance == null) {
			_mainControllerInstance = new MainController(parent, configPath);
		}
		
		return _mainControllerInstance;
	}

	/********************************/
	/** AbstractController Methods **/
	/********************************/
	@Override
	public Status initialize() {
		this.setState(ControllerState.UNKNOWN);
		
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
		
		if (shortCmdStr.equals("initSubControllers")) {
			if ( this.getState() == ControllerState.READY ) {
				this.writeWarn("Already started");
				return this.getControllerStatus();
			}
			
			if ( this.initializeSubControllers() != Status.OK ) {
				this.writeErr("Failed to init subcontrollers");
				return this.getControllerStatus();
			}
			
			this.setState(ControllerState.READY);
		} else if (shortCmdStr.equals("destSubControllers")) {
			if ( this.unloadSubControllers() != Status.OK ) {
				this.writeErr("Failed to dest subcontrollers");
				return this.getControllerStatus();
			}
			
			this.setState(ControllerState.UNKNOWN);
		} else if (shortCmdStr.equals("runTask")) {
			/* Deconstruct the paramStr to retrieve the task to be run */
			String paramStrSplit[] = paramStr.split("\\?");
			if (paramStrSplit.length < 1) {
				this.writeErr("Malformed runTask string: " + paramStr);
				return this.getControllerStatus();
			}
			
			String taskCmdStr = paramStrSplit[0];
			String taskParamStr = "";
			
			if (paramStrSplit.length == 2) {
				taskParamStr = paramStrSplit[1];
			}
			
			String taskIdArr[] = taskCmdStr.split("\\.");
			if (taskIdArr.length < 2) {
				this.writeErr("Invalid runTask Id");
				return this.getControllerStatus();
			}
			
			if (taskIdArr[0] == null) {
				this.writeErr("Invalid runTask Id");
				return this.getControllerStatus();
			}
			
			if (taskIdArr[1] == null) {
				this.writeErr("Invalid runTask Id");
				return this.getControllerStatus();
			}
			
			AbstractController controller = _mainInfo.getSubController(taskIdArr[1], taskIdArr[0]);
			if (controller == null) {
				this.writeErr("Controller not found for runTask");
				return this.getControllerStatus();
			}
			
			controller.performCommand(taskCmdStr, taskParamStr);
			
		} else if (shortCmdStr.equals("wait")) {
			long sleepTime = Long.valueOf(paramStr);
			long startTime = System.currentTimeMillis();
			OLog.info("Sleeping for " + sleepTime + " ms...");
			try {
				Thread.sleep(sleepTime);
			} catch (Exception e) {
				OLog.warn("Something went wrong.");
				e.printStackTrace();
			}
			long stopTime = System.currentTimeMillis();
			OLog.info("Thread woke up " + (stopTime-startTime) + "ms later." );
		} else if (shortCmdStr.equals("receiveData")) {
			for (MainControllerEventHandler e : _eventHandlers) {
				e.onDataAvailable();
			}
		} else {
			this.writeErr("Unknown or invalid command: " + shortCmdStr);
		}

		return this.getControllerStatus();
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

	/********************/
	/** Public Methods **/
	/********************/
	public Status start() {
		OLog.info("MainController start()");
		if ( this.getState() == ControllerState.READY ) {
			return Status.ALREADY_STARTED;
		}

		if (this.initializeSubControllers() != Status.OK) {
			OLog.err("Failed to initialize subcontrollers");
			return Status.FAILED;
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

		this.setState(ControllerState.UNKNOWN);	
		
		if ( _controllerRunThread == null ) {
			OLog.err("MainController run thread unavailable");
			
			/* Unload the subcontrollers */
			unloadSubControllers();
			
			return Status.FAILED;
		}
	
		if ( _controllerRunThread.isAlive() == false ) {
			OLog.info("MainController run thread already stopped");
			
			/* Unload the subcontrollers */
			unloadSubControllers();
			
			_controllerRunThread = null;
			
			return Status.OK;
		}

		_controllerRunThread.interrupt();
		
		try {
			_controllerRunThread.join();
		} catch (InterruptedException e) {
			OLog.info("Controller Run Thread Interrupted");
		}
		
		_controllerRunThread = null;
		
		/* Unload the subcontrollers */
		unloadSubControllers();
		
		return Status.OK;
	}
	
	public void registerEventHandler(MainControllerEventHandler eventHandler) {
		if(_eventHandlers.contains(eventHandler)) {
			OLog.warn("Handler already registered");
			return;
		}
		
		_eventHandlers.add(eventHandler);
		
		return;
	}
	
	public void unregisterEventHandler(MainControllerEventHandler eventHandler) {
		if(!_eventHandlers.contains(eventHandler)) {
			OLog.warn("Handler not yet registered");
			return;
		}
		
		_eventHandlers.remove(eventHandler);
		
		return;
	}
	
	public WaterQualityData getData() {
		if ( _waterQualityData == null ) {
			OLog.err("Data source is null");
			return null;
		}
		
		return new WaterQualityData(_waterQualityData);
	}
	

	/*********************/
	/** Private Methods **/
	/*********************/
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
	
	private Status initializeSubControllers() {
		/* Initialize data buffers */
		_chemPresenceData = new ChemicalPresenceData(1);
		_waterQualityData = new WaterQualityData(1);
		this._mainInfo.getDataStore().add("hg_as_detection_data", "ChemicalPresenceData", _chemPresenceData);
		this._mainInfo.getDataStore().add("h2o_quality_data", "WaterQualityData", _waterQualityData);
		
		/* Initialize all sub-controllers here */
		_bluetoothController = BluetoothController.getInstance(this._mainInfo);
		this._mainInfo.addSubController(_bluetoothController);
		
		_networkController = NetworkController.getInstance(this._mainInfo);
		this._mainInfo.addSubController(_networkController);
		
		_cameraController = CameraController.getInstance(this._mainInfo);
		this._mainInfo.addSubController(_cameraController);
		
		_sensorArrayController = SensorArrayController.getInstance(this._mainInfo);
		this._mainInfo.addSubController(_sensorArrayController);
		
		_bluetoothController.initialize();
		_networkController.initialize();
		_cameraController.initialize();
		_sensorArrayController.initialize();
		
		return Status.OK;
	}
	
	private Status unloadSubControllers() {
		/* Cleanup sub-controllers */
		if ( _sensorArrayController != null ) {
			_sensorArrayController.destroy();
		}

		if ( _cameraController != null ) {
			_cameraController.destroy();
		}

		if ( _networkController != null ) {
			_networkController.destroy();
		}
		
		if (_bluetoothController != null) {
			_bluetoothController.destroy();
		}

		/* Cleanup data buffers */
		_waterQualityData = null;
		_chemPresenceData = null;
		this._mainInfo.getDataStore().remove("h2o_quality_data");
		this._mainInfo.getDataStore().remove("hg_as_detection_data");
		
		return Status.OK;
	}
	
	@SuppressWarnings("unused")
	private Status startBluetoothController() {
		OLog.info("Starting BluetoothController...");
		
		if (_bluetoothController == null) {
			OLog.err("BluetoothController is NULL");
			return Status.FAILED;
		}
		
		_bluetoothController.initialize();

//		OLog.info("Getting paired device names...");
//		if ( _bluetoothController.getPairedDeviceNames() == null ) {
//			OLog.err("Failed to get paired device names");
//			return Status.FAILED;
//		}

		OLog.info("Connecting to device...");
		if ( _bluetoothController.connectToDeviceByName("HC-05") == Status.FAILED ) {
			OLog.err("Failed to connect to device");
			return Status.FAILED;
		}

		OLog.info("BluetoothController started successfully");
		return Status.OK;
	}
	
	@SuppressWarnings("unused")
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

    private void notifyRunTaskFinished() {
        if ( _eventHandlers == null ) {
            return;
        }

        /* Notify all event handlers */
        for ( MainControllerEventHandler ev : _eventHandlers ) {
            ev.onRunTaskFinished();
        }

        return;
    }
	
	/*******************/
	/** Inner Classes **/
	/*******************/
	private class ControllerRunTask implements Runnable {
		
		@Override
		public void run() {
			OLog.info("Run Task started");
			
			List<Task> taskList = _mainInfo.getConfig().getProcedure("default").getTaskList();
			
			for (int i = 0; i < 100; i++) {
				if (getState() == ControllerState.UNKNOWN) {
					OLog.info("Run task terminated.");
					break;
				}
				for (Task t : taskList) {
					if (getState() == ControllerState.UNKNOWN) {
						OLog.info("Run task terminated.");
						break;
					}
					
					OLog.info("Executing task: " + t.toString());
					/* Break apart the taskId */
					String taskId = t.getId();
					
					OLog.info("TASK ID: " + t.getId());
					
					if (t.getId().startsWith("system.main")) {
						performCommand(t.getId(), t.getParams());
						continue;
					}
					
					String taskIdArr[] = taskId.split("\\.");
					if (taskIdArr.length < 2) {
						OLog.info("Invalid length: " + taskIdArr.length);
						break;
					}
					
					if (taskIdArr[0] == null) {
						break;
					}
					
					if (taskIdArr[1] == null) {
						break;
					}
					
					AbstractController controller = _mainInfo.getSubController(taskIdArr[1], taskIdArr[0]);
					if (controller == null) {
						break;
					}
					
					ControllerStatus status = controller.performCommand(t.getId(), t.getParams());
					if (status.getLastCmdStatus() != Status.OK) {
						OLog.err(status.toString());
						break;
					}
					OLog.info("Task Finished: " + t.toString());
				}
			}
//			
//			_mainInfo.getConfig().getProcedure("default");
//			
//			for (int i = 0; i < 20; i++) {
//				/* Start the network controller */
//				if (startNetworkController() != Status.OK) {
//					OLog.err("Failed to start network controller");
//				}
//
//				/* Start the Bluetooh controller */
//				if (startBluetoothController() != Status.OK) {
//					OLog.err("Failed to start Bluetooth controller");
//					break;
//				}
//
//				/* Start the sensor array controller */
//				if (_sensorArrayController.initialize() == Status.FAILED) {
//					OLog.err("Failed to start sensor controller");
//					break;
//				}
//
//				/* Start the camera controller */
////				if (_cameraController.initialize() == Status.FAILED) {
////					OLog.err("Failed to start the camera controller"); 
////					break;
////				}
//
//				try {
//					Thread.sleep(1750);
//				} catch (InterruptedException e) {
//					/* This allows the running thread to be interrupted whenever
//					 * MainController.stop() is invoked by the UI */
//					OLog.info("Controller Run Thread Interrupted");
//				}
//				
//				/* Pull data from water quality sensors */
//				OLog.info("Reading Sensor Data...");
//				if ( _sensorArrayController.readAllSensors() == Status.OK ) {
//					_waterQualityDataAvailable = true;
//					_eventHandler.onDataAvailable();
//				}
//
//				/* Upload sensor data to server */
//				if ( _waterQualityDataAvailable == true ) {
//					if ( _networkController.getState() == ControllerState.READY ) {
//						_networkController.send(DEFAULT_DATA_SERVER_URL,
//								new HttpEncWaterQualityData(_waterQualityData));	
//					}
//					_waterQualityDataAvailable = false;
//				}
//
//				/* Pull data from the phone's camera */
////				OLog.info("Capturing Chem Strip Image...");
////				if ( _cameraController.captureImage() == Status.OK ) {
////					_chemPresenceDataAvailable = true;
////				}
////
////				try {
////					Thread.sleep(1750);
////				} catch (InterruptedException e) {
////					/* This allows the running thread to be interrupted whenever
////					 * MainController.stop() is invoked by the UI */
////					OLog.info("Controller Run Thread Interrupted");
////				}
////				
////				/* Upload the chemical presence data to server */
////				if ( _chemPresenceDataAvailable == true ) {
////					if ( _networkController.getState() == ControllerState.READY ) {
////						_networkController.send( DEFAULT_IMAGE_SERVER_URL,
////								new HttpEncChemicalPresenceData(_chemPresenceData) );	
////					}
////					_chemPresenceDataAvailable = false;
////				}
//				
//				try {
//					Thread.sleep(7500);
//				} catch (InterruptedException e) {
//					/* This allows the running thread to be interrupted whenever
//					 * MainController.stop() is invoked by the UI */
//					OLog.info("Controller Run Thread Interrupted");
//					break;
//				}
//			}
//			
		    /* Notify all event handlers that the run task is finished */
            notifyRunTaskFinished();
			OLog.info("Run Task finished");
		}
	}
}
