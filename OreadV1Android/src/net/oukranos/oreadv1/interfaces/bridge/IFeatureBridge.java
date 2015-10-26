package net.oukranos.oreadv1.interfaces.bridge;

import net.oukranos.oreadv1.types.Status;

public interface IFeatureBridge {
	public String getId();
	public String getPlatform();
	public boolean isReady();
	public Status initialize(Object initObject);
}
