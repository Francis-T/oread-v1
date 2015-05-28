package net.oukranos.oreadv1.types;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import net.oukranos.oreadv1.interfaces.JsonEncodableData;

public class SiteDeviceData implements JsonEncodableData {
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
	
	public void addErrorData(SiteDeviceErrorData data) {
		if (data == null) {
			return;
		}
		
		_errorDataList.add(data);
		
		return;
	}

	public String encodeToJsonString() {
		JSONObject request = new JSONObject();
		
		try {
			request.put("sitedevice_id", _siteDeviceId);
			request.put("context", _context);
			
			
			JSONArray arr = new JSONArray();
			for (SiteDeviceReportData rd : _reportDataList) {
				arr.put(rd.encodeToJson());
			}
			
			request.put("reportData", arr);
			
		} catch (JSONException e) {
			e.printStackTrace();
		}
		
		
		return request.toString();
	}

	@Override
	public JSONObject encodeToJson() {
		// TODO Auto-generated method stub
		return null;
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
}
