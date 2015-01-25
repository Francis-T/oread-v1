package net.oukranos.oreadv1.interfaces;

import net.oukranos.oreadv1.types.Status;

public interface CameraControlIntf {
	public Status triggerCameraInitialize();
	public Status triggerCameraCapture(CapturedImageMetaData container);
	public Status triggerCameraShutdown();
}
