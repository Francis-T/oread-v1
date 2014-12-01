package net.oukranos.OreadPrototype;

import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

/**
 * <b>Type1SensorDataEncoder Class</b> </br>Handles encoding of sensor data to
 * the proper format prior to sending it to a remote server
 */
public class Type1SensorDataEncoder
{
	/**
	 * Utility function for encoding Type1SensorData objects into a JSON string
	 * @param data - a Type1SensorData object containing the data to be encoded
	 * @return a String containing the sensor data encoded into JSON
	 */
	public static String encodeToJSON(Type1SensorData data)
	{
		final String LOG_ID_STRING = "[encodeToJSON]";
		if (data == null)
		{
			Log.i(LOG_ID_STRING, "Error: Data is null!");
			return "";
		}
		
		JSONObject request = new JSONObject();
		try {
			//reportData.
			request.put("origin", data.sensor_id);
			//request.put("time", data.timestamp);
			request.put("co2", data.dissolved_oxygen);
			request.put("conductivity", data.conductivity);
			request.put("pH", data.ph);
			request.put("temperature", 0.01);
			request.put("recordStatus", data.status);
			request.put("arsenic", 0.01);
			request.put("mercury", 0.01);
			request.put("copper", 0.01);
			request.put("zinc", 0.01);
			request.put("message", "test");
			request.put("tds", data.tds);
			request.put("salinity", data.salinity);
			request.put("dateRecorded", data.timestamp);
			
		} catch (JSONException e) {
			e.printStackTrace();
			return "";
		}
		
		/* Return the final JSON String */
		return request.toString();
	}
}
