package net.oukranos.oreadv1.interfaces;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import net.oukranos.oreadv1.types.ControllerState;
import net.oukranos.oreadv1.types.ControllerStatus;
import net.oukranos.oreadv1.types.MainControllerInfo;
import net.oukranos.oreadv1.types.Status;
import net.oukranos.oreadv1.util.OreadLogger;

public abstract class AbstractController {
	/* Get an instance of the OreadLogger class to handle logging */
	protected static final OreadLogger OLog = OreadLogger.getInstance();
	
	protected String _name = "controller";
	protected String _type = "unknown";
	private ControllerState _state = ControllerState.UNKNOWN;
	private Status _lastCommandStatus = Status.UNKNOWN;
	private String _logData = "";
	private Lock _stateLock = null;
	
	protected MainControllerInfo _mainInfo = null;
	
	public AbstractController() {
		_stateLock = new ReentrantLock();
		return;
	}
	
	public abstract Status initialize(Object initializer);
	public abstract Status start();
	public abstract ControllerStatus performCommand(String cmdStr, String paramStr);
	public abstract Status stop();
	public abstract Status destroy();

	/********************/
	/** Getter Methods **/
	/********************/
	public String getName() {
		return this._name;
	}
	
	public String getType() {
		return this._type;
	}
	
	public ControllerState getState() {
		ControllerState state;
		
		_stateLock.lock();
		state = this._state;
		_stateLock.unlock();
		
		return state;
	}
	
	public Status getLastCmdStatus() {
		return this._lastCommandStatus;
	}
	
	public String getLogData() {
		return this._logData;
	}
	
	public ControllerStatus getControllerStatus() {
		return (new ControllerStatus(this.getName(), this.getType(), 
				this.getState(), this.getLastCmdStatus(), this.getLogData()));
	}
	
	public String toString() {
		return this.getType() + "." + this.getName();
	}

	/********************/
	/** Setter Methods **/
	/********************/
	protected void setName(String name) {
		if (name == null) {
			return;
		}
		
		this._name = name;
		
		return;
	}
	
	protected void setType(String type) {
		if (type == null) {
			return;
		}
		
		this._type = type;
		
		return;
	}
	
	protected void setState(ControllerState state) {
		_stateLock.lock();
		this._state = state;
		OLog.dbg(getType() + "." + getName() + " state is now " + _state.toString());
		_stateLock.unlock();
	}
	
	protected void setLastCmdStatus(Status status) {
		this._lastCommandStatus = status;
	}
	
	protected void setLogData(String message) {
		this._logData = message;
	}
	
	/*****************************/
	/** Command Utility Methods **/
	/*****************************/
	protected Status verifyCommand(String cmdStr) {
		/* Check that inputs are valid */
		if (cmdStr == null) {
			this.writeErr("Invalid command string");
			return Status.FAILED;
		}
		
		String sigStr = this.getType() + "." + this.getName();
		/* Check if the given command matches this controller's signature */
		if ( cmdStr.startsWith(sigStr) == false ) {
			this.writeErr("Signature does not match against " + sigStr);
			return Status.FAILED;
		}
		
		return Status.OK;
	}
	
	protected String extractCommand(String cmdStr) {
		/* Get the command portion of the string */
		int shortCmdStartIdx = cmdStr.lastIndexOf('.') + 1;
		/* Check if the last index is sane */
		if (shortCmdStartIdx < 0) {
			this.writeErr("Could not extract command");
			return null;
		}
		String shortCmdStr = cmdStr.substring(shortCmdStartIdx);
		if (shortCmdStr == null) {
			this.writeErr("Malformed command string");
			return null;
		}
		
		if (shortCmdStr.isEmpty()) {
			this.writeErr("Malformed command string");
			return null;
		}
		
		return shortCmdStr;
	}
	
	/*****************************/
	/** Logging Utility Methods **/
	/*****************************/
	protected Status writeInfo(String message) {
		this.setLastCmdStatus(Status.OK);
		this.setLogData(message);
		
		OLog.info(this.getLogData());
		
		return this.getLastCmdStatus();
	}
	
	protected Status writeErr(String message) {
		this.setLastCmdStatus(Status.FAILED);
		this.setLogData(message);
		
		OLog.err(this.getLogData());
		
		return this.getLastCmdStatus();
	}
	
	protected Status writeWarn(String message) {
		this.setLastCmdStatus(Status.OK);
		this.setLogData(message);
		
		OLog.warn(this.getLogData());
		
		return this.getLastCmdStatus();
	}
	
	protected IPersistentDataBridge getPersistentDataBridge() {
		if (_mainInfo == null) {
			OLog.err("MainInfo is NULL in " + this.getType() + "." + this.getName() );
			return null;
		}
		
		IPersistentDataBridge pDataStore 
			= (IPersistentDataBridge) _mainInfo
				.getFeature("persistentDataStore");
		if (pDataStore == null) {
			return pDataStore;
		}
		
		if ( pDataStore.isReady() == false ) {
			if (pDataStore.initialize(_mainInfo) != Status.OK) {
				pDataStore = null;
			}
		}
		
		return pDataStore;
	}
}
