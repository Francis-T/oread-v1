package net.oukranos.oreadv1.controller;

import java.util.Arrays;

import net.oukranos.oreadv1.interfaces.AbstractController;
import net.oukranos.oreadv1.interfaces.SensorEventHandler;
import net.oukranos.oreadv1.types.ControllerState;
import net.oukranos.oreadv1.types.ControllerStatus;
import net.oukranos.oreadv1.types.DataStore;
import net.oukranos.oreadv1.types.MainControllerInfo;
import net.oukranos.oreadv1.types.Sensor;
import net.oukranos.oreadv1.types.Status;
import net.oukranos.oreadv1.types.WaterQualityData;
import net.oukranos.oreadv1.util.OLog;

public class SensorArrayController extends AbstractController implements
		SensorEventHandler {
	private static SensorArrayController _sensorArrayController = null;
	private MainControllerInfo _mainInfo = null;
	
	private BluetoothController _bluetoothController = null;
	private WaterQualityData _sensorData = null;
	private Thread _sensorControllerThread = null;
	private Sensor _activeSensor = null;

	private PHSensor _phSensor = null;
	private DissolvedOxygenSensor _do2Sensor = null;
	private ConductivitySensor _ecSensor = null;
	private TemperatureSensor _tempSensor = null;
	private TurbiditySensor _turbiditySensor = null;  

	private byte[] _tempDataBuffer = new byte[512];
//	private int _tempDataOffset = 0;
//
//	private boolean _isDataAvailable = false;

	/*************************/
	/** Initializer Methods **/
	/*************************/
	private SensorArrayController(MainControllerInfo mainInfo, BluetoothController bluetooth) {
		this._mainInfo = mainInfo;
		this._bluetoothController = bluetooth;
		
		this.setType("sensors");
		this.setName("water_quality");
		return;
	}

	public static SensorArrayController getInstance(MainControllerInfo mainInfo) {
		if (mainInfo == null) {
			OLog.err("Main controller info uninitialized or unavailable");
			return null;
		}
		
		BluetoothController bluetooth = (BluetoothController) mainInfo
				.getSubController("bluetooth", "comm");
		if (bluetooth == null) {
			OLog.err("No bluetooth controller available");
			return null;
		}
		
		if (_sensorArrayController == null) {
			_sensorArrayController = new SensorArrayController(mainInfo, bluetooth);
		}
		
		return _sensorArrayController;
	}

	/********************************/
	/** AbstractController Methods **/
	/********************************/
	@Override
	public Status initialize() {
		/* Retrieve the water quality data buffer */
		/* TODO Not sure if this is the best place to put this */
		DataStore dataStore = _mainInfo.getDataStore();
		if (dataStore == null) {
			OLog.err("Data store uninitialized or unavailable");
			return Status.FAILED;
		}
		
		WaterQualityData wqData = (WaterQualityData) dataStore
				.retrieveObject("h2o_quality_data");
		if ( wqData == null ) {
			OLog.err("Water quality data buffer unavailable");
			return Status.FAILED;
		}
		_sensorData = wqData;
		
		/* Register our event handlers */
		_bluetoothController.registerEventHandler(this);

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

		if (_tempSensor == null) {
			_tempSensor = new TemperatureSensor(_bluetoothController);
		}
		
		if (_turbiditySensor == null) {
			_turbiditySensor = new TurbiditySensor(_bluetoothController);
		}

		this.setState(ControllerState.READY);
		return Status.OK;
	}

	@Override
	public ControllerStatus performCommand(String cmdStr, String paramStr) {
		/* Check the command string*/
		if ( verifyCommand(cmdStr) != Status.OK ) {
			return this.getControllerStatus();
		}
		
		/* Extract the command only */
		String shortCmdStr = extractCommand(cmdStr);
		if (shortCmdStr == null) {
			return this.getControllerStatus();
		}
		
		/* Check which command to perform */
		if (shortCmdStr.equals("readPH") == true) {
			this.readSensor(_phSensor);
		} else if (shortCmdStr.equals("readDO") == true) {
			this.readSensor(_do2Sensor);
		} else if (shortCmdStr.equals("readEC") == true) {
			this.readSensor(_ecSensor);
		} else if (shortCmdStr.equals("readTM") == true) {
			this.readSensor(_tempSensor);
		} else if (shortCmdStr.equals("readTU") == true) {
			this.readSensor(_turbiditySensor);
		} else if (shortCmdStr.equals("readAll") == true) {
			this.readAllSensors();
		} else if (shortCmdStr.equals("calibratePH") == true) {
			if (paramStr == null) {
				this.writeErr("Invalid parameter string");
				return this.getControllerStatus();
			}
			this.calibrateSensor(_phSensor, paramStr);
		} else if (shortCmdStr.equals("calibrateDO") == true) {
			if (paramStr == null) {
				this.writeErr("Invalid parameter string");
				return this.getControllerStatus();
			}
			this.calibrateSensor(_do2Sensor, paramStr);
		} else if (shortCmdStr.equals("calibrateEC") == true) {
			if (paramStr == null) {
				this.writeErr("Invalid parameter string");
				return this.getControllerStatus();
			}
			this.calibrateSensor(_ecSensor, paramStr);
		} else if (shortCmdStr.equals("calibrateTM") == true) {
			if (paramStr == null) {
				this.writeErr("Invalid parameter string");
				return this.getControllerStatus();
			}
			this.calibrateSensor(_tempSensor, paramStr);
		} else if (shortCmdStr.equals("calibrateTU") == true) {
			if (paramStr == null) {
				this.writeErr("Invalid parameter string");
				return this.getControllerStatus();
			}
			this.calibrateSensor(_turbiditySensor, paramStr);
		} else if (shortCmdStr.equals("start") == true) {
			this.writeInfo("Started");
		} else {
			this.writeErr("Unknown or invalid command: " + shortCmdStr);
		}
		
		return this.getControllerStatus();
	}

	@Override
	public Status destroy() {
		this.setState(ControllerState.UNKNOWN);
		_bluetoothController.unregisterEventHandler(this);

		return Status.OK;
	}

	/********************/
	/** Public METHODS **/
	/********************/
	public Status readSensor(Sensor s) {
		if (this.getState() != ControllerState.READY) {
			OLog.err("Invalid state for sensor read");
			return Status.FAILED;
		}

		if (this.getState() == ControllerState.READY) {
			if (performSensorRead(s) != Status.OK) {
				s.clearReceivedData();
				OLog.err("Failed to receive from " + s.getName());
				return Status.FAILED;
			}

			ReceiveStatus rs = s.getReceiveDataStatus();
			if ((rs == ReceiveStatus.COMPLETE) || (rs == ReceiveStatus.PARTIAL)) {
				if (s.getReceivedDataSize() > 0) {
					OLog.info(new String(s.getReceivedData()).trim());

					s.getParsedData(_sensorData);
				}
			}
		}
		s.clearReceivedData();

		_sensorData.updateTimestamp();
		OLog.info("Read " + s.getName() + " finished.");
		return Status.OK;
	}
	
	public Status readAllSensors() {
		if (this.getState() != ControllerState.READY) {
			return Status.FAILED;
		}

		Sensor sensors[] = { _phSensor, _do2Sensor, _ecSensor }; // , _tempSensor, _turbiditySensor }; // TODO to be added

		for (Sensor s : sensors) {
			if (this.getState() == ControllerState.READY) {
				if (performSensorRead(s) != Status.OK) {
					s.clearReceivedData();
					OLog.err("Failed to receive from " + s.getName());
					return Status.FAILED;
				}

				ReceiveStatus rs = s.getReceiveDataStatus();
				if ((rs == ReceiveStatus.COMPLETE)
						|| (rs == ReceiveStatus.PARTIAL)) {
					if (s.getReceivedDataSize() > 0) {
						OLog.info(new String(s.getReceivedData()).trim());

						s.getParsedData(_sensorData);
					}
				}
			}
			s.clearReceivedData();
		}

		_sensorData.updateTimestamp();

		return Status.OK;
	}
	
	public Status calibrateSensor(Sensor s, String calibParams) {
		if (this.getState() != ControllerState.READY) {
			return Status.FAILED;
		}

		if (this.getState() == ControllerState.READY) {
			if (performSensorCalibrate(s, calibParams) != Status.OK) {
				s.clearReceivedData();
				OLog.err("Failed to receive from " + s.getName());
				return Status.FAILED;
			}

			ReceiveStatus rs = s.getReceiveDataStatus();
			if ((rs == ReceiveStatus.COMPLETE) || (rs == ReceiveStatus.PARTIAL)) {
				if (s.getReceivedDataSize() > 0) {
					OLog.info(new String(s.getReceivedData()).trim());
					/* TODO Examine the received data to see */ 
				}
			}
		}
		s.clearReceivedData();

		_sensorData.updateTimestamp();

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
		if (data.length >= maxLen) {
			return;
		}

		/* Copy data to temp buffer */
		if (data.length < maxLen) {
			System.arraycopy(data, 0, _tempDataBuffer, 0, data.length);
			dataLength = data.length;
		} else {
			System.arraycopy(data, 0, _tempDataBuffer, 0, maxLen);
			dataLength = maxLen;
			OLog.warn("Received data exceeds temp buffer size. Data might have been lost. ");
		}

		/* Receive the data if available */
		ReceiveStatus status = _activeSensor.receiveData(_tempDataBuffer,
				dataLength);

		/* Clear the temp data buffer */
		Arrays.fill(_tempDataBuffer, (byte) (0));

		/* Break the loop if the data is complete or we failed */
		if ((status == ReceiveStatus.COMPLETE)
				|| (status == ReceiveStatus.FAILED)) {
			if (status == ReceiveStatus.FAILED) {
				/* Log an error */
				OLog.err("Failed to receive data");
			}

			/* Interrupt the waiting sensor array controller thread */
			if ((_sensorControllerThread != null)
					&& (_sensorControllerThread.isAlive())) {
				_sensorControllerThread.interrupt();
			} else {
				OLog.warn("Original read sensor thread does not exist");
			}
			return;
		}

		/* For partial receives, wait for the next part */
	}

	/*********************/
	/** Private Methods **/
	/*********************/
	private Status performSensorRead(Sensor sensor) {
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

	private Status performSensorCalibrate(Sensor sensor, String calibParams) {
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

		if (sensor.calibrate(calibParams) != Status.OK) {
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

	/*******************/
	/** Inner Classes **/
	/*******************/
	private class PHSensor extends Sensor {
		private static final String READ_CMD_STR = "READ 1";
		private static final String INFO_CMD_STR = "FORCE 1 I";
		private static final String CALIBRATE_CMD_STR = "FORCE 1 Cal,";

		public PHSensor(BluetoothController bluetooth) {
			super(bluetooth);
			this.setName("pH Sensor");
		}

		@Override
		public Status read() {
			return send(READ_CMD_STR.getBytes());
		}

		@Override
		public Status getInfo() {
			return send(INFO_CMD_STR.getBytes());
		}

		@Override
		public Status calibrate(String params) {
			return send((CALIBRATE_CMD_STR + params).getBytes());
		}

		@Override
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
			final String dataStrSplit[] = dataStr.split(" ");
			final int splitNum = dataStrSplit.length;

			if (splitNum != 2) {
				OLog.err("Parsing failed " + this.getName());
				return Status.FAILED;
			}

			try {
				container.pH = Double.parseDouble(dataStrSplit[1]);
			} catch (NumberFormatException e) {
				container.pH = -1.0;
			}

			return Status.OK;
		}

	}

	private class DissolvedOxygenSensor extends Sensor {
		private static final String READ_CMD_STR = "READ 2";
		private static final String INFO_CMD_STR = "FORCE 2 I";
		private static final String CALIBRATE_CMD_STR = "FORCE 2 Cal";

		public DissolvedOxygenSensor(BluetoothController bluetooth) {
			super(bluetooth);
			this.setName("Dissolved Oxygen Sensor");
		}

		@Override
		public Status read() {
			return send(READ_CMD_STR.getBytes());
		}

		@Override
		public Status getInfo() {
			return send(INFO_CMD_STR.getBytes());
		}

		@Override
		public Status calibrate(String params) {
			if (params == null) {
				return Status.FAILED;
			}
			
			/* For the DO circuit, blank is a legit parameter */
			if (params.isEmpty()) {
				return send((CALIBRATE_CMD_STR).getBytes());
			}
			
			return send((CALIBRATE_CMD_STR + "," + params).getBytes());
		}

		@Override
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
			final String dataStrSplit[] = dataStr.split(" ");
			final int splitNum = dataStrSplit.length;

			if (splitNum != 2) {
				OLog.err("Parsing failed " + this.getName());
				return Status.FAILED;
			}

			try {
				container.dissolved_oxygen = Double
						.parseDouble(dataStrSplit[1]);
			} catch (NumberFormatException e) {
				container.dissolved_oxygen = -1.0;
			}

			return Status.OK;
		}
	}

	private class ConductivitySensor extends Sensor {
		private static final String READ_CMD_STR = "READ 3";
		private static final String INFO_CMD_STR = "FORCE 3 I";
		private static final String CALIBRATE_CMD_STR = "FORCE 3 CAl,";

		public ConductivitySensor(BluetoothController bluetooth) {
			super(bluetooth);
			this.setName("Conductivity Sensor");
		}

		@Override
		public Status read() {
			return send(READ_CMD_STR.getBytes());
		}

		@Override
		public Status getInfo() {
			return send(INFO_CMD_STR.getBytes());
		}

		@Override
		public Status calibrate(String params) {
			return send((CALIBRATE_CMD_STR + params).getBytes());
		}

		@Override
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
			final String dataStrSplit[] = dataStr.split(" ");
			final int splitNum = dataStrSplit.length;
			if ((splitNum <= 0) || (splitNum > 2)) {
				OLog.err("Parsing failed " + this.getName());
				return Status.FAILED;
			}

			final String econdSplit[] = dataStrSplit[1].split(",");
			final int ecSplitNum = econdSplit.length;
			if (ecSplitNum != 3) {
				OLog.err("Parsing failed " + this.getName());
				return Status.FAILED;
			}

			try {
				container.conductivity = Double.parseDouble(econdSplit[0]);
			} catch (NumberFormatException e) {
				container.conductivity = -1.0;
			}

			try {
				container.tds = Double.parseDouble(econdSplit[1]);
			} catch (NumberFormatException e) {
				container.tds = -1.0;
			}

			try {
				container.salinity = Double.parseDouble(econdSplit[2]);
			} catch (NumberFormatException e) {
				container.salinity = -1.0;
			}

			return Status.OK;
		}
	}

	private class TemperatureSensor extends Sensor {
		private static final String READ_CMD_STR = "READ 4";
		private static final String INFO_CMD_STR = "FORCE 4 X";
		private static final String CALIBRATE_CMD_STR = "FORCE 4 X";

		public TemperatureSensor(BluetoothController bluetooth) {
			super(bluetooth);
			this.setName("Temperature Sensor");
		}

		@Override
		public Status read() {
			return send(READ_CMD_STR.getBytes());
		}

		@Override
		public Status getInfo() {
			/* TODO Not yet implemented */
			return send(INFO_CMD_STR.getBytes());
		}

		@Override
		public Status calibrate(String params) {
			/* TODO Not yet implemented */
			return send(CALIBRATE_CMD_STR.getBytes());
		}

		@Override
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
			final String dataStrSplit[] = dataStr.split(" ");
			final int splitNum = dataStrSplit.length;

			if (splitNum != 2) {
				OLog.err("Parsing failed " + this.getName());
				return Status.FAILED;
			}
			
			final String tempSplit[] = dataStrSplit[1].split(",");
			final int tempSplitNum = tempSplit.length;
			if (tempSplitNum != 2) {
				OLog.err("Parsing failed " + this.getName());
				return Status.FAILED;
			}

			try {
				container.temperature = Double.parseDouble(tempSplit[0]);
			} catch (NumberFormatException e) {
				container.temperature = -1.0;
			}

			return Status.OK;
		}
	}

	private class TurbiditySensor extends Sensor {
		private static final String READ_CMD_STR = "READ 5";
		private static final String INFO_CMD_STR = "FORCE 5 X";
		private static final String CALIBRATE_CMD_STR = "FORCE 5 X";

		public TurbiditySensor(BluetoothController bluetooth) {
			super(bluetooth);
			this.setName("Turbidity Sensor");
		}

		@Override
		public Status read() {
			return send(READ_CMD_STR.getBytes());
		}

		@Override
		public Status getInfo() {
			/* TODO Not yet implemented */
			return send(INFO_CMD_STR.getBytes());
		}

		@Override
		public Status calibrate(String params) {
			/* TODO Not yet implemented */
			return send(CALIBRATE_CMD_STR.getBytes());
		}

		@Override
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
			final String dataStrSplit[] = dataStr.split(" ");
			final int splitNum = dataStrSplit.length;

			if (splitNum != 2) {
				OLog.err("Parsing failed " + this.getName());
				return Status.FAILED;
			}

			try {
				container.turbidity = Double.parseDouble(dataStrSplit[1]);
			} catch (NumberFormatException e) {
				container.turbidity = -1.0;
			}

			return Status.OK;
		}
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
