package net.oukranos.oreadv1.types;

import java.io.UnsupportedEncodingException;

import net.oukranos.oreadv1.interfaces.HttpEncodableData;
import net.oukranos.oreadv1.util.OreadLogger;

import org.apache.http.HttpEntity;
import org.apache.http.entity.StringEntity;
import org.json.JSONException;
import org.json.JSONObject;

public class HttpEncWaterQualityData extends WaterQualityData implements HttpEncodableData {
	/* Get an instance of the OreadLogger class to handle logging */
	private static final OreadLogger OLog = OreadLogger.getInstance();

	public HttpEncWaterQualityData(int id) {
		super(id);
	}
	
	public HttpEncWaterQualityData(WaterQualityData data) {
		super(data);
	}
	
	@Override
	public HttpEntity encodeDataToHttpEntity() {
		JSONObject request = new JSONObject();
		try {
			//reportData.
			request.put("origin", this.getId());
			//request.put("time", data.timestamp);
			request.put("co2", (this.dissolved_oxygen < 0.01 ? 0.01 : this.dissolved_oxygen) );
			request.put("conductivity", (this.conductivity < 0.01 ? 0.01 : this.conductivity) );
			request.put("pH", (this.pH < 0.01 ? 0.01 : this.pH) );
			request.put("temperature", (this.temperature < 0.01 ? 0.01 : this.temperature) );
			request.put("recordStatus", "OK");
			request.put("arsenic", 0.01);	// TODO Placeholder
			request.put("mercury", 0.01);	// TODO Placeholder
			request.put("copper", 0.01);	// TODO Placeholder
			request.put("zinc", 0.01);		// TODO Placeholder
			request.put("message", "test data");
			request.put("tds", (this.tds < 0.01 ? 0.01 : this.tds) );
			request.put("salinity", (this.salinity < 0.01 ? 0.01 : this.salinity) );
			request.put("dateRecorded", this.getTimestamp());
			
		} catch (JSONException e) {
			OLog.err("Encode data to JSON failed");
			return null;
		}
		
		HttpEntity e = null;
		try {
			e = new StringEntity("{\"reportData\":[" + request.toString() + "]}\r\n");
		} catch (UnsupportedEncodingException e1) {
			OLog.err("Generate HttpEntity failed");
			return null;
		}
		
		OLog.info("(HttpEncWaterQualityData) Message: " + ((StringEntity)e).toString() );
		
		return e;
	}
}
