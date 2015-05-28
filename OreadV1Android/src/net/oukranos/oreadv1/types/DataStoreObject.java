package net.oukranos.oreadv1.types;

import net.oukranos.oreadv1.util.OLog;

public class DataStoreObject {
	private String _id = "";
	private String _type = "";
	private Object _obj = null;
	
	private DataStoreObject(String id, String type, Object obj) {
		this._id = id;
		this._type = type;
		this._obj = obj;
		return;
	}
	
	public static DataStoreObject createNewInstance(String id, String type, Object obj) {
		if ((id == null) || (type == null) || (obj == null)) {
			OLog.err("Invalid input parameter/s");
			return null;
		}
		
		if (id.isEmpty() || type.isEmpty()) {
			OLog.err("Invalid input parameter/s");
			return null;
		}
		
		return (new DataStoreObject(id, type, obj));
	}
	
	public String getId() {
		return this._id;
	}
	
	public String getType() {
		return this._type;
	}
	
	public Object getObject() {
		return this._obj;
	}
}
