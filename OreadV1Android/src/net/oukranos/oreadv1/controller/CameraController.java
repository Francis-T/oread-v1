package net.oukranos.oreadv1.controller;

import net.oukranos.oreadv1.interfaces.AbstractController;
import net.oukranos.oreadv1.interfaces.CameraControlEventHandler;
import net.oukranos.oreadv1.interfaces.CameraControlIntf;
import net.oukranos.oreadv1.interfaces.CapturedImageMetaData;
import net.oukranos.oreadv1.interfaces.IPersistentDataBridge;
import net.oukranos.oreadv1.types.CameraTaskType;
import net.oukranos.oreadv1.types.ControllerState;
import net.oukranos.oreadv1.types.ControllerStatus;
import net.oukranos.oreadv1.types.DataStore;
import net.oukranos.oreadv1.types.MainControllerInfo;
import net.oukranos.oreadv1.types.Status;

public class CameraController extends AbstractController implements CameraControlEventHandler {
	private static final long MAX_AWAIT_CAMERA_RESPONSE_TIMEOUT = 5000;
	private static CameraController _cameraController = null;
	
	private CapturedImageMetaData _captureFileData = null;
	private CameraControlIntf _cameraInterface = null;
	private Thread _cameraControllerThread = null;

	/*************************/
	/** Initializer Methods **/
	/*************************/
	private CameraController(MainControllerInfo mainInfo) {
		this._captureFileData = null;
		
		this.setState(ControllerState.UNKNOWN);

		this.setType("sensors");
		this.setName("hg_as_detection");
		return;
	}
	
	public static CameraController getInstance(MainControllerInfo mainInfo) {
		if (mainInfo == null) {
			OLog.err("Invalid input parameter/s" +
					" in CameraController.getInstance()");
			return null;
		}
		
		if (_cameraController == null) {
			_cameraController = new CameraController(mainInfo);
		}
		
		_cameraController._mainInfo = mainInfo;
		_cameraController.setCameraControlIntf(mainInfo.getContext());
		
		return _cameraController;
	}

	/********************************/
	/** AbstractController Methods **/
	/********************************/
	@Override
	public Status initialize(Object initializer) {
		if ( (this.getState() != ControllerState.INACTIVE) &&
			   (this.getState() != ControllerState.UNKNOWN) ) {
			OLog.warn("CameraController already started");
			return Status.OK;
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
	public Status start() {
		// TODO Auto-generated method stub
		return Status.OK;
	}

	@Override
	public Status stop() {
		// TODO Auto-generated method stub
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
		
		/* Check which command to perform */
		if (shortCmdStr.equals("read") == true) {
			DataStore dataStore = _mainInfo.getDataStore();
			if (dataStore == null) {
				this.writeErr("Data store uninitialized or unavailable");
				return this.getControllerStatus();
			}
			
			CapturedImageMetaData cdImg = (CapturedImageMetaData) dataStore
					.retrieveObject("hg_as_detection_data");
			if (cdImg == null) {
				this.writeErr("Data store uninitialized or unavailable");
				return this.getControllerStatus();
			}

			OLog.info("Retrieving from: " + cdImg.hashCode());
			
			if (this.captureImage(cdImg) == Status.OK) {
				IPersistentDataBridge pDataStore = getPersistentDataBridge();
				pDataStore.remove("ASHG_CAPTURE_OK");
				pDataStore.put("ASHG_CAPTURE_OK", "false");
			}
		} else if (shortCmdStr.equals("start") == true) {
			this.writeInfo("Command Performed: Start");
			
		} else if (shortCmdStr.equals("stop") == true) {
			this.writeInfo("Command Performed: Stop");
			
		} else {
			this.writeErr("Unknown or invalid command: " + shortCmdStr);
		}
		
		return this.getControllerStatus();
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

	/********************/
	/** Public Methods **/
	/********************/
	public Status captureImage(CapturedImageMetaData captureDataBuffer) {
		if (captureDataBuffer == null) {
			OLog.err("Invalid input parameter/s" +
					" in CameraController.captureImage()");
			return Status.FAILED;
		}
		
		if ( this.getState() != ControllerState.READY ) {
			OLog.err("Invalid state: " + this.getState());
			return Status.FAILED;
		}
		
		this._captureFileData = captureDataBuffer;
		if ( _cameraInterface.triggerCameraCapture(_captureFileData) != Status.OK ) {
			return Status.FAILED;
		}

		this.setState(ControllerState.BUSY);
		/* Block the thread until a camera done event is received */
		waitForCameraEventDone();
		this.setState(ControllerState.READY);
		
		this._captureFileData = null;
		
		this.writeInfo("Image Capture Finished");
	
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

	/*********************/
	/** Private Methods **/
	/*********************/
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
	
	private void setCameraControlIntf(Object controlIntf) {
		/* TODO The camera interface still has to refer to OreadService due
		 *  	 to limitations set by Android. This is Ok for now, but we
		 *  	 need to replace it eventually */
		this._cameraInterface 
			= (CameraControlIntf) controlIntf;
		
		return;
	}
	
}

