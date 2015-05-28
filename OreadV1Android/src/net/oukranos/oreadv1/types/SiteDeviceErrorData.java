package net.oukranos.oreadv1.types;

public class SiteDeviceErrorData {
	private String _device = "";
	private String _message = "";
	
	public SiteDeviceErrorData(String device, String message) {
		_device = device;
		_message = message;
		return;
	}
	
	public String getDevice() {
		return this._device;
	}
	
	public String getMessage() {
		return this._message;
	}
}

