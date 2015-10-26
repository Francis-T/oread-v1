package net.oukranos.oreadv1.android;

import net.oukranos.oreadv1.CameraPreview;
import net.oukranos.oreadv1.interfaces.CapturedImageMetaData;
import net.oukranos.oreadv1.interfaces.bridge.ICameraBridge;
import net.oukranos.oreadv1.manager.FilesystemManager.FSMan;
import net.oukranos.oreadv1.types.Status;
import net.oukranos.oreadv1.util.OreadTimestamp;
import android.content.Context;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.view.WindowManager;

public class AndroidCameraBridge extends AndroidBridgeImpl implements ICameraBridge {
	private static final int 	DEFAULT_PICTURE_WIDTH  = 640;
	private static final int 	DEFAULT_PICTURE_HEIGHT = 480;
	
	private static AndroidCameraBridge _androidCameraBridge = null;
	
	private CameraState _state = CameraState.INACTIVE;

	private Camera _camera = null;
	private CameraPreview _cameraPreview = null;
	private Thread _cameraCaptureThread = null;
	private WindowManager _wm = null;
	
	private AndroidCameraBridge() {
		this._state = CameraState.INACTIVE;
		return;
	}
	
	public static AndroidCameraBridge getInstance() {
		if (_androidCameraBridge == null) {
			_androidCameraBridge = new AndroidCameraBridge();
		}
		
		return _androidCameraBridge;
	}

	@Override
	public String getId() {
		return "camera";
	}

	@Override
	public String getPlatform() {
		return "android";
	}
	
	@Override
	public Status initialize(Object initObject) {
		/* Attempt to load the initializer object */
		/*  Note: This method is in AndroidBridgeImpl */
		if (loadInitializer(initObject) != Status.OK) {
			OLog.err("Failed to initialize " + getPlatform() + "." + getId());
			return Status.FAILED;
		}
		
		if (_state != CameraState.INACTIVE) {
			OLog.err("Invalid camera state: " + _state.toString());
			return Status.FAILED;
		}
		
		/* Initialize the camera */
		if ( _camera == null ) {
			try {
				_camera = Camera.open();
				Camera.Parameters _cameraParams = _camera.getParameters();
				_cameraParams.setPictureSize(DEFAULT_PICTURE_WIDTH, DEFAULT_PICTURE_HEIGHT);
				_cameraParams.setPreviewSize(DEFAULT_PICTURE_WIDTH, DEFAULT_PICTURE_HEIGHT);
				_cameraParams.setRotation(90);
//				_cameraParams.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
				
				/* Define the camera focus areas */
//				Camera.Area focusArea = new Camera.Area(new Rect(-167,100,167,562), 1000);
//				Camera.Area focusArea = new Camera.Area(new Rect(-167,100,167,562), 1000);
//				List<Camera.Area> focusAreas = new ArrayList<Camera.Area>();
//				focusAreas.add(focusArea);
//				
//				_cameraParams.setFocusAreas(focusAreas);
				_camera.setParameters(_cameraParams);
				
			} catch (Exception e) {
				OLog.err("Error: Could not open camera.");
				return Status.FAILED;
			}
		}

		/* Initialize the invisible preview */
		if (_cameraPreview == null) {
			_cameraPreview = new CameraPreview(_context, _camera);

	        /** START: EXPERIMENTAL: preview on a service **/
	        _wm = (WindowManager) _context
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
		} else {
			OLog.warn("Camera preview is not null!");
			_camera.startPreview();
		}
		
		_state = CameraState.READY;
		
		return Status.OK;
	}

	@Override
	public Status capture(CapturedImageMetaData container) {	
		_cameraCaptureThread = Thread.currentThread();
		SavePictureCallback lProcessPic = new SavePictureCallback(container);
		try {
			_camera.takePicture(null, null, lProcessPic);
		} catch (Exception e) {
			OLog.err("Error: " + e.getMessage());
		}

		_state = CameraState.BUSY;
		
		/* Wait until the camera capture is received */	
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			OLog.info("Interrupted");
		}
		
		_cameraCaptureThread = null;
		_state = CameraState.READY;
		
		return Status.OK;
	}

	@Override
	public Status shutdown() {
		if (_camera != null) {
			if (_wm != null) {
				try {
					_wm.removeView(_cameraPreview);
				} catch (Exception e) {
					OLog.err("Known exception occurred: " + e.getMessage());
				}
				
				_cameraPreview = null;
				_wm = null;
			}
			_camera.release();
			_camera = null;
		}
		
		_state = CameraState.INACTIVE;
		
		return Status.OK;
	}

	private String getCaptureFilename() {
		return ( "OREAD_Image" + 
                 "_" + OreadTimestamp.getDateString() + 
                 "_" + OreadTimestamp.getTimestampString() + 
                 ".jpg" );
	}
	
	private void savePictureToFile(CapturedImageMetaData container, byte[] data) {
        /* Generate the log message parameters */
		String savePath = FSMan.getDefaultFilePath();
		String fileName = this.getCaptureFilename();
		
		/* Save captured image to file */
		FSMan.saveFileData(savePath, fileName, data);
		
		/* Update the metadata object */
		container.setCaptureFile(fileName, savePath);
		
		return;
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
			_camera.stopPreview();

			/* Unblock the thread waiting for the camera capture */
			if ( (_cameraCaptureThread != null) && (_cameraCaptureThread.isAlive()) ) {
				_cameraCaptureThread.interrupt();
			} else {
				OLog.warn("Camera capture thread does not exist");
			}
			
			return;
		}
		
	}

	/**********************************************************************/
	/**  Private enums                                                   **/
	/**********************************************************************/
	private enum CameraState {
		INACTIVE, READY, BUSY 
	}
}
