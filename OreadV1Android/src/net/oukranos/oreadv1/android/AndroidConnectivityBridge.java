package net.oukranos.oreadv1.android;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import net.oukranos.oreadv1.interfaces.ConnectivityBridgeIntf;
import net.oukranos.oreadv1.interfaces.DeviceIdentityIntf;
import net.oukranos.oreadv1.types.Status;
import net.oukranos.oreadv1.util.OreadLogger;

public class AndroidConnectivityBridge implements DeviceIdentityIntf, ConnectivityBridgeIntf {
	/* Get an instance of the OreadLogger class to handle logging */
	private static final OreadLogger OLog = OreadLogger.getInstance();
	
	private static AndroidConnectivityBridge _androidConnectivityBridge = null;
	private ConnectivityManager _connMgr = null;
	private TelephonyManager _phoneMgr = null;
	private Context _context = null; 
	private SignalStrengthListener _signalStrListener = null;
	
	private int _gsmSignalStr = 0;
	private int _evdoSignalStr = 0;
	private int _cdmaSignalStr = 0;
	
	private AndroidConnectivityBridge() {
		return;
	}

	public static AndroidConnectivityBridge getInstance() {
		if (_androidConnectivityBridge == null) {
			_androidConnectivityBridge = new AndroidConnectivityBridge();
		}
		
		return _androidConnectivityBridge;
	}
	
	public Status initialize(Object initObject) {
		if (initObject == null) {
			return Status.FAILED;
		}
		
		_context = (Context) initObject;
		_connMgr = this.getConnManager();
		_phoneMgr = this.getPhoneManager();

		if (_signalStrListener == null) {
			this.startSignalListener();
		}
		
		return Status.OK;
	}
	
	public Status destroy() {
		if (_signalStrListener != null) {
			this.stopSignalListener();
		}
		
		_context = null;
		_connMgr = null;
		_phoneMgr = null;
		
		return Status.OK;
	}
	
	@Override
	public String getDeviceId() {
		if (_context == null) {
			OLog.err("Invalid context");
			return "";
		}
		
		if (_phoneMgr == null) {
			_phoneMgr = this.getPhoneManager();
		}
		
		return (_phoneMgr.getDeviceId());
	}


	@Override
	public boolean isConnected() {
		if (_context == null) {
			OLog.err("Not attached to an Android activity");
			return false;
		}
		
		if (_connMgr == null) {
			_connMgr = this.getConnManager();
		}
		
		NetworkInfo activeNetwork = _connMgr.getActiveNetworkInfo();
		if (activeNetwork == null) {
			OLog.err("No active network");
			return false;
		}
		
		boolean isConnected = (activeNetwork != null && activeNetwork
				.isConnected());

		if (isConnected == false) {
			OLog.warn("No internet connectivity");
			return false;
		}

		return true;
	}

	@Override
	public String getConnectionType() {
		if (_context == null) {
			OLog.err("Not attached to an Android activity");
			return "";
		}
		
		if (_connMgr == null) {
			_connMgr = this.getConnManager();
		}
		NetworkInfo activeNetwork = _connMgr.getActiveNetworkInfo();
		if (activeNetwork == null) {
			OLog.err("No active network");
			return "";
		}
		
		return activeNetwork.getTypeName();
	}

	@Override
	public int getGsmSignalStrength() {
		if (_context == null) {
			OLog.err("Not attached to an Android activity");
			return 0;
		}
		
		if (_signalStrListener == null) {
			return 0;
		}
		
		return _gsmSignalStr;
	}

	@Override
	public int getCdmaSignalStrength() {
		if (_context == null) {
			OLog.err("Not attached to an Android activity");
			return 0;
		}
		
		if (_signalStrListener == null) {
			return 0;
		}
		
		return _cdmaSignalStr;
	}

	@Override
	public int getEvdoSignalStrength() {
		if (_context == null) {
			OLog.err("Not attached to an Android activity");
			return 0;
		}
		
		if (_signalStrListener == null) {
			return 0;
		}
		
		return _evdoSignalStr;
	}
	
	
	/** Private Methods **/
	private ConnectivityManager getConnManager() {
		if (_context == null) {
			OLog.err("Invalid context");
			return null;
		}
		return (ConnectivityManager) _context.getSystemService(Context.CONNECTIVITY_SERVICE);
	}
	
	private TelephonyManager getPhoneManager() {
		if (_context == null) {
			OLog.err("Invalid context");
			return null;
		}
		
		return (TelephonyManager) _context.getSystemService(Context.TELEPHONY_SERVICE);
	}
	
	private Status startSignalListener() {
		if (_context == null) {
			OLog.err("Invalid context");
			return Status.FAILED;
		}
		
		if (_phoneMgr == null) {
			_phoneMgr = this.getPhoneManager();
		}
		
		_signalStrListener = new SignalStrengthListener();
		_phoneMgr.listen(_signalStrListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
		
		return Status.OK;
	}
	
	private Status stopSignalListener() {
		if (_context == null) {
			OLog.err("Invalid context");
			return Status.FAILED;
		}
		
		if (_phoneMgr == null) {
			_phoneMgr = this.getPhoneManager();
		}
		
		if (_signalStrListener == null) {
			return Status.FAILED;
		}

		_phoneMgr.listen(_signalStrListener, PhoneStateListener.LISTEN_NONE);
		_signalStrListener = null;
		
		return Status.OK;
	}
	
	private class SignalStrengthListener extends PhoneStateListener {
		@Override
		public void onSignalStrengthsChanged(SignalStrength signalStrength) {
			super.onSignalStrengthsChanged(signalStrength);
			
			_gsmSignalStr = (2*signalStrength.getGsmSignalStrength()) - 113;
			_cdmaSignalStr = signalStrength.getCdmaDbm();
			_evdoSignalStr = signalStrength.getEvdoDbm();
			
			
			return;
		}
		
	}
	
}
