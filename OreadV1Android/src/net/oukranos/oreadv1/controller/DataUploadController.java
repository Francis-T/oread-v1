package net.oukranos.oreadv1.controller;

import net.oukranos.oreadv1.interfaces.AbstractController;
import net.oukranos.oreadv1.interfaces.HttpEncodableData;
import net.oukranos.oreadv1.interfaces.IPersistentDataBridge;
import net.oukranos.oreadv1.types.CachedReportData;
import net.oukranos.oreadv1.types.ControllerState;
import net.oukranos.oreadv1.types.ControllerStatus;
import net.oukranos.oreadv1.types.MainControllerInfo;
import net.oukranos.oreadv1.types.SiteDeviceData;
import net.oukranos.oreadv1.types.SiteDeviceImage;
import net.oukranos.oreadv1.types.SiteDeviceReportData;
import net.oukranos.oreadv1.types.Status;
import net.oukranos.oreadv1.util.DataStoreUtils.DSUtils;

public class DataUploadController extends AbstractController {
	private static final int CACHED_DATA_PULL_COUNT 	= 15;
	
	private static final String SITE_OBJ_WQD_REF 		= "site_device_data";
	private static final String SITE_OBJ_IMG_REF 		= "site_device_image";
	private static final String SITE_WQD_URL_REF 		= "live_data_url";
	private static final String SITE_IMG_URL_REF 		= "live_image_url";
	private static final String P_FLAG_IMG_AVAIL_REF 	= "IMG_CAPTURE_AVAILABLE";
	private static final String P_FLAG_WQD_AVAIL_REF  	= "WQ_DATA_AVAILABLE";

	private static final String CACHED_DATA_WQ_TYPE 	= "h2o_quality";
	private static final String CACHED_DATA_IMG_TYPE 	= "chem_presence";

	private static DataUploadController _dataUploadController = null;
	private NetworkController _networkController = null;
	private DatabaseController _databaseController = null;

	private DataUploadController() {
		this.setState(ControllerState.UNKNOWN);

		this.setType("data");
		this.setName("upload");
		return;
	}

	public static DataUploadController getInstance(MainControllerInfo mainInfo) {
		if (mainInfo == null) {
			OLog.err("Invalid input parameter/s" +
					" in DataUploadController.getInstance()");
			return null;
		}
		
		NetworkController networkController = (NetworkController) mainInfo
				.getSubController("network", "comm");
		if (networkController == null) {
			OLog.err("No network controller available" +
					" in DataUploadController.getInstance()");
			return null;
		}
		
		DatabaseController databaseController = (DatabaseController) mainInfo
				.getSubController("db", "storage");
		if (databaseController == null) {
			OLog.err("No database controller available" +
					" in DataUploadController.getInstance()");
			return null;
		}
		
		/* Instantiate the DataUploadController if it hasn't been done yet */
		if (_dataUploadController == null) {
			_dataUploadController = new DataUploadController();
		}
		
		_dataUploadController._mainInfo = mainInfo;
		_dataUploadController._networkController = networkController;
		_dataUploadController._databaseController = databaseController;

		return _dataUploadController;
	}

	/********************************/
	/** AbstractController Methods **/
	/********************************/
	@Override
	public Status initialize(Object initializer) {
		if (this.getState() != ControllerState.UNKNOWN) {
			return writeInfo("DataUploadController already initialized");
		}
		this.setState(ControllerState.INACTIVE);

		return writeInfo("DataUploadController initialized");
	}

	@Override
	public Status start() {
		// TODO Auto-generated method stub
		return Status.OK;
	}

	@Override
	public Status stop() {
		// TODO Auto-generated method stub
		return Status.OK;
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
		if (shortCmdStr.equals("processMultipleCachedData")) {
			processMultipleCachedData();
			this.writeInfo("Command Performed: Sent Cached Data to Server");
			
		} else if (shortCmdStr.equals("processCachedImage")) {
			processCachedImage();
			this.writeInfo("Command Performed: Sent Cached Image to Server");
			
		} else if (shortCmdStr.equals("start")) {
			initialize(null);
			this.writeInfo("Command Performed: Initialized Data Upload Controller");
			
		} else if (shortCmdStr.equals("stop")) {
			destroy();
			this.writeInfo("Command Performed: Cleaned Up Data Upload Controller");
			
		} else {
			this.writeErr("Unknown or invalid command: " + shortCmdStr);
		}

		return this.getControllerStatus();
	}

	@Override
	public Status destroy() {
		if (this.getState() == ControllerState.UNKNOWN) {
			return writeInfo("DataUploadController cleanup finished");
		}

		this.setState(ControllerState.UNKNOWN);

		return writeInfo("DataUploadController cleanup finished");
	}

	/*********************/
	/** Private Methods **/
	/*********************/
	private void processMultipleCachedData() {
		int recIdList[] = new int[CACHED_DATA_PULL_COUNT];

		/* Get the stored SiteDeviceData object */
		SiteDeviceData siteData = (SiteDeviceData) DSUtils
				.getStoredObject(_mainInfo.getDataStore(), 
						SITE_OBJ_WQD_REF);
		if (siteData == null) {
			writeErr("SiteDeviceData object not found");
			return;
		}

		/* Start querying the database */
		if (_databaseController.startQuery(CACHED_DATA_WQ_TYPE) != Status.OK) {
			return;
		}

		/* Fetch data into temporary storage */
		CachedReportData crDataTemp = null;
		int i = 0;
		for (i = 0; i < CACHED_DATA_PULL_COUNT; i++) {
			/* Fetch the cached report data */
			crDataTemp = new CachedReportData();
			Status status = _databaseController.fetchReportData(crDataTemp);
			if (status != Status.OK) {
				writeWarn("No more report data found");
				break;
			}

			/* Create the report data part from the cached report data */
			SiteDeviceReportData reportDataTemp = new SiteDeviceReportData("",
					"", 0.0f, "");
			status = reportDataTemp.decodeFromJson(crDataTemp.getData());
			if (status != Status.OK) {
				break;
			}

			/* Add the report data part to the site device data */
			siteData.addReportData(reportDataTemp);

			/*
			 * Add the record id to the list of records to be updated upon
			 * successful sending
			 */
			recIdList[i] = crDataTemp.getId();
			OLog.info("Finished processing cached record: "
					+ crDataTemp.getId());
		}

		/* Stop querying the database */
		if (_databaseController.stopQuery() != Status.OK) {
			return;
		}

		/* If there were no records found, exit */
		if (i == 0) {
			/*
			 * Add persistent data flag for unsent water quality data
			 * availability
			 */
			IPersistentDataBridge pDataStore = getPersistentDataBridge();
			if (pDataStore == null) {
				return;
			}
			pDataStore.put(P_FLAG_WQD_AVAIL_REF, "false");
			return;
		}

		// OLog.info("Sending data to server: \n" +
		// siteData.encodeToJsonString());
		if (sendDataToServer(siteData, SITE_WQD_URL_REF) != Status.OK) {
			return;
		}

		/* If successfully sent to the server, update the records */
		for (Integer recId : recIdList) {
			_databaseController.updateRecord(recId.toString(), true);
		}

		/*
		 * Check the database if more unsent data remains; otherwise, update the
		 * persistent data flag to "false"
		 */
		if (_databaseController.hasUnsentRecords(CACHED_DATA_WQ_TYPE) == false) {
			/*
			 * Add persistent data flag for unsent water quality data
			 * availability
			 */
			IPersistentDataBridge pDataStore = getPersistentDataBridge();
			if (pDataStore == null) {
				return;
			}
			pDataStore.put(P_FLAG_WQD_AVAIL_REF, "false");
		}

		writeInfo("Finished sending report data to server.");
		return;
	}

	private void processCachedImage() {
		/* Get the stored SiteDeviceData object */
		SiteDeviceImage siteImage = (SiteDeviceImage) DSUtils
				.getStoredObject(_mainInfo.getDataStore(),
						SITE_OBJ_IMG_REF);
		if (siteImage == null) {
			writeErr("SiteDeviceImage object not found");
			return;
		}

		/* Start querying the database */
		if (_databaseController.startQuery(CACHED_DATA_IMG_TYPE) != Status.OK) {
			return;
		}

		/* Fetch the cached report data into temporary storage */
		CachedReportData crDataTemp = new CachedReportData();
		Status status = _databaseController.fetchReportData(crDataTemp);
		if (status != Status.OK) {
			writeWarn("No more report data found");
			/*
			 * Add persistent data flag for unsent water quality data
			 * availability
			 */
			IPersistentDataBridge pDataStore = getPersistentDataBridge();
			if (pDataStore == null) {
				return;
			}
			pDataStore.put(P_FLAG_IMG_AVAIL_REF, "false");
			return;
		}

		/* Stop querying the database */
		if (_databaseController.stopQuery() != Status.OK) {
			return;
		}

		/* Extract the file meta data from the cached report data */
		/*
		 * For convenience, this is stored in the 'data' field as a
		 * comma-delimited string.
		 * 
		 * TODO: Filepaths MAY have commas too, so we need to add workarounds
		 * for those cases
		 */
		String fileMetaData[] = crDataTemp.getData().split(",");
		if (fileMetaData.length != 2) {
			writeErr("Invalid file metadata: " + crDataTemp.getData());
			String recId = Integer.toString(crDataTemp.getId());
			_databaseController.updateRecord(recId, true);
			return;
		}

		/* Write the report data */
		siteImage.setCaptureFile(fileMetaData[0], fileMetaData[1]);
		siteImage.addReportData(new SiteDeviceReportData("Mercury", "Water", 0f, ""));

		/*
		 * Do something with the accumulated image data (e.g. send to the
		 * server)
		 */
		OLog.info("Sending image to server");
		if (sendDataToServer(siteImage, SITE_IMG_URL_REF) != Status.OK) {
			return;
		}

		/* If successfully sent to the server, update the records */
		String recId = Integer.toString(crDataTemp.getId());
		_databaseController.updateRecord(recId, true);

		/*
		 * Check the database if more unsent data remains; otherwise, update the
		 * persistent data flag to "false"
		 */
		if (_databaseController.hasUnsentRecords(CACHED_DATA_IMG_TYPE) == false) {
			/*
			 * Add persistent data flag for unsent water quality data
			 * availability
			 */
			IPersistentDataBridge pDataStore = getPersistentDataBridge();
			if (pDataStore == null) {
				return;
			}
			pDataStore.put(P_FLAG_IMG_AVAIL_REF, "false");
		}

		writeInfo("Finished sending image to server.");
		return;
	}

	private Status sendDataToServer(HttpEncodableData srcData,
			String storedUrlId) {
		if (_networkController != null) {
			String url = null;

			try {
				url = (String) DSUtils.getStoredObject(
						_mainInfo.getDataStore(), storedUrlId);
				if (url == null) {
					writeErr("URL is NULL");
					return Status.FAILED;
				}
				_networkController.send(url, srcData);
			} catch (Exception e) {
				OLog.err("Exception occurred: " + e.getMessage());
			}
		}

		if (_networkController.isLastResponseAvailable() == false) {
			/* Wait for the response */
			if (_networkController.waitForResponse() != Status.OK) {
				writeErr("Failed to receive response from server");
				return Status.FAILED;
			}
		}

		/* Retrieve info about the Network Subcontroller's last response */
		int respCode = _networkController.getLastResponseCode();
		String respMsg = _networkController.getLastResponseMessage();

		/*
		 * Clear the Network Subcontroller's last stored response to indicate
		 * that we've already processed it
		 */
		_networkController.clearLastResponse();

		if (respCode != NetworkController.HTTP_RESPONSE_CODE_OK) {
			writeErr("Failed to send data to server" + "(Response: " + respCode
					+ " - " + respMsg + ")");
			return Status.FAILED;
		}

		return Status.OK;
	}
}
