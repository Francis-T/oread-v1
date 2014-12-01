package net.oukranos.oreadv1.controller;

import net.oukranos.oreadv1.interfaces.ImageDataIntf;

public class CameraController {
	private static CameraController _cameraController = null;
	private ImageDataIntf _captureFileData = null;
	
	private CameraController(ImageDataIntf captureDataBuffer) {
		this._captureFileData = captureDataBuffer;
		
		return;
	}
	
	public static CameraController getInstance(ImageDataIntf captureDataBuffer) {
		if (captureDataBuffer == null) {
			return null;
		}
		
		if (_cameraController == null) {
			_cameraController = new CameraController(captureDataBuffer);
		}
		
		return _cameraController;
	}
}
