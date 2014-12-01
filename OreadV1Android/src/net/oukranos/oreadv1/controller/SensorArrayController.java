package net.oukranos.oreadv1.controller;

import net.oukranos.oreadv1.interfaces.AbstractController;
import net.oukranos.oreadv1.interfaces.SensorEventHandler;
import net.oukranos.oreadv1.types.ControllerState;
import net.oukranos.oreadv1.types.Status;
import net.oukranos.oreadv1.types.WaterQualityData;

public class SensorArrayController extends AbstractController implements SensorEventHandler {
	private static SensorArrayController _sensorArrayController = null;
	private BluetoothController _bluetoothController = null;
	private WaterQualityData _sensorData = null;
	private Thread _sensorControllerThread = null;
	
	private PHSensor _phSensor = null;
	private DissolvedOxygenSensor _do2Sensor = null;
	private ConductivitySensor _ecSensor = null;
	
	private byte[] _tempDataBuffer = new byte[512];
	private boolean _isDataAvailable = false;
	
	private SensorArrayController(BluetoothController bluetooth, WaterQualityData sensorDataBuffer) {
		this._sensorData = sensorDataBuffer;
		this._bluetoothController = bluetooth;
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
				return Status.FAILED;
			}
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
		if (data != null) {
			final int maxLen = this._tempDataBuffer.length;
			
			/* Copy data to temp buffer */
			if ( data.length < maxLen ) {
				System.arraycopy(data, 0, this._tempDataBuffer, 0, data.length);
			} else {
				System.arraycopy(data, 0, this._tempDataBuffer, 0, maxLen);
			}
			
			this._isDataAvailable = true;
		}
		
		if (_sensorControllerThread != null) {
			if (_sensorControllerThread.isAlive()) {
				this._isDataAvailable = true;
				_sensorControllerThread.interrupt();
			}
		}
	}
	
	/* Private functions */
	private Status readSensor(Sensor sensor) {
		_sensorControllerThread = Thread.currentThread();
		
		if (sensor == null) {
			_sensorControllerThread = null;
			return Status.FAILED;
		}
		
		if (sensor.initialize() != Status.OK) {
			_sensorControllerThread = null;
			return Status.FAILED;
		}
		
		if (sensor.read() != Status.OK) {
			sensor.destroy();
			_sensorControllerThread = null;
			return Status.FAILED;
		}
		
		/* Attempt to receive multi-part data */
		for (int receives = 0; receives < 10; receives++) {
			boolean wasInterrupted = false;
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				wasInterrupted = true;
			}

			/* Timeout case */
			if (wasInterrupted == false) {
				/* TODO Log a warning */
				break;
			}
			
			/* Receive the data if available */
			if ( this._isDataAvailable == true ) {
				ReceiveStatus status = sensor.receiveData(this._tempDataBuffer);
				this._isDataAvailable = false;
				
				/* Break the loop if the data is complete or we failed */
				if ( (status == ReceiveStatus.COMPLETE) || 
						(status == ReceiveStatus.FAILED) ) {
					/* TODO Log an error */
					break;
				}
				/* For partial receives, loop back and wait for the next part */
			}
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
		
		public Sensor(BluetoothController bluetooth) {
			this._btController = bluetooth;
		}
		
		public Status initialize() {
//			if (this._state == State.READY) {
//				return Status.OK;
//			}
			if (this._btController == null) {
				return Status.FAILED;
			}
			
			this._state = State.READY;
			
			return Status.OK;
		}
		
		public Status send(byte[] data) {
			if (this._state != State.READY) {
				return Status.FAILED;
			}
			
			if (this._btController == null) {
				return Status.FAILED;
			}
			
			if (this._btController.getState() != ControllerState.READY) {
				return Status.FAILED;
			}
			
			_btController.broadcast(data);
			
			return Status.OK;
		}

		public abstract Status read();
		
		public Status destroy() {
			if (this._btController != null) {
				this._btController = null;
			}
			
			this._state = State.UNKNOWN;
			
			return Status.OK;
		}
		
		public ReceiveStatus receiveData(byte data[]) {
			if (data == null) {
				return ReceiveStatus.FAILED;
			}
			
			if ( (this._dataOffset + data.length) <  this._dataBuffer.length ) {
				byte readByte = 0;
				int offset = this._dataOffset;
				boolean isCompleteData = false;
				
				for (int i = 0; i < data.length; i++) {
					readByte = data[i];
					
					/* Check if this is a terminating byte */
					if ( (readByte == '\0') || (readByte == '\r') ) {
						break;
					}
					
					this._dataBuffer[offset] = readByte;
					
					offset += 1;
				}
				
				this._dataOffset = offset;
				
				if (isCompleteData) {
					return ReceiveStatus.COMPLETE;
				}
				
			}
			
			return ReceiveStatus.PARTIAL;
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
		COMPLETE, PARTIAL, IN_PROGRESS, FAILED
	}
}
