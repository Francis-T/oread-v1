package net.oukranos.oreadv1.util;

import java.util.ArrayList;

import net.oukranos.oreadv1.manager.FilesystemManager.FSMan;

import android.util.Log;

public class OreadLogger {
	private static OreadLogger _logger = null;
    private static final String DEFAULT_LOG_FILE_PREFIX = "OREAD_ExecLogV2";
    private static final String DEFAULT_LOG_FILE_EXT    = ".txt";
    
    private ArrayList<String> _oldLogBuffer = null;
	
	private OreadLogger() {
		_oldLogBuffer = new ArrayList<String>();
		return;
	}
	
	public static OreadLogger getInstance() {
		if (_logger == null)
		{
			_logger = new OreadLogger();
		}
		
		return _logger; 
	}
	
	public void info(String message) {
		String methodName = "INFO";
		String logMsg = ("Info: " + message);
		logToFile(logMsg);
		logToStack(logMsg);
		Log.i(methodName, logMsg);
		return;
	}

	public void err(String message) {
		String methodName = "ERROR";
		String logMsg = ("Error: " + message);
		logToFile(logMsg);
		logToStack(logMsg);
		Log.e(methodName , logMsg);
		return;
	}
	
	public void warn(String message) {
		String methodName = "WARN";
		String logMsg = ("Warning: " + message);
		logToFile(logMsg);
		logToStack(logMsg);
		Log.w(methodName , logMsg);
		return;
	}

	public void dbg(String message) {
		String methodName = "DEBUG";
		String logMsg = ("Debug: " + message);
		logToFile(logMsg);
		logToStack(logMsg);
		Log.d(methodName , logMsg);
		return;
	}
	
	public String getLastLogMessages(int maxLines) {
		String lastLogs = "";
		
		int linesPrinted = 0;
		for (int i = _oldLogBuffer.size() - 1; i > 0; i--) {
			lastLogs += _oldLogBuffer.get(i) + "\n";
			linesPrinted++;
			
			if (linesPrinted >= maxLines) {
				break;
			}
		}
		
		return lastLogs;
	}
	
	private void logToStack(String message) {
		if (_oldLogBuffer.size() >= 20) {
			_oldLogBuffer.remove(0);
		}
		
		String logMessage = "[" + OreadTimestamp.getTimeString() + "] " + message;
		
//		/* Truncate up to 50 chars only */
//		int end = 50;
//		if (logMessage.length() < 50) {
//			end = logMessage.length() - 1;
//		}
//		String truncMessage = logMessage.substring(0, end);
		
		_oldLogBuffer.add(logMessage);
		return;
	}
	
	private String getLogTimestamp() {
		return (OreadTimestamp.getDateString() + 
				" " + 
				OreadTimestamp.getTimeString());
	}
	
	private String getLogFilename() {
		return ( DEFAULT_LOG_FILE_PREFIX + 
                 "_" + OreadTimestamp.getDateString() + 
                 "_" + OreadTimestamp.getHourString() + 
                 DEFAULT_LOG_FILE_EXT );
	}
	
	private void logToFile(String message) {
        /* Generate the log message parameters */
		String savePath = FSMan.getDefaultFilePath();
		String fileName = this.getLogFilename();

        /* Generate the log message */
		String timestamp = this.getLogTimestamp();
		String logMessage = "[" + timestamp + "]" + message + "\n";

        /* Invoke a special version of saveFileData in FSMan to
         *  save log files (since the normal one would result in
         *  a recursive method call causing a stack overflow */
		FSMan.saveLogFileData(savePath, fileName, logMessage.getBytes());
		
		return;
	}
	
}
