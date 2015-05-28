package net.oukranos.oreadv1.interfaces;

import net.oukranos.oreadv1.types.Status;

public interface BluetoothBridgeIntf {

	public Status initialize(Object initObject);
	public Status connectDeviceByAddress(String address);
	public Status connectDeviceByName(String name);
	public Status broadcast(byte[] data);
	public Status destroy();
	public Status setEventHandler(BluetoothEventHandler eventHandler);
}
