package net.oukranos.oreadv1.devices.control;

import net.oukranos.oreadv1.types.ControlMechanism;
import net.oukranos.oreadv1.types.Status;

public class DrainValve extends ControlMechanism {
	private static final String ACTV_CMD_STR = "ACTV V1";
	private static final String DEACT_CMD_STR = "DEACT V1";

	public DrainValve() {
		setName("Drain Valve");
		setBlocking(true);
		setTimeoutDuration(30000);
	}

	@Override
	public Status activate() {
		OLog.info("Activating " + getName() + "...");
		return send(ACTV_CMD_STR.getBytes());
	}

	@Override
	public Status activate(String params) {
		return activate();
	}

	@Override
	public Status deactivate() {
		OLog.info("Deactivating " + getName() + "...");
		return send(DEACT_CMD_STR.getBytes());
	}

	@Override
	public Status deactivate(String params) {
		return deactivate();
	}

	@Override
	public Status pollStatus() {
		// TODO Auto-generated method stub
		return Status.OK;
	}
}
