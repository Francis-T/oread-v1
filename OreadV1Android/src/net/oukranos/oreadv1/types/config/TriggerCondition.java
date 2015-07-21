package net.oukranos.oreadv1.types.config;

public class TriggerCondition {
	private String _id = "";
	private String _description = "";
	private String _procedure = "";
	private String _condition = "";
	
	/** Constructors **/
	public TriggerCondition(String id, String condition) {
		this.setId(id);
		this.setCondition(condition);
		
		return;
	}
	
	public TriggerCondition(String id, String condition, 
			String procedure) {
		this(id, condition);
		this.setProcedure(procedure);
		
		return;
	}

	public TriggerCondition(String id, String condition, 
			String procedure, String description) {
		this(id, condition, procedure);
		this.setDescription(description);
		
		return;
	}
	
	/** Getter/Setter Methods **/
	public String getId() {
		return _id;
	}

	public void setId(String id) {
		this._id = id;
		return;
	}

	public String getDescription() {
		return _description;
	}

	public void setDescription(String description) {
		this._description = description;
		return;
	}

	public String getProcedure() {
		return _procedure;
	}

	public void setProcedure(String procedure) {
		this._procedure = procedure;
		return;
	}

	public String getCondition() {
		return _condition;
	}

	public void setCondition(String condition) {
		this._condition = condition;
		return;
	}
	
}
