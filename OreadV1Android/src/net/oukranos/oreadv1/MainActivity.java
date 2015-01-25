package net.oukranos.oreadv1;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import net.oukranos.oreadv1.controller.MainController;
import net.oukranos.oreadv1.interfaces.CameraControlIntf;
import net.oukranos.oreadv1.interfaces.CapturedImageMetaData;
import net.oukranos.oreadv1.interfaces.MainControllerEventHandler;
import net.oukranos.oreadv1.types.CameraTaskType;
import net.oukranos.oreadv1.types.ControllerState;
import net.oukranos.oreadv1.types.Status;
import net.oukranos.oreadv1.types.WaterQualityData;
import net.oukranos.oreadv1.util.OLog;
import net.oukranos.oreadv1.util.SystemUiHider;

import android.annotation.TargetApi;
import android.app.Activity;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ListView;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 * 
 * @see SystemUiHider
 */
public class MainActivity extends Activity implements MainControllerEventHandler, CameraControlIntf {
	/**
	 * Whether or not the system UI should be auto-hidden after
	 * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
	 */
	private static final boolean AUTO_HIDE = true;

	/**
	 * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
	 * user interaction before hiding the system UI.
	 */
	private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

	/**
	 * If set, will toggle the system UI visibility upon interaction. Otherwise,
	 * will show the system UI visibility upon interaction.
	 */
	private static final boolean TOGGLE_ON_CLICK = true;

	/**
	 * The flags to pass to {@link SystemUiHider#getInstance}.
	 */
	private static final int HIDER_FLAGS = SystemUiHider.FLAG_HIDE_NAVIGATION;
	
	private static final int DEFAULT_PICTURE_WIDTH  = 640;
	private static final int DEFAULT_PICTURE_HEIGHT = 480;

	/**
	 * The instance of the {@link SystemUiHider} for this activity.
	 */
	private SystemUiHider mSystemUiHider;
	
	private MainController _mainController = null;
	private PullDataTask _pullDataTask = null;
	private CameraControlTask _cameraControlTask = null;
	private SensorDataAdapter _sensorDataAdapter = null;
	private List<WaterQualityData> _sensorData = null;
	private Camera _camera = null;
	private CameraPreview _cameraPreview = null;
	private Thread _cameraCaptureThread = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		if (_mainController == null) {
			_mainController = MainController.getInstance(this);
			_mainController.setEventHandler(this);
			_mainController.initialize();
		}

		setContentView(R.layout.activity_main);

		final View controlsView = findViewById(R.id.fullscreen_content_controls);
		final View contentView = findViewById(R.id.fullscreen_content);
		final View listView = contentView.findViewById(R.id.sensor_data_list);
		
		if (listView == null) {
			OLog.warn("ListView is NULL");
			_mainController.destroy();
			_mainController = null;
			return;
		}
		
		_sensorData = new ArrayList<WaterQualityData>();

		// Set up an instance of SystemUiHider to control the system UI for
		// this activity.
		mSystemUiHider = SystemUiHider.getInstance(this, contentView,
				HIDER_FLAGS);
		mSystemUiHider.setup();
		mSystemUiHider
				.setOnVisibilityChangeListener(new SystemUiHider.OnVisibilityChangeListener() {
					// Cached values.
					int mControlsHeight;
					int mShortAnimTime;

					@Override
					@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
					public void onVisibilityChange(boolean visible) {
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
							// If the ViewPropertyAnimator API is available
							// (Honeycomb MR2 and later), use it to animate the
							// in-layout UI controls at the bottom of the
							// screen.
							if (mControlsHeight == 0) {
								mControlsHeight = controlsView.getHeight();
							}
							if (mShortAnimTime == 0) {
								mShortAnimTime = getResources().getInteger(
										android.R.integer.config_shortAnimTime);
							}
							controlsView
									.animate()
									.translationY(visible ? 0 : mControlsHeight)
									.setDuration(mShortAnimTime);
						} else {
							// If the ViewPropertyAnimator APIs aren't
							// available, simply show or hide the in-layout UI
							// controls.
							controlsView.setVisibility(visible ? View.VISIBLE
									: View.GONE);
						}

						if (visible && AUTO_HIDE) {
							// Schedule a hide().
							delayedHide(AUTO_HIDE_DELAY_MILLIS);
						}
					}
				});

		// Set up the user interaction to manually show or hide the system UI.
		contentView.setOnLongClickListener(new View.OnLongClickListener() {
			@Override
			public boolean onLongClick(View view) {
				if (TOGGLE_ON_CLICK) {
					mSystemUiHider.toggle();
				} else {
					mSystemUiHider.show();
				}
				return false;
			}
		});
		
		_sensorDataAdapter = new SensorDataAdapter(this, _sensorData);
		((ListView) listView).setAdapter(_sensorDataAdapter);
		OLog.info("Adapter set");

		// Upon interacting with UI controls, delay any scheduled hide()
		// operations to prevent the jarring behavior of controls going away
		// while interacting with the UI.
		findViewById(R.id.start_button).setOnTouchListener(mDelayHideTouchListener);
		findViewById(R.id.stop_button).setOnTouchListener(mDelayHideTouchListener);
		
		findViewById(R.id.start_button).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (_mainController.getState() == ControllerState.UNKNOWN) {
					_mainController.start();
				}
			}
		});

		findViewById(R.id.stop_button).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (_mainController.getState() == ControllerState.READY) {
					_mainController.stop();
				}
			}
		});

//		_camera = Camera.open();
//		List<Camera.Size> picSizeList = _camera.getParameters().getSupportedPictureSizes();
//		List<Camera.Size> prevSizeList = _camera.getParameters().getSupportedPreviewSizes();
//		
//		for ( Camera.Size p : picSizeList ) {
//			OLog.info("Picture Size: " + p.height + ", " + p.width);
//		}
//		
//		for ( Camera.Size p : prevSizeList ) {
//			OLog.info("Picture Size: " + p.height + ", " + p.width);
//		}
//		_camera = null;
		
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);

		// Trigger the initial hide() shortly after the activity has been
		// created, to briefly hint to the user that UI controls
		// are available.
		delayedHide(100);
		
		return;	
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		if (_mainController == null) {
			_mainController = MainController.getInstance(this);
			_mainController.initialize();
		}
		
		if ( _cameraState == CameraState.INACTIVE ) {
			if ( _camera == null ) {
				_camera = Camera.open();
				Camera.Parameters _cameraParams = _camera.getParameters();
				_cameraParams.setPictureSize(DEFAULT_PICTURE_WIDTH, DEFAULT_PICTURE_HEIGHT);
				_cameraParams.setPreviewSize(DEFAULT_PICTURE_WIDTH, DEFAULT_PICTURE_HEIGHT);
				_cameraParams.setRotation(90);
				_camera.setParameters(_cameraParams);
				
				if (_cameraPreview == null) {
					_cameraPreview = new CameraPreview(this, _camera);

					FrameLayout preview = (FrameLayout) this.findViewById(R.id.camera_preview);
					preview.addView(_cameraPreview);
				}
			}			
		}
				
		return;
	}
	
	@Override
	protected void onPause() {
		if (_mainController.getState() == ControllerState.READY) {
			_mainController.stop();
		}
		super.onPause();
		
		if ( _cameraState != CameraState.INACTIVE ) {
			if (_camera != null) {

				FrameLayout preview = (FrameLayout) this.findViewById(R.id.camera_preview);
				preview.removeAllViews();
				
				_camera.release();
				_camera = null;
			}
		}
		
		return;
	}
	
	@Override
	protected void onStop() {
		if (_mainController != null) {
			if (_mainController.getState() == ControllerState.READY) {
				_mainController.stop();
			}
			_mainController.destroy();
		}
		
		super.onStop();
		return;
	}

	@Override
	public void onDataAvailable() {
		/* Start a pull data task */
		if (_pullDataTask != null) {
			OLog.err("An old pull data task still exists");
			return;
		}
		
		_pullDataTask = new PullDataTask();
		_pullDataTask.execute();
		
		return;
	}

	/**
	 * Touch listener to use for in-layout UI controls to delay hiding the
	 * system UI. This is to prevent the jarring behavior of controls going away
	 * while interacting with activity UI.
	 */
	View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
		@Override
		public boolean onTouch(View view, MotionEvent motionEvent) {
			if (AUTO_HIDE) {
				delayedHide(AUTO_HIDE_DELAY_MILLIS);
			}
			return false;
		}
	};

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

	Handler mHideHandler = new Handler();
	Runnable mHideRunnable = new Runnable() {
		@Override
		public void run() {
			mSystemUiHider.hide();
		}
	};
	
	/**********************************************************************/
	/**  Private functions                                               **/
	/**********************************************************************/
	/**
	 * Schedules a call to hide() in [delay] milliseconds, canceling any
	 * previously scheduled calls.
	 */
	private void delayedHide(int delayMillis) {
		mHideHandler.removeCallbacks(mHideRunnable);
		mHideHandler.postDelayed(mHideRunnable, delayMillis);
	}
	
	
	/* Camera Functions */
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
			
			FrameLayout preview = (FrameLayout) this.findViewById(R.id.camera_preview);
			preview.addView(_cameraPreview);
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
			FrameLayout preview = (FrameLayout) this.findViewById(R.id.camera_preview);
			preview.removeAllViews();
			_camera.release();
			_camera = null;
		}
		
		_cameraState = CameraState.INACTIVE;
		
		return Status.OK;
	}
	
	/* TODO REFACTOR THIS */
	private void savePictureToFile(CapturedImageMetaData container, byte[] data) {
		final String root_sd = Environment.getExternalStorageDirectory().toString();
		final String LOG_ID_STRING = "[savePictureToFile]";
		
		/* Save the marker trace to a file */
		String savePath = root_sd + "/OreadPrototype";
		File saveDir = new File(savePath);
		
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

	/**  Task Classes  **/
	private class PullDataTask extends AsyncTask<Void, Void, TaskStatus> {

		@Override
		protected TaskStatus doInBackground(Void... params) {
			OLog.info("Pull data task started.");
			if (_mainController != null) {
				if (_sensorData == null) {
					OLog.err("Sensor data list is null");
					return TaskStatus.FAILED;
				}
				
				if (_sensorDataAdapter == null) {
					OLog.err("Sensor data adapter is null");
					return TaskStatus.FAILED;
				}
				
				/* Pull data from the MainController */
				WaterQualityData data = _mainController.getData(); // TODO This ID should be a variable instead
				if ( data == null ) {
					OLog.err("Failed to pull data");
					return TaskStatus.FAILED;
				}
				_sensorData.add(data);
				
				
				if (_sensorData.size() > 5) {
					/* If we have more than ten elements in the list, discard the first element */ 
					_sensorData.remove(0);
				}
				
				WaterQualityData d = _sensorData.get(_sensorData.size()-1);
				OLog.info("SensorData size: " + _sensorData.size());
				OLog.info(  "   pH: " + d.pH +
							"  DO2: " + d.dissolved_oxygen +
							" COND: " + d.conductivity + 
							" TEMP: " + d.temperature + 
							"  TDS: " + d.tds + 
							"  SAL: " + d.salinity );
			}
			
			if (_pullDataTask != null) {
				_pullDataTask = null;
			}

			OLog.info("Pull data task finished.");
			return TaskStatus.OK;
		}
		
		protected void onPostExecute(TaskStatus result) {
			if (result == TaskStatus.OK) {
				if (_sensorDataAdapter != null) {
					_sensorDataAdapter.notifyDataSetChanged();
				}
			}
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
	/**  Private enums                                                   **/
	/**********************************************************************/
	private enum TaskStatus {
		UNKNOWN, OK, ALREADY_STARTED, FAILED
	}
	
	private enum CameraState {
		INACTIVE, READY, BUSY 
	}
}
