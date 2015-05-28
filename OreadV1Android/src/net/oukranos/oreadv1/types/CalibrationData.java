package net.oukranos.oreadv1.types;

public class CalibrationData {
	private String _title = "Sensor Calibration";
	private String _instructions = "These are my instructions.";
	private boolean _allowContinuousRead = false;
	private boolean _hasParams = false;
	private String _paramPrefix = "";
	private String _command = "sensors.readAll";
	private String _sensor = "";
	private String _mode = "";
	private String _units = "";
	
	public CalibrationData(String sensor, String mode, String command) {
		this._sensor = sensor;
		this._mode = mode;
		this._command = command;
		return;
	}
	
	public String getTitle() {
		return this._title;
	}
	
	public String getInstructions() {
		return this._instructions;
	}
	
	public boolean shouldAllowRead() {
		return this._allowContinuousRead;
	}
	
	public String getCommand() {
		return this._command;
	}
	
	public String getSensor() {
		return this._sensor;
	}
	
	public String getMode() {
		return this._mode;
	}
	
	public void setAllowRead(boolean flag) {
		this._allowContinuousRead = flag;
		return;
	}
	
	public void setTitle(String title) {
		this._title = title;
		return;
	}
	
	public void setInstructions(String instructions) {
		this._instructions = instructions;
		return;
	}
	
	public void setMode(String mode) {
		this._mode = mode;
		return;
	}
	
	public void setSensor(String sensor) {
		this._sensor = sensor;
		return;
	}
	
	public void setCommand(String command) {
		this._command = command;
		return;
	}

	public boolean shouldAllowParams() {
		return _hasParams;
	}

	public void setAllowParams(boolean hasParams) {
		this._hasParams = hasParams;
	}

	public String getParamPrefix() {
		return _paramPrefix;
	}

	public void setParamPrefix(String _paramPrefix) {
		this._paramPrefix = _paramPrefix;
	}

	public String getUnits() {
		return _units;
	}

	public void setUnits(String _units) {
		this._units = _units;
	}
}
