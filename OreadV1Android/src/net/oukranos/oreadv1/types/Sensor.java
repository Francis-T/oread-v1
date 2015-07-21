package net.oukranos.oreadv1.types;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.oukranos.oreadv1.controller.BluetoothController;
import net.oukranos.oreadv1.util.OreadLogger;

public abstract class Sensor {
	/* Get an instance of the OreadLogger class to handle logging */
	private static final OreadLogger OLog = OreadLogger.getInstance();
	
	private String _name = "";
	private State _state = State.UNKNOWN;
	private BluetoothController _btController = null;
	private byte _dataBuffer[] = new byte[512];
	private int _dataOffset = 0;
	private ReceiveStatus _lastReceiveStatus = ReceiveStatus.UNKNOWN;
	private long _timeout = 2000; // TODO default

	/* Sensor Response Matrix */
	protected String R_RESP_PREF  	= "";
	protected String R_DATA_PART  	= "";
	protected String R_RESP_DATA  	= "";
	protected String R_RESP_OK  	= "";
	protected String R_RESP_ERR 	= "";

	public Sensor(BluetoothController bluetooth) {
		this._btController = bluetooth;
	}

	public Status initialize() {
		// if (this._state == State.READY) {
		// return Status.OK;
		// }
		if (this._btController == null) {
			OLog.err("BluetoothController is null");
			return Status.FAILED;
		}

		this._state = State.READY;

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

	public abstract Status read();
	public abstract Status getInfo();
	public abstract Status calibrate(String params);

	public Status destroy() {
		this._state = State.UNKNOWN;

		return Status.OK;
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

		if ((this._dataOffset + dataLen) < this._dataBuffer.length) {
			byte readByte = 0;
			int offset = this._dataOffset;
			boolean isCompleteData = false;

			for (int i = 0; i < dataLen; i++) {
				readByte = data[i];

				/* Check if this is a terminating byte */
				if ((readByte == '\0') || (readByte == '\r')) {
					isCompleteData = true;
					break;
				}

				this._dataBuffer[offset] = readByte;

				offset += 1;
			}

			this._dataOffset = offset;

			if (isCompleteData) {
				return (this._lastReceiveStatus = ReceiveStatus.COMPLETE);
			}

		}

		return (this._lastReceiveStatus = ReceiveStatus.PARTIAL);
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

	public Status getParsedData(WaterQualityData container) {
		if (container == null) {
			OLog.err("Data container is null for " + this.getName());
			return Status.FAILED;
		}

		final byte[] data = this.getReceivedData();
		if (data == null) {
			OLog.err("Received data buffer is null for " + this.getName());
			return Status.FAILED;
		}
		final String dataStr = new String(data).trim();
		
		if (dataStr.contains(R_RESP_PREF) == false) {
			OLog.err("Parsing failed for " + this.getName() + ": Response prefix not found!");
			return Status.FAILED;
		}
		
		/* Check if we've received data */
		if (dataStr.matches(R_RESP_PREF + R_RESP_DATA) == true) {
			Pattern dataPattern = Pattern.compile(R_DATA_PART);
			Matcher dataMatcher = dataPattern.matcher(dataStr);
			
			int matchCount = 0;
			while(dataMatcher.find()) {
				int startIdx = dataMatcher.start();
				int endIdx = dataMatcher.end();
				
				matchCount++;
				
				String matchStr = dataStr.substring(startIdx, endIdx);
				OLog.info("Found match: " + matchStr);
				
				/* Handle the parsed data based on the subclass implementation */
				this.handleParsedData(container, matchCount, matchStr);
				
				/* Capture only up to three data matches */
				if (matchCount == 3) {
					break;
				}
			}
		} else {
			OLog.warn(this.getName() + " input does not match read params");
		}
		
		if (dataStr.contains(R_RESP_PREF + R_RESP_OK) == true) {
			/* TODO */
		}
		

		if (dataStr.contains(R_RESP_PREF + R_RESP_ERR) == true) {
			/* TODO */
		}

		return Status.OK;
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
	
	public long getTimeout() {
		return this._timeout;
	}
	
	public void setTimeout(long timeout) {
		this._timeout = timeout;
		return;
	}
	
	protected abstract void handleParsedData(WaterQualityData container, int count, String match);


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