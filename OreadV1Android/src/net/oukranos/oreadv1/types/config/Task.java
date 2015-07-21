package net.oukranos.oreadv1.types.config;

import net.oukranos.oreadv1.types.Status;

public class Task {
	private String _id = "";
	private String _params = "";
	
	public Task(String id, String params) {
		this._id = id;
		this._params = params;
		return;
	}
	
	public String getId() {
		return (this._id);
	}
	
	public String getParams() {
		return (this._params);
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

	public Status setParams(String params) {
		if (params == null) {
			return Status.FAILED;
		}
		
		this._params = params;
		
		return Status.OK;
	}
	
	public String toString() {
		return "{ task id=\"" + this.getId() + "\"" +
				" params=\"" + this.getParams() + "\" }";
	}
}
