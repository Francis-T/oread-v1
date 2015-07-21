package net.oukranos.oreadv1.util;

import java.util.Calendar;
import java.util.TimeZone;

public class OreadTimestamp {
	public static String getHourString() {
		Calendar calInstance = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));

		int hour = calInstance.get(Calendar.HOUR_OF_DAY);
		
		String hourStr = (hour < 10 ? "0" + Integer.toString(hour) : Integer.toString(hour));
		
		return hourStr;
	}
	
	public static String getDateString() {
		Calendar calInstance = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));
		
		int year = calInstance.get(Calendar.YEAR);
		int month = calInstance.get(Calendar.MONTH);
		int day = calInstance.get(Calendar.DAY_OF_MONTH);

		String yearStr = (year < 10 ? "0" + Integer.toString(year) : Integer.toString(year));
		String monthStr = (month < 10 ? "0" + Integer.toString(month) : Integer.toString(month));
		String dayStr = (day < 10 ? "0" + Integer.toString(day) : Integer.toString(day));
		
		return (yearStr + monthStr + dayStr);
	}
	
	public static String getTimeString() {
		Calendar calInstance = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));

		int hour = calInstance.get(Calendar.HOUR_OF_DAY);
		int min = calInstance.get(Calendar.MINUTE);
		int sec = calInstance.get(Calendar.SECOND);
		
		String hourStr = (hour < 10 ? "0" + Integer.toString(hour) : Integer.toString(hour));
		String minStr = (min < 10 ? "0" + Integer.toString(min) : Integer.toString(min));
		String secStr = (sec < 10 ? "0" + Integer.toString(sec) : Integer.toString(sec));
		
		return (hourStr + ":" + minStr + "." + secStr);
	}
	
	public static String getTimestampString() {
		Calendar calInstance = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));

		int hour = calInstance.get(Calendar.HOUR_OF_DAY);
		int min = calInstance.get(Calendar.MINUTE);
		int sec = calInstance.get(Calendar.SECOND);
		
		String hourStr = (hour < 10 ? "0" + Integer.toString(hour) : Integer.toString(hour));
		String minStr = (min < 10 ? "0" + Integer.toString(min) : Integer.toString(min));
		String secStr = (sec < 10 ? "0" + Integer.toString(sec) : Integer.toString(sec));
		
		return (hourStr +  minStr + secStr);
	}
}
