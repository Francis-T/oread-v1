package net.oukranos.oreadv1.types;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.entity.StringEntity;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import net.oukranos.oreadv1.interfaces.HttpEncodableData;
import net.oukranos.oreadv1.interfaces.JsonEncodableData;
import net.oukranos.oreadv1.util.OreadLogger;

public class SiteDeviceData implements JsonEncodableData, HttpEncodableData {
	/* Get an instance of the OreadLogger class to handle logging */
	private static final OreadLogger OLog = OreadLogger.getInstance();
	
	private String _siteDeviceId = "";
	private String _context = "";
	private List<SiteDeviceReportData> _reportDataList = null;
	private List<SiteDeviceErrorData> _errorDataList = null;
	
	public SiteDeviceData(String id, String context) {
		_siteDeviceId = id;
		_context = context;
		
		_reportDataList = new ArrayList<SiteDeviceReportData>();
		_errorDataList = new ArrayList<SiteDeviceErrorData>();
		
		return;
	}
	
	public void addReportData(SiteDeviceReportData data) {
		if (data == null) {
			return;
		}
		
		_reportDataList.add(data);
		
		return;
	}
	
	public void clearReportData() {
		if (_reportDataList != null) {
			_reportDataList.clear();
		}
		return;
	}
	
	public void addErrorData(SiteDeviceErrorData data) {
		if (data == null) {
			return;
		}
		
		_errorDataList.add(data);
		
		return;
	}
	
	public void clearErrorData() {
		if (_errorDataList != null) {
			_errorDataList.clear();
		}
		return;
	}

	@Override
	public String encodeToJsonString() {
		JSONObject request = encodeToJson();
		if (request == null) {
			return "";
		}
		
		return request.toString();
	}

	@Override
	public JSONObject encodeToJson() {
		JSONObject request = new JSONObject();
		
		try {
			request.put("sitedevice_id", _siteDeviceId);
			request.put("context", _context);
			
			
			JSONArray reportDataArr = new JSONArray();
			for (SiteDeviceReportData rd : _reportDataList) {
				reportDataArr.put(rd.encodeToJson());
			}
			request.put("reportData", reportDataArr);

			JSONArray errDataArr = new JSONArray();
			for (SiteDeviceErrorData ed : _errorDataList) {
				errDataArr.put(ed.encodeToJson());
			}
			request.putOpt("errorData", errDataArr);
			
		} catch (JSONException e) {
			return null;
		}
		
		return request;
	}

	
	public static void main(String args[]) {
		SiteDeviceReportData sd1 = new SiteDeviceReportData("DO2", "mg/L", 7.00f, "OK");
		System.out.println("JSON: " + sd1.encodeToJsonString());

		SiteDeviceReportData sd2 = new SiteDeviceReportData("DO2", "mg/L", 9.43f, "OK");
		System.out.println("JSON: " + sd2.encodeToJsonString());
		
		SiteDeviceData sdat = new SiteDeviceData("test_device_id", "live");
		sdat.addReportData(sd1);
		sdat.addReportData(sd2);
		
		System.out.println("Result: " + sdat.encodeToJsonString());
		
		return;
	}

	@Override
	public HttpEntity encodeDataToHttpEntity() {
		JSONObject request  = encodeToJson();
		
		if (request == null) {
			OLog.err("Failed to get HttpDataEntity");
			return null;
		}

		HttpEntity e = null;
		try {
			e = new StringEntity(request.toString() + "\r\n");
		} catch (UnsupportedEncodingException e1) {
			OLog.err("Generate HttpEntity failed");
			return null;
		}
		
		OLog.info("(SiteDeviceData) Message: " + ((StringEntity)e).toString() );
		
		return e;
	}
}
