package net.oukranos.oreadv1.types;

public class ProcStateChangeInfo {
	private String _procStates = "";
	
	public ProcStateChangeInfo(String states) {
		this._procStates = states;
		return;
	}
	
	public ProcStateChangeInfo(ProcStateChangeInfo procStateInfo) {
		this._procStates = procStateInfo.getStateInfo();
		return;
	}
	
	public String getStateInfo() {
		return _procStates;
	}
}
