package net.oukranos.oreadv1.android;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import net.oukranos.oreadv1.interfaces.bridge.IConnectivityBridge;
import net.oukranos.oreadv1.interfaces.bridge.IDeviceInfoBridge;
import net.oukranos.oreadv1.types.Status;

public class AndroidConnectivityBridge 	extends 	AndroidBridgeImpl 
										implements 	IDeviceInfoBridge, 
													IConnectivityBridge {
	private static AndroidConnectivityBridge _androidConnectivityBridge = null;
	private ConnectivityManager _connMgr = null;
	private TelephonyManager _phoneMgr = null;
	private SignalStrengthListener _signalStrListener = null;
	
	private int _gsmSignalStr = 0;
	private int _evdoSignalStr = 0;
	private int _cdmaSignalStr = 0;
	
	private AndroidConnectivityBridge() {
		return;
	}

	@Override
	public String getId() {
		return "connectivity";
	}

	@Override
	public String getPlatform() {
		return "android";
	}

	public static AndroidConnectivityBridge getInstance() {
		if (_androidConnectivityBridge == null) {
			_androidConnectivityBridge = new AndroidConnectivityBridge();
		}
		
		return _androidConnectivityBridge;
	}
	
	public Status initialize(Object initObject) {
		/* Attempt to load the initializer object */
		/*  Note: This method is in AndroidBridgeImpl */
		if (loadInitializer(initObject) != Status.OK) {
			OLog.err("Failed to initialize " + getPlatform() + "." + getId());
			return Status.FAILED;
		}
		
		_connMgr = this.getConnManager(_context);
		_phoneMgr = this.getPhoneManager(_context);

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
			_phoneMgr = this.getPhoneManager(_context);
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
			_connMgr = this.getConnManager(_context);
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
			_connMgr = this.getConnManager(_context);
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
	private ConnectivityManager getConnManager(Context context) {
		if (context == null) {
			OLog.err("Invalid context");
			return null;
		}
		return (ConnectivityManager) _context.getSystemService(Context.CONNECTIVITY_SERVICE);
	}
	
	private TelephonyManager getPhoneManager(Context context) {
		if (context == null) {
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
			_phoneMgr = this.getPhoneManager(_context);
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
			_phoneMgr = this.getPhoneManager(_context);
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
