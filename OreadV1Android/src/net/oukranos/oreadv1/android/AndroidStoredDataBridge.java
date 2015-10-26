package net.oukranos.oreadv1.android;

import android.content.Context;
import android.content.SharedPreferences;
import net.oukranos.oreadv1.interfaces.IPersistentDataBridge;
import net.oukranos.oreadv1.types.Status;

public class AndroidStoredDataBridge extends AndroidBridgeImpl implements IPersistentDataBridge {
	private static final String SHARED_PREFS_ID = "OreadSharedPrefStr_dd31_778924";
	
	private static AndroidStoredDataBridge _dataStore = null;
	
	private SharedPreferences _sharedPrefs = null;
	
	private AndroidStoredDataBridge() {
		return;
	}
	
	public static AndroidStoredDataBridge getInstance() {
		if (_dataStore == null) {
			_dataStore = new AndroidStoredDataBridge();
		}
		
		return _dataStore;
	}
	
	private Status loadSharedPrefs() {
		if (_context == null) {
			return Status.FAILED;
		}
		
		_sharedPrefs = _context.getSharedPreferences(SHARED_PREFS_ID, 
				Context.MODE_PRIVATE);
		if (_sharedPrefs == null) {
			return Status.FAILED;
		}
		
		return Status.OK;
	}
	
	public Status initialize(Object initObject) {
		if (loadInitializer(initObject) != Status.OK) {
			OLog.err("Failed to initialize " + getPlatform() + "." + getId());
			return Status.FAILED;
		}
		
		if (loadSharedPrefs() != Status.OK) {
			return Status.FAILED;
		}
		
		return Status.OK;
	}

	@Override
	public String getId() {
		return "persistentDataStore";
	}

	@Override
	public String getPlatform() {
		return "android";
	}
	
	@Override
	public void put(String id, String value) {
		if (id == null) {
			return;
		}
		
		if (value == null) {
			return;
		}
		
		if (_sharedPrefs != null) {
			_sharedPrefs.edit().putString(id, value).commit();
		}
		return;
	}

	@Override
	public String get(String id) {
		if (id == null) {
			return null;
		}
		
		if (_sharedPrefs != null) {
			return _sharedPrefs.getString(id,"");
		}
		
		return null;
	}
	
	@Override
	public void remove(String id) {
		if (id == null) {
			return;
		}
		
		if (_sharedPrefs != null) {
			if (_sharedPrefs.contains(id)) {
				_sharedPrefs.edit().remove(id).commit();
			}
		}
		
		return;
	}
}
