package net.oukranos.oreadv1.types;

public class ProcChangeInfo {
	private String _procName = "";
	
	public ProcChangeInfo(String states) {
		this._procName = states;
		return;
	}
	
	public ProcChangeInfo(ProcChangeInfo procInfo) {
		this._procName = procInfo.getProcedure();
		return;
	}
	
	public String getProcedure() {
		return _procName;
	}
}
