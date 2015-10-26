package net.oukranos.oreadv1.interfaces;

import net.oukranos.oreadv1.interfaces.bridge.IFeatureBridge;

public interface IPersistentDataBridge extends IFeatureBridge {
	public void put(String id, String value);
	public String get(String id);
	public void remove(String id);
}
