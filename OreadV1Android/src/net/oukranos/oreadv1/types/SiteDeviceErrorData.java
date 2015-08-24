package net.oukranos.oreadv1.types;

import net.oukranos.oreadv1.interfaces.JsonEncodableData;

import org.json.JSONException;
import org.json.JSONObject;

public class SiteDeviceErrorData implements JsonEncodableData {
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

	@Override
	public String encodeToJsonString() {
		JSONObject request = encodeToJsonObject();
		if (request == null) {
			return "";
		}
		
		return request.toString();
    }

	@Override
	public JSONObject encodeToJsonObject() {
		JSONObject request = new JSONObject();
		try {
			request.put("device",   this._device);
			request.put("message", 	this._message);
		} catch (JSONException e) {
			System.out.println("Encode data to JSON failed");
			return null; 
		}
		
		return request;
    }
}

