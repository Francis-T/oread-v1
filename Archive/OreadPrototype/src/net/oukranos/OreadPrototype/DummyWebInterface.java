package net.oukranos.OreadPrototype;

import java.io.IOException;
import java.io.OutputStream;

import android.util.Log;

/**
 * <b>DummyWebInterface Class</b> 
 * </br>Shadows the functions of a WebInterface but routes the data to System.out instead. 
 */
public class DummyWebInterface implements WebInterface
{
	private final String DELIM_STRING = "---";
	private final String BOUNDARY_STRING = "dd31dd";
	private OutputStream mDummyOutputStream = System.out;
	
	public DummyWebInterface(String serverUrl)
	{
	}
	
	public boolean prepareConnection()
	{
		return this.establishConnection();
	}
	
	public boolean establishConnection()
	{
		return true;
	}
	
	public boolean sendJSONData(String paramName, String jsonStr)
	{
		final String LOG_ID_STRING = "[sendJSONData]";
		
		Log.i(LOG_ID_STRING, "INFO: Sending JSON data part...");
		
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
			
			/* Incorporate additional properties specific to sending of JSON objects */
			outStream.write(("Content-Disposition: form-data; name=\"" + paramName  + "\"\r\n").getBytes());
			outStream.write("Content-Type: application/json\r\n".getBytes());
			outStream.write("Accept: application/json\r\n".getBytes());
			outStream.write("\r\n".getBytes());

			
			outStream.write("{\"reportData\":[".getBytes());
			/* Write the JSON string into the body */
			outStream.write(jsonStr.getBytes());
			outStream.write("]}\r\n".getBytes());
			
		}
		catch (IOException e)
		{
			Log.e(LOG_ID_STRING, "ERROR: IOException occurred!");
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
		return mDummyOutputStream;
	}
	
	public void terminateConnections()
	{
		try {
			if (mDummyOutputStream != null)
			{
					mDummyOutputStream.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
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
