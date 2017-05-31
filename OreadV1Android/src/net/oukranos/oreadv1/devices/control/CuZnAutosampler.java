package net.oukranos.oreadv1.devices.control;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.oukranos.oreadv1.types.ControlMechanism;
import net.oukranos.oreadv1.types.MainControllerInfo;
import net.oukranos.oreadv1.types.Status;
import net.oukranos.oreadv1.types.WaterQualityData;
import net.oukranos.oreadv1.util.DataStoreUtils.DSUtils;

public class CuZnAutosampler extends ControlMechanism {
	private static final String ACTV_CMD_STR = "I2C 2 n x";
	private static final String DEACT_CMD_STR = "I2C 2 n y";
	private static final String STATE_CMD_STR = "I2C 2 y @";
	private static final String REGEX_NUMBER_STR = "^\\-*[0-9]+\\.[0-9]+$";

	public CuZnAutosampler() {
		setName("CuZn Autosampler");
		setBlocking(true);
//		setTimeoutDuration(40000);
//		setPollable(true);
//		setPollDuration(5000);
		setTimeoutDuration(720000);
		setPollable(true);
		setPollDuration(30000);
	}

	@Override
	public Status activate() {
		return send(ACTV_CMD_STR.getBytes());
	}

	@Override
	public Status activate(String params) {
		return send(ACTV_CMD_STR.getBytes());
	}

	@Override
	public Status deactivate() {
		return send(DEACT_CMD_STR.getBytes());
	}
	
	@Override
	public Status deactivate(String params) {
		return deactivate();
	}

	@Override
	public Status pollStatus() {
		return send(STATE_CMD_STR.getBytes());
	}

	@Override
	public boolean shouldContinuePolling() {
		byte data[] = getReceivedData();
		
		if (data == null) {
			OLog.warn("Received data is empty");
			return true;
		}
		
		String response = new String(data).trim();
		String cuValStr = extractValue(response, "Cu: ");
		String znValStr = extractValue(response, "Zn: ");
		
		/* Return early if neither values are present */
		if (cuValStr.isEmpty() && znValStr.isEmpty()) {
			return true;
		}
		
		/* Get the water quality data object */
		WaterQualityData wqData 
			= (WaterQualityData) DSUtils
				.getStoredObject(_mainInfo.getDataStore(), "h2o_quality_data");
		
		/* Store the Copper presence data if possible */
		if (cuValStr.isEmpty() == false) {
			Float cuVal = Float.parseFloat(cuValStr);
			
			if (wqData != null) {
				wqData.copper = cuVal;
			}
		}

		/* Store the Zinc presence data if possible */
		if (znValStr.isEmpty() == false) {
			Float znVal = Float.parseFloat(znValStr);
			
			if (wqData != null) {
				wqData.zinc = znVal;
			}
		}
		
		return false;
	}
	
	private String extractValue(String response, String prefix) {
		if (response.contains(prefix)) {
			int startIdx = response.indexOf(prefix) + prefix.length();
			int endIdx = response.indexOf(' ', startIdx);
			
			String respSubstr = response.substring(startIdx, endIdx);
			
			Pattern condPattern = Pattern.compile(REGEX_NUMBER_STR);
			Matcher condMatcher = condPattern.matcher(respSubstr);
			
			if (condMatcher.find()) {
				int mtStartIdx = condMatcher.start();
				int mtEndIdx = condMatcher.end();
				
				return respSubstr.substring(mtStartIdx, mtEndIdx);
			}
			
			return "";
		}
		
		return "";
	}
}
