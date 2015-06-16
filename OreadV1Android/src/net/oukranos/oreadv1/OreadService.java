package net.oukranos.oreadv1;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.StringEntity;

import net.oukranos.oreadv1.android.AndroidConnectivityBridge;
import net.oukranos.oreadv1.android.AndroidInternetBridge;
import net.oukranos.oreadv1.controller.MainController;
import net.oukranos.oreadv1.interfaces.CameraControlIntf;
import net.oukranos.oreadv1.interfaces.CapturedImageMetaData;
import net.oukranos.oreadv1.interfaces.HttpEncodableData;
import net.oukranos.oreadv1.interfaces.MainControllerEventHandler;
import net.oukranos.oreadv1.interfaces.OreadServiceApi;
import net.oukranos.oreadv1.interfaces.OreadServiceListener;
import net.oukranos.oreadv1.manager.ConfigManager;
import net.oukranos.oreadv1.types.CameraTaskType;
import net.oukranos.oreadv1.types.Configuration;
import net.oukranos.oreadv1.types.ControllerState;
import net.oukranos.oreadv1.types.Data;
import net.oukranos.oreadv1.types.OreadServiceControllerStatus;
import net.oukranos.oreadv1.types.OreadServiceWaterQualityData;
import net.oukranos.oreadv1.types.SendableData;
import net.oukranos.oreadv1.types.Status;
import net.oukranos.oreadv1.types.WaterQualityData;
import net.oukranos.oreadv1.util.ConfigXmlParser;
import net.oukranos.oreadv1.util.OLog;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.WindowManager;

public class OreadService extends Service implements MainControllerEventHandler, CameraControlIntf  {
	private static final int 	DEFAULT_PICTURE_WIDTH  = 640;
	private static final int 	DEFAULT_PICTURE_HEIGHT = 480;
	private static final String DEFAULT_DEVICE_CONFIG_URL_BASE = "http://miningsensors.info/deviceconf";
	
	private final String _root_sd = Environment.getExternalStorageDirectory().toString();
	private final String _savePath = _root_sd + "/OreadPrototype";
	private final String _defaultConfigFile = (_savePath + "/oread_config.xml");
	
	private MainController _mainController = null;
	private AndroidInternetBridge _networkBridge = null;
	private AndroidConnectivityBridge _connBridge = null;
	
	private PullDataTask _pullDataTask = null;
	private CameraControlTask _cameraControlTask = null;

	private Camera _camera = null;
	private CameraPreview _cameraPreview = null;
	private Thread _cameraCaptureThread = null;
	private WindowManager _wm = null;
	
	private String _originator = null;
	private String _directive = null;
	OreadServiceWakeReceiver _wakeAlarm = null;
	private Object _wqDataLock = new Object();
	private OreadServiceWaterQualityData _wqData = null;
	private List<OreadServiceListener> _serviceListeners = new ArrayList<OreadServiceListener>();
	
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
			_state = ServiceState.ACTIVE;
		}

		/* Update the config file if a new version exists on the remote server */
		ConfigManager cfgMan = ConfigManager.getInstance();
		if (cfgMan.runConfigFileUpdate(this) != Status.OK) {
			OLog.info("Config File Update Failed");
		}
		
		OLog.info("OreadService onStartCommand() finished");
		return super.onStartCommand(intent, flags, startId);
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		
		_state = ServiceState.UNKNOWN;
		
		/* Initialize the main controller upon creation */
		initializeMainController();
		
		OLog.info("Service created.");
		return;
	}
	
	@Override
	public boolean onUnbind(Intent intent) {
		_isServiceBound = false;
		
		return super.onUnbind(intent);
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		_originator = null;
		_directive = null;
		
		/* Attempt to deactivate the service if possible */
		deactivateService();
		
		/* Destroy the main controller */
		destroyMainController();
		
		OLog.info("Service destroyed.");
		return;
	}

	@Override
	public void onDataAvailable() {
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
		
		return;
	}

	@Override
	public void onFinish() {
		_originator = null;
		_directive = null;
		
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
		
		return;
	}
	

	/**********************************************************************/
	/**  Private Methods                                                 **/
	/**********************************************************************/
	private Status initializeMainController() {
		if (_mainController == null) {
			_mainController = MainController.getInstance(this, this._defaultConfigFile);
			_mainController.registerEventHandler(this);
			_mainController.initialize();
		}
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
        initializeMainController();
        
        if (_mainController.getState() == ControllerState.UNKNOWN) {
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
	

	/********************/
	/** Camera Methods **/
	/********************/
	@SuppressWarnings("unused")
	private CameraState _cameraState = CameraState.INACTIVE;
	private Status cameraInitialize() {
		/* Initialize the camera */
		if ( _camera == null ) {
			try {
				_camera = Camera.open();
				Camera.Parameters _cameraParams = _camera.getParameters();
				_cameraParams.setPictureSize(DEFAULT_PICTURE_WIDTH, DEFAULT_PICTURE_HEIGHT);
				_cameraParams.setPreviewSize(DEFAULT_PICTURE_WIDTH, DEFAULT_PICTURE_HEIGHT);
				_cameraParams.setRotation(90);
				_camera.setParameters(_cameraParams);
				
			} catch (Exception e) {
				OLog.err("Error: Could not open camera.");
				return Status.FAILED;
			}
		}

		/* Initialize the invisible preview */
		if (_cameraPreview == null) {
			_cameraPreview = new CameraPreview(this, _camera);

	        /** START: EXPERIMENTAL: preview on a service **/
	        _wm = (WindowManager) this
	                .getSystemService(Context.WINDOW_SERVICE);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    1, 1, //Must be at least 1x1
                    WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                    0,
                    //Don't know if this is a safe default
                    PixelFormat.UNKNOWN);

            //Don't set the preview visibility to GONE or INVISIBLE
            _wm.addView(_cameraPreview, params);
	        /** END EXPERIMENTAL: preview on a service **/
			//preview.addView(_cameraPreview); // TODO OLD
		}
		
		_cameraState = CameraState.READY;
		
		return Status.OK;
	}
	
	private Status cameraCapture(CapturedImageMetaData container) {
		SavePictureCallback lProcessPic = new SavePictureCallback(container);
		try {
			_camera.takePicture(null, null, lProcessPic);
		} catch (Exception e) {
			OLog.err("Error: " + e.getMessage());
		}

		_cameraState = CameraState.BUSY;
		
		/* Wait until the camera capture is received */		
		_cameraCaptureThread = Thread.currentThread();
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			OLog.info("Interrupted");
		}
		
		_cameraCaptureThread = null;
		_cameraState = CameraState.READY;
		
		return Status.OK;
	}
	
	private Status cameraShutdown() {
		if (_camera != null) {
			if (_wm != null) {
				try {
					_wm.removeView(_cameraPreview);
				} catch (Exception e) {
					OLog.err("Known exception occurred: " + e.getMessage());
				}
			}
			_camera.release();
			_camera = null;
		}
		
		_cameraState = CameraState.INACTIVE;
		
		return Status.OK;
	}

	
	/* TODO REFACTOR THIS */
	private void savePictureToFile(CapturedImageMetaData container, byte[] data) {
		final String LOG_ID_STRING = "[savePictureToFile]";
		
		/* Save the marker trace to a file */
		File saveDir = new File(_savePath);
		
		if (!saveDir.exists())
		{
			saveDir.mkdirs();
		}
		
		Calendar calInstance = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));
		int hour = calInstance.get(Calendar.HOUR_OF_DAY);
		int min = calInstance.get(Calendar.MINUTE);
		int sec = calInstance.get(Calendar.SECOND);
		
		String hourStr = (hour < 10 ? "0" + Integer.toString(hour) : Integer.toString(hour));
		String minStr = (min < 10 ? "0" + Integer.toString(min) : Integer.toString(min));
		String secStr = (sec < 10 ? "0" + Integer.toString(sec) : Integer.toString(sec));
		
		File saveFile = null;

		saveFile = new File(saveDir, ("OREAD_Image_" + hourStr + minStr + secStr + ".jpg"));
		
		try {
			if (!saveFile.createNewFile())
			{
				Log.e(LOG_ID_STRING, "Error: Failed to create save file!");
				return;
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		try {
			FileOutputStream saveFileStream = new FileOutputStream(saveFile);
			
			saveFileStream.write(data);
			
			saveFileStream.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		/* TODO CHECK IF THE PATHS and NAMES are correct */
		container.setCaptureFile(saveFile.getName(), saveFile.getParent());
		
		OLog.info("Saved! (see FilePath:" + container.getCaptureFilePath() + " FileName:" + container.getCaptureFileName() +" )");
	}
	

	/**********************************************************************/
	/**  CameraController Triggers                                       **/
	/**********************************************************************/
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

	

	/**********************************************************************/
	/**  Private classes                                                 **/
	/**********************************************************************/
	private class SavePictureCallback implements Camera.PictureCallback {
		private CapturedImageMetaData _saveContainer = null;
		
		public SavePictureCallback(CapturedImageMetaData container) {
			this._saveContainer = container;
			
			return;
		}
		
		@Override
		public void onPictureTaken(byte[] data, Camera camera) {
			OLog.info("SavePictureCallback invoked.");
			savePictureToFile(_saveContainer, data);
			_camera.startPreview();

			/* Unblock the thread waiting for the camera capture */
			if ( (_cameraCaptureThread != null) && (_cameraCaptureThread.isAlive()) ) {
				_cameraCaptureThread.interrupt();
			} else {
				OLog.warn("Camera capture thread does not exist");
			}
			
			return;
		}
		
	}
	
	private void saveConfigFileData(byte[] data) {
		final String LOG_ID_STRING = "[savePictureToFile]";
		
		/* Save the marker trace to a file */
		File saveDir = new File(_savePath);
		
		if (!saveDir.exists())
		{
			saveDir.mkdirs();
		}
		
		File saveFile = null;

		saveFile = new File(saveDir, "oread_config.xml");
		
		try {
			if (!saveFile.createNewFile())
			{
				Log.e(LOG_ID_STRING, "Error: Failed to create save file!");
				return;
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		try {
			FileOutputStream saveFileStream = new FileOutputStream(saveFile);
			
			saveFileStream.write(data);
			
			saveFileStream.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		OLog.info("Saved! (see FilePath:" + saveFile.getParent() + 
							 " FileName:" + saveFile.getName() +" )");
		return;
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
							OLog.logToFile("Failed to notify listeners");
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
			switch (this.type) {
				case INITIALIZE:
					cameraInitialize();
					OLog.info("Camera initialization done");
					break;
				case CAPTURE:
					if (container != null) {
						cameraCapture(container);	
					} else {
						OLog.err("Invalid container for image capture data");
					}
					OLog.info("Camera capture done");
					break;
				case SHUTDOWN:
					cameraShutdown();
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
		public OreadServiceWaterQualityData getData() throws RemoteException {
			synchronized (_wqDataLock) {
				return _wqData;
			}
		}

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
    		
    		_state = ServiceState.ACTIVE;
    		
			return;
		}

		@Override
		public void stop() throws RemoteException {
            if (deactivateService() != Status.OK) {
                OLog.err("OreadService API Stop Failed");
            }
    		
    		_state = ServiceState.STOPPED; 
    		
    		unscheduleNextWakeUpEvent();
    		
			return;
		}

		@Override
		public void runCommand(String command, String params)
				throws RemoteException {
			if (_mainController == null) {
				return;
			}
			
			_mainController.performCommand(command, params);
			
			OLog.info("OreadService MainController Perform Command Invoked");
			
			return;
		}

		@Override
		public OreadServiceControllerStatus getStatus() throws RemoteException {
			if (_mainController != null) {
				return new OreadServiceControllerStatus(_mainController.getControllerStatus());
			}
			return null;
		}
		
	};

	/**********************************************************************/
	/**  Private enums                                                   **/
	/**********************************************************************/
	private enum TaskStatus {
		UNKNOWN, OK, ALREADY_STARTED, FAILED
	}
	
	private enum CameraState {
		INACTIVE, READY, BUSY 
	}

	private enum ServiceState { 
		UNKNOWN, STARTED, ACTIVE, STOPPED
	};
}
