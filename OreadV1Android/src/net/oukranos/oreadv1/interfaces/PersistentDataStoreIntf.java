package net.oukranos.oreadv1.interfaces;

public interface PersistentDataStoreIntf {
	public void put(String id, String value);
	public String get(String id);
}
