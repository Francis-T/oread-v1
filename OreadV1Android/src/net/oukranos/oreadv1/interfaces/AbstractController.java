package net.oukranos.oreadv1.interfaces;

import net.oukranos.oreadv1.types.ControllerState;
import net.oukranos.oreadv1.types.Status;

public abstract class AbstractController {
	protected String _name = "Unnamed";
	private ControllerState _state = ControllerState.UNKNOWN;
	private Status _lastCommandStatus = Status.UNKNOWN;
	private String _logData = "";
	
	public abstract Status initialize();
	public abstract Status destroy();

	protected String getName() {
		return this._name;
	}
	
	protected void setName(String name) {
		if (name == null) {
			return;
		}
		
		this._name = name;
		
		return;
	}
	
	public ControllerState getState() {
		return this._state;
	}
	
	protected void setState(ControllerState state) {
		this._state = state;
	}
	
	public Status getLastCmdStatus() {
		return this._lastCommandStatus;
	}
	
	protected void setLastCmdStatus(Status status) {
		this._lastCommandStatus = status;
	}
	
	public String getLogData() {
		return this._logData;
	}
	
	protected void setLogData(String message) {
		this._logData = message;
	}
}
