package net.oukranos.oreadv1.types.config;

import java.util.ArrayList;
import java.util.List;

import net.oukranos.oreadv1.types.Status;

public class Procedure {
	private String _id = "";
	private List<Task> _taskList = null;
	
	public Procedure(String id) {
		this._id = id;
		this._taskList = new ArrayList<Task>();
		
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
    
    public Status addTask(String id, String params) {
        if ((id == null) || (params == null)) {
            return Status.FAILED;
        }

        if (id.isEmpty() == true) {
            return Status.FAILED;
        }

        _taskList.add(new Task(id, params));
        return Status.OK;
    }
    
    public List<Task> getTaskList() {
    	return this._taskList;
    }

	public String toString() {
		String procStr = "{ procedure";
		
		procStr += " id=\"" + this.getId() + "\"";
		
		if (this._taskList.isEmpty() == false) {
			procStr += " task-list=[";
			
			for (Task t : this._taskList) {
				procStr += "\n";
				procStr += "    " + t.toString();
			}
			
			procStr += " ]";
		}
		
		procStr += " }";
		
		return procStr;
	}
}
