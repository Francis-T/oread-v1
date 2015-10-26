package net.oukranos.oreadv1.interfaces.bridge;

import net.oukranos.oreadv1.interfaces.BluetoothEventHandler;
import net.oukranos.oreadv1.types.Status;

public interface IBluetoothBridge extends IFeatureBridge {

	public Status initialize(Object initObject);
	public Status connectDeviceByAddress(String address);
	public Status connectDeviceByName(String name);
	public Status broadcast(byte[] data);
	public Status destroy();
	public Status setEventHandler(BluetoothEventHandler eventHandler);
}
