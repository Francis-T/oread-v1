package net.oukranos.OreadPrototype;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import android.util.Log;

/**
 * <b>CustomWebInterface Class</b> 
 * </br>Manages connections and sends data to a remote server 
 */
public class CustomWebInterface implements WebInterface
{
	private final String DELIM_STRING = "---";
	private final String BOUNDARY_STRING = "dd31dd";
	private String mServerUrl = null;
	private HttpURLConnection mConn = null;
	
	public CustomWebInterface(String serverUrl)
	{
		//mServerUrl = serverUrl;
		mServerUrl = "http://miningsensors.ateneo.edu:8080/uploadData";
	}
	
	public boolean prepareConnection()
	{
		final String LOG_ID_STRING = "[prepareConnection]";
		
		try
		{
			mConn = (HttpURLConnection) new URL(mServerUrl).openConnection();
			mConn.setRequestMethod("POST");
			mConn.setDoInput(true);
			mConn.setDoOutput(true);
			//mConn.setRequestProperty("Connection", "Keep-Alive");
			//mConn.setRequestProperty("Content-Type", "multipart/form-data; boundary= + BOUNDARY_STRING");
			mConn.setRequestProperty("Content-Type", "text/plain; charset=ISO-8859-1");
			mConn.setRequestProperty("Host", "miningsensors.ateneo.edu:8080");
			mConn.setRequestProperty("User-Agent", "Apache-HttpClient/4.2.5 (java 1.5)");
		}
		catch (MalformedURLException e)
		{
			Log.e(LOG_ID_STRING, "ERROR: Malformed URL!");
			return false;
		}
		catch (IOException e)
		{
			Log.e(LOG_ID_STRING, "ERROR: IOException Occurred!");
			e.printStackTrace();
			return false;
		}
		
		return this.establishConnection();
	}
	
	public boolean establishConnection()
	{
		final String LOG_ID_STRING = "[establishConnection]";
		
		if (mConn == null)
		{
			Log.e(LOG_ID_STRING, "ERROR: Null Connection Object!");
			return false;
		}
		
		try {
			mConn.connect();
		} catch (IOException e) {
			Log.e(LOG_ID_STRING, "ERROR: IOException Occurred!");
			e.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	public boolean sendJSONData(String paramName, String jsonStr)
	{
		final String LOG_ID_STRING = "[sendJSONData]";
		
		Log.i(LOG_ID_STRING, "INFO: Sending JSON data part...");
		
		/* Check that the connection object is not null */
		if(mConn == null)
		{
			Log.e(LOG_ID_STRING, "ERROR: Null Connection Object!");
			return false;
		}
		
		/* Check that the jsonStr is not null */
		if (jsonStr == null)
		{
			Log.e(LOG_ID_STRING, "ERROR: Data string is null.");
			return false;
		}
		
		/* Get the output stream */
		OutputStream outStream = null;
		try
		{
			outStream = this.getOutputStream();
		} 
		catch (IOException e)
		{
			/* Ensure that outStream is null upon failure */
			outStream = null;
			Log.e(LOG_ID_STRING, "ERROR: Get output stream failed.");
		}
		
		if (outStream == null)
		{
			Log.e(LOG_ID_STRING, "ERROR: Output stream is null.");
			return false;
		}

		int errId = 0;
		try
		{
			/* Initialize for multi-part */
			//outStream.write((DELIM_STRING + BOUNDARY_STRING + "\r\n").getBytes());
			//++errId;
			
			/* Incorporate additional properties specific to sending of JSON objects */
			//outStream.write(("Content-Disposition: form-data; name=\"" + paramName  + "\"\r\n").getBytes());
			//++errId;
			//outStream.write("Content-Type: application/json\r\n".getBytes());
			//++errId;
			//outStream.write("Accept: application/json\r\n".getBytes());
			//++errId;
			//outStream.write("\r\n".getBytes());
			//++errId;
			
			outStream.write("{\"reportData\":[".getBytes());
			
			/* Write the JSON string into the body */
			outStream.write(jsonStr.getBytes());
			++errId;
			outStream.write("]}\r\n".getBytes());
			++errId;
		}
		catch (IOException e)
		{
			Log.e(LOG_ID_STRING, "ERROR: IOException occurred! [" + errId + "]");
			return false;
		}

		Log.i(LOG_ID_STRING, "INFO: Finished sending JSON data part.");
		return true;
	}
	
	/* TODO: Separate handler for Files instead of pure binary data? 
	 * 		 The argument for that method should be a File object 
	 * 		 instead. That way we won't have to load potentially huge
	 * 		 byte arrays and pass them as arguments. But this will do
	 * 		 for now. 
	 **/
	public boolean sendBinaryData(String paramName, String fileName, byte[] binData)
	{
		final String LOG_ID_STRING = "[sendBinaryData]";

		Log.i(LOG_ID_STRING, "INFO: Sending binary data part...");
		
		/* Check that the connection object is not null */
		if(mConn == null)
		{
			Log.e(LOG_ID_STRING, "ERROR: Null Connection Object!");
			return false;
		}
		
		/* Check that the binData is not null */
		if (binData == null)
		{
			Log.e(LOG_ID_STRING, "ERROR: Binary data argument is null.");
			return false;
		}
		
		/* Get the output stream */
		OutputStream outStream = null;
		try
		{
			outStream = this.getOutputStream();
		} 
		catch (IOException e)
		{
			/* Ensure that outStream is null upon failure */
			outStream = null;
		}
		
		if (outStream == null)
		{
			Log.e(LOG_ID_STRING, "ERROR: Output stream is null.");
			return false;
		}

		
		try
		{
			/* Initialize for multi-part */
			outStream.write((DELIM_STRING + BOUNDARY_STRING + "\r\n").getBytes());
			
			/* Initialize additional properties for sending binary data */
			outStream.write(("Content-Disposition: form-data; name=\"" + paramName  + "\"; filename=\"" + fileName + "\"\r\n").getBytes());
			outStream.write( ("Content-Type: application/octet-stream\r\n"  ).getBytes());
			outStream.write( ("Content-Transfer-Encoding: binary\r\n"  ).getBytes());
			outStream.write("\r\n".getBytes());
			
			/* Write the binary data */
			outStream.write(binData);
			outStream.write("\r\n".getBytes());
		}
		catch (IOException e)
		{
			Log.e(LOG_ID_STRING, "ERROR: IOException occurred!");
			return false;
		}

		Log.i(LOG_ID_STRING, "INFO: Finished sending JSON data part.");
		return true;
	}
	
	public boolean finishSending()
	{
		final String LOG_ID_STRING = "[finishSending]";

		Log.i(LOG_ID_STRING, "INFO: Sending termination part...");
		
		/* Check that the connection object is not null */
		if(mConn == null)
		{
			Log.e(LOG_ID_STRING, "ERROR: Null Connection Object!");
			return false;
		}
		
		/* Get the output stream */
		OutputStream outStream = null;
		try
		{
			outStream = this.getOutputStream();
		} 
		catch (IOException e)
		{
			/* Ensure that outStream is null upon failure */
			outStream = null;
		}
		
		if (outStream == null)
		{
			Log.e(LOG_ID_STRING, "ERROR: Output stream is null.");
			return false;
		}
		
		try
		{
			/* Add termination for multi-part */
			outStream.write((DELIM_STRING + BOUNDARY_STRING + DELIM_STRING + "\r\n").getBytes());
			
			/* Close the output stream */
			outStream.close();
		}
		catch (IOException e)
		{
			Log.e(LOG_ID_STRING, "ERROR: IOException occurred!");
			return false;
		}

		Log.i(LOG_ID_STRING, "INFO: Finished sending termination part.");
		return true;
	}
	
	public OutputStream getOutputStream() throws IOException
	{
		final String LOG_ID_STRING = "[getOutputStream]";
		if (mConn == null)
		{
			Log.e(LOG_ID_STRING, "ERROR: Null Connection Object!");
			return null;
		}
		
		return mConn.getOutputStream();
	}
	
	public void terminateConnections()
	{
		if (mConn != null)
		{
			mConn.disconnect();
			mConn = null;
		}
	}

	@Override
	public boolean sendFileData(String paramName, String jsonStr,
			String filePath) {
		final String LOG_ID_STRING = "[sendFileData]";
		Log.i(LOG_ID_STRING, "INFO: Not Implemented");
		return true;
	}
}
