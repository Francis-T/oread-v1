package net.oukranos.oreadv1.devices.control;

import net.oukranos.oreadv1.interfaces.IPersistentDataBridge;
import net.oukranos.oreadv1.types.ControlMechanism;
import net.oukranos.oreadv1.types.MainControllerInfo;
import net.oukranos.oreadv1.types.Status;

public class AsHgAutosampler extends ControlMechanism {
	private static final String ACTV_CMD_STR = "I2C 4 n x";
	private static final String DEACT_CMD_STR = "I2C 4 n y";
	private static final String STATE_CMD_STR = "I2C 4 y @";

	public AsHgAutosampler() {
		setName("AsHg Autosampler");
		setBlocking(false);
		setTimeoutDuration(10000);
		setPollable(true);
		setPollDuration(30000);

		return;
	}
	
	@Override 
	public Status initialize(MainControllerInfo mainInfo) {
		IPersistentDataBridge pDataStore = getPersistentDataBridge();
		if (pDataStore == null) {
			OLog.err("PersistentDataBridge unavailable");
			return Status.FAILED;
		}

		pDataStore.put("ASHG_START_TIME", "0l");
		
		return super.initialize(mainInfo);
	}

	@Override
	public Status activate() {
		return activate("2");
	}
	
	@Override
	public Status activate(String params) {
		OLog.info("Activating " + getName() + "...");
		int dataLen = ACTV_CMD_STR.getBytes().length;
		byte data[] = new byte[dataLen+2];

		IPersistentDataBridge pDataStore = getPersistentDataBridge();
		if (pDataStore == null) {
			OLog.err("PersistentDataBridge unavailable");
			return Status.FAILED;
		}
		
		String posStr = "";
		if (params.equals("@")) {
			posStr = pDataStore.get("ASHG_CUV_NUM");
			if (posStr.equals("")) {
				OLog.info("Empty");
				posStr = "2";
			}
		} else {
			posStr = "2";
		}
		
		data = new String(ACTV_CMD_STR + " " + posStr).getBytes();
		int pos = Integer.decode(posStr) + 1;
		if (pos > 30) {
			pos = 2;
		}
		pDataStore.remove("ASHG_CUV_NUM");
		pDataStore.put("ASHG_CUV_NUM", ("" + pos));
		OLog.info("Pos updated: " + pDataStore.get("ASHG_CUV_NUM"));
		OLog.info("Sending data: " + new String(data));
		
		pDataStore.put("ASHG_START_TIME", Long.toString(System.currentTimeMillis()));
		pDataStore.put("ASHG_READ_ACTIVE", "true");
		
		return send(data);
	}
	
	@Override
	public Status deactivate() {
		OLog.info("Deactivating " + getName() + "...");
		IPersistentDataBridge pDataStore = getPersistentDataBridge();
		if (pDataStore == null) {
			OLog.err("PersistentDataBridge unavailable");
			return Status.FAILED;
		}
		
		pDataStore.put("ASHG_START_TIME", "0l");
		pDataStore.put("ASHG_READ_ACTIVE", "false");
		
		return send(DEACT_CMD_STR.getBytes());
	}

	@Override
	public Status deactivate(String params) {
		return deactivate();
	}

	@Override
	public Status pollStatus() {
		OLog.info("Getting device state for " + getName() + "...");
		return send(STATE_CMD_STR.getBytes());
	}

	@Override
	public boolean shouldContinuePolling() {
		byte data[] = getReceivedData();
		if (data == null) {
			OLog.warn("Received data is empty");
			return true;
		}

		IPersistentDataBridge pDataStore = getPersistentDataBridge();
		if (pDataStore == null) {
			OLog.err("PersistentDataBridge unavailable");
			return false;
		}
		
		String response = new String(data).trim();
		if (response.contains("State: ")) {
			int startIdx 	= response.indexOf("State: ");
			char stateChar 	= response.charAt(startIdx + 7);
			OLog.info("Found Response: " + response);
			
			/* If the Autosampler is at State: 3, then that means the mobile
			 *  phone can begin capturing an image of the LFSB */
			if (stateChar == '3') {
				pDataStore.put("ASHG_READY_TO_CAPTURE", "true");
			}
			
			if (stateChar != '0') {
				pDataStore.put("ASHG_READ_ACTIVE", "true");
			} else {
				pDataStore.put("ASHG_READ_ACTIVE", "false");
			}
			
			pDataStore.put("CURR_ASHG_STATE", (""+stateChar) );
			
			return false;
		}
		
		pDataStore.put("CURR_ASHG_STATE", "9" );
		OLog.info("Found Response: " + response);
		return true;
	}

	/*********************/
	/** Private Methods **/
	/*********************/
}
