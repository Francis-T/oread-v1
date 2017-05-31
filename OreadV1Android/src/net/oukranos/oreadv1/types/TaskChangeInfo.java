package net.oukranos.oreadv1.types;

public class TaskChangeInfo {
	private String _taskName = "";
	
	public TaskChangeInfo(String name) {
		this._taskName = name;
		return;
	}
	
	public TaskChangeInfo(TaskChangeInfo taskInfo) {
		this._taskName = taskInfo.getTask();
		return;
	}
	
	public String getTask() {
		return _taskName;
	}
}
