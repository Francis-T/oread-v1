package net.oukranos.oreadv1.types;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import net.oukranos.oreadv1.interfaces.HttpEncodableData;
import net.oukranos.oreadv1.interfaces.JsonEncodableData;
import net.oukranos.oreadv1.util.OreadLogger;

public class SiteDeviceImage implements JsonEncodableData, HttpEncodableData {
	/* Get an instance of the OreadLogger class to handle logging */
	private static final OreadLogger OLog = OreadLogger.getInstance();
	
	private String _siteDeviceId = "";
	private String _context = "";
	private String _filePath = "";
	private String _fileName = "";
	private List<SiteDeviceReportData> _reportDataList = null;
	private List<SiteDeviceErrorData> _errorDataList = null;
	
	public SiteDeviceImage(String id, String context, String path, String filename) {
		_siteDeviceId = id;
		_context = context;
		_filePath = path;
		_fileName = filename;
		
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

	public void setCaptureFile(String fileName, String path) {
		if ( fileName != null ) {
			_fileName = fileName;
		}
		
		if ( path != null ) {
			_filePath = path;	
		}
		
		return;
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

	@Override
	public HttpEntity encodeDataToHttpEntity() {
		JSONObject request  = encodeToJsonObject();
		
		if (request == null) {
			OLog.err("Failed to get HttpDataEntity");
			return null;
		}

		MultipartEntity e = null;
		try {
			e = new MultipartEntity();
			
			/* New remote server implementation assumes that the reading 'params'
			 *   for visual capture data will be sent as separate 'parts' in the
			 *   MultipartHttpPost. This allows the photo to be sent as just another
			 *   'part' of the reading, retaining the data representation model
			 *   somewhat */
			JSONArray reportDataArr = request.getJSONArray("reportData");
			if (reportDataArr == null) {
				return null;
			}
			JSONObject reportData = reportDataArr.getJSONObject(0);
			if (reportData == null) {
				return null;
			}
			
			
			String dateRecordedStr 	= Long.toString(reportData.getLong("dateRecorded"));
			String readingOfStr 	= reportData.getString("readingOf");
			String unitsStr 		= reportData.getString("units");
			String valueStr 		= Double.toString(reportData.getDouble("value"));
			String errMsgStr		= reportData.getString("errMsg");
			
			/* Extract the strings from the JSON object and apply them as separate "String"
			 *   bodies to the MultipartHttpEntity */
			e.addPart("sitedevice_id", 	new StringBody(_siteDeviceId));
			e.addPart("context", 		new StringBody(_context)); 
			e.addPart("dateRecorded",  	new StringBody(dateRecordedStr));
			e.addPart("readingOf",     	new StringBody(readingOfStr));
			e.addPart("units",         	new StringBody(unitsStr));
			e.addPart("value",      	new StringBody(valueStr));
			e.addPart("errMsg",       	new StringBody(errMsgStr));
			
			/* Finally, add the captured file */
	        FileBody isb = new FileBody(new File(getCaptureFilePath() + "/" + getCaptureFileName()), "image/jpeg");
	        OLog.info("File Info:     MIME-Type: " + isb.getMimeType() + " Media-Type: " + isb.getMediaType());
	        e.addPart("photo", isb);
	        
		} catch (Exception e1) {
			OLog.err("Generate HttpEntity failed");
			OLog.err("    " + e1.getMessage());
			return null;
		}
		
		OLog.info("(SiteDeviceImage) Message: " + ((MultipartEntity)e).toString() );
		
		return e;
	}
	
	/** Private Methods **/
	private String getCaptureFilePath() {
		return this._filePath;
	}
	
	private String getCaptureFileName() {
		return this._fileName;
	}
}
