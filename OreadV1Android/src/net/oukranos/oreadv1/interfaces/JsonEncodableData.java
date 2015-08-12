package net.oukranos.oreadv1.interfaces;

import org.json.JSONObject;

public interface JsonEncodableData {
	public String encodeToJson();
	public JSONObject encodeToJsonObject();
}
