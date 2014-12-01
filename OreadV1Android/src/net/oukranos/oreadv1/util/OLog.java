package net.oukranos.oreadv1.util;

import android.util.Log;

public class OLog {
	public static void info(String message) {
		Log.i(getMethodName(2), "Info: " + message);
		return;
	}

	public static void err(String message) {
		Log.e(getMethodName(2), "Error: " + message);
		return;
	}
	
	public static void warn(String message) {
		Log.w(getMethodName(2), "Warning: " + message);
		return;
	}
	
	/**
	 * Get the method name for a depth in call stack. <br />
	 * Utility function
	 * @source http://stackoverflow.com/questions/442747/getting-the-name-of-the-current-executing-method/5891326#5891326
	 * @param depth depth in the call stack (0 means current method, 1 means call method, ...)
	 * @return method name
	 */
	public static String getMethodName(final int depth) {
		final StackTraceElement[] ste = Thread.currentThread().getStackTrace();
		return (ste[ste.length - 1 - depth].getMethodName());
	}
}
