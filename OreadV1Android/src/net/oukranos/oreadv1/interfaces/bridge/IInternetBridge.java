package net.oukranos.oreadv1.interfaces.bridge;

import net.oukranos.oreadv1.types.SendableData;
import net.oukranos.oreadv1.types.Status;

public interface IInternetBridge extends IFeatureBridge {
	public Status initialize(Object initObject);
	public Status send(SendableData sendableData);
	public byte[] getResponse();
	public Status destroy();
}
