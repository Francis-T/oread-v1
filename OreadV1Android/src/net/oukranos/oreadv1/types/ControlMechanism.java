package net.oukranos.oreadv1.types;

import java.util.Arrays;

import net.oukranos.oreadv1.controller.BluetoothController;
import net.oukranos.oreadv1.interfaces.IPersistentDataBridge;
import net.oukranos.oreadv1.util.OreadLogger;

public abstract class ControlMechanism {
	/* Get an instance of the OreadLogger class to handle logging */
	protected static final OreadLogger OLog = OreadLogger.getInstance();
	
	/* Configurable properties */
	private String 				_name = "";
	private boolean 			_isBlocking = false;
	private boolean 			_isStatusPollable = false;
	private long 				_waitTimeout = 2000;
	private long 				_pollTimeout = 2000;

	/* Internal properties */
	private State 				_state = State.UNKNOWN;
	private byte 				_dataBuffer[] = new byte[512];
	private int 				_dataOffset = 0;
	private ReceiveStatus 		_lastReceiveStatus = ReceiveStatus.UNKNOWN;
	private BluetoothController 	_btController = null;
	private IPersistentDataBridge	_pDataStore = null;
	
	protected MainControllerInfo		_mainInfo = null;

	public Status initialize(MainControllerInfo mainInfo) {
		this._state = State.READY;
		this._mainInfo = mainInfo;

		return Status.OK;
	}

	public Status send(byte[] data) {
		if (this._state != State.READY) {
			OLog.err("Invalid sensor state");
			return Status.FAILED;
		}

		if (this._btController == null) {
			OLog.err("BluetoothController is null");
			return Status.FAILED;
		}

		if (this._btController.getState() != ControllerState.ACTIVE) {
			OLog.err("BluetoothController has not been connected");
			return Status.FAILED;
		}

		_btController.broadcast(data);

		return Status.OK;
	}

	public abstract Status activate();
	public abstract Status activate(String params);
	public abstract Status deactivate();
	public abstract Status deactivate(String params);
	public abstract Status pollStatus();
	public boolean shouldContinuePolling() {
		return false;
	}
	
	public Status destroy() {
		this._state = State.UNKNOWN;

		return Status.OK;
	}
	
	public boolean isBlocking() {
		return this._isBlocking;
	}
	
	public void setBlocking(boolean isBlocking) {
		this._isBlocking = isBlocking;
		return;
	}
	
	public boolean isPollable() {
		return this._isStatusPollable;
	}
	
	public void setPollable(boolean isPollable) {
		this._isStatusPollable = isPollable;
		return;
	}
	
	public long getPollDuration() {
		return this._pollTimeout;
	}
	
	public void setPollDuration(long timeout) {
		this._pollTimeout = timeout;
		return;
	}
	
	public long getTimeoutDuration() {
		return this._waitTimeout;
	}
	
	public void setTimeoutDuration(long timeout) {
		this._waitTimeout = timeout;
		return;
	}

	public ReceiveStatus receiveData(byte data[], int dataLen) {
		if (data == null) {
			OLog.err("Data is null");
			return (this._lastReceiveStatus = ReceiveStatus.FAILED);
		}

		if (dataLen <= 0) {
			OLog.err("Data length is invalid");
			/* TODO Should empty data be considered a failure? */
			return (this._lastReceiveStatus = ReceiveStatus.FAILED);
		}
		
		this.clearReceivedData();
		
		/* Set the offsets back to zero */
		_dataOffset = 0;

		OLog.info("Checking buffer lengths");
		OLog.info("    offset  = " + Integer.toString(_dataOffset));
		OLog.info("    datalen = " + Integer.toString(dataLen));
		OLog.info("    max_len = " + Integer.toString(_dataBuffer.length));
		if ((this._dataOffset + dataLen) < this._dataBuffer.length) {
			byte readByte = 0;
			int offset = this._dataOffset;

			OLog.info("Processing data...");
			for (int i = 0; i < dataLen; i++) {
				readByte = data[i];

//				/* Check if this is a terminating byte */
//				if ((readByte == '\0') || (readByte == '\r') || (readByte == '\n')) {
//					isCompleteData = true;
//					break;
//				}

				this._dataBuffer[offset] = readByte;

				offset += 1;
			}

			this._dataOffset = offset;
//			OLog.info("Done processing." + "[" + new String(this._dataBuffer) + "]" +
//					  "New offset = " + Integer.toString(_dataOffset));
		} else {
			OLog.info("Cannot process due to invalid lengths");
		}

		return (this._lastReceiveStatus = ReceiveStatus.COMPLETE);
	}

	public ReceiveStatus getReceiveDataStatus() {
		return (this._lastReceiveStatus);
	}

	public byte[] getReceivedData() {
		if (_dataOffset <= 0) {
			return null;
		}
		return _dataBuffer;
	}

	public int getReceivedDataSize() {
		return (_dataOffset < 0 ? 0 : _dataOffset);
	}

	public Status clearReceivedData() {
		Arrays.fill(_dataBuffer, (byte) (0));
		_dataOffset = 0;
		return Status.OK;
	}

	public String getName() {
		return this._name;
	}

	protected void setName(String name) {
		this._name = name;
		return;
	}
	
	protected IPersistentDataBridge getPersistentDataBridge() {
		return _pDataStore;
	}

	public void setPersistentDataBridge(IPersistentDataBridge dataBridge) {
		this._pDataStore = dataBridge;
		return;
	}
	
	protected BluetoothController getBluetoothController() {
		return _btController;
	}

	public void setBluetoothController(BluetoothController bluetoothController) {
		this._btController = bluetoothController;
		return;
	}

	/*************************/
	/** Shared Enumerations **/
	/*************************/
	public enum State {
		UNKNOWN, READY, BUSY
	}
	
	public enum ReceiveStatus {
		COMPLETE, PARTIAL, IN_PROGRESS, FAILED, UNKNOWN
	}
}