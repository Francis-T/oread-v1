package net.oukranos.oreadv1;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.oukranos.oreadv1.android.AndroidBluetoothBridge;
import net.oukranos.oreadv1.android.AndroidCameraBridge;
import net.oukranos.oreadv1.android.AndroidConnectivityBridge;
import net.oukranos.oreadv1.android.AndroidInternetBridge;
import net.oukranos.oreadv1.android.AndroidStoredDataBridge;
import net.oukranos.oreadv1.controller.MainController;
import net.oukranos.oreadv1.interfaces.CameraControlIntf;
import net.oukranos.oreadv1.interfaces.CapturedImageMetaData;
import net.oukranos.oreadv1.interfaces.IPersistentDataBridge;
import net.oukranos.oreadv1.interfaces.MainControllerEventHandler;
import net.oukranos.oreadv1.interfaces.OreadServiceApi;
import net.oukranos.oreadv1.interfaces.OreadServiceListener;
import net.oukranos.oreadv1.interfaces.bridge.ICameraBridge;
import net.oukranos.oreadv1.manager.ConfigManager;
import net.oukranos.oreadv1.types.CameraTaskType;
import net.oukranos.oreadv1.types.ControllerState;
import net.oukranos.oreadv1.types.DataStore;
import net.oukranos.oreadv1.types.MainControllerInfo;
import net.oukranos.oreadv1.types.OreadServiceControllerStatus;
import net.oukranos.oreadv1.types.OreadServiceProcChangeInfo;
import net.oukranos.oreadv1.types.OreadServiceProcStateChangeInfo;
import net.oukranos.oreadv1.types.OreadServiceTaskChangeInfo;
import net.oukranos.oreadv1.types.OreadServiceWaterQualityData;
import net.oukranos.oreadv1.types.Status;
import net.oukranos.oreadv1.types.WaterQualityData;
import net.oukranos.oreadv1.types.config.Configuration;
import net.oukranos.oreadv1.util.OreadLogger;

import android.annotation.TargetApi;
import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.RemoteException;

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class OreadService extends Service implements MainControllerEventHandler, CameraControlIntf  {
	/* Get an instance of the OreadLogger class to handle logging */
	private static final OreadLogger OLog = OreadLogger.getInstance();
	
	private final String _root_sd = Environment.getExternalStorageDirectory().toString();
	private final String _savePath = _root_sd + "/OreadPrototype";
	private final String _defaultConfigFile = (_savePath + "/oread_config.xml");
	
	private MainController _mainController = null;
	private MainControllerInfo _mainInfo = null;
	
	private PullDataTask _pullDataTask = null;

	private String _originator = null;
	private String _directive = null;
	OreadServiceWakeReceiver _wakeAlarm = null;
	private Object _wqDataLock = new Object();
	private List<OreadServiceListener> _serviceListeners = new ArrayList<OreadServiceListener>();

	private OreadServiceWaterQualityData 	_wqData 				= null;
	private OreadServiceProcStateChangeInfo _procStateChangeInfo 	= null;
	private OreadServiceProcChangeInfo 		_procChangeInfo 		= null;
	private OreadServiceTaskChangeInfo 		_taskChangeInfo 		= null;
	
	/* State variables */
	private ServiceState _state = ServiceState.UNKNOWN;
	private boolean _isServiceBound = false;

	@Override
	public IBinder onBind(Intent intent) {
		if (OreadService.class.getName().equals(intent.getAction()) == true) {
			OLog.info("Service bound.");
			return _apiEndpoint;
		}
		
		_isServiceBound = true;
		
		return null;
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		try {
			
			OLog.info("OreadService onStartCommand() invoked");
			if (intent == null) {
				OLog.info("OreadService onStartCommand() finished prematurely");
				return super.onStartCommand(intent, flags, startId);
			}
	
			_state = ServiceState.STARTED;
			
			/* Obtain the originator and directive variables from the intent */
			_originator = intent.getStringExtra(OreadServiceWakeReceiver.EXTRA_ORIGINATOR);
			_directive = intent.getStringExtra(OreadServiceWakeReceiver.EXTRA_DIRECTIVE);
			
			/* If the service was triggered by the OreadService Wake alarm, then the
			 *   service has to automatically activate the MainController */
			if ( this.isWakeTriggered() == true ) {
				OLog.info("OreadService onStartCommand() triggers service activation");
				activateService();
			}
			
			OLog.info("OreadService onStartCommand() finished");

		} catch(Exception e) {
			OLog.err("Something went wrong: " + e.getMessage());
			OLog.stackTrace(e);
		}
		return super.onStartCommand(intent, flags, startId);
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		
		try {
			
			_state = ServiceState.UNKNOWN;
			
			/* Initialize the main controller upon creation */
			initializeMainController();
			
			OLog.info("Service created.");
		
		} catch(Exception e) {
			OLog.err("Something went wrong: " + e.getMessage());
			OLog.stackTrace(e);
		}
		return;
	}
	
	@Override
	public boolean onUnbind(Intent intent) {
		_isServiceBound = false;
		
		return super.onUnbind(intent);
	}
	
	@Override
	public void onDestroy() {
		try {
			
			super.onDestroy();
			
			_originator = null;
			_directive = null;
			
			/* Attempt to deactivate the service if possible */
			deactivateService();
			
			OLog.info("Service destroyed.");
			
		} catch(Exception e) {
			OLog.err("Something went wrong: " + e.getMessage());
			OLog.stackTrace(e);
		}
		return;
	}

	@Override
	public void onDataAvailable() {
		try {
			
			if (_pullDataTask != null) {
				OLog.err("An old pull data task still exists!");
				if (_pullDataTask.getStatus() ==  AsyncTask.Status.FINISHED) {
					_pullDataTask = null;
				} else {
					_pullDataTask.cancel(true);
					_pullDataTask = null;
				}
			}
			
			_pullDataTask = new PullDataTask();
			_pullDataTask.execute();
			
		} catch (Exception e) {
			OLog.err("Something went wrong: " + e.getMessage());
			OLog.stackTrace(e);
		}
		return;
	}

	@Override
	public void onFinish() {
		try {
			
			_originator = null;
			_directive = null;
	
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
			/* Attempt to deactivate the service if possible */
			deactivateService();
			
			/* Schedule the next wake up event before terminating */
			if (this._state == ServiceState.ACTIVE) {
				scheduleNextWakeUpEvent();
			}
	
			/* If OreadService has been started by a wake trigger, then close the service
			 *   once the MainController's task is finished. */
			if (this.isWakeTriggered()) {
				
				/* If the service is no longer bound to an app, then the service can
				 * be fully closed while waiting for the next wake trigger */
				if (!_isServiceBound) {
					stopSelf();
				}
			}
			
			this._state = ServiceState.UNKNOWN;
			
			/* Destroy the MainController */
			destroyMainController();
		
		} catch (Exception e) {
			OLog.err("Something went wrong: " + e.getMessage());
			OLog.stackTrace(e);
		}
		return;
	}

	@Override
	public void onProcStateChanged(String newState) {
		_procStateChangeInfo = new OreadServiceProcStateChangeInfo(newState);
		
		/* Notify the listeners */
		synchronized (_serviceListeners) {
			for (OreadServiceListener l : _serviceListeners) {
				try {
					l.handleOperationProcStateChanged();
				} catch (RemoteException e) {
					OLog.err("Failed to notify listeners");
				}
			}
		}
		
		return;
	}

	@Override
	public void onProcChanged(String newProc) {
		_procChangeInfo = new OreadServiceProcChangeInfo(newProc);
		
		/* Notify the listeners */
		synchronized (_serviceListeners) {
			for (OreadServiceListener l : _serviceListeners) {
				try {
					l.handleOperationProcChanged();
				} catch (RemoteException e) {
					OLog.err("Failed to notify listeners");
				}
			}
		}
		
		return;
	}

	@Override
	public void onTaskChanged(String newTask) {
		_taskChangeInfo = new OreadServiceTaskChangeInfo(newTask);
		
		/* Notify the listeners */
		synchronized (_serviceListeners) {
			for (OreadServiceListener l : _serviceListeners) {
				try {
					l.handleOperationTaskChanged();
				} catch (RemoteException e) {
					OLog.err("Failed to notify listeners");
				}
			}
		}
		return;
	};

	/**********************************************************************/
	/**  Private Methods                                                 **/
	/**********************************************************************/
	private Status initializeMainController() {
		// TODO Handle the case where the main controller has already been started
		
		/* Setup the MainControllerInfo object */
        _mainInfo = getMainControllerInfo();
        
		/* Instantiate the MainController if it hasn't been yet */
		if (_mainController == null) {
			_mainController = MainController.getInstance(null); // TODO 
		}
		
        if (_mainController.getState() == ControllerState.INACTIVE) { // TODO
            _mainController.start();
            
            OLog.info("OreadService MainController Started");
            return Status.OK;
        }

		_mainController.initialize(_mainInfo);
		_mainController.registerEventHandler(this);
        
        OLog.info("OreadService MainController Initialized");
		
		return Status.OK;
	}
	
	private MainControllerInfo getMainControllerInfo() {
        // TODO What if MainControllerInfo already exists?
		MainControllerInfo mainInfo = new MainControllerInfo();

		mainInfo.setDataStore(new DataStore());
		mainInfo.setContext(this);
		
		/* Instantiate the Android Bridge objects */
		AndroidBluetoothBridge bluetoothBridge =
				AndroidBluetoothBridge.getInstance();
		AndroidCameraBridge cameraBridge =
				AndroidCameraBridge.getInstance();
		AndroidConnectivityBridge connBridge =
				AndroidConnectivityBridge.getInstance();
		AndroidInternetBridge internetBridge = 
				AndroidInternetBridge.getInstance();
		AndroidStoredDataBridge storedDataBridge = 
				AndroidStoredDataBridge.getInstance();
		
//		if (bluetoothBridge.initialize(mainInfo) != Status.OK) {
//			OLog.warn("Failed to initialize BluetoothBridge");
//		}
//		if (cameraBridge.initialize(mainInfo) != Status.OK) {
//			OLog.warn("Failed to initialize CameraBridge");
//		}
//		
//		if (connBridge.initialize(mainInfo) != Status.OK) {
//			OLog.warn("Failed to initialize ConnectivityBridge");
//		}
//		
//		if (internetBridge.initialize(mainInfo) != Status.OK) {
//			OLog.warn("Failed to initialize InternetBridge");
//		}
//		
//		if (storedDataBridge.initialize(mainInfo) != Status.OK) {
//			OLog.warn("Failed to initialize StoredDataBridge");
//		}
		
		mainInfo.addFeature(bluetoothBridge);
		mainInfo.addFeature(cameraBridge);
		mainInfo.addFeature(connBridge);
		mainInfo.addFeature(internetBridge);
		mainInfo.addFeature(storedDataBridge);
		
		loadConfig(mainInfo);
		
		return mainInfo;
	}
	
	private Status loadConfig(MainControllerInfo mainInfo) {
		if (mainInfo == null) {
			OLog.err("MainInfo is NULL in loadConfig()");
			return Status.FAILED;
		}
		
		/* Get a ConfigManager instance */
		ConfigManager cfgMan = ConfigManager.getInstance();

		/* Update the config file if a new version exists on the remote server */
		if (cfgMan.initialize(mainInfo) == Status.OK) {
			if (cfgMan.runConfigFileUpdate() != Status.OK) {
				OLog.info("Config File Update Failed");
			}
		} else {
			OLog.warn("Failed to initialize ConfigManager");
		}
		
		/* Get the loaded config file from the ConfigManager */
		//Configuration config = cfgMan.getConfig(_defaultConfigFile); // TODO This should be getLoadedConfig();
		Configuration config = cfgMan.getLoadedConfig();
		if (config == null) {
			OLog.warn("Invalid config file");
			config = cfgMan.getConfig(this._defaultConfigFile);
		}
		
		mainInfo.setConfig(config);
		
		return Status.OK;
	}

    private boolean isWakeTriggered() {
        if ( (_originator == null) || (_directive == null) ) {
            return false;
        }
        
        if (!_originator.equals(OreadServiceWakeReceiver.ORIGINATOR_ID)) {
        	return false;
        }
        
        if (!_directive.equals(OreadServiceWakeReceiver.DIRECTIVE_ID)) {
        	return false;
        }

        return true;
    }

	private Status activateService() {
		if (_mainController != null) {
	        OLog.info("MainController State: " + _mainController.getState().toString());
		}
		
        initializeMainController(); // TODO Possibly redundant
        
        // TODO What about other MainController states?
        if (_mainController.getState() == ControllerState.INACTIVE) {
            _mainController.start();
            
            OLog.info("OreadService MainController Started");
        }
        
		_state = ServiceState.ACTIVE;
        
		return Status.OK;
    } 

    private Status scheduleNextWakeUpEvent() {
    	Long sleepInterval = _mainController.getSleepInterval();
    	
    	_wakeAlarm = new OreadServiceWakeReceiver();
    	_wakeAlarm.setAlarm(this, sleepInterval);
    	
    	return Status.OK;
    }

    private Status unscheduleNextWakeUpEvent() {
    	if (_wakeAlarm == null) {
    		_wakeAlarm = new OreadServiceWakeReceiver();
    	}
    	_wakeAlarm.cancelAlarm(this);
    	
    	return Status.OK;
    }

    private Status deactivateService() {
		if (_mainController == null) {
			return Status.OK;
		}
		
		if (_mainController.getState() != ControllerState.UNKNOWN) {
			_mainController.stop();
			
			OLog.info("OreadService MainController Stopped");
		}
		
		return Status.OK;
    }

	private Status destroyMainController() {
		if (_mainController != null) {
			_mainController.destroy();
			_mainController = null;
		}
		return Status.OK;
	}

	
	private void reloadOldWaterQualityData() {
		String oldWaterQualityData = null;
		
		oldWaterQualityData = getOldWaterQualityData();
		if (oldWaterQualityData == null) {
			return;
		}
		

		/* Find each sequence within the string that matches */
		Pattern dataPattern = Pattern.compile("[-]*[0-9]+\\.*[0-9]*");
		Matcher dataMatcher = dataPattern.matcher(oldWaterQualityData);
		int matchCount = 0;
		double matchValue[] = new double[5];
		
		while(dataMatcher.find()) {
			int startIdx = dataMatcher.start();
			int endIdx = dataMatcher.end();
			
			String matchStr = oldWaterQualityData.substring(startIdx, endIdx);
			
			matchValue[matchCount] = Double.parseDouble(matchStr);
					
			matchCount++;
		}
		
		_wqData.pH 				 = matchValue[0];
		_wqData.dissolved_oxygen = matchValue[1];
		_wqData.conductivity 	 = matchValue[2];
		_wqData.temperature 	 = matchValue[3];
		_wqData.turbidity 		 = matchValue[4];
		
		return;
	}
	
	private String getOldWaterQualityData() {
		IPersistentDataBridge pDataStore = getPersistentDataBridge();
		if (pDataStore == null) {
			return "0.0,0.0,0.0,0.0,0.0";
		}

		String oldWaterQualityData = null;
		oldWaterQualityData = pDataStore.get("LAST_WQ_DATA");
		
		if (oldWaterQualityData == null) {
			return "0.0,0.0,0.0,0.0,0.0";
		}
		
		OLog.info("Old Water Quality Data: " + oldWaterQualityData);
		
		return oldWaterQualityData;
	}
	
	private IPersistentDataBridge getPersistentDataBridge() {
		IPersistentDataBridge pDataStore 
			= (IPersistentDataBridge) _mainInfo
				.getFeature("persistentDataStore");
		if (pDataStore == null) {
			return pDataStore;
		}
		
		if ( pDataStore.isReady() == false ) {
			if (pDataStore.initialize(_mainInfo) != Status.OK) {
				pDataStore = null;
			}
		}
		
		return pDataStore;
	}

	/**********************************************************************/
	/**  CameraController Triggers                                       **/
	/**********************************************************************/
	private CameraControlTask _cameraControlTask = null;
	
	@Override
	public Status triggerCameraInitialize() {
		/* Start a camera control task to initialize the camera */
		if ( _cameraControlTask != null ) {
			OLog.err("An old camera ctrl task still exists");
			return Status.FAILED;
		}
		
		_cameraControlTask = new CameraControlTask(CameraTaskType.INITIALIZE);
		_cameraControlTask.execute();
		
		return Status.OK;
	}

	@Override
	public Status triggerCameraCapture(CapturedImageMetaData container) {
		/* Start a camera control task to take a picture with the camera */
		if ( _cameraControlTask != null ) {
			OLog.err("An old camera ctrl task still exists");
			return Status.FAILED;
		}
		
		_cameraControlTask = new CameraControlTask(CameraTaskType.CAPTURE, container);
		_cameraControlTask.execute();
		
		return Status.OK;
	}

	@Override
	public Status triggerCameraShutdown() {
		/* Start a camera control task to shutdown the camera */
		if ( _cameraControlTask != null ) {
			OLog.err("An old camera ctrl task still exists");
			return Status.FAILED;
		}
		
		_cameraControlTask = new CameraControlTask(CameraTaskType.SHUTDOWN);
		_cameraControlTask.execute();
		
		return Status.OK;
	}
	
	private ICameraBridge getCameraBridge() {
		ICameraBridge cameraBridge 
			= (ICameraBridge) _mainInfo
				.getFeature("camera");
		if (cameraBridge == null) {
			return cameraBridge;
		}
		
		return cameraBridge;
	}
	
	/**********************************************************************/
	/**  Task Classes                                                    **/
	/**********************************************************************/
	private class PullDataTask extends AsyncTask<Void, Void, TaskStatus> {

		@Override
		protected TaskStatus doInBackground(Void... params) {
			OLog.info("Pull data task started.");
			if (_mainController != null) {
				/* Pull data from the MainController */
				WaterQualityData data = _mainController.getData(); // TODO This ID should be a variable instead
				if ( data == null ) {
					OLog.err("Failed to pull data");
					return TaskStatus.FAILED;
				}
				_wqData = new OreadServiceWaterQualityData(data);

				/* Notify the listeners */
				synchronized (_serviceListeners) {
					for (OreadServiceListener l : _serviceListeners) {
						try {
							l.handleWaterQualityData();
						} catch (RemoteException e) {
							OLog.err("Failed to notify listeners");
						}
					}
				}
			}
			
			if (_pullDataTask != null) {
				_pullDataTask = null;
			}

			OLog.info("Pull data task finished.");
			return TaskStatus.OK;
		}
		
	}
	
	private class CameraControlTask extends AsyncTask<Void, Void, Void> {
		private CameraTaskType type = null;
		private CapturedImageMetaData container = null;
		
		public CameraControlTask(CameraTaskType type) {
			this.type = type;
		}

		public CameraControlTask(CameraTaskType type, CapturedImageMetaData container) {
			this.type = type;
			this.container = container;
		}

		@Override
		protected Void doInBackground(Void... params) {
			return null;
		}
		
		protected void onPostExecute(Void params) {
			ICameraBridge cameraBridge = getCameraBridge();
			if (cameraBridge == null) {
				OLog.err("CameraBridge unavailable");
				_cameraControlTask = null;
				return;
			}
			
			switch (this.type) {
				case INITIALIZE:
					cameraBridge.initialize(_mainInfo);
					OLog.info("Camera initialization done");
					break;
				case CAPTURE:
					if (container != null) {
						cameraBridge.capture(container);
					} else {
						OLog.err("Invalid container for image capture data");
					}
					OLog.info("Camera capture done");
					break;
				case SHUTDOWN:
					cameraBridge.shutdown();
					OLog.info("Camera shutdown done");
					break;
				default:
					OLog.err("Invalid Camera Control Task");
					break;
			}
			
			_cameraControlTask = null;
			
			return;
		}
	}
	
	/**********************************************************************/
	/**  API endpoint                                                    **/
	/**********************************************************************/
	private OreadServiceApi.Stub _apiEndpoint = new OreadServiceApi.Stub() {

		@Override
		public void addListener(OreadServiceListener listener)
				throws RemoteException {
			synchronized(_serviceListeners) {
				_serviceListeners.add(listener);
			}
		}
		
		@Override
		public void removeListener(OreadServiceListener listener)
				throws RemoteException {
			synchronized(_serviceListeners) {
				_serviceListeners.remove(listener);
			}
			
		}

		@Override
		public void start() throws RemoteException {
			if (activateService() != Status.OK) {
				OLog.err("OreadService API Start Failed");
			}
    		
			return;
		}

		@Override
		public void stop() throws RemoteException {
            if (deactivateService() != Status.OK) {
                OLog.err("OreadService API Stop Failed");
            }
            
            /* Special state which prevents the service from 
             *  automatically creating a wake up event for
             *  the service */
            _state = ServiceState.STOPPED;
    		
    		unscheduleNextWakeUpEvent();
    		
			return;
		}

		@Override
		public String runCommand(String command, String params)
				throws RemoteException {
			if (_mainController == null) {
				return Status.FAILED.toString();
			}
			
			_mainController.performCommand(command, params);
			
			OLog.info("OreadService MainController Perform Command Invoked");
			
			return Status.OK.toString();
		}

		@Override
		public OreadServiceWaterQualityData getData() throws RemoteException {
			synchronized (_wqDataLock) {
				reloadOldWaterQualityData();
				return _wqData;
			}
		}

		@Override
		public OreadServiceControllerStatus getStatus() throws RemoteException {
			if (_mainController != null) {
				return new OreadServiceControllerStatus(_mainController.getControllerStatus());
			}
			return null;
		}

		@Override
		public String getLogs(int lines) throws RemoteException {
			return OLog.getLastLogMessages(lines);
		}

		@Override
		public OreadServiceProcStateChangeInfo getProcStates()
				throws RemoteException {
			return _procStateChangeInfo;
		}

		@Override
		public OreadServiceProcChangeInfo getProc() throws RemoteException {
			return _procChangeInfo;
		}

		@Override
		public OreadServiceTaskChangeInfo getTask() throws RemoteException {
			return _taskChangeInfo;
		}
		
	};

	/**********************************************************************/
	/**  Private enums                                                   **/
	/**********************************************************************/
	private enum TaskStatus {
		UNKNOWN, OK, ALREADY_STARTED, FAILED
	}

	private enum ServiceState { 
		UNKNOWN, STARTED, ACTIVE, STOPPED
	}
}
