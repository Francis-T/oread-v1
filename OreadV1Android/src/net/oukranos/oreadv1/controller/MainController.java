package net.oukranos.oreadv1.controller;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import android.content.Context;
import net.oukranos.oreadv1.interfaces.AbstractController;
import net.oukranos.oreadv1.interfaces.MainControllerEventHandler;
import net.oukranos.oreadv1.interfaces.MethodEvaluatorIntf;
import net.oukranos.oreadv1.types.ChemicalPresenceData;
import net.oukranos.oreadv1.types.ControllerState;
import net.oukranos.oreadv1.types.ControllerStatus;
import net.oukranos.oreadv1.types.DataStore;
import net.oukranos.oreadv1.types.DataStoreObject;
import net.oukranos.oreadv1.types.MainControllerInfo;
import net.oukranos.oreadv1.types.SiteDeviceData;
import net.oukranos.oreadv1.types.SiteDeviceImage;
import net.oukranos.oreadv1.types.SiteDeviceReportData;
import net.oukranos.oreadv1.types.Status;
import net.oukranos.oreadv1.types.WaterQualityData;
import net.oukranos.oreadv1.types.config.Configuration;
import net.oukranos.oreadv1.types.config.Data;
import net.oukranos.oreadv1.types.config.Procedure;
import net.oukranos.oreadv1.types.config.Task;
import net.oukranos.oreadv1.types.config.TriggerCondition;
import net.oukranos.oreadv1.util.ConditionEvaluator;
import net.oukranos.oreadv1.util.ConfigXmlParser;
import net.oukranos.oreadv1.util.OreadLogger;

public class MainController extends AbstractController implements MethodEvaluatorIntf {
	public static final long DEFAULT_SLEEP_INTERVAL = 900000; /* 15m * 60s * 1000ms = 900000 ms */
	
	/* Get an instance of the OreadLogger class to handle logging */
	private static final OreadLogger OLog = OreadLogger.getInstance();
	
	private static MainController _mainControllerInstance = null;
	private Thread _controllerRunThread = null;
	private Runnable _controllerRunTask = null;

	private WaterQualityData _waterQualityData = null;
	@SuppressWarnings("unused")
	private boolean _waterQualityDataAvailable = false;
	private ChemicalPresenceData _chemPresenceData = null;
	@SuppressWarnings("unused")
	private boolean _chemPresenceDataAvailable = false;
	private SiteDeviceData _siteDeviceData = null;
	private SiteDeviceImage _siteDeviceImage = null;
	
	private BluetoothController _bluetoothController = null;
	private SensorArrayController _sensorArrayController = null;
	private CameraController _cameraController = null;
	private NetworkController _networkController = null;
	private AutomationController _deviceController = null;

	private MainControllerInfo _mainInfo = null;
	private List<MainControllerEventHandler> _eventHandlers = null;

	/*************************/
	/** Initializer Methods **/
	/*************************/
	private MainController(Context parent) {
		/* Set the main controller's base parameters */
		this.setName("main");
		this.setType("system");
		this.setState(ControllerState.UNKNOWN);
		
		/* Set the main controller info with a dummy configuration */
		this._mainInfo = new MainControllerInfo(new Configuration("dummy"), new DataStore());
		
		/* Set the context object */
		this._mainInfo.setContext(parent);
		
		/* Initialize list of event handlers */
		this._eventHandlers = new ArrayList<MainControllerEventHandler>();
		
		return;
	}
	
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
		
		/* Store all Config data objects in the DataStore */
		List<Data> dataList = this._mainInfo.getConfig().getDataList();
		for (Data d : dataList) {
			this._mainInfo.getDataStore().add(d.getId(), d.getType(), d.getValue());
		}
		
		/* Set the context object */
		this._mainInfo.setContext(parent);
		
		/* Initialize list of event handlers */
		this._eventHandlers = new ArrayList<MainControllerEventHandler>();
		
		return;
	}
	
	private MainController(Context parent, Configuration configuration) {
		/* Set the main controller's base parameters */
		this.setName("main");
		this.setType("system");
		this.setState(ControllerState.UNKNOWN);
		
		/* Set the main controller info */
		this._mainInfo = new MainControllerInfo(configuration, new DataStore());
		
		/* Store all Config data objects in the DataStore */
		List<Data> dataList = this._mainInfo.getConfig().getDataList();
		for (Data d : dataList) {
			this._mainInfo.getDataStore().add(d.getId(), d.getType(), d.getValue());
		}
		
		/* Set the context object */
		this._mainInfo.setContext(parent);
		
		/* Initialize list of event handlers */
		this._eventHandlers = new ArrayList<MainControllerEventHandler>();
		
		return;
	}
	
	public static MainController getInstance(Context parent) {
		if (_mainControllerInstance == null) {
			_mainControllerInstance = new MainController(parent);
		}
		
		return _mainControllerInstance;
	}
	
	public static MainController getInstance(Context parent, String configPath) {
		if (_mainControllerInstance == null) {
			_mainControllerInstance = new MainController(parent, configPath);
		}
		
		return _mainControllerInstance;
	}
	
	public static MainController getInstance(Context parent, Configuration configuration) {
		if (_mainControllerInstance == null) {
			_mainControllerInstance = new MainController(parent, configuration);
		}
		
		return _mainControllerInstance;
	}

	/********************************/
	/** AbstractController Methods **/
	/********************************/
	@Override
	public Status initialize(Object initializer) {
		this.setState(ControllerState.UNKNOWN);
		
		if (initializer == null) {
			OLog.err("Invalid initializer object");
			return Status.FAILED;
		}
		
		String initObjClass = initializer.getClass().getSimpleName();
		if (initObjClass.equals("Configuration") == false) {
			OLog.err("Invalid initializer object (expected Configuration): " 
					+ initObjClass);
			return Status.FAILED;
		}
		
		Configuration config = (Configuration) initializer;
		
		/* Assimilate the config file */
		if (_mainInfo == null) {
			_mainInfo = new MainControllerInfo(config, new DataStore());
		} else {
			_mainInfo.setConfig(config);
		}
		
		/* Store all Config data objects in the DataStore */
		List<Data> dataList = _mainInfo.getConfig().getDataList();
		for (Data d : dataList) {
			_mainInfo.getDataStore().add(d.getId(), d.getType(), d.getValue());
		}
		
		OLog.info("MainController Initialized.");
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
			
			this.writeInfo("Command Performed: Initialized Subcontrollers");
			this.setState(ControllerState.READY);
		} else if (shortCmdStr.equals("destSubControllers")) {
			if ( this.unloadSubControllers() != Status.OK ) {
				this.writeErr("Failed to dest subcontrollers");
				return this.getControllerStatus();
			}

			this.writeInfo("Command Performed: Destroy Subcontrollers");
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
			this.writeInfo("Command Performed: Wait for " + sleepTime + "ms");
		} else if (shortCmdStr.equals("processImage")) {
			processImageData(_chemPresenceData);
			this.writeInfo("Command Performed: Process Image Data");
		} else if (shortCmdStr.equals("clearImage")) {
			clearImageData();
			this.writeInfo("Command Performed: Clear Image Data");
		} else if (shortCmdStr.equals("processData")) {
			processWaterQualityData(_waterQualityData);
			this.writeInfo("Command Performed: Process Water Quality Data");
		} else if (shortCmdStr.equals("clearData")) {
			clearWaterQualityData();
			this.writeInfo("Command Performed: Clear Water Quality Data");
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
	
	/********************************/
	/** MethodEvalutorIntf Methods **/
	/********************************/
	@Override
	public DataStoreObject evaluate(String methodName) {
		// TODO Auto-generated method stub
		
		if (methodName.equals("getCurrentHour()")) {
			Calendar c = Calendar.getInstance();
			Integer hour = c.get(Calendar.HOUR_OF_DAY);
			
			return DataStoreObject.createNewInstance("getCurrentHour", "integer", hour);
		} else if (methodName.equals("getCurrentMinute()")) {
			Calendar c = Calendar.getInstance();
			Integer minute = c.get(Calendar.MINUTE);
			
			return DataStoreObject.createNewInstance("getCurrentMinute", "integer", minute);
		}
		
		
		return DataStoreObject.createNewInstance("default", "string", "default");
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
			_controllerRunThread.join(10000);
		} catch (InterruptedException e) {
			OLog.info("Controller Run Thread Interrupted");
		}
		
		_controllerRunThread = null;
		
		/* Unload the subcontrollers */
		try {
			unloadSubControllers();
		} catch (Exception e) {
			OLog.err("Exception ocurred: " + e.getMessage());
		}
		
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
	
	public long getSleepInterval() {
		DataStore ds = this._mainInfo.getDataStore();
		if (ds == null) {
			OLog.warn("DataStore is null!");
			return DEFAULT_SLEEP_INTERVAL;
		}
		
		DataStoreObject d = 
				(DataStoreObject) ds.retrieve("custom_sleep_interval");
		if ( d == null ) {
			OLog.warn("DataStoreObject is null!");
			return DEFAULT_SLEEP_INTERVAL;
		}
		
		if ( d.getType().equals("long") == false ) {
			OLog.warn("DataStoreObject type is incorrect: " + d.getType());
			return DEFAULT_SLEEP_INTERVAL;
		}
		
		Long interval = null;
		try {
			interval = Long.decode((String)(d.getObject()));
		} catch (NumberFormatException e) {
			interval = DEFAULT_SLEEP_INTERVAL;
		}
		
		return interval;
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
		_siteDeviceData = new SiteDeviceData("DV862808028030255", "test");
		_siteDeviceImage = new SiteDeviceImage("DV862808028030255", "test", "", "");
		
		this._mainInfo.getDataStore().add("hg_as_detection_data", "ChemicalPresenceData", _chemPresenceData);
		this._mainInfo.getDataStore().add("h2o_quality_data", "WaterQualityData", _waterQualityData);
		this._mainInfo.getDataStore().add("sendable_data_site", "SiteDeviceData", _siteDeviceData);
		this._mainInfo.getDataStore().add("sendable_image_site", "SiteDeviceImage", _siteDeviceImage);
		
		/* Initialize all sub-controllers here */
		_bluetoothController = BluetoothController.getInstance(this._mainInfo);
		this._mainInfo.addSubController(_bluetoothController);
		
		_networkController = NetworkController.getInstance(this._mainInfo);
		this._mainInfo.addSubController(_networkController);
		
		_cameraController = CameraController.getInstance(this._mainInfo);
		this._mainInfo.addSubController(_cameraController);

		_sensorArrayController = SensorArrayController.getInstance(this._mainInfo);
		this._mainInfo.addSubController(_sensorArrayController);
		
		_deviceController = AutomationController.getInstance(this._mainInfo);
		this._mainInfo.addSubController(_deviceController);
		
		_bluetoothController.initialize(null);
		_networkController.initialize(null);
		_cameraController.initialize(null);
		_sensorArrayController.initialize(null);
		_deviceController.initialize(null);
		
		return Status.OK;
	}
	
	private Status unloadSubControllers() {
		/* Cleanup sub-controllers */
		if ( _deviceController != null ) {
			_deviceController.destroy();
		}
		
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
		this._mainInfo.getDataStore().remove("sendable_data_site");
		this._mainInfo.getDataStore().remove("sendable_image_site");
		this._mainInfo.getDataStore().remove("live_data_url");
		
		return Status.OK;
	}
	
	@SuppressWarnings("unused")
	private Status startBluetoothController() {
		OLog.info("Starting BluetoothController...");
		
		if (_bluetoothController == null) {
			OLog.err("BluetoothController is NULL");
			return Status.FAILED;
		}
		
		_bluetoothController.initialize(null);

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
				retStatus = _networkController.initialize(null);
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
	
	private void processImageData(ChemicalPresenceData d) {
		_siteDeviceImage.setCaptureFile(d.getCaptureFileName(), d.getCaptureFilePath());
		_siteDeviceImage.addReportData(new SiteDeviceReportData("photo", "", 0f, ""));
		
		return;
	}
	
	private void processWaterQualityData(WaterQualityData d) {
		
		_siteDeviceData.addReportData(new SiteDeviceReportData("pH", "", (float)(d.pH), "OK"));
		_siteDeviceData.addReportData(new SiteDeviceReportData("DO2", "mg/L", (float)(d.dissolved_oxygen), "OK"));
		_siteDeviceData.addReportData(new SiteDeviceReportData("Conductivity", "uS/cm", (float)(d.conductivity), "OK"));
		_siteDeviceData.addReportData(new SiteDeviceReportData("Temperature", "deg C", (float)(d.temperature), "OK"));
		_siteDeviceData.addReportData(new SiteDeviceReportData("Turbidity", "NTU", (float)(d.turbidity), "OK"));
		
		return;
	}
	
	private void clearWaterQualityData() {
		_siteDeviceData.clearReportData();
		
		return;
	}
	
	private void clearImageData() {
		_siteDeviceImage.clearReportData();
		
		return;
	}
	
	private void notifyRunTaskFinished() {
		if (_eventHandlers == null) {
			return;
		}
		
		for (MainControllerEventHandler ev : _eventHandlers) {
			ev.onFinish();
		}
		
		OLog.info("Run Task finished");
		return;
	}
	
	private MethodEvaluatorIntf getMethodEvaluator() {
		return this;
	}
	
	/*******************/
	/** Inner Classes **/
	/*******************/
	private class ControllerRunTask implements Runnable {
		private Configuration _runConfig = null;
		
		@Override
		public void run() {
			OLog.info("Run Task started");
			
			/* Load the main config for faster reference */
			_runConfig = _mainInfo.getConfig();
			
			List<Procedure> procList = this.loadProceduresToRun();
			
			/* Run each procedure in the procedure list */
			for (Procedure procedure : procList) {
				long procStart = System.currentTimeMillis();
				if (this.execute(procedure) != Status.OK) {
					OLog.err("Procedure run failed: " + procedure.toString());
					break;
				}
				long procEnd = System.currentTimeMillis();
				
				OLog.info("Procedure \"" 
							+ procedure.getId() 
							+ "\" Completed at " 
							+ Long.toString(procEnd-procStart) 
							+ " msecs");
			}
			
			/* Notify all event handlers that the MainController has finished
			 *   executing all procedures */
			notifyRunTaskFinished();
			
			return;
		}
		
		private Status execute(Procedure p) {
			Status retStatus = Status.FAILED;
			List<Task> taskList = p.getTaskList();
			
			for (Task t : taskList) {
				if (getState() == ControllerState.UNKNOWN) {
					OLog.info("Run task terminated.");
					retStatus = Status.OK;
					break;
				}
				
				OLog.info("Loaded task: " + t.toString() + 
						"( id: " + t.getId() +" )");
				
				/* Check first if the task is valid for execution */
				if (checkTaskValidity(t) == false) {
					OLog.err("Invalid task: " + t.toString());
					retStatus = Status.FAILED;
					break;
				}
				
				/* If this is a system task, then execute it using 
				 *   the MainController's own performCommand() method */
				if (t.getId().startsWith("system.main")) {
					ControllerStatus status = performCommand(t.getId(), t.getParams());
					if (status.getLastCmdStatus() != Status.OK) {
						OLog.err("Task failed: " + t.toString());
						OLog.err(status.toString());
						retStatus = Status.FAILED;
						break;
					}
					retStatus = Status.OK;
					OLog.info("Task Finished: " + t.toString());
					continue;
				}
				
				/* Get the sub controller for this task */
				AbstractController controller = _mainInfo.getSubController(t.getId());
				if (controller == null) {
					OLog.err("Invalid task: " + t.toString());
					retStatus = Status.FAILED;
					break;
				}
				
				/* Perform the command using the appropriate subcontroller */
				ControllerStatus status = controller.performCommand(t.getId(), t.getParams());
				if (status.getLastCmdStatus() != Status.OK) {
					OLog.err("Task failed: " + t.toString());
					OLog.err(status.toString());
					retStatus = Status.FAILED;
					break;
				}
				retStatus = Status.OK;
				OLog.info("Task Finished: " + t.toString());
			}
			
			OLog.info("Procedure Finished: " + p.getId());
			return retStatus;
		}
		
		private List<Procedure> loadProceduresToRun() {
			List<Procedure> procList = new ArrayList<Procedure>();

			ConditionEvaluator condEval = new ConditionEvaluator();
			condEval.setDataStore(_mainInfo.getDataStore());
			condEval.setMethodEvaluator(getMethodEvaluator());
			
			/* Load the run condition list */
			List<TriggerCondition> conditions = _runConfig.getConditionList();
			for (TriggerCondition t : conditions) {
				boolean result = false;
				
				/* Evaluate the condition */
				OLog.info("Condition Found: " + t.getCondition());
				result = condEval.evaluate(t.getCondition());
				
				if (result == true) {
					String procName = t.getProcedure();
					
					if (procName == null) {
						OLog.warn("Procedure is null for trigger: "  
									+ t.getId());
						continue;
					}
					
					if (procName.isEmpty()) {
						OLog.warn("Procedure is blank for trigger: "  
									+ t.getId());
						continue;
					}
					
					/* Add this procedure to the run list */
					procList.add(_runConfig.getProcedure(procName));
				}
			}
			
			return procList;

//			List<Procedure> procList = new ArrayList<Procedure>();
//			procList.add(_runConfig.getProcedure("default"));
//			return procList;
		}
		
		private boolean checkTaskValidity(Task t) {
			if (t == null) {
				return false;
			}
			
			/* Break apart the taskId */
			String taskIdArr[] = t.getId().split("\\.");
			if (taskIdArr.length < 2) {
				OLog.info("Invalid length: " + taskIdArr.length);
				return false;
			}
			
			if ((taskIdArr[0] == null) || (taskIdArr[1] == null)) {
				return false;
			}
			
			return true;
		}
	}
}
