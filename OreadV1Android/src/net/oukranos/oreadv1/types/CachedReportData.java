package net.oukranos.oreadv1.types;

public class CachedReportData {
	private int	_id = 0;
	private String _timestamp = "";
	private String _status = "";
	private String _type = "";
	private String _subtype = "";
	private String _data = "";
	
	public CachedReportData() {
		return;
	}
	
	public CachedReportData(int id, String timestamp, String type, 
			String subtype, String status, String data) {
		this._id = id;
		this._timestamp = timestamp;
		this._type = type;
		this._subtype = subtype;
		this._status = status;
		this._data = data;
		
		return;
	}

	/********************/
	/** Getter Methods **/
	/********************/
	public int getId() { return _id; }
	public String getTimestamp() { return _timestamp; }
	public String getStatus() { return _status; }
	public String getType() { return _type; }
	public String getSubtype() { return _subtype; };
	public String getData() { return _data; }
	
	/********************/
	/** Setter Methods **/
	/********************/
	public void setId(int id) {
		_id = id;
	}
	
	public void setTimestamp(String timestamp) { 
		_timestamp = timestamp; 
	}
	
	public void setStatus(String status) {
		_status = status;
	}
	
	public void setType(String type) {
		_type = type;
	}
	
	public void setSubtype(String subtype) {
		_subtype = subtype;
	}

	public void setData(String data) {
		_data = data;
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("{\n");
		sb.append("    id = " + this.getId() + ",\n");
		sb.append("    timestamp = " + this.getTimestamp() + ",\n");
		sb.append("    type = " + this.getType() + ",\n");
		sb.append("    subtype = " + this.getSubtype() + ",\n");
		sb.append("    status = " + this.getStatus() + ",\n");
		sb.append("    data = " + this.getData() + "\n");
		sb.append("}\n");
		return sb.toString();
	}
}
