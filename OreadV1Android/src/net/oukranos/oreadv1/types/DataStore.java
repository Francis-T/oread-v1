package net.oukranos.oreadv1.types;

import java.util.ArrayList;
import java.util.List;

import net.oukranos.oreadv1.util.OLog;

public class DataStore {
	private List<DataStoreObject> _dataList = null;
	
	public DataStore() {
		_dataList = new ArrayList<DataStoreObject>();
		return;
	}
	
	public Status add(String id, String type, Object obj) {
		if ((id == null) || (type == null) || (obj == null)) {
			OLog.err("Invalid input parameter/s");
			return Status.FAILED;
		}
		
		if (id.isEmpty() || type.isEmpty()) {
			OLog.err("Invalid input parameter/s");
			return Status.FAILED;
		}
		
		if (_dataList == null) {
			OLog.err("Data object list is null");
			return Status.FAILED;
		}
		
		/* Check if there is already a duplicate identifier */
		for (DataStoreObject d : _dataList) {
			if (id.equals(d.getId()) == true) {
				OLog.err("Duplicate id exists");
				return Status.FAILED;
			}
		}
		
		/* TODO Type checking should also be implemented in the future */
		
		/* Get a new data store object instance */
		DataStoreObject dataObj = DataStoreObject.createNewInstance(id, type, obj); 
		if (dataObj == null) {
			OLog.err("Failed to create data object");
			return Status.FAILED;
		}
		
		/* Add this to the list */
		_dataList.add(dataObj);
		
		return Status.OK;
	}
	
	public Status remove(String id) {
		if (id == null) {
			OLog.err("Invalid input parameter/s");
			return Status.FAILED;
		}
		
		if (id.isEmpty()) {
			OLog.err("Invalid input parameter/s");
			return Status.FAILED;
		}
		
		if (_dataList == null) {
			OLog.err("Data object list is null");
			return Status.FAILED;
		}

		/* Remove the object matching the given identifier */
		for (int idx = 0; idx < _dataList.size(); idx++) {
			if (id.equals(_dataList.get(idx).getId()) == true) {
				_dataList.remove(idx);
				return Status.OK;
			}
		}
		
		/* If no such object exists, return a failure status */
		OLog.err("Failed to locate object with id: " + id);
		return Status.FAILED;
	}
	
	public DataStoreObject retrieve(String id) {
		if (id == null) {
			OLog.err("Invalid input parameter/s");
			return null;
		}
		
		if (id.isEmpty()) {
			OLog.err("Invalid input parameter/s");
			return null;
		}
		
		if (_dataList == null) {
			OLog.err("Data object list is null");
			return null;
		}

		/* Return the object matching the given identifier */
		for (DataStoreObject dataObj : _dataList) {
			if (id.equals(dataObj.getId()) == true) {
				return dataObj;
			}
		}
		
		/* If no such object exists, return a failure status */
		OLog.err("Failed to locate object with id: " + id);
		return null;
	}
	
	public Object retrieveObject(String id) {
		DataStoreObject dataObj = this.retrieve(id);
		if (dataObj == null) {
			OLog.err("Failed to retrieve data object");
			return null;
		}
		
		return (dataObj.getObject()); 
	}
}
