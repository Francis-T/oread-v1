package net.oukranos.oreadv1.controller;

import android.app.Activity;
import net.oukranos.oreadv1.interfaces.AbstractController;
import net.oukranos.oreadv1.interfaces.CameraControlEventHandler;
import net.oukranos.oreadv1.interfaces.CameraControlIntf;
import net.oukranos.oreadv1.interfaces.CapturedImageMetaData;
import net.oukranos.oreadv1.types.CameraTaskType;
import net.oukranos.oreadv1.types.ControllerState;
import net.oukranos.oreadv1.types.Status;
import net.oukranos.oreadv1.util.OLog;

public class CameraController extends AbstractController implements CameraControlEventHandler {
	private static final long MAX_AWAIT_CAMERA_RESPONSE_TIMEOUT = 5000;
	private static CameraController _cameraController = null;
	private CapturedImageMetaData _captureFileData = null;
	private CameraControlIntf _cameraInterface = null;
	private Activity _parentActivity = null;
	private Thread _cameraControllerThread = null;
	
	private CameraController(Activity parent, CapturedImageMetaData captureDataBuffer) {
		this._captureFileData = captureDataBuffer;
		this._parentActivity = parent;
		this._cameraInterface = (CameraControlIntf)(this._parentActivity);
		this.setState(ControllerState.UNKNOWN);
		
		return;
	}
	
	public static CameraController getInstance(Activity parent, CapturedImageMetaData captureDataBuffer) {
		if (parent == null) {
			return null;
		}
		
		if (captureDataBuffer == null) {
			return null;
		}
		
		if (_cameraController == null) {
			_cameraController = new CameraController( parent, captureDataBuffer );
		}
		
		return _cameraController;
	}

	@Override
	public Status initialize() {
		if ( (this.getState() != ControllerState.INACTIVE) &&
			   (this.getState() != ControllerState.UNKNOWN) ) {
			OLog.warn("CameraController already started");
			return Status.ALREADY_STARTED;
		}
		
		if ( _cameraInterface.triggerCameraInitialize() != Status.OK ) {
			return Status.FAILED;
		}
		
		/* Block the thread until a camera done event is received */
		waitForCameraEventDone();
			
		this.setState(ControllerState.READY);
		
		return Status.OK;
	}

	@Override
	public Status destroy() {
		if ( (this.getState() != ControllerState.READY) &&
				(this.getState() != ControllerState.BUSY) ) {
			OLog.warn("CameraController already stopped");
			return Status.OK;
		}

		/* If we're still capturing an image, unblock the waiting thread 
		 * first before trying to proceed with cleanup */
		if ( this.getState() == ControllerState.BUSY ) {
 			/* Unblock the thread */
			if ( (_cameraControllerThread != null) && 
				 (_cameraControllerThread.isAlive()) ) {
				_cameraControllerThread.interrupt();
			} else {
				OLog.warn("Original camera controller thread does not exist");
			}
		}


		if ( _cameraInterface.triggerCameraShutdown() != Status.OK ) {
			return Status.FAILED;
		}
		
		/* Block the thread until a camera done event is received */
		waitForCameraEventDone();
		
		this.setState(ControllerState.INACTIVE);
		
		return Status.OK;
	}
	
	public Status captureImage() {
		if ( this.getState() != ControllerState.READY ) {
			OLog.err("Invalid state: " + this.getState());
			return Status.FAILED;
		}

		if ( _cameraInterface.triggerCameraCapture(_captureFileData) != Status.OK ) {
			return Status.FAILED;
		}

		this.setState(ControllerState.BUSY);
		/* Block the thread until a camera done event is received */
		waitForCameraEventDone();
		this.setState(ControllerState.READY);
	
		return Status.OK;
	}

	@Override
	public void onCameraEventDone(CameraTaskType type, Status status) {
		/* Unblock the thread */
		if ((_cameraControllerThread != null) && (_cameraControllerThread.isAlive())) {
			_cameraControllerThread.interrupt();
		} else {
			OLog.warn("Original camera controller thread does not exist");
		}
		
		return;
	}

	private void waitForCameraEventDone() {
 		/* Wait until the camera interface's response is received */
		_cameraControllerThread = Thread.currentThread();
		try {
			Thread.sleep(MAX_AWAIT_CAMERA_RESPONSE_TIMEOUT);
		} catch (InterruptedException e) {
			OLog.info("Interrupted");
		}
		_cameraControllerThread = null;

		return;
	}
}

