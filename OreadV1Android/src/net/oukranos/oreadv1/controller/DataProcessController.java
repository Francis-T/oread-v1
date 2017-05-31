package net.oukranos.oreadv1.controller;

import net.oukranos.oreadv1.interfaces.AbstractController;
import net.oukranos.oreadv1.interfaces.IPersistentDataBridge;
import net.oukranos.oreadv1.types.ChemicalPresenceData;
import net.oukranos.oreadv1.types.ControllerState;
import net.oukranos.oreadv1.types.ControllerStatus;
import net.oukranos.oreadv1.types.MainControllerInfo;
import net.oukranos.oreadv1.types.SiteDeviceData;
import net.oukranos.oreadv1.types.SiteDeviceImage;
import net.oukranos.oreadv1.types.SiteDeviceReportData;
import net.oukranos.oreadv1.types.Status;
import net.oukranos.oreadv1.types.WaterQualityData;
import net.oukranos.oreadv1.util.DataStoreUtils.DSUtils;

public class DataProcessController extends AbstractController {
	private static DataProcessController _dataProcessController = null;

	private DataProcessController(MainControllerInfo mainInfo) {
		this.setState(ControllerState.UNKNOWN);

		this.setType("data");
		this.setName("process");
		return;
	}

	public static DataProcessController getInstance(MainControllerInfo mainInfo) {
		if (mainInfo == null) {
			OLog.err("Invalid input parameter/s in " +
					"DataProcessController.getInstance()");
			return null;
		}
		
		if (_dataProcessController == null) {
			_dataProcessController = new DataProcessController(mainInfo);
		}
		
		_dataProcessController._mainInfo = mainInfo;

		return _dataProcessController;
	}

	/********************************/
	/** AbstractController Methods **/
	/********************************/
	@Override
	public Status initialize(Object initializer) {
		
		/* Check the controller state prior to initialize */
		ControllerState state = getState();
		if (state != ControllerState.UNKNOWN) {
			return writeErr(toString() 
					+ " state is invalid for initialize(): " 
					+ state.toString());
		}
		
		this.setState(ControllerState.INACTIVE);

		return writeInfo("DataProcessController initialized");
	}

	@Override
	public Status start() {
		
		/* Check the controller state prior to start */
		ControllerState state = getState();
		if (state != ControllerState.INACTIVE) {
			return writeErr(toString() 
					+ " state is invalid for start(): " 
					+ state.toString());
		}
		
		this.setState(ControllerState.READY);
		
		return writeInfo("DataProcessController started");
	}

	@Override
	public ControllerStatus performCommand(String cmdStr, String paramStr) {
		/* Check the command string */
		if (verifyCommand(cmdStr) != Status.OK) {
			return this.getControllerStatus();
		}

		/* Extract the command only */
		String shortCmdStr = extractCommand(cmdStr);
		if (shortCmdStr == null) {
			return this.getControllerStatus();
		}

		/* Check which command to perform */
		if (shortCmdStr.equals("lfsbCapture")) {
			/* Get the stored ChemicalPresenceData object */
			ChemicalPresenceData captureData = 
					(ChemicalPresenceData) DSUtils
						.getStoredObject(_mainInfo.getDataStore(), 
								"hg_as_detection_data");
			if (captureData == null) {
				writeErr("ChemicalPresenceData object not found");
			} else {
				OLog.info("Retrieving from: " + captureData.hashCode());
				processImageData(captureData);
				this.writeInfo("Command Performed: Process Image Data");
			}
			
		} else if (shortCmdStr.equals("clearLfsbCapture")) {
			clearImageData();
			this.writeInfo("Command Performed: Clear Image Data");
			
		} else if (shortCmdStr.equals("waterQuality")) {
			/* Get the stored WaterQualityData object */
			WaterQualityData captureData = 
					(WaterQualityData) DSUtils
						.getStoredObject(_mainInfo.getDataStore(), 
								"h2o_quality_data");
			if (captureData == null) {
				writeErr("WaterQualityData object not found");
			} else {
				OLog.info("Retrieving from: " + captureData.hashCode());
				OLog.info("Capture Data Contents: " + captureData.toString());
				if (processWaterQualityData(captureData) == Status.OK) {
					writeInfo("Command Performed: Process Water Quality Data");
				}
			}
			
		} else if (shortCmdStr.equals("clearWaterQuality")) {
			clearWaterQualityData();
			this.writeInfo("Command Performed: Clear Water Quality Data");
			
		} else if (shortCmdStr.equals("clearWaterQuality")) {
			initialize(null);
			this.writeInfo("Command Performed: Initialized Data Process Controller");
			
		} else if (shortCmdStr.equals("start") == true) {
			this.writeInfo("Command Performed: Start");
			
		} else if (shortCmdStr.equals("stop") == true) {
			this.writeInfo("Command Performed: Stop");
			
		} else {
			this.writeErr("Unknown or invalid command: " + shortCmdStr);
			
		}

		return this.getControllerStatus();
	}

	@Override
	public Status stop() {
		
		/* Check the controller state prior to start */
		ControllerState state = getState();
		if (state != ControllerState.INACTIVE) {
			return writeErr(toString() 
					+ " state is invalid for start(): " 
					+ state.toString());
		}
		
		return writeInfo("DataProcessController stopped");
	}

	@Override
	public Status destroy() {
		if (this.getState() == ControllerState.UNKNOWN) {
			return writeInfo("DataProcessController cleanup finished");
		}

		this.setState(ControllerState.UNKNOWN);

		return writeInfo("DataProcessController cleanup finished");
	}

	/*********************/
	/** Private Methods **/
	/*********************/
	private void processImageData(ChemicalPresenceData d) {
		/* Get the stored SiteDeviceImage object */
		SiteDeviceImage siteImage = 
				(SiteDeviceImage) DSUtils
					.getStoredObject(_mainInfo.getDataStore(), 
							"site_device_image");
		if (siteImage == null) {
			writeErr("SiteDeviceImage object not found");
			return;
		}
		
		siteImage.setCaptureFile(d.getCaptureFileName(), d.getCaptureFilePath());
		siteImage.addReportData(new SiteDeviceReportData("Mercury", "Water", 0f, ""));
		
		DSUtils.updateStoredObject(_mainInfo.getDataStore(), "site_device_image", 
				siteImage);

		/* Add persistent data flag for unsent image capture availability */
		IPersistentDataBridge pDataStore = getPersistentDataBridge();
		if (pDataStore == null) {
			return;
		}
		pDataStore.put("IMG_CAPTURE_AVAILABLE", "true");
		
		return;
	}
	
	private Status processWaterQualityData(WaterQualityData d) {
		/* Get the stored SiteDeviceData object */
		SiteDeviceData origData = 
				(SiteDeviceData) DSUtils
					.getStoredObject(_mainInfo.getDataStore(), 
							"site_device_data");
		if (origData == null) {
			writeErr("SiteDeviceData object not found");
			return Status.FAILED;
		}
		
		/* Copy the id and context into a new SiteDeviceData object */
		SiteDeviceData siteData 
			= new SiteDeviceData(origData.getId(), 
								 origData.getContext());
		
		/* Add the water quality parameters as report data */
		SiteDeviceReportData repData;
		String addtlInfo;
		
		addtlInfo = "OK";
		if ((d.pH < 0.1) || (d.pH > 15)) {
			addtlInfo = "Invalid data for pH: " + d.pH;
			writeErr(addtlInfo);
		}
		repData = new SiteDeviceReportData("pH", "Water", 
				(float)(d.pH), addtlInfo);
		siteData.addReportData(repData);

		addtlInfo = "OK";
		if ((d.dissolved_oxygen < 0) || (d.dissolved_oxygen > 21)) {
			addtlInfo = "Invalid data for DO2: " + d.dissolved_oxygen;
			writeErr(addtlInfo);
		}
		repData = new SiteDeviceReportData("DO2", "Water", 
				(float)(d.dissolved_oxygen), addtlInfo);
		siteData.addReportData(repData);

		addtlInfo = "OK";
		if ((d.conductivity < 0.1) || (d.conductivity > 120)) {
			addtlInfo = "Invalid data for Conductivity: " + d.conductivity;
			writeErr(addtlInfo);
		}
		repData = new SiteDeviceReportData("Conductivity", "Water", 
				(float)(d.conductivity), addtlInfo);
		siteData.addReportData(repData);

		addtlInfo = "OK";
		if ((d.temperature < 0.1) || (d.temperature > 101)) {
			addtlInfo = "Invalid data for Temperature: " + d.temperature;
			writeErr(addtlInfo);
		}
		repData = new SiteDeviceReportData("Temperature", "Water", 
				(float)(d.temperature), addtlInfo);
		siteData.addReportData(repData);

		addtlInfo = "OK";
		if ((d.turbidity < 0) || (d.turbidity > 155)) {
			addtlInfo = "Invalid data for Turbidity: " + d.turbidity;
			writeErr(addtlInfo);
		}
		repData = new SiteDeviceReportData("Turbidity", "Water", 
				(float)(d.turbidity), addtlInfo);
		siteData.addReportData(repData);
		
		addtlInfo = "OK";
		repData = new SiteDeviceReportData("Copper", "Water", 
				(float)(d.copper), addtlInfo);
		siteData.addReportData(repData);
		repData = new SiteDeviceReportData("Zinc", "Water", 
				(float)(d.zinc), addtlInfo);
		siteData.addReportData(repData);
		
		/* Replace the old SiteDeviceData object */
		DSUtils.updateStoredObject(_mainInfo.getDataStore(), "site_device_data", 
				siteData);
		
		/* Add persistent data flag for unsent water quality data availability */
		IPersistentDataBridge pDataStore = getPersistentDataBridge();
		if (pDataStore == null) {
			writeErr("Could not get persistent data bridge");
			return Status.FAILED;
		}
		pDataStore.put("WQ_DATA_AVAILABLE", "true");
		
		/* Consolidate all water quality params in one string */
		StringBuilder sb = new StringBuilder();
		sb.append(Double.toString(d.pH) + ",");
		sb.append(Double.toString(d.dissolved_oxygen) + ",");
		sb.append(Double.toString(d.conductivity) + ",");
		sb.append(Double.toString(d.temperature) + ",");
		sb.append(Double.toString(d.turbidity));
		
		/* Store last obtained data for quick display upon app screen reload */
		pDataStore.put("LAST_WQ_DATA", sb.toString());
		OLog.info("Saved Water Quality Data: " + pDataStore.get("LAST_WQ_DATA"));
		
		return Status.OK;
	}
	
	private void clearWaterQualityData() {
		/* Get the stored SiteDeviceData object */
		SiteDeviceData siteData = 
				(SiteDeviceData) DSUtils
				.getStoredObject(_mainInfo.getDataStore(), 
						"site_device_data");
		if (siteData == null) {
			writeErr("SiteDeviceData object not found");
			return;
		}
		
		/* Clear all report data in the SiteDeviceData object */
		siteData.clearReportData();
		
		return;
	}
	
	private void clearImageData() {
		/* Get the stored SiteDeviceData object */
		SiteDeviceImage siteImage = 
				(SiteDeviceImage) DSUtils
				.getStoredObject(_mainInfo.getDataStore(), 
						"site_device_image");
		if (siteImage == null) {
			writeErr("SiteDeviceImage object not found");
			return;
		}
		
		/* Clear all report data in the SiteDeviceImage object */
		siteImage.clearReportData();
		
		return;
	}
}
