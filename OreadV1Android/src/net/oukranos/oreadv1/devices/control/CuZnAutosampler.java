package net.oukranos.oreadv1.devices.control;

import net.oukranos.oreadv1.types.ControlMechanism;
import net.oukranos.oreadv1.types.Status;

public class CuZnAutosampler extends ControlMechanism {
	private static final String ACTV_CMD_STR = "I2C 2 n x";
	private static final String DEACT_CMD_STR = "I2C 2 n y";
	private static final String STATE_CMD_STR = "I2C 2 y @";

	public CuZnAutosampler() {
		setName("CuZn Autosampler");
		setBlocking(true);
//		setTimeoutDuration(40000);
//		setPollable(true);
//		setPollDuration(5000);
		setTimeoutDuration(720000);
		setPollable(true);
		setPollDuration(30000);
	}

	@Override
	public Status activate() {
		return send(ACTV_CMD_STR.getBytes());
	}

	@Override
	public Status activate(String params) {
		return send(ACTV_CMD_STR.getBytes());
	}

	@Override
	public Status deactivate() {
		return send(DEACT_CMD_STR.getBytes());
	}
	
	@Override
	public Status deactivate(String params) {
		return deactivate();
	}

	@Override
	public Status pollStatus() {
		return send(STATE_CMD_STR.getBytes());
	}

	@Override
	public boolean shouldContinuePolling() {
		byte data[] = getReceivedData();
		
		if (data == null) {
			OLog.warn("Received data is empty");
			return true;
		}
		
		String response = new String(data);
		if ((response.startsWith("Cu:")) || (response.startsWith("Zn:"))) {
			OLog.info("Data obtained - " + response);
			return false;
		}
		
		return true;
	}
}
