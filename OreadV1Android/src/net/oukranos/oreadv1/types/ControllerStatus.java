package net.oukranos.oreadv1.types;

/** 
 *  TODO: Should expand this later 
 * @description This class is used to return information about the controller
 *  
 **/
public class ControllerStatus {
	private String _name = "controller";
	private String _type = "unknown";
	private ControllerState _state = ControllerState.UNKNOWN;
	private Status _lastCommandStatus = Status.UNKNOWN;
	private String _logData = "";
	
	/* Blank constructor for this object's subclasses */ 
	protected ControllerStatus() {
		return;
	}
	
	public ControllerStatus(String name, String type, ControllerState state, 
			Status cmdStatus, String info) {
		this._name = name;
		this._type = type;
		this._state = state;
		this._lastCommandStatus = cmdStatus;
		this._logData = info;
		return;
	}
	
	/** Getter Methods **/
	public String getName() {
		return this._name;
	}
	
	public String getType() {
		return this._type;
	}
	
	public ControllerState getState() {
		return this._state;
	}
	
	public Status getLastCmdStatus() {
		return this._lastCommandStatus;
	}
	
	public String getLastCmdInfo() {
		return this._logData;
	}
	
	/** Setter Methods **/
	public void setFields(String name, String type, ControllerState state, 
			Status cmdStatus, String info) {
		this._name = name;
		this._type = type;
		this._state = state;
		this._lastCommandStatus = cmdStatus;
		this._logData = info;
		return;
	}
	
	public void setName(String name) {
		this._name = name;
		return;
	}
	
	public void setType(String type) {
		this._type = type;
		return;
	}
	
	public void setState(ControllerState state) {
		this._state = state;
		return;
	}
	
	public void setLastCmdStatus(Status status) {
		this._lastCommandStatus = status;
		return;
	}
	
	public void setLastCmdInfo(String info) {
		this._logData = info;
		return;
	}
	/** ToString **/
	public String toString() {
		String str = "";
		
		str += "{ ";
		str += this.getName() + ", ";
		str += this.getType() + ", ";
		str += this.getState().toString() + ", ";
		str += this.getLastCmdStatus().toString() + ", ";
		str += this.getLastCmdInfo().toString();
		str += " }";
		
		return str;
	}
}
