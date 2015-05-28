package net.oukranos.oreadv1.types;

public class Module {
	private String _id;
	private String _type;
	
	public Module(String id, String type) {
		this._id = id;
		this._type = type;
		return;
	}
	
	public String getId() {
		return (this._id);
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

	public String getType() {
		return (this._type);
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
	
	public String toString() {
		return ("{ module id=\"" + this.getId() + "\"" +
				" type=\"" + this.getType() + "\"" +
				" }");
	}
}
