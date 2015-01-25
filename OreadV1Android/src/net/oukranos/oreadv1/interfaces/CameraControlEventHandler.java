package net.oukranos.oreadv1.interfaces;

import net.oukranos.oreadv1.types.CameraTaskType;
import net.oukranos.oreadv1.types.Status;

public interface CameraControlEventHandler {
	public void onCameraEventDone(CameraTaskType type, Status status);
}
