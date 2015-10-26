package net.oukranos.oreadv1.interfaces.bridge;

import net.oukranos.oreadv1.interfaces.CapturedImageMetaData;
import net.oukranos.oreadv1.types.Status;

public interface ICameraBridge extends IFeatureBridge {
	public Status initialize(Object initObject);
	public Status capture(CapturedImageMetaData container);
	public Status shutdown();
}
