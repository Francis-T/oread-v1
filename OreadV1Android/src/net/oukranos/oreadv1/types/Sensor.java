package net.oukranos.oreadv1.types;

import java.util.Arrays;

import net.oukranos.oreadv1.controller.BluetoothController;
import net.oukranos.oreadv1.controller.SensorArrayController.ReceiveStatus;
import net.oukranos.oreadv1.controller.SensorArrayController.State;
import net.oukranos.oreadv1.util.OLog;

public abstract class Sensor {
	private String _name = "";
	private State _state = State.UNKNOWN;
	private BluetoothController _btController = null;
	private byte _dataBuffer[] = new byte[512];
	private int _dataOffset = 0;
	private ReceiveStatus _lastReceiveStatus = ReceiveStatus.UNKNOWN;

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

	public abstract Status getParsedData(WaterQualityData container);

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
}