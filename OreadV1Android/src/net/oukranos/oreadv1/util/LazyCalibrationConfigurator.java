package net.oukranos.oreadv1.util;

import java.util.ArrayList;
import java.util.List;

import net.oukranos.oreadv1.types.CalibrationData;

public class LazyCalibrationConfigurator {
	private List<CalibrationData> _calibDataList = new ArrayList<CalibrationData>();
	private static LazyCalibrationConfigurator _lazyConfig = null; 
	
	private LazyCalibrationConfigurator() {
		CalibrationData cd = null;
		
		cd = new CalibrationData("pH", "clear calibration", "sensors.water_quality.calibratePH");
		cd.setAllowParams(false);
		cd.setParamPrefix("clear");
		cd.setAllowRead(true);
		cd.setTitle("pH - Calibration: Clear");
		cd.setInstructions("Clears the current pH calibration. You should do this first if you are planning to use any of the other calibration modes. ");
		cd.setUnits("");
		_calibDataList.add(cd);

		cd = new CalibrationData("pH", "mid calibration", "sensors.water_quality.calibratePH");
		cd.setAllowParams(true);
		cd.setParamPrefix("mid");
		cd.setAllowRead(true);
		cd.setTitle("pH - Calibration: Mid");
		cd.setInstructions("Calibrate the pH mid point to be used. This should be done prior to any other pH calibration modes. " +
						   "Enter your mid point value by clicking 'Options' below");
		cd.setUnits("");
		_calibDataList.add(cd);

		cd = new CalibrationData("pH", "low calibration", "sensors.water_quality.calibratePH");
		cd.setAllowParams(true);
		cd.setParamPrefix("low");
		cd.setAllowRead(true);
		cd.setTitle("pH - Calibration: Low");
		cd.setInstructions("Calibrate the pH low point to be used. Mid point calibration should ideally be done first. " +
							"Enter your low point value by clicking 'Options' below");
		cd.setUnits("");
		_calibDataList.add(cd);

		cd = new CalibrationData("pH", "high calibration", "sensors.water_quality.calibratePH");
		cd.setAllowParams(true);
		cd.setParamPrefix("high");
		cd.setAllowRead(true);
		cd.setTitle("pH - Calibration: Mid");
		cd.setInstructions("Calibrate the pH high point to be used. Mid and low point calibration should ideally be done first. " +
						   "Enter your high point value by clicking 'Options' below");
		cd.setUnits("");
		_calibDataList.add(cd);
		
		cd = new CalibrationData("DO2", "clear calibration", "sensors.water_quality.calibrateDO");
		cd.setAllowParams(false);
		cd.setParamPrefix("clear");
		cd.setAllowRead(true);
		cd.setTitle("DO2 - Calibration: Clear");
		cd.setInstructions("Clears the current DO2 calbration. You should do this first if you are planning to use any of the other calibration modes. ");
		cd.setUnits("mg/L");
		_calibDataList.add(cd);
		
		cd = new CalibrationData("DO2", "air calibration", "sensors.water_quality.calibrateDO");
		cd.setAllowParams(false);
		cd.setParamPrefix("");
		cd.setAllowRead(true);
		cd.setTitle("DO2 - Calibration: Air");
		cd.setInstructions("Calibrates the DO2 sensor probe to air. You should do this first if you are planning to use any of the other calibration modes. ");
		cd.setUnits("mg/L");
		_calibDataList.add(cd);

		cd = new CalibrationData("DO2", "zero calibration", "sensors.water_quality.calibrateDO");
		cd.setAllowParams(false);
		cd.setParamPrefix("0");
		cd.setAllowRead(true);
		cd.setTitle("DO2 - Calibration: Zero");
		cd.setInstructions("Calibrates the DO2 sensor probe to zero dissolved oxygen. Air calibration should be done prior to this.");
		cd.setUnits("mg/L");
		_calibDataList.add(cd);

		cd = new CalibrationData("EC", "clear calibration", "sensors.water_quality.calibrateEC");
		cd.setAllowParams(false);
		cd.setParamPrefix("clear");
		cd.setAllowRead(false);
		cd.setTitle("EC - Calibration: Clear");
		cd.setInstructions("Clears the current EC calbration. You should do this first if you are planning to use any of the other calibration modes. ");
		cd.setUnits("uS/cm");
		_calibDataList.add(cd);

		cd = new CalibrationData("EC", "dry calibration", "sensors.water_quality.calibrateEC");
		cd.setAllowParams(false);
		cd.setParamPrefix("dry");
		cd.setAllowRead(false);
		cd.setTitle("EC - Calibration: Dry");
		cd.setInstructions("Calibrates the dry Conductivity sensor probe to air. You should do this first if you are planning to use any of the other calibration modes. ");
		cd.setUnits("uS/cm");
		_calibDataList.add(cd);

		cd = new CalibrationData("EC", "single calibration", "sensors.water_quality.calibrateEC");
		cd.setAllowParams(true);
		cd.setParamPrefix("one,");
		cd.setAllowRead(false);
		cd.setTitle("EC - Calibration: Single-point");
		cd.setInstructions("Calibrates the Conductivity sensor probe to a single-point. ");
		cd.setUnits("uS/cm");
		_calibDataList.add(cd);

		cd = new CalibrationData("EC", "low calibration", "sensors.water_quality.calibrateEC");
		cd.setAllowParams(true);
		cd.setParamPrefix("low,");
		cd.setAllowRead(false);
		cd.setTitle("EC - Calibration: Low");
		cd.setInstructions("Calibrates the Conductivity sensor probe to a low-point. ");
		cd.setUnits("uS/cm");
		_calibDataList.add(cd);

		cd = new CalibrationData("EC", "high calibration", "sensors.water_quality.calibrateEC");
		cd.setAllowParams(true);
		cd.setParamPrefix("high,");
		cd.setAllowRead(false);
		cd.setTitle("EC - Calibration: High");
		cd.setInstructions("Calibrates the Conductivity sensor probe to a high-point. ");
		cd.setUnits("uS/cm");
		_calibDataList.add(cd);

		cd = new CalibrationData("TEMP", "manual calibration", "sensors.water_quality.calibrateTM");
		cd.setAllowParams(true);
		cd.setParamPrefix("high,");
		cd.setAllowRead(false);
		cd.setTitle("TEMP - Calibration: Manual");
		cd.setInstructions("Calibration for temperature has to be done manually for now ");
		cd.setUnits("deg C");
		_calibDataList.add(cd);
		
		return;
	}
	
	public static LazyCalibrationConfigurator getInstance() {
		if (_lazyConfig == null) {
			_lazyConfig = new LazyCalibrationConfigurator();
		}
		
		return _lazyConfig; 
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
