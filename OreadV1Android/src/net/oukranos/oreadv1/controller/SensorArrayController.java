package net.oukranos.oreadv1.controller;

import java.util.Arrays;

import net.oukranos.oreadv1.interfaces.AbstractController;
import net.oukranos.oreadv1.interfaces.SensorEventHandler;
import net.oukranos.oreadv1.types.ControllerState;
import net.oukranos.oreadv1.types.Status;
import net.oukranos.oreadv1.types.WaterQualityData;
import net.oukranos.oreadv1.util.OLog;

public class SensorArrayController extends AbstractController implements SensorEventHandler {
	private static SensorArrayController _sensorArrayController = null;
	private BluetoothController _bluetoothController = null;
	private WaterQualityData _sensorData = null;
	private Thread _sensorControllerThread = null;
	private Sensor _activeSensor = null;
	
	private PHSensor _phSensor = null;
	private DissolvedOxygenSensor _do2Sensor = null;
	private ConductivitySensor _ecSensor = null;
	
	private byte[] _tempDataBuffer = new byte[512];
	private int _tempDataOffset = 0;
	
	private boolean _isDataAvailable = false;
	
	private SensorArrayController(BluetoothController bluetooth, WaterQualityData sensorDataBuffer) {
		_sensorData = sensorDataBuffer;
		_bluetoothController = bluetooth;
		return;
	}
	
	public static SensorArrayController getInstance(BluetoothController bluetooth, WaterQualityData sensorDataBuffer) {
		if (sensorDataBuffer == null) {
			return null;
		}
		
		if (_sensorArrayController == null) {
			_sensorArrayController = new SensorArrayController(bluetooth, sensorDataBuffer);
		}
		
		return _sensorArrayController;
	}
	@Override
	public Status initialize() {
		_bluetoothController.setEventHandler(this);
		
		/* Initialize the sensors */
		if (_phSensor == null) { 
			_phSensor = new PHSensor(_bluetoothController);
		}
		
		if (_do2Sensor == null) {
			_do2Sensor = new DissolvedOxygenSensor(_bluetoothController);
		}
		
		if (_ecSensor == null) {
			_ecSensor = new ConductivitySensor(_bluetoothController);
		}
		
		this.setState(ControllerState.READY);
		return Status.OK;	
	}
	
	public Status readSensorData() {
		if (this.getState() != ControllerState.READY) {
			return Status.FAILED;
		}
		
		Sensor sensors[] = { _phSensor, _do2Sensor, _ecSensor };
		
		for (Sensor s : sensors) {
			if ( readSensor(s) != Status.OK ) {
				OLog.err("Failed to receive from " + s.getName());
				return Status.FAILED;
			}
			
			ReceiveStatus rs = s.getReceiveDataStatus();
			if ((rs == ReceiveStatus.COMPLETE) || (rs == ReceiveStatus.PARTIAL)) {
				if (s.getReceivedDataSize() > 0) {
					OLog.info(new String(s.getReceivedData()).trim());
				}
			}
			s.clearReceivedData();
		}
		
		return Status.OK;
	}

	@Override
	public Status destroy() {
		this.setState(ControllerState.UNKNOWN);
		
		return Status.OK;
	}

	@Override
	public void onDataReceived(byte[] data) {
		if (_activeSensor == null) {
			/* Discard incoming data while no sensors are active */
			OLog.err("No sensors are active");
			return;
		}
		
		if (data == null) {
			OLog.err("Received data is null");
			return;
		}
		
		final int maxLen = _tempDataBuffer.length;
		int dataLength = 0;
		
		/* Check if the buffer still has space to receive the data */
		if ( data.length >= maxLen ) {
			return;
		}
		
		/* Copy data to temp buffer */
		if ( data.length < maxLen ) {
			System.arraycopy(data, 0, _tempDataBuffer, 0, data.length);
			dataLength = data.length;
		} else {
			System.arraycopy(data, 0, _tempDataBuffer, 0, maxLen);
			dataLength = maxLen;
			OLog.warn("Received data exceeds temp buffer size. Data might have been lost. " );
		}
		
		
		/* Receive the data if available */
		ReceiveStatus status = _activeSensor.receiveData(_tempDataBuffer, dataLength);
		
		/* Clear the temp data buffer */
		Arrays.fill(_tempDataBuffer, (byte)(0));
		
		/* Break the loop if the data is complete or we failed */
		if ( ( status == ReceiveStatus.COMPLETE ) || ( status == ReceiveStatus.FAILED ) ) {
			if ( status == ReceiveStatus.FAILED ) {
				/* Log an error */
				OLog.err("Failed to receive data");
			}
			
			/* Interrupt the waiting sensor array controller thread */
			if ( ( _sensorControllerThread != null ) && ( _sensorControllerThread.isAlive() ) ) {
				_sensorControllerThread.interrupt();
			} else {
				OLog.warn("Original read sensor thread does not exist");
			}
			return;
		}
			
		/* For partial receives, wait for the next part */
	}
	
	/* Private functions */
	private Status readSensor(Sensor sensor) {
		_sensorControllerThread = Thread.currentThread();
		
		if (sensor == null) {
			OLog.err("Sensor is null");
			_sensorControllerThread = null;
			return Status.FAILED;
		}
		
		if (sensor.initialize() != Status.OK) {
			OLog.err("Failed to initialize " + sensor.getName());
			_sensorControllerThread = null;
			return Status.FAILED;
		}
		
		if (sensor.read() != Status.OK) {
			OLog.err("Failed to read from " + sensor.getName());
			sensor.destroy();
			_sensorControllerThread = null;
			return Status.FAILED;
		}
		
		/* Set the current active sensor */
		_activeSensor = sensor;
		
		/* Wait until the sensor's response is received */
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			OLog.info("Interrupted");
		}
		
		sensor.destroy();
		_sensorControllerThread = null;
		
		return Status.OK;
	}
	
	/* Inner Classes */
	private enum State {
		UNKNOWN, READY, BUSY
	}
	
	private abstract class Sensor {
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
//			if (this._state == State.READY) {
//				return Status.OK;
//			}
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
			
			if ( (this._dataOffset + dataLen) <  this._dataBuffer.length ) {
				byte readByte = 0;
				int offset = this._dataOffset;
				boolean isCompleteData = false;
				
				for (int i = 0; i < dataLen; i++) {
					readByte = data[i];
					
					/* Check if this is a terminating byte */
					if ( (readByte == '\0') || (readByte == '\r') ) {
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
			if ( _dataOffset <= 0 ) {
				return null;
			}
			return _dataBuffer;
		}
		
		public int getReceivedDataSize() {
			return ( _dataOffset < 0 ? 0 : _dataOffset );
		}
		
		public Status clearReceivedData() {
			Arrays.fill(_dataBuffer, (byte)(0));
			_dataOffset = 0;
			return Status.OK;
		}
		
		protected String getName() {
			return this._name;
		}
		
		protected void setName(String name) {
			this._name = name;
			return;
		}
		
	}
	
	private class PHSensor extends Sensor {
		private static final String READ_CMD_STR = "READ 0";
		private static final String INFO_CMD_STR = "CMD 0 I";
		private static final String CALIBRATE_PH_4_CMD_STR = "CMD 0 F";
		private static final String CALIBRATE_PH_7_CMD_STR = "CMD 0 S";
		private static final String CALIBRATE_PH_10_CMD_STR = "CMD 0 T";

		public PHSensor(BluetoothController bluetooth) {
			super(bluetooth);
			this.setName("pH Sensor");
		}

		@Override
		public Status read() {
			return send(READ_CMD_STR.getBytes());
		}
		
		public Status getInfo() {
			return send(INFO_CMD_STR.getBytes());
		}
		
		public Status calibrate(int phLevel) {
			switch (phLevel) {
				case 4:
					return send(CALIBRATE_PH_4_CMD_STR.getBytes());
				case 7:
					return send(CALIBRATE_PH_7_CMD_STR.getBytes());
				case 10:
					return send(CALIBRATE_PH_10_CMD_STR.getBytes());
				default:
					break;
			}
			return Status.FAILED;
		}

	}
	
	private class DissolvedOxygenSensor extends Sensor {
		private static final String READ_CMD_STR = "READ 1";
		private static final String INFO_CMD_STR = "CMD 1 I";
		private static final String CALIBRATE_DO_CMD_STR = "CMD 1 M";

		public DissolvedOxygenSensor(BluetoothController bluetooth) {
			super(bluetooth);
			this.setName("DO2 Sensor");
		}

		@Override
		public Status read() {
			return send(READ_CMD_STR.getBytes());
		}
		
		public Status getInfo() {
			return send(INFO_CMD_STR.getBytes());
		}
		
		public Status calibrate() {
			return send(CALIBRATE_DO_CMD_STR.getBytes());
		}
	}
	
	private class ConductivitySensor extends Sensor {
		private static final String READ_CMD_STR = "READ 2";
		private static final String INFO_CMD_STR = "CMD 2 I";
		private static final String CALIBRATE_EC_DEFAULT_CMD_STR = "CMD 2 Z0";
		private static final String CALIBRATE_EC_220_CMD_STR = "CMD 2 Z2";
		private static final String CALIBRATE_EC_3000_CMD_STR = "CMD 2 Z30";
		private static final String CALIBRATE_EC_10500_CMD_STR = "CMD 2 Z10";
		private static final String CALIBRATE_EC_40000_CMD_STR = "CMD 2 Z40";
		private static final String CALIBRATE_EC_62000_CMD_STR = "CMD 2 Z62";
		private static final String CALIBRATE_EC_90000_CMD_STR = "CMD 2 Z90";

		public ConductivitySensor(BluetoothController bluetooth) {
			super(bluetooth);
			this.setName("EC Sensor");
		}

		@Override
		public Status read() {
			return send(READ_CMD_STR.getBytes());
		}
		
		public Status getInfo() {
			return send(INFO_CMD_STR.getBytes());
		}
		
		public Status calibrate() {
			/* TODO Add other calibration modes here */
			return send(CALIBRATE_EC_DEFAULT_CMD_STR.getBytes());
		}
	}
	
	private enum ReceiveStatus {
		COMPLETE, PARTIAL, IN_PROGRESS, FAILED, UNKNOWN
	}
}
