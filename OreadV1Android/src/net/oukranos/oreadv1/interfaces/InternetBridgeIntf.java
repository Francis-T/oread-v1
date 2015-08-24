package net.oukranos.oreadv1.interfaces;

import net.oukranos.oreadv1.types.SendableData;
import net.oukranos.oreadv1.types.Status;

public interface InternetBridgeIntf {
	public Status initialize(Object initObject);
	public Status send(SendableData sendableData);
	public byte[] getResponse();
	public Status destroy();
}
