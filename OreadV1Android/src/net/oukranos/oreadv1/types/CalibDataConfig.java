package net.oukranos.oreadv1.types;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.xmlpull.v1.XmlPullParserException;

import net.oukranos.oreadv1.util.CalibConfigXmlParser;

public class CalibDataConfig {
	private static CalibDataConfig _calibDataConfig = null;
	private List<CalibrationData> _calibDataList = null;
	
	private CalibDataConfig(String configFile) {
		CalibConfigXmlParser parser = new CalibConfigXmlParser();
		
		try {
			_calibDataList = parser.parseXml(configFile);
		} catch (XmlPullParserException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		if (_calibDataList == null) {
			_calibDataList = new ArrayList<CalibrationData>();
			return;
		}
		
		return;
	}
	
	public static CalibDataConfig getInstance(String configFile) {
		if (configFile == null) {
			return _calibDataConfig;
		}
		
		if ( (new File(configFile)).exists() == false ) {
			return _calibDataConfig;
		}
		
		if (_calibDataConfig == null) {
			_calibDataConfig = new CalibDataConfig(configFile);
		}
		
		return _calibDataConfig; 
	}
	

	public List<CalibrationData> getDataList() {
		return this._calibDataList;
	}
	
	public CalibrationData getMatch(String data) {
		String dataSplit[] = data.split("-");
		if (dataSplit.length < 2) {
			return null;
		}
		
		String sensor = dataSplit[0].trim();
		String mode = dataSplit[1].trim();
		
		for (CalibrationData d : _calibDataList) {
			if (sensor.equals(d.getSensor()) && mode.equals(d.getMode())) {
				return d;
			}
		}
		
		return null;
	}

}
