package net.oukranos.oreadv1.util;

import net.oukranos.oreadv1.types.DataStore;
import net.oukranos.oreadv1.types.DataStoreObject;
import net.oukranos.oreadv1.types.Status;

public class DataStoreUtils {
	/* Get an instance of the OreadLogger class to handle logging */
	private static final OreadLogger OLog = OreadLogger.getInstance();
	
	public static Object getStoredObject(DataStore dataStore, String dataId) {
		if (dataStore == null) {
			OLog.err("MainController DataStore unavailable");
			return null;
		}
		
		DataStoreObject dataObj = dataStore.retrieve(dataId);
		if (dataObj == null) {
			OLog.err("DataStoreObject could not be retrieved");
			return null;
		}
		
		Object obj = dataObj.getObject();
		if (obj == null) {
			OLog.err("Object could not be retrieved");
			return null;
		}
		
		return obj;
	}
	
	public static Status updateStoredObject(DataStore dataStore, String dataId, Object newObj)
	{
		if (dataStore == null) {
			OLog.err("MainController DataStore unavailable");
			return Status.FAILED;
		}
		
		DataStoreObject dataObj = dataStore.retrieve(dataId);
		if (dataObj == null) {
			OLog.err("DataStoreObject could not be retrieved");
			return Status.FAILED;
		}
		
		dataObj.setObject(newObj);
		
		return Status.OK;
	}

	/* Short-hand identifier class for DataStoreUtils */
	public class DSUtils extends DataStoreUtils {
		;
	}
}
