package net.oukranos.oreadv1.controller;

import java.util.HashMap;

import net.oukranos.oreadv1.database.SimpleDbHelper;
import net.oukranos.oreadv1.database.SimpleDbContract.CachedData;
import net.oukranos.oreadv1.interfaces.AbstractController;
import net.oukranos.oreadv1.interfaces.bridge.IBluetoothBridge;
import net.oukranos.oreadv1.interfaces.bridge.IDatabaseBridge;
import net.oukranos.oreadv1.types.CachedReportData;
import net.oukranos.oreadv1.types.ChemicalPresenceData;
import net.oukranos.oreadv1.types.ControllerState;
import net.oukranos.oreadv1.types.ControllerStatus;
import net.oukranos.oreadv1.types.DataStore;
import net.oukranos.oreadv1.types.DataStoreObject;
import net.oukranos.oreadv1.types.GenericData;
import net.oukranos.oreadv1.types.MainControllerInfo;
import net.oukranos.oreadv1.types.SiteDeviceReportData;
import net.oukranos.oreadv1.types.Status;
import net.oukranos.oreadv1.types.WaterQualityData;

public class DatabaseController2 extends AbstractController {
	private static DatabaseController2 _databaseController = null;
	
	private IDatabaseBridge _databaseBridge = null;

	private DatabaseController2() {
		this.setType("storage");
		this.setName("db");
		return;
	}

	public static DatabaseController2 getInstance(MainControllerInfo mainInfo) {
		if (mainInfo == null) {
			OLog.err("Invalid input parameter/s in " +
					"DatabaseController.getInstance()");
			return null;
		}

		if (_databaseController == null) {
			_databaseController = new DatabaseController2();
		}
		
		_databaseController._mainInfo = mainInfo;

		return _databaseController;
	}

	/********************/
	/** Public Methods **/
	/********************/
	@Override
	public Status initialize(Object initObject) {
		_databaseBridge = getDatabaseBridge();
		if (_databaseBridge == null) {
			return Status.FAILED;
		}

		writeInfo("DatabaseController Initialized");
		return Status.OK;
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

		if (shortCmdStr.equals("start") == true) {
			this.initialize(null);

		} else if (shortCmdStr.equals("stop") == true) {
			this.destroy();

		} else if (shortCmdStr.equals("storeWaterQualityData") == true) {
			this.storeWaterQualityData(paramStr);

		} else if (shortCmdStr.equals("storeAsHgCaptureData") == true) {
			this.storeChemPresenceData(paramStr);

		} else if (shortCmdStr.equals("startQuery") == true) {
			this.startQuery(paramStr);

		} else if (shortCmdStr.equals("fetchInto") == true) {
			this.fetchData(paramStr);

		} else if (shortCmdStr.equals("stopQuery") == true) {
			this.stopQuery();

		} else if (shortCmdStr.equals("updateRecordAsUnsent") == true) {
			this.updateRecord(paramStr, false);

		} else if (shortCmdStr.equals("updateRecordAsSent") == true) {
			this.updateRecord(paramStr, true);

		} else if (shortCmdStr.equals("clearDatabase") == true) {
			this.clearDatabase();

		} else {
			this.writeErr("Unknown or invalid command: " + shortCmdStr);
		}

		return this.getControllerStatus();
	}

	@Override
	public Status destroy() {
		this.setState(ControllerState.UNKNOWN);
		stopQuery();

		if (_databaseBridge != null) {
			if (_databaseBridge.isDatabaseOpen()) {
				_databaseBridge.closeDatabase();
			}
			_databaseBridge = null;
		}

		_databaseController = null;

		writeInfo("DatabaseController destroyed");
		return Status.OK;
	}

	public Status startQuery(String subtype) {
		if (_databaseBridge.isCursorActive()) {
			writeErr("Cursor was already initialized");
			return Status.FAILED;
		}

		String columns[] = { CachedData.COL_ID, CachedData.COL_TIMESTAMP,
				CachedData.COL_STATUS, CachedData.COL_DATA,
				CachedData.COL_TYPE, CachedData.COL_SUBTYPE };

		/* Sort by oldest data first (FIFO) */
		String sortOrder = CachedData.COL_TIMESTAMP;

		/* Filter only 'unsent' data */
		String filter = CachedData.COL_STATUS + "<> ? AND "
				+ CachedData.COL_TYPE + " = ?";
		String filterArgs[] = { "S", subtype };

		/* Open the database if it has not yet been opened */
		if (_databaseBridge.isDatabaseOpen() == false) {
			if (_databaseBridge.openDatabase() != Status.OK) {
				writeErr("Failed to access the database");
				return Status.FAILED;
			}
		}

		/* Query the database */
		return _databaseBridge.startQuery(CachedData.TABLE_NAME, columns, 
				filter, filterArgs, null, null, sortOrder);
	}

	public Status fetchData(String dataId) {
		DataStore dataStore = _mainInfo.getDataStore();
		if (dataStore == null) {
			writeErr("MainController DataStore unavailable");
			return Status.FAILED;
		}

		DataStoreObject dataObj = dataStore.retrieve(dataId);
		if (dataObj == null) {
			writeErr("DataStoreObject could not be retrieved");
			return Status.FAILED;
		}

		Object obj = dataObj.getObject();
		if (obj == null) {
			writeErr("Object could not be retrieved");
			return Status.FAILED;
		}

		/* Verify that the stored object is of the correct class */
		if (!obj.getClass().getSimpleName().equals("CachedReportData")) {
			writeErr("Unexpected Object class: "
					+ obj.getClass().getSimpleName()
					+ "; Expected: CachedReportData");
			return Status.FAILED;
		}

		CachedReportData data = (CachedReportData) dataObj.getObject();
		if (data == null) {
			writeErr("CachedReportData could not be retrieved");
			return Status.FAILED;
		}

		if (this.fetchReportData(data) != Status.OK) {
			return Status.FAILED;
		}

		return Status.OK;
	}

	public Status fetchReportData(CachedReportData data) {
		if (_databaseBridge.isCursorActive() == false) {
			writeErr("No active cursor for fetching data");
			return Status.FAILED;
		}

		if (_databaseBridge.recordsAvailable() == false) {
			writeErr("No more data to fetch");
			return Status.FAILED;
		}
		
		HashMap<String, GenericData> record = _databaseBridge.fetchData();
		
		try {
			int recId = 
				(Integer) record.get(CachedData.COL_ID).getValue();
			String recTimestamp = 
				(String) record.get(CachedData.COL_TIMESTAMP).getValue();
			String recType = 
					(String) record.get(CachedData.COL_TYPE).getValue();
			String recSubtype = 
					(String) record.get(CachedData.COL_SUBTYPE).getValue();
			String recStatus = 
					(String) record.get(CachedData.COL_STATUS).getValue();
			String recData = 
					(String) record.get(CachedData.COL_DATA).getValue();

			/* Set the variables for the cached report data */
			data.setId(recId);
			data.setTimestamp(recTimestamp);
			data.setType(recType);
			data.setSubtype(recSubtype);
			data.setStatus(recStatus);
			data.setData(recData);
			
		} catch (Exception e) {
			OLog.err("Failed to get record values");
			OLog.stackTrace(e);
			
			return Status.FAILED;
		}

		/* Move the cursor to the next record */
		if (_databaseBridge.nextRecord() != Status.OK) {
			OLog.err("Failed to move to next record");
			return Status.FAILED;
		}
		
		writeInfo("Report Data Contents: " + data.toString());

		return Status.OK;
	}

	public Status stopQuery() {
		if (_databaseBridge.finishQuery() != Status.OK) {
			if (_activeDb.isOpen()) {
				_activeDb.close();
			}
			_activeDb = null;
		}

		return Status.OK;
	}

	public Status storeChemPresenceData(String dataId) {
		DataStore dataStore = _mainInfo.getDataStore();
		if (dataStore == null) {
			writeErr("MainController DataStore unavailable");
			return Status.FAILED;
		}

		DataStoreObject dataObj = dataStore.retrieve(dataId);
		if (dataObj == null) {
			writeErr("DataStoreObject could not be retrieved");
			return Status.FAILED;
		}

		Object obj = dataObj.getObject();
		if (obj == null) {
			writeErr("Object could not be retrieved");
			return Status.FAILED;
		}

		/* Verify that the stored object is of the correct class */
		if (!obj.getClass().getSimpleName().equals("ChemicalPresenceData")) {
			writeErr("Unexpected Object class: "
					+ obj.getClass().getSimpleName()
					+ "; Expected: ChemicalPresenceData");
			return Status.FAILED;
		}

		ChemicalPresenceData data = (ChemicalPresenceData) dataObj.getObject();
		if (data == null) {
			writeErr("ChemicalPresenceData could not be retrieved");
			return Status.FAILED;
		}

		return storeData(data);
	}

	public Status storeData(ChemicalPresenceData d) {
		SQLiteDatabase db = this.openDatabaseConn();
		if (db == null) {
			writeErr("Could not open database connection");
			return Status.FAILED;
		}

		String filename = d.getCaptureFileName();
		String filepath = d.getCaptureFilePath();

		SiteDeviceReportData reportData 
			= new SiteDeviceReportData("Arsenic", "Water", 0.0f, "OK");

		this.insertCachedData(db, reportData, "chem_presence", filename + ","
				+ filepath);

		/* Close the active database */
		this.closeDatabaseConn();

		return Status.OK;
	}

	public Status storeWaterQualityData(String dataId) {
		DataStore dataStore = _mainInfo.getDataStore();
		if (dataStore == null) {
			writeErr("MainController DataStore unavailable");
			return Status.FAILED;
		}

		DataStoreObject dataObj = dataStore.retrieve(dataId);
		if (dataObj == null) {
			writeErr("DataStoreObject could not be retrieved");
			return Status.FAILED;
		}

		Object obj = dataObj.getObject();
		if (obj == null) {
			writeErr("Object could not be retrieved");
			return Status.FAILED;
		}

		/* Verify that the stored object is of the correct class */
		if (!obj.getClass().getSimpleName().equals("WaterQualityData")) {
			writeErr("Unexpected Object class: "
					+ obj.getClass().getSimpleName()
					+ "; Expected: WaterQualityData");
			return Status.FAILED;
		}

		WaterQualityData data = (WaterQualityData) dataObj.getObject();
		if (data == null) {
			writeErr("WaterQualityData could not be retrieved");
			return Status.FAILED;
		}

		/* Store the water quality data in the database */
		return storeData(data);
	}

	public Status storeData(WaterQualityData d) {
		SQLiteDatabase db = this.openDatabaseConn();
		if (db == null) {
			writeErr("Could not open database connection");
			return Status.FAILED;
		}

		writeInfo("Inserting data...");
		SiteDeviceReportData reportData = null;

		String addtlInfo = "OK";
		if ((d.pH < 0.1) || (d.pH > 15)) {
			addtlInfo = "Invalid data for pH: " + d.pH;
			writeErr(addtlInfo);
		}
		reportData = new SiteDeviceReportData("pH", "Water", 
				(float) (d.pH), addtlInfo);
		this.insertCachedData(db, reportData, "h2o_quality");

		addtlInfo = "OK";
		if ((d.dissolved_oxygen < 0) || (d.dissolved_oxygen > 21)) {
			addtlInfo = "Invalid data for DO2: " + d.dissolved_oxygen;
			writeErr(addtlInfo);
		}
		reportData = new SiteDeviceReportData("DO2", "Water",
				(float) (d.dissolved_oxygen), addtlInfo);
		this.insertCachedData(db, reportData, "h2o_quality");

		addtlInfo = "OK";
		if ((d.conductivity < 0.1) || (d.conductivity > 120)) {
			addtlInfo = "Invalid data for Conductivity: " + d.conductivity;
			writeErr(addtlInfo);
		}
		reportData = new SiteDeviceReportData("Conductivity", "Water",
				(float) (d.conductivity), addtlInfo);
		this.insertCachedData(db, reportData, "h2o_quality");

		addtlInfo = "OK";
		if ((d.temperature < 0.1) || (d.temperature > 101)) {
			addtlInfo = "Invalid data for Temperature: " + d.temperature;
			writeErr(addtlInfo);
		}
		reportData = new SiteDeviceReportData("Temperature", "Water",
				(float) (d.temperature), addtlInfo);
		this.insertCachedData(db, reportData, "h2o_quality");

		addtlInfo = "OK";
		if ((d.turbidity < 0) || (d.turbidity > 155)) {
			addtlInfo = "Invalid data for Turbidity: " + d.turbidity;
			writeErr(addtlInfo);
		}
		reportData = new SiteDeviceReportData("Turbidity", "Water",
				(float) (d.turbidity), addtlInfo);
		this.insertCachedData(db, reportData, "h2o_quality");

		addtlInfo = "OK";
		reportData = new SiteDeviceReportData("Copper", "Water",
				(float) (d.copper), addtlInfo);
		this.insertCachedData(db, reportData, "h2o_quality");
		
		reportData = new SiteDeviceReportData("Zinc", "Water",
				(float) (d.zinc), addtlInfo);
		this.insertCachedData(db, reportData, "h2o_quality");

		writeInfo("Finished. Closing database...");

		/* Close the active database */
		this.closeDatabaseConn();

		writeInfo("Finished");

		return Status.OK;
	}

	public Status clearDatabase() {
		SQLiteDatabase db = this.openDatabaseConn();
		if (db == null) {
			writeErr("Could not open database connection");
			return Status.FAILED;
		}

		/* Delete all records in the database */
		db.delete(CachedData.TABLE_NAME, null, null);

		/* Close the active database */
		this.closeDatabaseConn();

		return Status.OK;
	}

	public Status updateRecord(String recordId, boolean hasBeenSent) {
		SQLiteDatabase db = this.openDatabaseConn();
		if (db == null) {
			writeErr("Could not open database connection");
			return Status.FAILED;
		}

		String sentFlag = (hasBeenSent ? "S" : " ");
		/* Set this flag as the new value for the status column */
		ContentValues values = new ContentValues();
		values.put(CachedData.COL_STATUS, sentFlag);

		/* Set the filter parameter (i.e. record id only) */
		String filter = CachedData.COL_ID + " = ?";
		String filterArgs[] = { recordId };

		writeInfo("Updating Record#" + recordId + " status to " + sentFlag
				+ "...");
		/* Update the database */
		db.update(CachedData.TABLE_NAME, values, filter, filterArgs);

		/* Close the active database */
		this.closeDatabaseConn();

		return Status.OK;
	}

	public Status updateRecord(String recordId, boolean hasBeenSent,
			String extraFilter, String extraFilterArgs[]) {
		SQLiteDatabase db = this.openDatabaseConn();
		if (db == null) {
			writeErr("Could not open database connection");
			return Status.FAILED;
		}

		String sentFlag = (hasBeenSent ? "S" : " ");
		/* Set this flag as the new value for the status column */
		ContentValues values = new ContentValues();
		values.put(CachedData.COL_STATUS, sentFlag);

		/* Set the filter parameter (i.e. record id only) */
		String filter = CachedData.COL_ID + " = ?";
		if (extraFilter != null) {
			filter += " " + extraFilter;
		}
		
		int extraLen = extraFilterArgs.length;
		String filterArgs[] = new String[extraLen + 1];
		filterArgs[0] = recordId;
		
		for (int i = 0; i < extraLen; i++) {
			filterArgs[i+1] = extraFilterArgs[i];
		}

		writeInfo("Updating Record#" + recordId + " status to " + sentFlag
				+ "...");
		/* Update the database */
		db.update(CachedData.TABLE_NAME, values, filter, filterArgs);

		/* Close the active database */
		this.closeDatabaseConn();

		return Status.OK;
	}

	/* TODO Needs a PerformCommand entry */
	public boolean hasUnsentRecords(String type) {
		startQuery(type);

		/* TODO TESTING... */
		if (_activeCursor.getCount() <= 0) {
			this.stopQuery();
			return false;
		}

		stopQuery();
		return true;
	}

	/*********************/
	/** Private Methods **/
	/*********************/
	private IDatabaseBridge getDatabaseBridge() {
		IDatabaseBridge databaseBridge 
			= (IDatabaseBridge) _mainInfo
				.getFeature("database");
		if (databaseBridge == null) {
			return _databaseBridge;
		}
		
		if ( databaseBridge.isReady() == false ) {
			if (databaseBridge.initialize(_mainInfo) != Status.OK) {
				databaseBridge = null;
			}
		}
		
		return databaseBridge;
	}
	
	private Status insertCachedData(SQLiteDatabase db,
			SiteDeviceReportData data, String type) {
		return insertCachedData(db, data, type, data.encodeToJsonString());
	}

	private Status insertCachedData(SQLiteDatabase db,
			SiteDeviceReportData data, String type, String customDataField) {
		if (db == null) {
			return Status.FAILED;
		}

		if (data == null) {
			return Status.FAILED;
		}

		ContentValues values = new ContentValues();
		values.put(CachedData.COL_TIMESTAMP, data.getTimestamp());
		values.put(CachedData.COL_STATUS, " ");
		values.put(CachedData.COL_DATA, customDataField);
		values.put(CachedData.COL_TYPE, type);
		values.put(CachedData.COL_SUBTYPE, data.getType());

		long recordNum = db.insert(CachedData.TABLE_NAME, "null", values);
		
		writeInfo("Insert Report Data: " + data.encodeToJsonString() + ", " + type + ", " + customDataField);
		writeInfo("Data successfully added to database (Record#" + recordNum + ")" );

		return Status.OK;
	}

	private SQLiteDatabase openDatabaseConn() {
		if (_dbHelper == null) {
			writeErr("Database accessor not initialized");
			return null;
		}

		if (_activeDb != null) {
			writeErr("DB is currently being accessed");
			return null;
		}

		/* Open the database */
		_activeDb = _dbHelper.getWritableDatabase();
		if (_activeDb == null) {
			writeErr("Failed to access the database");
			return null;
		}

		return _activeDb;
	}

	private void closeDatabaseConn() {
		_activeDb.close();
		_activeDb = null;
		return;
	}
}
