package net.oukranos.oreadv1.android;

import android.content.Context;
import android.content.SharedPreferences;
import net.oukranos.oreadv1.interfaces.PersistentDataStoreIntf;
import net.oukranos.oreadv1.util.OreadLogger;

public class AndroidStoredDataBridge implements PersistentDataStoreIntf {
	/* Get an instance of the OreadLogger class to handle logging */
	private static final OreadLogger OLog = OreadLogger.getInstance();
	private static final String SHARED_PREFS_ID = "OreadSharedPrefStr_dd31_778924";
	
	private static AndroidStoredDataBridge _dataStore = null;
	private static Context _context = null;
	
	private SharedPreferences _sharedPrefs = null;
	
	public AndroidStoredDataBridge(Object params) {
		_sharedPrefs = _context.getSharedPreferences(SHARED_PREFS_ID, 
				Context.MODE_PRIVATE);
		return;
	}
	
	public static AndroidStoredDataBridge getInstance(Object params) {
		if (params == null) {
			OLog.err("Invalid instantiation parameters");
			return null;
		}
		
		/* Update context */
		try {
			_context = (Context) params;
		} catch (Exception e) {
			OLog.err("Exception occurred: " + e.getMessage());
			return null;
		}
		
		if (_dataStore == null) {
			_dataStore = new AndroidStoredDataBridge(params);
		}
		
		return _dataStore;
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

}
