package net.oukranos.oreadv1.interfaces;

import net.oukranos.oreadv1.types.Status;

public interface ConnectivityBridgeIntf {
	public Status initialize(Object initObject);
	public boolean isConnected();
	public String getConnectionType();
	public int getGsmSignalStrength();
	public int getCdmaSignalStrength();
	public int getEvdoSignalStrength();
	public Status destroy();
}
