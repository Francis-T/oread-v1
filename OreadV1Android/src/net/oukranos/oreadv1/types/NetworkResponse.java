package net.oukranos.oreadv1.types;

public class NetworkResponse {
	private int 	_statusCode = -1;
	private String 	_statusMsg = "";
	private byte[] 	_data = null;
	
	public NetworkResponse() {
		return;
	}
	
	public void setStatusCode(int code) {
		this._statusCode = code;
	}
	
	public void setStatusMsg(String msg) {
		this._statusMsg = msg;
	}
	
	public void setData(byte[] d) {
		if (d == null) {
			return;
		}
		
		int len = d.length;
		
		_data = new byte[len];
		System.arraycopy(d, 0, _data, 0, len);
		
		return;
	}
	
	public int getStatusCode() {
		return _statusCode;
	}
	
	public String getStatusMessage() {
		return _statusMsg;
	}
	
	public byte[] getData() {
		return _data;
	}
}
