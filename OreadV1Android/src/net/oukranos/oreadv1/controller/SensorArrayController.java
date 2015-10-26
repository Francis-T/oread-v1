package net.oukranos.oreadv1.controller;

import java.util.Arrays;

import net.oukranos.oreadv1.interfaces.AbstractController;
import net.oukranos.oreadv1.interfaces.SensorEventHandler;
import net.oukranos.oreadv1.types.ControllerState;
import net.oukranos.oreadv1.types.ControllerStatus;
import net.oukranos.oreadv1.types.DataStore;
import net.oukranos.oreadv1.types.MainControllerInfo;
import net.oukranos.oreadv1.types.Sensor;
import net.oukranos.oreadv1.types.Sensor.ReceiveStatus;
import net.oukranos.oreadv1.types.Status;
import net.oukranos.oreadv1.types.WaterQualityData;

public class SensorArrayController extends AbstractController implements SensorEventHandler {
	private static SensorArrayController _sensorArrayController = null;
	
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

	/*************************/
	/** Initializer Methods **/
	/*************************/
	private SensorArrayController() {
		this.setType("sensors");
		this.setName("water_quality");
		return;
	}

	public static SensorArrayController getInstance(MainControllerInfo mainInfo) {
		if (mainInfo == null) {
			OLog.err("Invalid input parameter/s" +
					" in SensorArrayController.getInstance()");
			return null;
		}
		
		BluetoothController bluetooth = (BluetoothController) mainInfo
				.getSubController("bluetooth", "comm");
		if (bluetooth == null) {
			OLog.err("No bluetooth controller available");
			return null;
		}
		
		if (_sensorArrayController == null) {
			_sensorArrayController = new SensorArrayController();
		}
		
		_sensorArrayController._mainInfo = mainInfo;
		_sensorArrayController._bluetoothController = bluetooth;
		
		return _sensorArrayController;
	}

	/********************************/
	/** AbstractController Methods **/
	/********************************/
	@Override
	public Status initialize(Object initializer) {
		/* Initialize the sensors */
		if (_phSensor == null) {
			_phSensor = new PHSensor();
		}

		if (_do2Sensor == null) {
			_do2Sensor = new DissolvedOxygenSensor();
		}

		if (_ecSensor == null) {
			_ecSensor = new ConductivitySensor();
		}

		if (_tempSensor == null) {
			_tempSensor = new TemperatureSensor();
		}
		
		if (_turbiditySensor == null) {
			_turbiditySensor = new TurbiditySensor();
		}

		this.setState(ControllerState.READY);
		return Status.OK;
	}

	@Override
	public Status start() {
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
		OLog.info("Loading sensor data from: " + _sensorData.hashCode());
		
		/* Register our event handlers */
		_bluetoothController.registerEventHandler(this);
		_phSensor.setBluetoothController(_bluetoothController);
		_do2Sensor.setBluetoothController(_bluetoothController);
		_ecSensor.setBluetoothController(_bluetoothController);
		_tempSensor.setBluetoothController(_bluetoothController);
		_turbiditySensor.setBluetoothController(_bluetoothController);
		return Status.OK;
	}

	@Override
	public Status stop() {
		_bluetoothController.unregisterEventHandler(this);
		_phSensor.setBluetoothController(null);
		_do2Sensor.setBluetoothController(null);
		_ecSensor.setBluetoothController(null);
		_tempSensor.setBluetoothController(null);
		_turbiditySensor.setBluetoothController(null);
		
		_sensorData = null;
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
			this.start();
			this.writeInfo("Command Performed: Start");
			
		} else if (shortCmdStr.equals("stop") == true) {
			this.stop();
			this.writeInfo("Command Performed: Stop");
			
		} else {
			this.writeErr("Unknown or invalid command: " + shortCmdStr);
		}
		
		return this.getControllerStatus();
	}

	@Override
	public Status destroy() {
		this.stop();
		this.setState(ControllerState.UNKNOWN);

		return Status.OK;
	}

	/********************/
	/** Public METHODS **/
	/********************/
	public Status readSensor(Sensor s) {
		if (this.getState() != ControllerState.READY) {
			writeErr("Invalid state for sensor read");
			return Status.FAILED;
		}
		
		/* Clear received data prior to taking any readings */
		s.clearReceivedData();

		if (this.getState() == ControllerState.READY) {
			if (performSensorRead(s) != Status.OK) {
				writeErr("Failed to receive from " + s.getName());
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

		_sensorData.updateTimestamp();
		writeInfo("Read " + s.getName() + " finished. Stored in " + _sensorData.hashCode() );
		return Status.OK;
	}
	
	/* XXX This command may safely be deprecated as it is not expected to
	 *		used anymore now that individual commands are available */
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
			Thread.sleep(sensor.getTimeout());
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
			Thread.sleep(sensor.getTimeout());
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
		private static final String READ_CMD_STR = "READ pH";
		private static final String INFO_CMD_STR = "FORCE pH I";
		private static final String CALIBRATE_CMD_STR = "FORCE pH Cal,";

		public PHSensor() {
			this.setName("pH Sensor");
			
			/* Configure the response matchers */ // TODO Should be abstracted
			R_RESP_PREF  = "pH: ";
			R_DATA_PART	 = "[-]*[0-9]+\\.*[0-9]*";
			R_RESP_DATA  = R_DATA_PART;
			R_RESP_OK  	 = "\\*OK";
			R_RESP_ERR 	 = "\\*ERR";

			//this.setTimeout(2000); //TODO
			
			return;
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
		protected void handleParsedData(WaterQualityData container, int count, String match) {
			if (container == null) {
				return;
			}
			
			if (match == null) {
				return;
			}
			
			if (count != 1) {
				return;
			}

			try {
				container.pH = Double.parseDouble(match);
			} catch (NumberFormatException e) {
				OLog.err("Failed to parse value: " + match);
				container.pH = -1.0;
			}
			
			return;
		}
	}

	private class DissolvedOxygenSensor extends Sensor {
		private static final String READ_CMD_STR = "READ DO2";
		private static final String INFO_CMD_STR = "FORCE DO2 I";
		private static final String CALIBRATE_CMD_STR = "FORCE DO2 Cal";

		public DissolvedOxygenSensor() {
			this.setName("Dissolved Oxygen Sensor");

			/* Configure the response matchers */ // TODO Should be abstracted
			R_RESP_PREF  = "DO: ";
			R_DATA_PART	 = "[-]*[0-9]+\\.*[0-9]*";
			R_RESP_DATA  = R_DATA_PART;
			R_RESP_OK  	 = "\\*OK";
			R_RESP_ERR 	 = "\\*ERR";

			//this.setTimeout(2000); //TODO
			
			return;
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
		protected void handleParsedData(WaterQualityData container, int count, String match) {
			if (container == null) {
				return;
			}
			
			if (match == null) {
				return;
			}
			
			if (count != 1) {
				return;
			}
			
			try {
				container.dissolved_oxygen 
					= Double.parseDouble(match);
			} catch (NumberFormatException e) {
				OLog.err("Failed to parse value: " + match);
				container.dissolved_oxygen = -1.0;
			}
			
			return;
		}
	}

	private class ConductivitySensor extends Sensor {
		private static final String READ_CMD_STR = "READ EC";
		private static final String INFO_CMD_STR = "FORCE EC I";
		private static final String CALIBRATE_CMD_STR = "FORCE EC Cal,";


		public ConductivitySensor() {
			this.setName("Conductivity Sensor");

			/* Configure the response matchers */ // TODO Should be abstracted
			R_RESP_PREF  = "EC: ";
			R_DATA_PART	 = "[-]*[0-9]+\\.*[0-9]*";
			R_RESP_DATA  = R_DATA_PART + "," + R_DATA_PART + "," + R_DATA_PART + "," + R_DATA_PART;
			R_RESP_OK  	 = "\\*OK";
			R_RESP_ERR 	 = "\\*ERR";

			//this.setTimeout(2000); //TODO
			
			return;
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
		protected void handleParsedData(WaterQualityData container, int count, String match) {
			if (container == null) {
				return;
			}
			
			if (match == null) {
				return;
			}
			
			switch (count) {
				case 1:
					try {
						container.conductivity 
							= Double.parseDouble(match);
					} catch (NumberFormatException e) {
						OLog.err("Failed to parse value: " + match);
						container.conductivity = -1.0;
					}
					break;
				case 2:
					try {
						container.tds 
							= Double.parseDouble(match);
					} catch (NumberFormatException e) {
						OLog.err("Failed to parse value: " + match);
						container.tds = -1.0;
					}
					break;
				case 3:
					try {
						container.salinity 
							= Double.parseDouble(match);
					} catch (NumberFormatException e) {
						OLog.err("Failed to parse value: " + match);
						container.tds = -1.0;
					}
					break;
				default:
					break;
			}
			return;
		}
	}

	private class TemperatureSensor extends Sensor {
		private static final String READ_CMD_STR = "READ TM";
		private static final String INFO_CMD_STR = "FORCE TM X";
		private static final String CALIBRATE_CMD_STR = "FORCE TM X";

		public TemperatureSensor() {
			this.setName("Temperature Sensor");

			/* Configure the response matchers */ // TODO Should be abstracted
			R_RESP_PREF  = "TM: ";
			R_DATA_PART	 = "[-]*[0-9]+\\.*[0-9]*";
			R_RESP_DATA  = R_DATA_PART + "," + R_DATA_PART;
			R_RESP_OK  	 = "\\*OK";
			R_RESP_ERR 	 = "\\*ERR";

			//this.setTimeout(2000); //TODO
			
			return;
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

//		@Override
//		public Status getParsedData(WaterQualityData container) {
//			if (container == null) {
//				OLog.err("Data container is null for " + this.getName());
//				return Status.FAILED;
//			}
//
//			final byte[] data = this.getReceivedData();
//			if (data == null) {
//				OLog.err("Received data buffer is null for " + this.getName());
//				return Status.FAILED;
//			}
//			final String dataStr = new String(data).trim();
//			final String dataStrSplit[] = dataStr.split(" ");
//			final int splitNum = dataStrSplit.length;
//
//			if (splitNum != 2) {
//				OLog.err("Parsing failed " + this.getName());
//				return Status.FAILED;
//			}
//
//			try {
//				container.temperature = Double.parseDouble(dataStrSplit[1]);
//			} catch (NumberFormatException e) {
//				container.temperature = -1.0;
//			}
//
//			return Status.OK;
//		}

		@Override
		protected void handleParsedData(WaterQualityData container, int count, String match) {
			if (container == null) {
				return;
			}
			
			if (match == null) {
				return;
			}
			
			if (count != 1) {
				return;
			}

			try {
				container.temperature = Double.parseDouble(match);
			} catch (NumberFormatException e) {
				OLog.err("Failed to parse value: " + match);
				container.temperature = -1.0;
			}
			
			return;
		}
	}

	private class TurbiditySensor extends Sensor {
		private static final String READ_CMD_STR = "READ TU";
		private static final String INFO_CMD_STR = "FORCE TU X";
		private static final String CALIBRATE_CMD_STR = "FORCE TU X";

		public TurbiditySensor() {
			this.setName("Turbidity Sensor");

			/* Configure the response matchers */ // TODO Should be abstracted
			R_RESP_PREF  = "TU: ";
			R_DATA_PART	 = "[-]*[0-9]+\\.*[0-9]*";
			R_RESP_DATA  = R_DATA_PART + "," + R_DATA_PART;
			R_RESP_OK  	 = "\\*OK";
			R_RESP_ERR 	 = "\\*ERR";
			
			//this.setTimeout(2000); //TODO
			
			return;
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
		protected void handleParsedData(WaterQualityData container, int count, String match) {
			if (container == null) {
				return;
			}
			
			if (match == null) {
				return;
			}
			
			if (count != 1) {
				return;
			}

			try {
				container.turbidity = Double.parseDouble(match);
			} catch (NumberFormatException e) {
				OLog.err("Failed to parse value: " + match);
				container.turbidity = -1.0;
			}
			
			return;
		}
	}
}
