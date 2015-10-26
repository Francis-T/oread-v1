package net.oukranos.oreadv1.android;

import android.content.Context;
import net.oukranos.oreadv1.types.MainControllerInfo;
import net.oukranos.oreadv1.types.Status;
import net.oukranos.oreadv1.util.OreadLogger;

public abstract class AndroidBridgeImpl {
	protected static final OreadLogger OLog = OreadLogger.getInstance();
	protected MainControllerInfo _mainInfo = null;
	protected Context _context = null;
	
	protected Status loadInitializer(Object initObject) {
		if (initObject == null) {
			OLog.err("Invalid initializer object");
			return Status.FAILED;
		}
		
		_mainInfo = null;
		try {
			_mainInfo = (MainControllerInfo) initObject;
		} catch(Exception e) {
			OLog.err("Initializer object is not a valid " +
					 "MainControllerInfo object");
			return Status.FAILED;
		}
		
		try {
			_context = (Context) _mainInfo.getContext();
		} catch(Exception e) {
			OLog.err("Could not extract a valid context");
			return Status.FAILED;
		}
		
		return Status.OK;
	}
	
	protected Status unloadInitializers()
	{
		_mainInfo = null;
		_context = null;
		
		return Status.OK;
	}
	
	// Override-able
	public boolean isReady() {
		if ((_context == null) || (_mainInfo == null)) {
			return false;
		}
		
		return true;
	}
}
