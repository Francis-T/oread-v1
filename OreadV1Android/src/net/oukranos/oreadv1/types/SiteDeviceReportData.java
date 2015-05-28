package net.oukranos.oreadv1.types;

import net.oukranos.oreadv1.interfaces.JsonEncodableData;

import org.json.JSONException;
import org.json.JSONObject;

public class SiteDeviceReportData implements JsonEncodableData {
	private long _dateRecorded = 0;
	private String _type = "";
	private String _units = "";
	private float _value = 0.0f;
	private String _errMsg = "";
	
	public SiteDeviceReportData(String type, String units, float value, String err) {
		_dateRecorded = System.currentTimeMillis();
		_type = type;
		_units = units;
		_value = value;
		_errMsg = err;
		return;
	}
	
	public long getTimestamp() {
		return this._dateRecorded;
	}
	
	public String getType() {
		return this._type;
	}
	
	public String getUnits() {
		return this._units;
	}
	
	public float getValue() {
		return this._value;
	}
	
	public String getErrMsg() {
		return this._errMsg;
	}

	public String encodeToJsonString() {
		JSONObject request = new JSONObject();
		try {
			request.put("dateRecorded", this.getTimestamp());
			request.put("readingOf", 	this._type);
			request.put("units", 		this._units);
			request.put("value", 		this._value);
			request.put("errMsg", 		this._errMsg);
		} catch (JSONException e) {
			System.out.println("Encode data to JSON failed");
			return null;
		}
		
		return request.toString();
	}

	public JSONObject encodeToJson() {
		JSONObject request = new JSONObject();
		try {
			request.put("dateRecorded", this.getTimestamp());
			request.put("readingOf", 	this._type);
			request.put("units", 		this._units);
			request.put("value", 		this._value);
			request.put("errMsg", 		this._errMsg);
		} catch (JSONException e) {
			System.out.println("Encode data to JSON failed");
			return null;
		}
		
		return request;
	}
	
	public static void main(String args[]) {
		SiteDeviceReportData sd = new SiteDeviceReportData("pH", "", 7.00f, "OK");
		System.out.println("JSON: " + sd.encodeToJsonString());
		return;
	}
}
