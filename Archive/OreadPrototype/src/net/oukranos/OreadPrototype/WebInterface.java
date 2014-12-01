package net.oukranos.OreadPrototype;

import java.io.IOException;
import java.io.OutputStream;

/**
 * <b>WebInterface Interface</b> </br>An interface for managing connections
 * and sending data to a web-based server
 */
public interface WebInterface
{
	/**
	 * Prepares the connection to the remote server
	 * @return a boolean indicating the exit status for this method
	 */
	public boolean prepareConnection();

	/**
	 * Attempts to establish a connection to the remote server
	 * @return a boolean indicating the exit status for this method
	 */
	public boolean establishConnection();

	/**
	 * Sends JSON data through the active connection
	 * @param paramName - a String parameter name for the JSON data block
	 * @param jsonStr - a String containing the JSON data
	 * @return a boolean indicating the exit status for this method
	 */
	public boolean sendJSONData(String paramName, String jsonStr);

	/**
	 * Sends binary data (e.g. image data) through the active connection
	 * @param paramName - a String parameter name for the binary data block
	 * @param fileName - a String indicating the file name origin of the binary data
	 * @param binData - an array of Bytes containing the binary data
	 * @return a boolean indicating the exit status for this method
	 */
	public boolean sendBinaryData(String paramName, String fileName, byte[] binData);
	
	/**
	 * Adds the appropriate termination sequences for multi-part messages
	 * @return a boolean indicating the exit status for this method
	 */

	/**
	 * Sends file data (e.g. image data) through the active connection
	 * @param paramName - a String parameter name for the binary data block
	 * @param jsonStr - a String containing the non-binary part of the message
	 * @param filePath - a String containing the path to the file to be sent
	 * @return a boolean indicating the exit status for this method
	 */
	public boolean sendFileData(String paramName, String jsonStr, String filePath);
	
	/**
	 * Adds the appropriate termination sequences for multi-part messages
	 * @return a boolean indicating the exit status for this method
	 */
	
	public boolean finishSending();

	/**
	 * Gets the OutputStream currently being used by the active connection
	 * @return the OutputStream being used by the active connection
	 * @throws IOException
	 */
	public OutputStream getOutputStream() throws IOException;

	/**
	 * Terminates all active connection
	 */
	public void terminateConnections();
}
