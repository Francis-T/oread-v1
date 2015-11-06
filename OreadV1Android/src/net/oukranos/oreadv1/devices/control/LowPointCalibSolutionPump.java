package net.oukranos.oreadv1.devices.control;

import net.oukranos.oreadv1.types.ControlMechanism;
import net.oukranos.oreadv1.types.Status;

public class LowPointCalibSolutionPump extends ControlMechanism {
	private static final String ACTV_CMD_STR = "ACTV P1";
	private static final String DEACT_CMD_STR = "DEACT P1";

	public LowPointCalibSolutionPump() {
		setName("Low-Point Calib Solution Pump");
		setBlocking(true);
		setTimeoutDuration(10000);
		return;
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