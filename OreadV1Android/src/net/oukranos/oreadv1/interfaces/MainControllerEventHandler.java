package net.oukranos.oreadv1.interfaces;

public interface MainControllerEventHandler {
	public void onDataAvailable();
	public void onFinish();
	public void onProcStateChanged(String newState);
	public void onProcChanged(String newProc);
	public void onTaskChanged(String newTask);
//	public void onBluetoothStarted();
//	public void onBluetoothStopped();
//	public void onNetworkConnected();
//	public void onNetworkDisconnected();
}
