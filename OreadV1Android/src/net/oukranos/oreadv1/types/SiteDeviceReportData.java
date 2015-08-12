package net.oukranos.oreadv1.types;

import net.oukranos.oreadv1.interfaces.JsonEncodableData;
import net.oukranos.oreadv1.util.OreadLogger;

import org.json.JSONException;
import org.json.JSONObject;

public class SiteDeviceReportData implements JsonEncodableData {
	/* Get an instance of the OreadLogger class to handle logging */
	private static final OreadLogger OLog = OreadLogger.getInstance();
	
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
	
	public void setTimestamp(long timestamp) {
		_dateRecorded = timestamp;
		return;
	}
	
	public long getTimestamp() {
		return this._dateRecorded;
	}

	public void setType(String type) {
		_type = type;
		return;
	}
	
	public String getType() {
		return this._type;
	}

	public void setUnits(String units) {
		_units = units;
		return;
	}
	
	public String getUnits() {
		return this._units;
	}

	public void setValue(float value) {
		_value = value;
		return;
	}
	
	public float getValue() {
		return this._value;
	}

	public void setErrMsg(String errMsg) {
		_errMsg = errMsg;
		return;
	}
	
	public String getErrMsg() {
		return this._errMsg;
	}
	
	public Status decodeFromJson(String jsonStr) {
		JSONObject jsonObject = null;
		try {
			jsonObject = new JSONObject(jsonStr);
		} catch (JSONException e) {
			OLog.err("Decode data from JSON failed");
			return Status.FAILED;
		}
		
		long timestamp = 0;
		String type = "";
		String units = "";
		float value = 0.0f;
		String errMsg = "";
		
		/* Buffer the values extracted from the JSON object first just in
		 * 	case a JSONException occurs so that we don't end up with a
		 *  partially set SiteDeviceReportData object */
		try {
			timestamp 	= jsonObject.getLong("dateRecorded");
			type 		= jsonObject.getString("readingOf");
			units 		= jsonObject.getString("units");
			value  		= (float)(jsonObject.getDouble("value"));
			errMsg		= jsonObject.getString("errMsg");
			
		} catch (JSONException e) {
			OLog.err("Decode data from JSON failed");
			return Status.FAILED;
		}
		
		/* Set the actual values */
		this.setTimestamp(timestamp);
		this.setType(type);
		this.setUnits(units);
		this.setValue(value);
		this.setErrMsg(errMsg);
		
		return Status.OK;
	}

	@Override
	public String encodeToJson() {
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
		System.out.println("JSON: " + sd.encodeToJson());
		return;
	}
}
