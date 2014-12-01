/*
 * 
 */
package ketai.net.bluetooth;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;

import net.oukranos.bluetooth.BluetoothInterface;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

/**
 * The Class KBluetoothConnection.
 */
public class KBluetoothConnection extends Thread {

	/** The mm socket. */
	private final BluetoothSocket mmSocket;
	
	/** The mm in stream. */
	private final InputStream mmInStream;
	
	/** The mm out stream. */
	private final OutputStream mmOutStream;
	
	/** The is connected. */
	private boolean isConnected = false;
	
	/** The address. */
	private String address = "";
	
	/** The btm. */
	private KetaiBluetooth btm;
	
	/** BluetoothInterface handle **/
	private BluetoothInterface mBluetoothEvent;

	/**
	 * Instantiates a new bluetooth connection.
	 *
	 * @param _btm the Bluetooth managing class
	 * @param socket the socket reference for the connection
	 */
	public KBluetoothConnection(KetaiBluetooth _btm, BluetoothSocket socket, BluetoothInterface _btEvent) {
		System.out.println("create Connection thread to "
				+ socket.getRemoteDevice().getName());
		btm = _btm;
		mmSocket = socket;
		InputStream tmpIn = null;
		OutputStream tmpOut = null;
		address = socket.getRemoteDevice().getAddress();

		try {
			// socket.connect();
			tmpIn = socket.getInputStream();
			tmpOut = socket.getOutputStream();
		} catch (IOException e) {
			System.out.println("temp sockets not created: " + e.getMessage());
		}

		mmInStream = tmpIn;
		mmOutStream = tmpOut;
		
		mBluetoothEvent = _btEvent;
		
		isConnected = true;
	}

	/**
	 * Gets the hardware address.
	 *
	 * @return the address (hardware)
	 */
	public String getAddress() {
		return address;
	}

	/**
	 * Gets the device name.
	 *
	 * @return the device name
	 */
	public String getDeviceName() {
		if (mmSocket == null)
			return "";
		return mmSocket.getRemoteDevice().getName();
	}

	/* (non-Javadoc)
	 * @see java.lang.Thread#run()
	 */
	@SuppressLint("NewApi")
	public void run() {
		System.out.println("BEGIN mConnectedThread to " + address);
		int bytesInStream = 0;

		// Keep listening to the InputStream while connected
		while (true) {
			try {
				
				bytesInStream = mmInStream.available();
				/* Check if there are bytes available for reading in our input stream */
				if (bytesInStream > 0)
				{
					byte[] buffer = new byte[bytesInStream];
					
					//System.out.println("[KBluetoothConnection] INFO: Data available!");
					
					// Read from the InputStream
					mmInStream.read(buffer);
					
					/* Relay the buffer's contents back upstairs */
					mBluetoothEvent.onDataReceived(buffer);
				} else {
				// // Send the obtained bytes to the UI Activity
				// mHandler.obtainMessage(BluetoothChat.MESSAGE_READ, bytes, -1,
				// buffer).sendToTarget();
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
	
					}
				}
			} catch (IOException e) {
				btm.removeConnection(this);
				System.out.println(getAddress() + " disconnected" + e.getMessage());
				// notify manager that we've gone belly up
				// connectionLost();
				isConnected = false;
				break;
			}
		}
	}

	/**
	 * Checks if we are connected.
	 *
	 * @return true, if is connected
	 */
	public boolean isConnected() {
		return isConnected;
	}

	/**
	 * Write data to the connection
	 *
	 * @param buffer the buffer
	 */
	public void write(byte[] buffer) {
		try {
			// System.out.println("KBTConnection thread writing " + buffer.length
			// + " bytes to " + address);

			mmOutStream.write(buffer);
		} catch (IOException e) {
			System.out.println(getAddress() + ": Exception during write"
					+ e.getMessage());
			btm.removeConnection(this);
		}
	}

	/**
	 * Cancel, close out the resource
	 */
	public void cancel() {
		try {
			mmSocket.close();
		} catch (IOException e) {
			System.out.println("close() of connect socket failed" + e.getMessage());
		}
	}

}
