package net.oukranos.OreadPrototype;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;

import android.util.Log;

/**
 * <b>CustomWebInterface Class</b> 
 * </br>Manages connections and sends data to a remote server 
 */
public class NewCustomWebInterface implements WebInterface
{
	private final String DELIM_STRING = "---";
	private final String BOUNDARY_STRING = "dd31dd";
	private String mDataServerUrl = null;
	private String mImageServerUrl = null;
	
	private static DefaultHttpClient httpClient = new DefaultHttpClient();
	
	public NewCustomWebInterface(String serverUrl)
	{
		mDataServerUrl = serverUrl;
	}
	
	public NewCustomWebInterface(String dataServerUrl, String imgServerUrl) {
		mDataServerUrl = dataServerUrl;
		mImageServerUrl = imgServerUrl;
	}
	
	public boolean prepareConnection()
	{
		return true;
	}
	
	public boolean establishConnection()
	{
		return true;
	}
	
	public boolean sendJSONData(String paramName, String jsonStr)
	{
		final String LOG_ID_STRING = "[sendJSONData]";
		
		Log.i(LOG_ID_STRING, "INFO: Sending JSON data part...");
		
		try {
			InputStream is = null;
			try {
		        HttpPost httpPost = new HttpPost(mDataServerUrl);
		        httpPost.setEntity(new StringEntity("{\"reportData\":[" + jsonStr + "]}\r\n"));
	
		        
		        HttpResponse httpResponse = httpClient.execute(httpPost);
		        HttpEntity httpEntity = httpResponse.getEntity();
		        is = httpEntity.getContent();           
		        InputStreamReader isr = new InputStreamReader(is);
		            
		        BufferedReader reader = new BufferedReader(isr);
		        StringBuilder sb = new StringBuilder();
		        String line = null;
		        while ((line = reader.readLine()) != null) {
		                sb.append(line + "\n");
		        }
		        
		        return ( sb.toString().equals("") );
		    } finally {
				try {
					is.close();
				}
				catch(Exception e)
				{
				}
			}	
		} catch (Exception e) {
			e.printStackTrace();
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
		Log.i(LOG_ID_STRING, "INFO: Finished sending JSON data part.");
		return true;
	}
	
	public boolean sendFileData(String paramName, String jsonStr, String filePath) {

		final String LOG_ID_STRING = "[sendMultipartJSONData]";
		
		Log.i(LOG_ID_STRING, "INFO: Sending Multipart JSON data...");
		
		try {
			InputStream is = null;
			try {
				if (filePath == null) {
					Log.w(LOG_ID_STRING, "WaRNING: File not found.");
					return false;
				} 
				
		        HttpPost httpPost = new HttpPost(mImageServerUrl);
		        
		        MultipartEntity multipartContent = new MultipartEntity();
		        multipartContent.addPart("message", new StringBody(jsonStr));
		        FileBody isb = new FileBody(new File(filePath));                                                        
	            multipartContent.addPart("picture", isb);
		        httpPost.setEntity(multipartContent);
		        
		        HttpResponse httpResponse = httpClient.execute(httpPost);
		        HttpEntity httpEntity = httpResponse.getEntity();
		        is = httpEntity.getContent();           
		        InputStreamReader isr = new InputStreamReader(is);
		            
		        BufferedReader reader = new BufferedReader(isr);
		        StringBuilder sb = new StringBuilder();
		        String line = null;
		        while ((line = reader.readLine()) != null) {
		                sb.append(line + "\n");
		        }
		        
		        return ( sb.toString().equals("") );
		    } finally {
				try {
					is.close();
				}
				catch(Exception e)
				{
				}
			}	
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		Log.i(LOG_ID_STRING, "INFO: Finished sending Multipart JSON data.");
		return true;
	}
	
	public boolean finishSending()
	{
		final String LOG_ID_STRING = "[finishSending]";

		Log.i(LOG_ID_STRING, "INFO: Sending termination part...");
		Log.i(LOG_ID_STRING, "INFO: Finished sending termination part.");
		return true;
	}
	
	public void terminateConnections()
	{
		return;
	}

	@Override
	public OutputStream getOutputStream() throws IOException {
		// TODO Auto-generated method stub
		return null;
	}
}
