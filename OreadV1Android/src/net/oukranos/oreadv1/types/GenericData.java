package net.oukranos.oreadv1.types;


public class GenericData {
	protected String _id = "";
	protected String _type = "";
	protected Object _value = "";
	
	public GenericData(String id, String type, Object value) {
		this._id = id;
		this._type = type;
		this._value = value;
		
		return;
	}
	
	public String getId() {
		return (this._id);
	}
	
	public String getType() {
		return (this._type);
	}
	
	public Object getValue() {
		return (this._value);
	}
	
	public Status setId(String id) {
		if (id == null) {
			return Status.FAILED;
		}
		
		if (id.isEmpty() == true) {
			return Status.FAILED;
		}
		
		this._id = id;
		
		return Status.OK;
	}

	public Status setType(String type) {
		if (type == null) {
			return Status.FAILED;
		}
		
		if (type.isEmpty() == true) {
			return Status.FAILED;
		}
		
		this._type = type;
		
		return Status.OK;
	}

	public Status setValue(Object value) {
		if (value == null) {
			return Status.FAILED;
		}
		
		this._value = value;
		
		return Status.OK;
	}
	
	public String toString() {
		String dataStr = "{ data";
		
		dataStr += "id=\"" 		+ this.getId() + "\" ";
		dataStr += "type=\"" 	+ this.getType() + "\" ";
		dataStr += "value=\"" 	+ this.getValue().toString() + "\" ";
		
		dataStr += " }";
		
		return dataStr;
	}
}
