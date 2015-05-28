package net.oukranos.oreadv1.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.TimeZone;

import android.os.Environment;
import android.util.Log;

public class OLog {
	public static void info(String message) {
		String methodName = getMethodName(1);
		String logMsg = ("(" + methodName +")" + "Info: " + message);
		logToFile(logMsg);
		Log.i(methodName , logMsg);
		return;
	}

	public static void err(String message) {
		String methodName = getMethodName(1);
		String logMsg = ("(" + methodName +")" + "Error: " + message);
		logToFile(logMsg);
		Log.e(methodName , logMsg);
		return;
	}
	
	public static void warn(String message) {
		String methodName = getMethodName(1);
		String logMsg = ("(" + methodName +")" + "Warning: " + message);
		logToFile(logMsg);
		Log.w(methodName , logMsg);
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
		return (ste[ste.length - depth].getClassName()) + "."  + (ste[ste.length - depth].getMethodName());
	}
	
	/* TODO Consider refactoring this later */
	public static void logToFile(String message) {
		final String root_sd = Environment.getExternalStorageDirectory().toString();
		String savePath = root_sd + "/OreadPrototype";
		File saveDir = new File(savePath);
		
		if (!saveDir.exists())
		{
			saveDir.mkdirs();
		}
		
		Calendar calInstance = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));
		int year = calInstance.get(Calendar.YEAR);
		int month = calInstance.get(Calendar.MONTH);
		int day = calInstance.get(Calendar.DAY_OF_MONTH);
		int hour = calInstance.get(Calendar.HOUR_OF_DAY);
		int min = calInstance.get(Calendar.MINUTE);
		int sec = calInstance.get(Calendar.SECOND);
		
		String dateStr = Integer.toString(year) + "." + Integer.toString(month) + "." + Integer.toString(day);
		String hourStr = (hour < 10 ? "0" + Integer.toString(hour) : Integer.toString(hour));
		String minStr = (min < 10 ? "0" + Integer.toString(min) : Integer.toString(min));
		String secStr = (sec < 10 ? "0" + Integer.toString(sec) : Integer.toString(sec));
		
		String logMessage = "[" + dateStr + " " + hourStr + ":" + minStr + "." + secStr + "]" + message + "\n";
		
		File saveFile = null;
		saveFile = new File(saveDir, ("OREAD_ExecLogV2.txt"));
		
		if (saveFile.exists() == false) {
			try {
				if (!saveFile.createNewFile())
				{
					Log.e("logToFile", "Error: Failed to create save file!");
					return;
				}
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}

		try {
			FileOutputStream saveFileStream = new FileOutputStream(saveFile, true);
			saveFileStream.write(logMessage.getBytes());
			
			saveFileStream.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return;
	}
	
}
