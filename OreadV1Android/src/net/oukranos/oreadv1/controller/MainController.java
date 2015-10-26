package net.oukranos.oreadv1.controller;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.oukranos.oreadv1.interfaces.AbstractController;
import net.oukranos.oreadv1.interfaces.IPersistentDataBridge;
import net.oukranos.oreadv1.interfaces.MainControllerEventHandler;
import net.oukranos.oreadv1.interfaces.MethodEvaluatorIntf;
import net.oukranos.oreadv1.interfaces.bridge.IConnectivityBridge;
import net.oukranos.oreadv1.interfaces.bridge.IDeviceInfoBridge;
import net.oukranos.oreadv1.types.CachedReportData;
import net.oukranos.oreadv1.types.ChemicalPresenceData;
import net.oukranos.oreadv1.types.ControllerState;
import net.oukranos.oreadv1.types.ControllerStatus;
import net.oukranos.oreadv1.types.DataStore;
import net.oukranos.oreadv1.types.DataStoreObject;
import net.oukranos.oreadv1.types.MainControllerInfo;
import net.oukranos.oreadv1.types.SiteDeviceData;
import net.oukranos.oreadv1.types.SiteDeviceImage;
import net.oukranos.oreadv1.types.Status;
import net.oukranos.oreadv1.types.WaterQualityData;
import net.oukranos.oreadv1.types.config.Configuration;
import net.oukranos.oreadv1.types.config.Procedure;
import net.oukranos.oreadv1.types.config.Task;
import net.oukranos.oreadv1.types.config.TriggerCondition;
import net.oukranos.oreadv1.util.ConditionEvaluator;

public class MainController extends AbstractController implements MethodEvaluatorIntf {
	public static final long DEFAULT_SLEEP_INTERVAL = 900000; /* 15m * 60s * 1000ms = 900000 ms */
	
	private static MainController _mainControllerInstance = null;
	private Thread _controllerRunThread = null;
	private Runnable _controllerRunTask = null;

	private WaterQualityData _waterQualityData = null;
	private ChemicalPresenceData _chemPresenceData = null;
	private SiteDeviceData _siteDeviceData = null;
	private SiteDeviceImage _siteDeviceImage = null;
	private CachedReportData _reportDataTemp = null;
	
	private Class<?> _subcontrollerClasses[] = 
	{
		BluetoothController.class,
		NetworkController.class,
		CameraController.class,
		SensorArrayController.class,
		AutomationController.class,
		DatabaseController.class,
		DataUploadController.class,
		DataProcessController.class
	};
	
	private List<MainControllerEventHandler> _eventHandlers = null;
	
	private long _procStart = 0;
	private long _procEnd = 0; 

	/*************************/
	/** Initializer Methods **/
	/*************************/
	private MainController() {
		/* Set the main controller's base parameters */
		this.setName("main");
		this.setType("system");
		this.setState(ControllerState.UNKNOWN);
		
		/* Initialize list of event handlers */
		this._eventHandlers = new ArrayList<MainControllerEventHandler>();
		
		return;
	}
	
	public static MainController getInstance(MainControllerInfo mainInfo) {
		if (mainInfo == null) {
			OLog.warn("Null input parameter/s" +
					" in MainController.getInstance()");
		}
		
		if (_mainControllerInstance == null) {
			_mainControllerInstance = new MainController();
		}
		
		_mainControllerInstance._mainInfo = mainInfo;
		
		return _mainControllerInstance;
	}

	/********************************/
	/** AbstractController Methods **/
	/********************************/
	@Override
	public Status initialize(Object initializer) {
		this.setState(ControllerState.INACTIVE);
		
		if (initializer == null) {
			OLog.err("Invalid initializer object in MainController.initialize()");
			return Status.FAILED;
		}
		
		String initObjClass = initializer.getClass().getSimpleName();
		if (initObjClass.equals("MainControllerInfo") == false) {
			OLog.err("Invalid initializer object (expected MainControllerInfo): " 
					+ initObjClass);
			return Status.FAILED;
		}

		/* Use the initializer object as the main controller info */
		_mainInfo = (MainControllerInfo) initializer;
		
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
		
		if (shortCmdStr.equals("start")) {
			if ( this.getState() == ControllerState.READY ) {
				this.writeWarn("Already started");
				return this.getControllerStatus();
			}
			
			if ( this.start() != Status.OK ) {
				this.writeErr("Failed to start MainController");
				return this.getControllerStatus();
			}
			
			this.writeInfo("Command Performed: Started MainController");
		} else if (shortCmdStr.equals("stop")) {
			if ( ( this.getState() == ControllerState.INACTIVE ) ||
				 ( this.getState() == ControllerState.UNKNOWN ) ){
				this.writeWarn("Already stopped");
			}
			
			if ( this.stop() != Status.OK ) {
				this.writeErr("Failed to stop MainController");
				return this.getControllerStatus();
			}
			
			this.writeInfo("Command Performed: Stopped MainController");
		} else if (shortCmdStr.equals("initSubControllers")) { // TODO
			if ( this.initializeSubControllers() != Status.OK ) {
				this.writeErr("Failed to init subcontrollers");
				return this.getControllerStatus();
			}
			
			this.writeInfo("Command Performed: Initialized Subcontrollers");
		} else if (shortCmdStr.equals("destSubControllers")) { // TODO
			if ( this.unloadSubControllers() != Status.OK ) {
				this.writeErr("Failed to dest subcontrollers");
				return this.getControllerStatus();
			}

			this.writeInfo("Command Performed: Destroy Subcontrollers");
			this.setState(ControllerState.UNKNOWN); // TODO
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
				OLog.warn("System wait interrupted: Something may have gone wrong.");
			}
			long stopTime = System.currentTimeMillis();
			OLog.info("Thread woke up " + (stopTime-startTime) + "ms later." );
			this.writeInfo("Command Performed: Wait for " + sleepTime + "ms");
		} else if (shortCmdStr.equals("receiveData")) {
			for (MainControllerEventHandler e : _eventHandlers) {
				e.onDataAvailable();
			}
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
			
		} else if (shortCmdStr.equals("savePersistentData")) {
			String dataParam[] = paramStr.split(",");
			if (dataParam.length == 2) {
				IPersistentDataBridge pDataStore = getPersistentDataBridge();
			
				pDataStore.remove(dataParam[0]);
				pDataStore.put(dataParam[0], dataParam[1]);
				
				this.writeInfo("Command Performed: Saved Persistent Data (" +
						dataParam[0] + " = " + dataParam[1]);
			} else {
				this.writeErr("Failed to Save Persistent Data");
			}
		} else if (shortCmdStr.equals("updateLastCalibTime")) {
			IPersistentDataBridge pDataStore = getPersistentDataBridge();
			
			pDataStore.remove("LAST_CALIB_TIME");
			String currentTime = Long.toString(System.currentTimeMillis());
			pDataStore.put("LAST_CALIB_TIME", currentTime);
			
			
			this.writeInfo("Command Performed: Updated last calibration time");
		} else if (shortCmdStr.equals("updateLastLfsbCaptureTime")) {
			IPersistentDataBridge pDataStore = getPersistentDataBridge();
			
			pDataStore.remove("LAST_LFSB_CAPTURE_TIME");
			String currentTime = Long.toString(System.currentTimeMillis());
			pDataStore.put("LAST_LFSB_CAPTURE_TIME", currentTime);
			
			
			this.writeInfo("Command Performed: Updated last LFSB capture time");
		} else if (shortCmdStr.equals("updateLastWQReadTime")) {
			IPersistentDataBridge pDataStore = getPersistentDataBridge();
			
			pDataStore.remove("LAST_WQ_READ_TIME");
			String currentTime = Long.toString(System.currentTimeMillis());
			pDataStore.put("LAST_WQ_READ_TIME", currentTime);
			
			
			this.writeInfo("Command Performed: Updated last water quality read time");
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
			IPersistentDataBridge pDataStore = getPersistentDataBridge();
			if (pDataStore != null) {
				result = pDataStore.get("WQ_DATA_AVAILABLE");
			}
			
			/* Default to false */
			if (result == null) {
				result = "false";
			}
			
			if (result.equals("")) {
				result = "false";
			}
			
			return DataStoreObject.createNewInstance("isWaterQualityDataAvailable", "string", result);
		} else if (methodName.equals("isImageCaptureAvailable()")) {
			String result = null;
			IPersistentDataBridge pDataStore = getPersistentDataBridge();
			if (pDataStore != null) {
				result = pDataStore.get("IMG_CAPTURE_AVAILABLE");
			}
			
			/* Default to false */
			if (result == null) {
				result = "false";
			}
			
			if (result.equals("")) {
				result = "false";
			}
			
			/* XXX */
			if ( !(result.equalsIgnoreCase("false") || 
					result.equalsIgnoreCase("true")) ) {
				result = "false";
			}
			
			return DataStoreObject.createNewInstance("isImageCaptureAvailable", "string", result);
		} else if (methodName.equals("getTimeSinceLastCalibration()")) {
			String result = null;
			IPersistentDataBridge pDataStore = getPersistentDataBridge();
			if (pDataStore != null) {
				result = pDataStore.get("LAST_CALIB_TIME");
			}
			
			if (result == null) {
				result = "0";
			}
			
			long lastCalibTime = 0l;
			
			try {
				lastCalibTime = Long.parseLong(result);
			} catch (Exception e) {
				lastCalibTime = 0l;
			}
			
			return DataStoreObject.createNewInstance("getTimeSinceLastCalibration", 
					"long", System.currentTimeMillis() - lastCalibTime );
		} else if (methodName.equals("getTimeSinceLastLfsbCapture()")) {
			String result = null;
			IPersistentDataBridge pDataStore = getPersistentDataBridge();
			if (pDataStore != null) {
				result = pDataStore.get("LAST_LFSB_CAPTURE_TIME");
			}
			
			if (result == null) {
				result = "0";
			}
			
			long lastLfsbCaptureTime = 0l;
			
			try {
				lastLfsbCaptureTime = Long.parseLong(result);
			} catch (Exception e) {
				lastLfsbCaptureTime = 0l;
			}
			
			return DataStoreObject.createNewInstance("getTimeSinceLastLfsbCapture", 
					"long", System.currentTimeMillis() - lastLfsbCaptureTime );
		} else if (methodName.equals("getTimeSinceLastWQRead()")) {
			String result = null;
			IPersistentDataBridge pDataStore = getPersistentDataBridge();
			if (pDataStore != null) {
				result = pDataStore.get("LAST_WQ_READ_TIME");
			}
			
			if (result == null) {
				result = "0";
			}
			
			long lastWQRead = 0l;
			
			try {
				lastWQRead = Long.parseLong(result);
			} catch (Exception e) {
				lastWQRead = 0l;
			}
			
			return DataStoreObject.createNewInstance("getTimeSinceLastWQRead", 
					"long", System.currentTimeMillis() - lastWQRead );
		} else if (methodName.equals("getCurrCalibrationState()")) {
			String result = null;
			IPersistentDataBridge pDataStore = getPersistentDataBridge();
			if (pDataStore != null) {
				result = pDataStore.get("CURR_CALIB_STATE");
			}
			
			if (result == null) {
				result = "0";
			}
			
			int currCalibState = 0;
			
			try {
				currCalibState = Integer.parseInt(result);
			} catch (Exception e) {
				currCalibState = 0;
			}
			
			return DataStoreObject.createNewInstance("getCurrCalibrationState", 
					"integer", currCalibState );
		} else if (methodName.equals("isAutosamplerActive()")) {
			String result = null;
			IPersistentDataBridge pDataStore = getPersistentDataBridge();
			if (pDataStore != null) {
				result = pDataStore.get("ASHG_READ_ACTIVE");
			}
			
			if (result == null) {
				result = "false";
			}
			
			if (result.equals("")) {
				result = "false";
			}
			
			return DataStoreObject.createNewInstance("isAutosamplerActive", 
					"string", result );
		} else if (methodName.equals("getTimeSinceAutosamplerActivation()")) {
			String result = null;
			IPersistentDataBridge pDataStore = getPersistentDataBridge();
			if (pDataStore != null) {
				result = pDataStore.get("ASHG_START_TIME");
			}
			
			if (result == null) {
				result = "0";
			}
			
			long startTime = 0l;
			
			try {
				startTime = Long.parseLong(result);
			} catch (Exception e) {
				startTime = 0l;
			}
			
			return DataStoreObject.createNewInstance("getTimeSinceAutosamplerActivation", 
					"long", System.currentTimeMillis() - startTime );
		} else if (methodName.equals("shouldCaptureImage()")) {
			String result = null;
			
			IPersistentDataBridge pDataStore = getPersistentDataBridge();
			if (pDataStore != null) {
				result = pDataStore.get("ASHG_READY_TO_CAPTURE");
			}
			
			if (result == null) {
				result = "false";
			}
			
			if (result.equals("")) {
				result = "false";
			}
			
			return DataStoreObject.createNewInstance("shouldCaptureImage", 
					"string", result );
		} else if (methodName.equals("getAutosamplerState()")) {
			String result = null;
			IPersistentDataBridge pDataStore = getPersistentDataBridge();
			if (pDataStore != null) {
				result = pDataStore.get("CURR_ASHG_STATE");
			}
			
			if (result == null) {
				result = "0";
			}
			
			int currAutosamplerState = 0;
			
			try {
				currAutosamplerState = Integer.parseInt(result);
			} catch (Exception e) {
				currAutosamplerState = 0;
			}
			
			return DataStoreObject.createNewInstance("getAutosamplerState", 
					"integer", currAutosamplerState );
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

		this.initializeRunTaskLoop();
		
		if ( this.startRunTaskLoop() == Status.FAILED ) {
			this.setState(ControllerState.INACTIVE);
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
		
		if (this.getState() == ControllerState.TERMINATING) {
			OLog.info("MainController already terminating");
			return Status.OK;
		}
		
		/* Set the controller state to Terminating */
		this.setState(ControllerState.TERMINATING);
		
		if ( _controllerRunThread == null ) {
			//OLog.err("MainController run thread unavailable"); TODO
			
			/* Unload the subcontrollers */
			unloadSubControllers();
			
			return Status.OK;
		}
	
		if ( _controllerRunThread.isAlive() == false ) {
			//OLog.info("MainController run thread already stopped"); TODO
			
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

		this.setState(ControllerState.INACTIVE);	
		
		/* Unload the subcontrollers */
		try {
			unloadSubControllers();
		} catch (Exception e) {
			OLog.err("Exception ocurred: " + e.getMessage());
		}
		
		this.setState(ControllerState.UNKNOWN);
		
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

	
	private IConnectivityBridge getConnectivityBridge() {
		IConnectivityBridge connBridge = (IConnectivityBridge) _mainInfo
				.getFeature("connectivity");
		if (connBridge == null) {
			return null;
		}
		
		if ( connBridge.isReady() == false ) {
			if (connBridge.initialize(_mainInfo) != Status.OK) {
				connBridge = null;
			}
		}
		
		return connBridge;
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
	
	private Status initializeDataBuffers() {
		DataStore ds = _mainInfo.getDataStore();
		if (_mainInfo.getDataStore() == null) {
			return Status.FAILED;
		}
		
		/* Initialize the data buffer objects */
		_chemPresenceData = new ChemicalPresenceData(1);
		_waterQualityData = new WaterQualityData(1);
		_reportDataTemp = new CachedReportData();

		String deviceId = "";
		
		IConnectivityBridge connBridge = getConnectivityBridge();
		if (connBridge != null) {
			deviceId = ("DV" + ((IDeviceInfoBridge)connBridge).getDeviceId());
		} else {
			deviceId = "TEST_DEVICE"; 
		}
		_siteDeviceData = new SiteDeviceData(deviceId, "test");
		_siteDeviceImage = new SiteDeviceImage(deviceId, "test", "", "");	
		
		
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
		
		if (_mainInfo.getSubcontrollerList().size() == 0) {
			/* Instantiate all subcontrollers first */
			for (int idx = 0; idx < _subcontrollerClasses.length; idx++) {
				Class<?> c = _subcontrollerClasses[idx];
				
				Class<?> methodArgs[] = { MainControllerInfo.class };
				try {
					Method getInstanceMethod 
						= c.getMethod("getInstance", methodArgs);
					
					if (_mainInfo.getSubcontrollerList().size() > 0) {
						
					}
					
					AbstractController s = null;
					s = (AbstractController) getInstanceMethod
							.invoke(null, _mainInfo);
					if (s.initialize(null) != Status.OK)
					{
						OLog.warn("Failed to initialize subcontroller: " + s.toString());
						return Status.FAILED;
					}
					_mainInfo.addSubController(s);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		
		/* Set state to READY once all subcontrollers are initialized */
		this.setState(ControllerState.READY);
		
		return Status.OK;
	}
	
	
	private Status unloadSubControllers() {
		/* Cleanup sub-controllers */
		for (AbstractController s : _mainInfo.getSubcontrollerList()) {
			String subcFullName = s.toString();
			if (s.destroy() != Status.OK) {
				OLog.warn("Failed to cleanup " + subcFullName);
			}
		}
		
		/* Clear all registered subcontrollers */
		_mainInfo.removeAllSubControllers();

		/* Cleanup data buffers */
		_waterQualityData = null;
		_chemPresenceData = null;

//		this._mainInfo.getDataStore().remove("h2o_quality_data");
//		this._mainInfo.getDataStore().remove("hg_as_detection_data");
//		this._mainInfo.getDataStore().remove("report_data_temp");
//		this._mainInfo.getDataStore().remove("site_device_data");
//		this._mainInfo.getDataStore().remove("site_device_image");
//		this._mainInfo.getDataStore().remove("live_data_url");

		/* Add persistent data flag for unsent water quality data availability */
		IPersistentDataBridge pDataStore = getPersistentDataBridge();
		if (pDataStore != null) {
			pDataStore.remove("LAST_WQ_DATA");
		}
		
		return Status.OK;
	}

	/** XXX ****************************** XXX **/
	/** XXX BEGIN: Testing Command Methods XXX **/
	/** XXX ****************************** XXX **/
	private void unsendSentData() {
		DatabaseController databaseController = (DatabaseController) _mainInfo
				.getSubController("db", "storage");
		if (databaseController == null) {
			OLog.err("No database controller available");
			return;
		}
		
		for (int i = 150; i < 201; i++) {
			databaseController.updateRecord(Integer.toString(i), false);
		}		
		
		/* Add persistent data flag for unsent water quality data availability */
		IPersistentDataBridge pDataStore = getPersistentDataBridge();
		if (pDataStore == null) {
			return;
		}
		pDataStore.put("WQ_DATA_AVAILABLE", "true");
		
		return;
	}
	
	private void unsendSentImages() {
		DatabaseController databaseController = (DatabaseController) _mainInfo
				.getSubController("db", "storage");
		if (databaseController == null) {
			OLog.err("No database controller available");
			return;
		}
		
		for (int i = 201; i < 220; i++) {
			databaseController.updateRecord(Integer.toString(i), false);
		}		
		
		/* Add persistent data flag for unsent water quality data availability */
		IPersistentDataBridge pDataStore = getPersistentDataBridge();
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
		DatabaseController databaseController = (DatabaseController) _mainInfo
				.getSubController("db", "storage");
		if (databaseController == null) {
			OLog.err("No database controller available");
			return;
		}
		
		DataStore ds = _mainInfo.getDataStore();
		
		Object obj = ds.retrieveObject("lastProcessedCachedRecordId");
		if (obj == null) {
			writeErr("No cached records have been processed yet");
			return;
		}
		
		Integer recId = (Integer) obj;
		databaseController.updateRecord(recId.toString(), true);
		
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
			
			/* Initialize the procedure execution timer variables */
			_procStart = 0;
			_procEnd = 0;
			
			/* Generate the runMap */
			//Map<TriggerCondition,Procedure> runMap = generateRunMap();

			/* Generate the runList */
			List<RunListElement> runList = generateRunList();
			
			/* Setup the condition evaluator */
			ConditionEvaluator condEval = new ConditionEvaluator();
			condEval.setDataStore(_mainInfo.getDataStore());
			condEval.setMethodEvaluator(getMethodEvaluator());
			
			/* Cycle through each item in the runList */
			int condIdx = 0;
			for ( RunListElement rle : runList ) {
				TriggerCondition cond = rle.getCondition();
				
				/* Evaluate the condition */
				OLog.info("Evaluating condition " + (++condIdx) + ": " + cond.getId());
				boolean result = condEval.evaluate(cond.getCondition());
				if (result == true) {
					/* Get the procedure associated with this
					 *  condition on the RunMap */
					Procedure procedure = rle.getProcedure();
					if (procedure == null) {
						OLog.warn("Invalid procedure for condition: "
									+ cond.getId());
						continue;
					}
					
					/* Update the procedure start time */
					_procStart = System.currentTimeMillis();
					
					/* Execute the procedure */
					if (executeProcedure(procedure) != Status.OK)
					{
						_procEnd = System.currentTimeMillis();
						
						OLog.err("Procedure execution failed: "
									+ procedure.getId());
						OLog.info("Procedure \"" 
									+ procedure.getId() 
									+ "\" Finished at " 
									+ Long.toString(_procEnd-_procStart) 
									+ " msecs");
						continue;
					}

					/* Update the procedure finish time */
					_procEnd = System.currentTimeMillis();
					
					/* TODO Should we allow procedures to be executed twice
					 * 	if they are associated with multiple conditions? */
					OLog.info("Procedure \"" 
								+ procedure.getId() 
								+ "\" Finished at " 
								+ Long.toString(_procEnd-_procStart) 
								+ " msecs");
				} else {
					OLog.info("Condition is false: " + cond.getId());
				}
			}

			/* Notify all event handlers that the MainController has finished
			 *   executing all procedures */
			notifyRunTaskFinished();
			
			return;
		}
		
		private Status executeProcedure(Procedure p) {
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
				if (isSystemCommand(t) == true) {
					retStatus = executeTask(_mainControllerInstance, t);
					if (retStatus != Status.OK) {
						break;
					}
					continue;
				}
				
				/* Get the sub controller for this task */
				AbstractController controller = _mainInfo.getSubController(t.getId());
				if (controller == null) {
					OLog.err("No subcontroller for task: " + t.toString());
					retStatus = Status.FAILED;
					break;
				}
				
				/* Perform the command using the appropriate subcontroller */
				retStatus = executeTask(controller, t);
				if (retStatus != Status.OK) {
					break;
				}
			}
			return retStatus;
		}
		
		private Status executeTask(AbstractController controller, Task t)
		{
			/* Initialize the task execution timer variables */
			long taskStart = 0;
			long taskEnd = 0;
			
			try {
				taskStart = System.currentTimeMillis();
				ControllerStatus status = controller.performCommand(t.getId(), t.getParams());
				taskEnd = System.currentTimeMillis();
				
				if (status.getLastCmdStatus() != Status.OK) {
					OLog.err("Task failed: " + t.toString());
					OLog.err(status.toString());
					OLog.info("Task Finished: " + t.toString() + " at " + 
								Long.toString(taskEnd-taskStart) + "msecs");
					return Status.FAILED;
				}
				
			} catch (Exception e) {
				OLog.err("Task failed: " + t.toString());
				OLog.err("Exception ocurred: " + e.getMessage() );
				OLog.info("Task Finished: " + t.toString() + " at " + 
						Long.toString(taskEnd-taskStart) + "msecs");
				return Status.FAILED;
			}
			OLog.info("Task Finished: " + t.toString() + " at " + 
					Long.toString(taskEnd-taskStart) + "msecs");
			return Status.OK;
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
		
		private boolean isSystemCommand(Task t) {
			return t.getId().startsWith("system.main");
		}
		
		private List<RunListElement> generateRunList() {
			/* Initialize the object lists */
			List<RunListElement> runList = 
					new ArrayList<RunListElement>();
			List<TriggerCondition> condList = _runConfig.getConditionList();
			
			/* Cycle through each trigger condition */
			for (TriggerCondition cond : condList) {
	            /* Extract the procedure id from the condition */
				String procId = cond.getProcedure();
				if (procId == null) {
					OLog.warn("Invalid procedure in condition: " 
								+ cond.getId());
					continue;
				}
				
				/* Retrieve the equivalent Procedure object */
	            Procedure proc = _runConfig.getProcedure(procId);
	            if (proc == null) {
	            	OLog.warn("Procedure not found in config: " 
	            				+ cond.getProcedure());
	            	OLog.warn("Failed to fully process condition: "
	            				+ cond.getCondition());
	            	continue;
	            }
	            
	            /* In the runList, each TriggerCondition is unique and
	             *  has one or more Procedures mapped to it. 
	             *  
	             * If the runList already contains a TriggerCondition, 
	             *  then log a warning and skip it. */
	            if ( runList.contains(cond) )
	            {
	            	OLog.warn("Run List already contains condition: " 
	            				+ cond.getId());
	            	continue;
	            }
	            
	            /* Add the TriggerCondition/Procedure pair to the runMap */
	            runList.add(new RunListElement(cond, proc));
			}
			
			return runList;
		}
		
		private Map<TriggerCondition,Procedure> generateRunMap() {
			/* Initialize the object lists */
			Map<TriggerCondition,Procedure> runMap 
				= new HashMap<TriggerCondition,Procedure>();
			List<TriggerCondition> condList = _runConfig.getConditionList();
			
			/* Cycle through each trigger condition */
			for (TriggerCondition cond : condList) {
	            /* Extract the procedure id from the condition */
				String procId = cond.getProcedure();
				if (procId == null) {
					OLog.warn("Invalid procedure in condition: " 
								+ cond.getId());
					continue;
				}
				
				/* Retrieve the equivalent Procedure object */
	            Procedure proc = _runConfig.getProcedure(procId);
	            if (proc == null) {
	            	OLog.warn("Procedure not found in config: " 
	            				+ cond.getProcedure());
	            	OLog.warn("Failed to fully process condition: "
	            				+ cond.getCondition());
	            	continue;
	            }
	            
	            /* In the runMap, each TriggerCondition is unique and
	             *  has one or more Procedures mapped to it. 
	             *  
	             * If the runMap already contains a TriggerCondition, 
	             *  then log a warning and skip it. */
	            if ( runMap.containsKey(cond) )
	            {
	            	OLog.warn("Run List already contains condition: " 
	            				+ cond.getId());
	            	continue;
	            }
	            
	            /* Add the TriggerCondition/Procedure pair to the runMap */
	            runMap.put(cond, proc);
			}

			return runMap;
		}

		
		private class RunListElement {
			private TriggerCondition _cond = null;
			private Procedure _proc = null;
			
			public RunListElement(TriggerCondition cond, Procedure proc) {
				this._cond = cond;
				this._proc = proc;
				
				return;
			}
			
			public TriggerCondition getCondition() {
				return _cond;
			}
			
			public Procedure getProcedure() {
				return _proc;
			}
		}
	}
}
