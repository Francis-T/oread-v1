package net.oukranos.oreadv1.controller;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;

import android.content.Context;
import net.oukranos.oreadv1.android.AndroidStoredDataBridge;
import net.oukranos.oreadv1.interfaces.AbstractController;
import net.oukranos.oreadv1.interfaces.MainControllerEventHandler;
import net.oukranos.oreadv1.interfaces.MethodEvaluatorIntf;
import net.oukranos.oreadv1.types.CachedReportData;
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
	private ChemicalPresenceData _chemPresenceData = null;
	private SiteDeviceData _siteDeviceData = null;
	private SiteDeviceImage _siteDeviceImage = null;
	
	private BluetoothController _bluetoothController = null;
	private SensorArrayController _sensorArrayController = null;
	private CameraController _cameraController = null;
	private NetworkController _networkController = null;
	private AutomationController _deviceController = null;
	private DatabaseController _databaseController = null;

	private MainControllerInfo _mainInfo = null;
	private List<MainControllerEventHandler> _eventHandlers = null;
	
	private long _procStart = 0;
	private long _procEnd = 0; 

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
			
		} else if (shortCmdStr.equals("waitUntilTimeSinceStarted")) {
			long elapsedTimeSinceStart = System.currentTimeMillis() - _procStart;
			long sleepTime = Long.valueOf(paramStr) - elapsedTimeSinceStart;
			if (sleepTime < 0) {
				sleepTime = 0;
			}
			
			OLog.info("Sleeping for " + sleepTime + " ms...");
			try {
				Thread.sleep(sleepTime);
			} catch (Exception e) {
				OLog.warn("Something went wrong.");
				e.printStackTrace();
			}
			long stopTime = System.currentTimeMillis();
			OLog.info("Thread woke up " + (stopTime-elapsedTimeSinceStart) + "ms later." );
			this.writeInfo("Command Performed: Wait for " + sleepTime + "ms");
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
		} else if (shortCmdStr.equals("processMultipleCachedData")) {
			processMultipleCachedData();
			this.writeInfo("Command Performed: Sent Cached Data to Server");
		} else if (shortCmdStr.equals("processCachedImage")) {
			processCachedImage();
			this.writeInfo("Command Performed: Sent Cached Image to Server");

		/** XXX ********************** XXX **/
		/** XXX BEGIN Testing Commands XXX **/
		/** XXX ********************** XXX **/
		} else if (shortCmdStr.equals("generateWaterQualityData")) {
			generateWaterQualityData();
			this.writeInfo("Command Performed: Read* Water Quality Data");
		} else if (shortCmdStr.equals("processCachedData")) {
			processCachedReportData();
			this.writeInfo("Command Performed: Process* Water Quality Data");
		} else if (shortCmdStr.equals("updateCachedData")) {
			updateCachedReportData();
			this.writeInfo("Command Performed: Updated Cached Data");
		} else if (shortCmdStr.equals("unsendData")) {
			unsendSentData();
			this.writeInfo("Command Performed: Refreshed Cached Data Sent Status");
		} else if (shortCmdStr.equals("unsendImages")) {
			unsendSentImages();
			this.writeInfo("Command Performed: Refreshed Cached Images Sent Status");
		/** XXX ******************** XXX **/
		/** XXX END Testing Commands XXX **/
		/** XXX ******************** XXX **/
			
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
		} else if (methodName.equals("isWaterQualityDataAvailable()")) {
			String result = null;
			AndroidStoredDataBridge pDataStore 
				= AndroidStoredDataBridge.getInstance(_mainInfo.getContext());
			if (pDataStore != null) {
				result = pDataStore.get("WQ_DATA_AVAILABLE");
			}
			
			/* Default to false */
			if (result == null) {
				result = "false";
			}
			
			return DataStoreObject.createNewInstance("isWaterQualityDataAvailable", "string", result);
		} else if (methodName.equals("isImageCaptureAvailable()")) {
			String result = null;
			AndroidStoredDataBridge pDataStore 
				= AndroidStoredDataBridge.getInstance(_mainInfo.getContext());
			if (pDataStore != null) {
				result = pDataStore.get("IMG_CAPTURE_AVAILABLE");
			}
			
			/* Default to false */
			if (result == null) {
				result = "false";
			}
			
			/* XXX */
			if ( !(result.equalsIgnoreCase("false") || 
					result.equalsIgnoreCase("true")) ) {
				result = "false";
			}
			
			return DataStoreObject.createNewInstance("isImageCaptureAvailable", "string", result);
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
	private CachedReportData _reportDataTemp = null;
	
	private Status initializeDataBuffers() {
		DataStore ds = _mainInfo.getDataStore();
		if (_mainInfo.getDataStore() == null) {
			return Status.FAILED;
		}
		
		
		
		/* Initialize the data buffer objects */
		_chemPresenceData = new ChemicalPresenceData(1);
		_waterQualityData = new WaterQualityData(1);
		_reportDataTemp = new CachedReportData();
		_siteDeviceData = new SiteDeviceData("DV862808028030255", "test");
		_siteDeviceImage = new SiteDeviceImage("DV862808028030255", "test", "", "");
		
		ds.add("hg_as_detection_data", "ChemicalPresenceData", _chemPresenceData);
		ds.add("h2o_quality_data", "WaterQualityData", _waterQualityData);
		ds.add("report_data_temp", "ReportDataTemp", _reportDataTemp);
		ds.add("site_device_data", "SiteDeviceData", _siteDeviceData);
		ds.add("site_device_image", "SiteDeviceImage", _siteDeviceImage);
		
		return Status.OK;
	}
	
	private Status initializeSubControllers() {
		if (initializeDataBuffers() != Status.OK) {
			OLog.err("Failed to initialise data buffers");
			return Status.FAILED;
		}
		
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
		
		_databaseController = DatabaseController.getInstance(this._mainInfo);
		this._mainInfo.addSubController(_databaseController);
		
		_bluetoothController.initialize(null);
		_networkController.initialize(null);
		_cameraController.initialize(null);
		_sensorArrayController.initialize(null);
		_deviceController.initialize(null);
		_databaseController.initialize(null);
		
		return Status.OK;
	}
	
	private Status unloadSubControllers() {
		/* Cleanup sub-controllers */
		if (_databaseController != null) {
			_databaseController.destroy();
			_databaseController = null;
		}
		
		if ( _deviceController != null ) {
			_deviceController.destroy();
			_deviceController = null;
		}
		
		if ( _sensorArrayController != null ) {
			_sensorArrayController.destroy();
			_sensorArrayController = null;
		}

		if ( _cameraController != null ) {
			_cameraController.destroy();
			_cameraController = null;
		}

		if ( _networkController != null ) {
			_networkController.destroy();
			_networkController = null;
		}
		
		if (_bluetoothController != null) {
			_bluetoothController.destroy();
			_bluetoothController = null;
		}

		/* Cleanup data buffers */
		_waterQualityData = null;
		_chemPresenceData = null;
		
		this._mainInfo.getDataStore().remove("h2o_quality_data");
		this._mainInfo.getDataStore().remove("hg_as_detection_data");
		this._mainInfo.getDataStore().remove("report_data_temp");
		this._mainInfo.getDataStore().remove("site_device_data");
		this._mainInfo.getDataStore().remove("site_device_image");
		this._mainInfo.getDataStore().remove("live_data_url");

		/* Add persistent data flag for unsent water quality data availability */
		AndroidStoredDataBridge pDataStore 
			= AndroidStoredDataBridge.getInstance(_mainInfo.getContext());
		if (pDataStore != null) {
			pDataStore.remove("LAST_WQ_DATA");
		}
		
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

		/* Add persistent data flag for unsent image capture availability */
		AndroidStoredDataBridge pDataStore 
			= AndroidStoredDataBridge.getInstance(_mainInfo.getContext());
		if (pDataStore == null) {
			return;
		}
		pDataStore.put("IMG_CAPTURE_AVAILABLE", "true");
		
		return;
	}
	
	private void processWaterQualityData(WaterQualityData d) {
		/* Get the stored SiteDeviceData object */
		SiteDeviceData siteData = 
				(SiteDeviceData) getStoredObject("site_device_data");
		if (siteData == null) {
			writeErr("SiteDeviceData object not found");
			return;
		}
		
		/* TODO Validate the water quality parameters */
		
		/* Add the water quality parameters as report data */
		SiteDeviceReportData repData;
		
		repData = new SiteDeviceReportData("pH", "", 
				(float)(d.pH), "OK");
		siteData.addReportData(repData);
		
		repData = new SiteDeviceReportData("DO2", "mg/L", 
				(float)(d.dissolved_oxygen), "OK");
		siteData.addReportData(repData);
		
		repData = new SiteDeviceReportData("Conductivity", "uS/cm", 
				(float)(d.conductivity), "OK");
		siteData.addReportData(repData);
		
		repData = new SiteDeviceReportData("Temperature", "deg C", 
				(float)(d.temperature), "OK");
		siteData.addReportData(repData);
		
		repData = new SiteDeviceReportData("Turbidity", "NTU", 
				(float)(d.turbidity), "OK");
		siteData.addReportData(repData);
		
		/* Add persistent data flag for unsent water quality data availability */
		AndroidStoredDataBridge pDataStore 
			= AndroidStoredDataBridge.getInstance(_mainInfo.getContext());
		if (pDataStore == null) {
			return;
		}
		pDataStore.put("WQ_DATA_AVAILABLE", "true");
		
		/* Consolidate all water quality params in one string */
		StringBuilder sb = new StringBuilder();
		sb.append(Double.toString(d.pH) + ",");
		sb.append(Double.toString(d.dissolved_oxygen) + ",");
		sb.append(Double.toString(d.conductivity) + ",");
		sb.append(Double.toString(d.temperature) + ",");
		sb.append(Double.toString(d.turbidity));
		
		/* Store last obtained data for quick display upon app screen reload */
		pDataStore.put("LAST_WQ_DATA", sb.toString());
		OLog.info("Saved Water Quality Data: " + pDataStore.get("LAST_WQ_DATA"));
		
		return;
	}
	
	private void clearWaterQualityData() {
		/* Get the stored SiteDeviceData object */
		SiteDeviceData siteData = 
				(SiteDeviceData) getStoredObject("site_device_data");
		if (siteData == null) {
			writeErr("SiteDeviceData object not found");
			return;
		}
		
		/* Clear all report data in the SiteDeviceData object */
		siteData.clearReportData();
		
		return;
	}
	
	private void clearImageData() {
		/* Get the stored SiteDeviceData object */
		SiteDeviceImage siteImage = 
				(SiteDeviceImage) getStoredObject("site_device_image");
		if (siteImage == null) {
			writeErr("SiteDeviceImage object not found");
			return;
		}
		
		/* Clear all report data in the SiteDeviceImage object */
		siteImage.clearReportData();
		
		return;
	}
	
	private void processMultipleCachedData() {
		int recIdList[] = new int[10];

		/* Get the stored SiteDeviceData object */
		SiteDeviceData siteData = 
				(SiteDeviceData) getStoredObject("site_device_data");
		if (siteData == null) {
			writeErr("SiteDeviceData object not found");
			return;
		}

		/* Start querying the database */
		if (_databaseController.startQuery("h2o_quality") != Status.OK) {
			return;
		}
		
		/* Fetch data into temporary storage */
		CachedReportData crDataTemp = null;
		int i = 0;
		for (i = 0; i < 10; i++) {
			/* Fetch the cached report data */
			crDataTemp = new CachedReportData();
			Status status = _databaseController.fetchReportData(crDataTemp);
			if (status != Status.OK) {
				writeWarn("No more report data found");	
				break;
			}
			
			/* Create the report data part from the cached report data */
			SiteDeviceReportData reportDataTemp 
				= new SiteDeviceReportData("", "", 0.0f, "");
			status 
				= reportDataTemp.decodeFromJson(crDataTemp.getData());
			if (status != Status.OK) {
				break;
			}
			
			/* Add the report data part to the site device data */
			siteData.addReportData(reportDataTemp);
			
			/* Add the record id to the list of records to be updated upon 
			 *  successful sending */ 
			recIdList[i] = crDataTemp.getId();
		}
		
		/* Stop querying the database */
		if (_databaseController.stopQuery() != Status.OK) {
			return;
		}
		
		/* TODO Do something with the accumulated report data (e.g. send to the server) */
		if (i == 0) {
			return;
		}
		
//		OLog.info("Sending data to server: \n" + siteData.encodeToJsonString());
		if (_networkController != null) {
			String url = null;
			String data = null;
			
			try {
				url = (String) _mainInfo.getDataStore()
						.retrieveObject("live_data_url");
				_networkController.send(url, siteData);
			} catch (Exception e) {
				OLog.err("Exception occurred: " + e.getMessage());
			}
		}

		/* TODO Wait for a bit... */
		/* TODO ...Then retrieve the Network Subcontroller's last http response status code  */ 
		
		/* TODO If successfully sent to the server, update the records */
		for (Integer recId : recIdList) {
			_databaseController.updateRecord(recId.toString(), true);
		}
		
		/* Check the database if more unsent data remains;
		 * 	otherwise, update the persistent data flag to "false" */
		if (_databaseController.hasUnsentRecords("h2o_quality") == false) {		
			/* Add persistent data flag for unsent water quality data availability */
			AndroidStoredDataBridge pDataStore 
				= AndroidStoredDataBridge.getInstance(_mainInfo.getContext());
			if (pDataStore == null) {
				return;
			}
			pDataStore.put("WQ_DATA_AVAILABLE", "false");
		}
		
		writeInfo("Finished sending report data to server.");
		return;
	}
	
	private void processCachedImage() {
		/* Get the stored SiteDeviceData object */
		SiteDeviceImage siteImage = 
				(SiteDeviceImage) getStoredObject("site_device_image");
		if (siteImage == null) {
			writeErr("SiteDeviceImage object not found");
			return;
		}

		/* Start querying the database */
		if (_databaseController.startQuery("chem_presence") != Status.OK) {
			return;
		}
		
		/* Fetch the cached report data into temporary storage */
		CachedReportData crDataTemp = new CachedReportData();
		Status status = _databaseController.fetchReportData(crDataTemp);
		if (status != Status.OK) {
			writeWarn("No more report data found");
			return;
		}
		
		/* Stop querying the database */
		if (_databaseController.stopQuery() != Status.OK) {
			return;
		}
		
		/* Extract the file meta data from the cached report data */
		/*  For convenience, this is stored in the 'data' field as
		 * 	a comma-delimited string.
		 * 
		 *  TODO: Filepaths MAY have commas too, so we need to
		 *  	  add workarounds for those cases */
		String fileMetaData[] = crDataTemp.getData().split(",");
		if (fileMetaData.length != 2) {
			writeErr("Invalid file metadata: " + crDataTemp.getData());
			return;
		}
		
		/* Write the report data */
		siteImage.setCaptureFile(fileMetaData[0], fileMetaData[1]);
		siteImage.addReportData(new SiteDeviceReportData("photo", "", 0f, ""));
		
		/* Do something with the accumulated image data (e.g. send to the server) */
		OLog.info("Sending image to server");
		if (_networkController != null) {
			String url = null;
			String data = null;
			
			try {
				url = (String) _mainInfo.getDataStore()
						.retrieveObject("live_image_url");
				_networkController.send(url, siteImage);
			} catch (Exception e) {
				OLog.err("Exception occurred: " + e.getMessage());
			}
		}
		
		/* TODO Wait for a bit... */
		/* TODO ...Then retrieve the Network Subcontroller's last http response status code  */ 
		
		/* TODO If successfully sent to the server, update the records */
		String recId = Integer.toString(crDataTemp.getId());
		_databaseController.updateRecord(recId, true);
		
		/* Check the database if more unsent data remains;
		 * 	otherwise, update the persistent data flag to "false" */
		if (_databaseController.hasUnsentRecords("chem_presence") == false) {		
			/* Add persistent data flag for unsent water quality data availability */
			AndroidStoredDataBridge pDataStore 
				= AndroidStoredDataBridge.getInstance(_mainInfo.getContext());
			if (pDataStore == null) {
				return;
			}
			pDataStore.put("IMG_CAPTURE_AVAILABLE", "false");
		}
		
		writeInfo("Finished sending image to server.");
		return;
	}
	
	private Object getStoredObject(String dataId) {
		DataStore dataStore = _mainInfo.getDataStore();
		if (dataStore == null) {
			writeErr("MainController DataStore unavailable");
			return null;
		}
		
		DataStoreObject dataObj = dataStore.retrieve(dataId);
		if (dataObj == null) {
			writeErr("DataStoreObject could not be retrieved");
			return null;
		}
		
		Object obj = dataObj.getObject();
		if (obj == null) {
			writeErr("Object could not be retrieved");
			return null;
		}
		
		return obj;
	}

	/** XXX ****************************** XXX **/
	/** XXX BEGIN: Testing Command Methods XXX **/
	/** XXX ****************************** XXX **/
	private void unsendSentData() {
		for (int i = 150; i < 201; i++) {
			_databaseController.updateRecord(Integer.toString(i), false);
		}		
		
		/* Add persistent data flag for unsent water quality data availability */
		AndroidStoredDataBridge pDataStore 
			= AndroidStoredDataBridge.getInstance(_mainInfo.getContext());
		if (pDataStore == null) {
			return;
		}
		pDataStore.put("WQ_DATA_AVAILABLE", "true");
		
		return;
	}
	
	private void unsendSentImages() {
		for (int i = 201; i < 220; i++) {
			_databaseController.updateRecord(Integer.toString(i), false);
		}		
		
		/* Add persistent data flag for unsent water quality data availability */
		AndroidStoredDataBridge pDataStore 
			= AndroidStoredDataBridge.getInstance(_mainInfo.getContext());
		if (pDataStore == null) {
			return;
		}
		pDataStore.put("IMG_CAPTURE_AVAILABLE", "true");
		
		return;
	}
	
	private void generateWaterQualityData() {
		_waterQualityData.pH = 7.00f + new Random().nextFloat();
		_waterQualityData.dissolved_oxygen = 9.08f + new Random().nextFloat();
		_waterQualityData.conductivity = 100.0f + new Random().nextFloat();
		_waterQualityData.temperature = 27.6f + new Random().nextFloat();
		_waterQualityData.turbidity = 0.0f + new Random().nextFloat();
		
		return;
	}
	
	private void processCachedReportData() {
		OLog.info("CachedData: " + _reportDataTemp.toString());
		
		DataStore ds = _mainInfo.getDataStore();
		if (ds.retrieve("lastProcessedCachedRecordId") != null) {
			ds.remove("lastProcessedCachedRecordId");
		}

		ds.add("lastProcessedCachedRecordId", "int", _reportDataTemp.getId());
		
		return;
	}
	
	private void updateCachedReportData() {
		DataStore ds = _mainInfo.getDataStore();
		
		Object obj = ds.retrieveObject("lastProcessedCachedRecordId");
		if (obj == null) {
			writeErr("No cached records have been processed yet");
			return;
		}
		
		Integer recId = (Integer) obj;
		_databaseController.updateRecord(recId.toString(), true);
		
		return;
		
	}
	/** XXX **************************** XXX **/
	/** XXX END: Testing Command Methods XXX **/
	/** XXX **************************** XXX **/
	
	
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
			
			_procStart = 0;
			_procEnd = 0;
			
			/* Run each procedure in the procedure list */
			for (Procedure procedure : procList) {
				_procStart = System.currentTimeMillis();
				if (this.execute(procedure) != Status.OK) {
					OLog.err("Procedure run failed: " + procedure.toString());
					break;
				}
				_procEnd = System.currentTimeMillis();
				
				OLog.info("Procedure \"" 
							+ procedure.getId() 
							+ "\" Completed at " 
							+ Long.toString(_procEnd-_procStart) 
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
					try {
						ControllerStatus status = performCommand(t.getId(), t.getParams());
						if (status.getLastCmdStatus() != Status.OK) {
							OLog.err("Task failed: " + t.toString());
							OLog.err(status.toString());
							retStatus = Status.FAILED;
							break;
						}
						retStatus = Status.OK;
						OLog.info("Task Finished: " + t.toString());
					} catch (Exception e) {
						OLog.err("Task failed: " + t.toString());
						OLog.err("Exception ocurred: " + e.getMessage() );
						retStatus = Status.FAILED;
						break;
					}
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
				try {
					ControllerStatus status = controller.performCommand(t.getId(), t.getParams());
					if (status.getLastCmdStatus() != Status.OK) {
						OLog.err("Task failed: " + t.toString());
						OLog.err(status.toString());
						retStatus = Status.FAILED;
						break;
					}
					retStatus = Status.OK;
					OLog.info("Task Finished: " + t.toString());
				} catch (Exception e) {
					OLog.err("Task failed: " + t.toString());
					OLog.err("Exception ocurred: " + e.getMessage() );
					retStatus = Status.FAILED;
					break;
				}
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
