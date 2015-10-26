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
	private String _readCat = "";
	private float _value = 0.0f;
	private String _errMsg = "";
	
	public SiteDeviceReportData(String type, String readCat, float value, String err) {
		_dateRecorded = System.currentTimeMillis();
		_type = type;
		_readCat = readCat;
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

	public void setReadCat(String readCat) {
		_readCat = readCat;
		return;
	}
	
	public String getReadCat() {
		return this._readCat;
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
		
		//OLog.info("Decoding JSON: \n" + jsonStr);
		
		long timestamp = 0;
		String type = "";
		String readCat = "";
		float value = 0.0f;
		String errMsg = "";
		
		/* Buffer the values extracted from the JSON object first just in
		 * 	case a JSONException occurs so that we don't end up with a
		 *  partially set SiteDeviceReportData object */
		try {
			timestamp 	= jsonObject.getLong("dateRecorded");
			type 		= jsonObject.getString("readingOf");
			readCat 	= jsonObject.getString("readingCat");
			value  		= (float)(jsonObject.getDouble("value"));
			errMsg		= jsonObject.getString("errMsg");
			
		} catch (JSONException e) {
			OLog.err("Decode data from JSON failed");
			return Status.FAILED;
		}
		
		/* Set the actual values */
		this.setTimestamp(timestamp);
		this.setType(type);
		this.setReadCat(readCat);
		this.setValue(value);
		this.setErrMsg(errMsg);
		
		return Status.OK;
	}

	@Override
	public String encodeToJsonString() {
		JSONObject request = encodeToJsonObject();
		if (request == null) {
			return "";
		}
//		OLog.info("JSON Encoded Data:");
//		OLog.info("    dateRecorded: " + this.getTimestamp());
//		OLog.info("    readingOf: " + this._type);
//		OLog.info("    units: " + this._units);
//		OLog.info("    value: " + this._value);
//		OLog.info("    errMsg: " + this._errMsg);
//
//		OLog.info("Sub(+1)-level JSON Object: " + request.toString());
		return request.toString();
	}

	@Override
	public JSONObject encodeToJsonObject() {
		JSONObject request = new JSONObject();
		try {
			request.put("dateRecorded", this.getTimestamp());
			request.put("readingOf", 	this._type);
			request.put("readingCat", 	this._readCat);
			request.put("value", 		this._value);
			request.put("errMsg", 		this._errMsg);
		} catch (JSONException e) {
			System.out.println("Encode data to JSON failed");
			return null;
		}
//		OLog.info("Sub-level JSON Object: " + request.toString());
		
		return request;
	}
	
	public static void main(String args[]) {
		SiteDeviceReportData sd = new SiteDeviceReportData("pH", "", 7.00f, "OK");
		System.out.println("JSON: " + sd.encodeToJsonString());
		return;
	}
}
