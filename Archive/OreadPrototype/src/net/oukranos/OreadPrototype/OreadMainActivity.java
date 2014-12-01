package net.oukranos.OreadPrototype;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.TimeZone;

import net.oukranos.bluetooth.BluetoothInterface;
import ketai.net.bluetooth.KetaiBluetooth;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.hardware.Camera;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

public class OreadMainActivity extends ListActivity implements OnClickListener, BluetoothInterface
{
	/* Static variables */
	private static long TRANSMIT_INTERVAL = 1500;//10000; /* 10 second intervals. Change if needed */
	private static final int MENU_ITEM_TOGGLE_SERVER = 1001;
	private static final int MENU_ITEM_SET_SERVER_URL = 1002;
	private static final int MENU_ITEM_ACTIVATE_SERVER = 1003;

	private static final int MENU_ITEM_SET_REMOTE_DEVICE = 1004;
	private static final int MENU_ITEM_CONNECT_TO_DEVICE = 1005;
	private static final int MENU_ITEM_DISCONNECT_FROM_DEVICE = 1006;
	private static final int MENU_ITEM_SAVE_DATA = 1007;
	private static final int MENU_ITEM_SET_READOUT_TIME = 1008;
	private static final int MENU_ITEM_SET_CAMERA_VIS = 1009;
	
	private static final int MENU_GROUP_SERVER = 0;
	private static final int MENU_GROUP_BLUETOOTH = 1;
	
	private static final long CMD_WAIT_INTERVAL = 750;
	
	private static final int STATUS_FAILED = -1;
	private static final int STATUS_OK = 1;
	
	private static final String DEFAULT_DATA_SERVER_URL = "http://miningsensors.ateneo.edu:8080/uploadData";
	private static final String DEFAULT_IMAGE_SERVER_URL = "http://miningsensors.ateneo.edu:8080/uploadImage";
	private static final String DEFAULT_BT_DEVICE_ADDR = "HC-05";
	
	
	/* Boolean flags */
	private boolean shouldStopTasks = false;
	private boolean shouldUseServer = true; /* XXX: For testing only. Remove this eventually. */
	private boolean isBluetoothStarted = false;
	private boolean _bDeviceCommStarted = false;
	private boolean _bIsSensorDataAvailable = false;
	private boolean _bIsImageDataAvailable = false;
	
	/* X-wide Objects */
	private String mDataServerUrl = DEFAULT_DATA_SERVER_URL;
	private String mImageServerUrl = DEFAULT_IMAGE_SERVER_URL;
	private TestDataSenderTask mSenderTask = null;
	private ProtoProcessCommandTask mCmdProcessTask = null;
	private Thread mCmdProcessThread = null;
	private WebInterface mWebInterface = null;
	private SharedPreferences mSharedPrefs = null;
	private Type1SensorData mSensorData = null;
	private String mLastImageFile = "";
	
	private String mTargetDevice = DEFAULT_BT_DEVICE_ADDR;
	private KetaiBluetooth mKBluetooth;
	private String mBTReceiveStr = "";
	
	private ArrayAdapter<String> mArrayAdapter;
	private Camera mCamera = null;
	private CameraPreview mPreview = null;
	
	private long mCmdExecInterval = CMD_WAIT_INTERVAL;
	//private ArrayList<String> mMessages;
	
	/* GUI Components */
//	private EditText mSendMsgField = null;
//	private Button mActivateButton = null; /* XXX : Candidate for deletion */
//	private Button mSendButton = null;
	private ImageButton mBTConnectButton = null;
	private ImageButton mDeviceCommButton = null;
	private ImageButton mServerCommButton = null;
	private ImageButton mCaptureImageButton = null;
	
	/* Overriden Methods */
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_oread_main);
		final String LOG_ID_STRING = "[onCreate]";
		
		/* Set OnClickListener on the Active/Inactive Button */
//		mActivateButton = (Button) this.findViewById(R.id.server_activate_btn);
//		mActivateButton.setOnClickListener(this);

		/* Initialize Bluetooth */
		mKBluetooth = new KetaiBluetooth(this, this);
		if (mKBluetooth == null)
		{
			Log.e(LOG_ID_STRING, "Error: Failed to initialize Bluetooth!");
			this.finish();
			return;
		}
		
		/* Get the shared preferences file */
		mSharedPrefs = this.getSharedPreferences("OreadSharedPrefStr_dd31_778923", Context.MODE_PRIVATE);
		if (mSharedPrefs == null)
		{
			Log.w(LOG_ID_STRING, "Warning: Shared preferences file not available.");
		}

		// Initialize the list adapter
		// This list adapter shall contain the pair-able device names
		mArrayAdapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_list_item_1);

		// Set the adapter to be used by the list
		this.setListAdapter(mArrayAdapter);
		
		/* Initialize the GUI components */
		if (initializeGUIComponents() != true)
		{
			this.finish();
			return;
		}
		
		/* Initialize the camera */
		if ( mCamera == null ) {
			try {
				mCamera = Camera.open();
				mCamera.setDisplayOrientation(90);
			} catch (Exception e) {
				Log.e(LOG_ID_STRING, "Error: Could not open camera.");
				this.finish();
				return;
			}
		}
		
		/* Initialize the invisible preview */
		if (mPreview == null) {
			mPreview = new CameraPreview(this, mCamera);
			
			FrameLayout preview = (FrameLayout) this.findViewById(R.id.camera_preview);
			preview.addView(mPreview);
		}
		
		
		/* Initialize the sensor data object */
		if (mSensorData == null) {
			double sensMatrixTmp[] = { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0 };
			mSensorData = Type1SensorData.create(1, "OK", sensMatrixTmp);
		}
		
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		if (mSharedPrefs != null)
		{
			/* Reload the old server url value on resume */ 
			mDataServerUrl = mSharedPrefs.getString("OLD_SERVER_URL_VAL", "http://miningsensors.ateneo.edu:8080/uploadData");
			
			/* Reload the name of the old target BT device on resume */
			mTargetDevice = mSharedPrefs.getString("OLD_DEVICE_NAME_VAL", "HC-05");
		}
		
		if (mCamera == null) {
			mCamera = Camera.open();
			mCamera.setDisplayOrientation(90);
			if (mPreview == null) {
				mPreview = new CameraPreview(this, mCamera);

				FrameLayout preview = (FrameLayout) this.findViewById(R.id.camera_preview);
				preview.addView(mPreview);
			}
		}
	}
	
	@Override
	protected void onPause() {
		this.onShutdown();
		super.onPause();
	}
	
	@Override
	protected void onStop() {
		this.onShutdown();
		super.onStop();
	}

//	@Override
//	protected void onDestroy()
//	{
//		final String LOG_ID_STRING = "[onDestroy]";
//		shutdownApp();
//		
//		super.onDestroy();
//	}
	
	private void onShutdown() {
		final String LOG_ID_STRING = "[onShutdown]";
		/* If Bluetooth has been started before, it would be safest to call the
		 * stop function here just in case. */
		if (mKBluetooth != null)
		{
			mKBluetooth.stop();
		}
		
		if (mCamera != null) {

			FrameLayout preview = (FrameLayout) this.findViewById(R.id.camera_preview);
			preview.removeAllViews();
			
			mCamera.release();
			mCamera = null;
		}
		
		if ((mCmdProcessThread != null) && (mCmdProcessThread.isAlive())) {
			_shouldStopCmdProc = true;
			mCmdProcessThread.interrupt();
			try {
				mCmdProcessThread.join();
			} catch (InterruptedException e) {
				Log.i(LOG_ID_STRING, "Error: Cmd Process Thread Termination Interrupted.");
			}
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		getMenuInflater().inflate(R.menu.oread_main, menu);
		menu.add(MENU_GROUP_SERVER, MENU_ITEM_TOGGLE_SERVER, Menu.NONE, "Toggle Server Connection");
		menu.add(MENU_GROUP_SERVER, MENU_ITEM_SET_SERVER_URL, Menu.NONE, "Set Server URL");

		menu.add(MENU_GROUP_BLUETOOTH, MENU_ITEM_SET_REMOTE_DEVICE, Menu.NONE, "Set Sensor Device");
//		menu.add(MENU_GROUP_BLUETOOTH, MENU_ITEM_CONNECT_TO_DEVICE, Menu.NONE, "Connect to Sensor Device");
//		menu.add(MENU_GROUP_BLUETOOTH, MENU_ITEM_DISCONNECT_FROM_DEVICE, Menu.NONE, "Disconnect from Sensor Device");
		menu.add(MENU_GROUP_BLUETOOTH, MENU_ITEM_SAVE_DATA, Menu.NONE, "Save Received Data");
		menu.add(MENU_GROUP_BLUETOOTH, MENU_ITEM_SET_READOUT_TIME, Menu.NONE, "Set Readout Time");
		menu.add(MENU_GROUP_BLUETOOTH, MENU_ITEM_SET_CAMERA_VIS, Menu.NONE, "(Debug) Toggle Camera Vis");
		
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		final String LOG_ID_STRING = "[onOptionsItemSelected]";
		switch(item.getItemId())
		{
			case MENU_ITEM_TOGGLE_SERVER:
				/* Toggle whether the app should attempt to connect to a remote server
				 * or just route all outgoing data to System.out */
				shouldUseServer = !shouldUseServer;
				
				Toast.makeText(this, "Value Changed: Will" + (shouldUseServer ? " " : " not ") + "use server", Toast.LENGTH_LONG).show();
				break;
			case MENU_ITEM_SET_SERVER_URL:
				/* Create an AlertDialog allowing the user to change the default server url */
				final EditText dataServerUrlInput = new EditText(this);
				final EditText imgServerUrlInput = new EditText(this);
				dataServerUrlInput.setText(mSharedPrefs.getString("OLD_SERVER_URL_VAL", DEFAULT_DATA_SERVER_URL));
				imgServerUrlInput.setText(mSharedPrefs.getString("OLD_IMG_SERVER_URL_VAL", DEFAULT_IMAGE_SERVER_URL));
				
				dataServerUrlInput.setTextSize(10);
				imgServerUrlInput.setTextSize(10);
				
				dataServerUrlInput.setHorizontallyScrolling(true);
				imgServerUrlInput.setHorizontallyScrolling(true);
				
				final LinearLayout textPanel = new LinearLayout(this);
				textPanel.setOrientation(LinearLayout.VERTICAL);
				textPanel.addView(dataServerUrlInput);
				textPanel.addView(imgServerUrlInput);
				
				new AlertDialog.Builder(this)
			    .setTitle("Set the Server URL")
			    .setMessage("Enter the server URL to be used:")
			    .setView(textPanel)
			    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
			        public void onClick(DialogInterface dialog, int whichButton) {
			        	mDataServerUrl = dataServerUrlInput.getText().toString();
			        	mImageServerUrl = imgServerUrlInput.getText().toString();
			        	
			        	/* Also, save this to the shared prefs */
			        	mSharedPrefs.edit().putString("OLD_SERVER_URL_VAL", mDataServerUrl).commit();
			        	mSharedPrefs.edit().putString("OLD_IMG_SERVER_URL_VAL", mImageServerUrl).commit();
			        }
			    }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			        public void onClick(DialogInterface dialog, int whichButton) {
			            // Do nothing.
			        }
			    }).show();
				
				break;
			case MENU_ITEM_SET_REMOTE_DEVICE:
				/* Create an AlertDialog allowing the user to change the default BT device name */
				final EditText name_input = new EditText(this);
				name_input.setText(mSharedPrefs.getString("OLD_DEVICE_NAME_VAL", ""));
				
				new AlertDialog.Builder(this)
			    .setTitle("Set the Bluetooth Device")
			    .setMessage("Enter the name of the BT sensor device to be used:")
			    .setView(name_input)
			    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
			        public void onClick(DialogInterface dialog, int whichButton) {
			        	mTargetDevice = name_input.getText().toString();
			        	
			        	/* Also, save this to the shared prefs */
			        	mSharedPrefs.edit().putString("OLD_DEVICE_NAME_VAL", mTargetDevice).commit();
			        }
			    }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			        public void onClick(DialogInterface dialog, int whichButton) {
			            // Do nothing.
			        }
			    }).show();
				
				break;
//			case MENU_ITEM_CONNECT_TO_DEVICE:
//				/* Check that Bluetooth has been initialized properly */
//				if (mKBluetooth == null)
//				{
//					break;
//				}
//				
//				/* Start Bluetooth */
//				isBluetoothStarted = mKBluetooth.start();
//				
//				if (mKBluetooth.getPairedDeviceNames() == null)
//				{
//					Log.e(LOG_ID_STRING, "Error: Could not get paired devices!");
//					break;
//				}
//				
//				/* Connect to the Bluetooth device */
//				if ((mTargetDevice != null) && (!mTargetDevice.equals("")))
//				{
//					mKBluetooth.connectToDeviceByName(mTargetDevice);
//				}
//
//				mBTConnectButton.setColorFilter(getResources().getColor(R.color.green));
//				
//				break;
//			case MENU_ITEM_DISCONNECT_FROM_DEVICE:
//				/* Disconnect from the Bluetooth device */
//				if (mKBluetooth != null)
//				{
//					isBluetoothStarted = false;
//					mKBluetooth.stop();
//
//					mBTConnectButton.setColorFilter(getResources().getColor(R.color.red));
//				}
//				
//				break;
			case MENU_ITEM_SET_READOUT_TIME:
				/* Create an AlertDialog allowing txhe user to change the default server url */
				final EditText readout_time_input = new EditText(this);
				readout_time_input.setText(mSharedPrefs.getString("OLD_READOUT_TIME_VAL", "750"));
				
				new AlertDialog.Builder(this)
			    .setTitle("Set the readout time")
			    .setMessage("Enter the readout time (in msec) to be used:")
			    .setView(readout_time_input)
			    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
			        public void onClick(DialogInterface dialog, int whichButton) {
			        	Long tempLong = Long.decode(readout_time_input.getText().toString());
			        	
			        	if (tempLong != null) {
			        		mCmdExecInterval = tempLong.longValue();
				        	/* Also, save this to the shared prefs */
				        	mSharedPrefs.edit().putString("OLD_READOUT_TIME_VAL", readout_time_input.getText().toString()).commit();
				        	
				        	Log.i(LOG_ID_STRING, "Info: New Readout Time Saved ("+mCmdExecInterval+")");
			        	} else {
			        		mCmdExecInterval = CMD_WAIT_INTERVAL;
				        	Log.e(LOG_ID_STRING, "Info: Failed to Set Readout Time ("+readout_time_input.getText().toString()+").");
			        	}
			        }
			    }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			        public void onClick(DialogInterface dialog, int whichButton) {
			            // Do nothing.
			        }
			    }).show();
				
				break;
				
			case MENU_ITEM_SAVE_DATA:
				saveDataToFile();
				break;
			case MENU_ITEM_SET_CAMERA_VIS:
				View v = this.findViewById(R.id.camera_preview);
				if (v.getVisibility() == View.VISIBLE) {
					v.setVisibility(View.INVISIBLE);
				} else {
					v.setVisibility(View.VISIBLE);
				}
			default:
				break; /* Do nothing */
		}
		
		return true;
	}
	@Override
	public void onClick(View v)
	{
		final String LOG_ID_STRING = "[OnClick]";
		
		switch (v.getId())
		{
			case R.id.toggle_device_btn:
				if ( _bDeviceCommStarted == false ) {
					/* Start device communication if it hasn't been started yet */
					/* Check the activity status */
					if ((mCmdProcessTask == null) && (_shouldStopCmdProc == false))
					{
						_runCmdIdx = 0;
						_shouldStopCmdProc = false;
						
						/* Begin a new cmd processing task */
						mCmdProcessTask = new ProtoProcessCommandTask();
						
						if ( mCmdProcessThread == null ) 
						{
							mCmdProcessThread = new Thread(mCmdProcessTask);
						}
						
						if ( !(mCmdProcessThread.isAlive()) ) 
						{
							mCmdProcessThread.start();
						}
						
						Log.i(LOG_ID_STRING, "Info: New cmd process task created.");
						
						/* Change the button's icon color to green */
						((ImageButton) v).setColorFilter(getResources().getColor(R.color.green), PorterDuff.Mode.SRC_IN);
					} else {
						/* Change the button's icon color to green */
						((ImageButton) v).setColorFilter(getResources().getColor(R.color.red), PorterDuff.Mode.SRC_IN);
					}
				} else {
					/* Otherwise, stop device communication */
					if (_shouldStopCmdProc == false)
					{
						/* Initiate termination of the current cmd processing task */
						_shouldStopCmdProc = true;
						
						if (mCmdProcessThread != null) {
							mCmdProcessThread.interrupt();
						}
						
						Log.i(LOG_ID_STRING, "Info: Initiated cmd process task termination.");
					}
					
					/* Change the button's icon color to red */
					((ImageButton) v).setColorFilter(getResources().getColor(R.color.red), PorterDuff.Mode.SRC_IN);
				}
				
				break;
			case R.id.toggle_server_btn:
				/* Check the activity status */
				ConnectivityManager cm = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
		 
				NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
				boolean isConnected = (activeNetwork != null && activeNetwork.isConnected());
				
				if ( !isConnected ) {
					new AlertDialog.Builder(this)
				    .setTitle("Error")
				    .setMessage("Internet Connection Unavailable")
				    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
				        public void onClick(DialogInterface dialog, int whichButton) {
				        	// Do nothing
				        }
				    }).show();
					break;
				}
				
				if ((mSenderTask == null) && (shouldStopTasks == false))
				{
					
					/* Begin a new sender task */
					mSenderTask = new TestDataSenderTask();
					mSenderTask.execute();

					/* Modify the Button */
					if (mServerCommButton != null)
					{
						mServerCommButton.setColorFilter(getResources().getColor(R.color.green), PorterDuff.Mode.SRC_IN);
					}
					
					Toast.makeText(this, "Info: Server Conn On!", Toast.LENGTH_LONG);
					
					Log.i(LOG_ID_STRING, "Info: New sender task created.");
				}
				else if (shouldStopTasks == false)
				{
					/* Initiate termination of the current sender task */
					shouldStopTasks = true;

					/* Modify the Button */
					if (mServerCommButton != null)
					{
						mServerCommButton.setColorFilter(getResources().getColor(R.color.red), PorterDuff.Mode.SRC_IN);
					}
					
					Toast.makeText(this, "Info: Server Conn Off!", Toast.LENGTH_LONG);
					
					Log.i(LOG_ID_STRING, "Info: Initiated sender task termination.");
				}
			case R.id.capture_image_btn:
				SavePictureCallback lProcessPic = new SavePictureCallback();
				try {
					mCamera.takePicture(null, null, lProcessPic);
				} catch (Exception e) {
					Log.e(LOG_ID_STRING, "Error: " + e.getMessage());
				}
				break;
			case R.id.toggle_bluetooth_btn:
				if (isBluetoothStarted  == false) {
					/* Start bluetooth communication if it hasn't been started yet */
					/* Check that Bluetooth has been initialized properly */
					if (mKBluetooth == null)
					{
						break;
					}
					
					/* Start Bluetooth */
					isBluetoothStarted = mKBluetooth.start();
					
					if (mKBluetooth.getPairedDeviceNames() == null)
					{
						Log.e(LOG_ID_STRING, "Error: Could not get paired devices!");
						break;
					}
					
					/* Connect to the Bluetooth device */
					if ((mTargetDevice != null) && (!mTargetDevice.equals("")))
					{
						mKBluetooth.connectToDeviceByName(mTargetDevice);
					}

					/* Change the button's icon color to green */
					((ImageButton) v).setColorFilter(getResources().getColor(R.color.green), PorterDuff.Mode.SRC_IN);
				} else {
					/* Otherwise, stop device communication */
					/* Disconnect from the Bluetooth device */
					if (mKBluetooth != null)
					{
						isBluetoothStarted = false;
						mKBluetooth.stop();
					}
					
					/* Change the button's icon color to red */
					((ImageButton) v).setColorFilter(getResources().getColor(R.color.red), PorterDuff.Mode.SRC_IN);
				}
				break;
			default:
				break; /* Do nothing and break */
		}
	}
	
	private class SavePictureCallback implements Camera.PictureCallback {
		@Override
		public void onPictureTaken(byte[] data, Camera camera) {
			Log.i("[SavePictureCallback]", "Invoked.");
			savePictureToFile(data);
			mCamera.startPreview();
			_bIsImageDataAvailable = true;
		}
		
	}
	
	private boolean sensorDataReceived[] = { false, false, false };
	private String sensorDataBuffer = "";
	
	private String extractSensorData(String dataStr, String sensTypeStr) {
		if ( (dataStr == null) || 
			 (dataStr.equals("") == true) ||
			 (sensTypeStr == null) || 
			 (sensTypeStr.equals("") == true) ) {
			return null;
		}
		String dataStrPart[] = dataStr.split(" ");
		
		int i = 0;
		for ( /* no init req'd */; i < dataStrPart.length; i++) {
			if ( dataStrPart[i].contains(sensTypeStr) == true ) {
				break;
			}
		}
		
		if (i >= dataStrPart.length) {
			/* sensTypeStr not found */
			return null;
		}
		
		if ( dataStrPart[i+1] == null || dataStrPart[i+1].equals("") ) {
			/* no sensor data string */
			return null;
		}
		
		return (dataStrPart[i+1]);
	}

	@Override
	public void onDataReceived(byte[] data) {
		if (data == null) {
			Log.e("[onDataReceived]", "Error: Data is NULL!");
			return;
		}
		
		mBTReceiveStr += new String(data, 0, data.length);
		
		/* Check if we have received the null terminator */
		if ((data[data.length-1] == '\n') || (data[data.length-1] == '\0')) {	
			Log.i("[onDataReceived]", "Info: Data received");
			if (mBTReceiveStr.contains("pH") == true) {	
				Log.i("[onDataReceived]", "Info: Received pH");
				// Extract PH data
				double sensData = 0.0;
				try {
					sensData = Double.parseDouble( extractSensorData( mBTReceiveStr, "pH:" ) );
				} catch (NumberFormatException e) {
					sensData = 0.0;
				}
				
				if (mSensorData != null) {
					mSensorData.ph = sensData;
				}
				
				sensorDataReceived[0] = true;
			} else if (mBTReceiveStr.contains("DO") == true) {
				Log.i("[onDataReceived]", "Info: Received DO");
				// Extract DO2 data
				double sensData = 0.0;
				try {
					sensData = Double.parseDouble( extractSensorData( mBTReceiveStr, "DO:" ) );
				} catch (NumberFormatException e) {
					sensData = 0.0;
				}

				if (mSensorData != null) {
					mSensorData.dissolved_oxygen = sensData;
				}
				
				sensorDataReceived[1] = true;
			} else if (mBTReceiveStr.contains("EC") == true) {
				Log.i("[onDataReceived]", "Info: Received EC");
				// Extract Conductivity data
				double sensData[] = { 0.0, 0.0, 0.0 };
				
				String ecStrArr[] = null;
				String ecStrTmp = extractSensorData( mBTReceiveStr, "EC:" );
				if ((ecStrTmp != null) && 
					(ecStrTmp.equals("") == false)) {
					ecStrArr = ecStrTmp.split(",");	
				}
				
				if ((ecStrArr != null) &&
					(ecStrArr.length == sensData.length)) {
					try {
						if (ecStrArr[0].equals("") == false) {
							Log.i("DEBUG", "Data 1: " + ecStrArr[0] );
							sensData[0] = Double.parseDouble( ecStrArr[0] );
						}
					} catch (NumberFormatException e) {
						sensData[0] = 0.0;
					}

					try {
						if (ecStrArr[1].equals("") == false) {
							Log.i("DEBUG", "Data 1: " + ecStrArr[1] );
							sensData[1] = Double.parseDouble( ecStrArr[1] );
						}
					} catch (NumberFormatException e) {
						sensData[1] = 0.0;
					}

					try {
						if (ecStrArr[2].equals("") == false) {
							Log.i("DEBUG", "Data 1: " + ecStrArr[2] );
							sensData[2] = Double.parseDouble( ecStrArr[2] );
						}
					} catch (NumberFormatException e) {
						sensData[2] = 0.0;
					}
				}
				

				if (mSensorData != null) {
					mSensorData.conductivity = sensData[0];
					mSensorData.tds = sensData[1];
					mSensorData.salinity = sensData[2];
				}
				
				sensorDataReceived[2] = true;
			} else {
				new InsertMessageToListTask().execute(mBTReceiveStr);
				mBTReceiveStr = "";
				return;
			}

			sensorDataBuffer += mBTReceiveStr.replace('\r', ' ').replace('\n', ' ');
			
			boolean allSensDataReceived = true;
			for (int i = 0; i < sensorDataReceived.length; i++) {
				if (sensorDataReceived[i] == false) {
					allSensDataReceived = false;
				}
			}
			
			if (allSensDataReceived == true) {
				if (mSensorData != null) {
					mSensorData.addTimestamp();
				} else {
					Log.i("[onDataReceived]", "INFO: mSensorData is NULL!");
				}
				
				if (_bIsSensorDataAvailable == false) {
					_bIsSensorDataAvailable = true;
				}
				
				/* Insert the completed message to the list */
				new InsertMessageToListTask().execute(sensorDataBuffer);
				sensorDataBuffer = "";
				sensorDataReceived[0] = false;
				sensorDataReceived[1] = false;
				sensorDataReceived[2] = false;
			}
			
			//Log.i("[Message]", mBTReceiveStr);
			mBTReceiveStr = "";
		}
	}
	
	/* Private Methods */
	/**
	 * Initializes the WebInterface to be used for connecting to a remote server
	 * @param serverUrl - a string indicating the URL of the remote server to access 
	 * @return a boolean indicating the exit status for this method
	 */
	private boolean initializeWebInterface(String dataServerUrl, String imgServerUrl) {
		final String LOG_ID_STRING = "[initializeWebInterface]";
		
		if (mWebInterface != null)
		{
			Log.i(LOG_ID_STRING, "Info: Custom Web Interface already initialized");
			return true;
		}
		
		/* Allow runtime toggle for switching between the dummy and actual web interface.
		 * This is mostly for testing purposes only. */
		if (shouldUseServer) {
			mWebInterface = new NewCustomWebInterface(dataServerUrl, imgServerUrl);
		} else {
			/* The DummyWebInterface simply routes the data that would have been sent to
			 * the server to System.out so that we can check if it is ok */
			mWebInterface = new DummyWebInterface(dataServerUrl);
		}
		
		Log.i(LOG_ID_STRING, "Info: Preparing connection...");
		if (mWebInterface.prepareConnection() == false)
		{
			Log.i(LOG_ID_STRING, "Error: Failed to prepare the connection.");
			
			/* Attempt to terminate WebInterface connections */
			mWebInterface.terminateConnections();
			mWebInterface = null;
			return false;
		}

		Log.i(LOG_ID_STRING, "Info: Establishing connection...");
		if (mWebInterface.establishConnection() == false)
		{
			Log.e(LOG_ID_STRING, "Error: Failed to establish the connection.");
			
			/* Attempt to terminate WebInterface connections */
			mWebInterface.terminateConnections();
			mWebInterface = null;
			return false;
		}
		Log.i(LOG_ID_STRING, "Info: Connection Established.");
		return true;
	}
	
	private boolean initializeGUIComponents()
	{
		final String LOG_ID_STRING = "[initializeGUIComponents]";
		
//		mBTConnectButton = (Button) this.findViewById(R.id.server_activate_btn);
//		if (mBTConnectButton == null)
//		{
//			Log.i(LOG_ID_STRING, "Error: Connect Button element not found!");
//			return false;
//		}
		
//		RelativeLayout bottomPanel = (RelativeLayout) this.findViewById(R.id.bot_panel);
//		if (bottomPanel == null)
//		{
//			Log.i(LOG_ID_STRING, "Error: Bottom Panel element not found!");
//			return false;
//		}
//		mSendField = (EditText) bottomPanel.findViewById(R.id.send_msg_field);
//		if (mSendField == null)
//		{
//			Log.i(LOG_ID_STRING, "Error: Send Field element not found!");
//			return false;
//		}
		
		LinearLayout btnPanel = (LinearLayout) this.findViewById(R.id.btn_panel);
		if (btnPanel == null)
		{
			Log.i(LOG_ID_STRING, "Error: Button Panel element not found!");
			return false;
		}
		
		mDeviceCommButton = (ImageButton) btnPanel.findViewById(R.id.toggle_device_btn);
		if (mDeviceCommButton != null) {
			mDeviceCommButton.setOnClickListener(this);
		} else {
			Log.i(LOG_ID_STRING, "Error: Device Comm Button element not found!");
			return false;
		}
		
		mServerCommButton = (ImageButton) btnPanel.findViewById(R.id.toggle_server_btn);
		if (mServerCommButton != null) {
			mServerCommButton.setOnClickListener(this);
		} else {
			Log.i(LOG_ID_STRING, "Error: Server Comm Button element not found!");
			return false;
		}
		
		mCaptureImageButton = (ImageButton) btnPanel.findViewById(R.id.capture_image_btn);
		if (mCaptureImageButton != null) {
			mCaptureImageButton.setOnClickListener(this);
		} else {
			Log.i(LOG_ID_STRING, "Error: Capture Image Button element not found!");
			return false;
		}
		
		mBTConnectButton = (ImageButton) btnPanel.findViewById(R.id.toggle_bluetooth_btn);
		if (mBTConnectButton != null) {
			mBTConnectButton.setOnClickListener(this);
		} else {
			Log.i(LOG_ID_STRING, "Error: Bluetooth Connect Button element not found!");
			return false;
		}

		if ( this.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA) == false ) {
			Log.e(LOG_ID_STRING, "Error: Device has no camera ???");
			return true;
		}
		
//		mSelectButton = (Button) btnPanel.findViewById(R.id.select_btn);
//		if (mSelectButton != null)
//		{
//			mSelectButton.setOnClickListener(this);
//		}
//		else
//		{
//			Log.i(LOG_ID_STRING, "Error: Select Button element not found!");
//			return false;
//		}
//		LinearLayout btnPanelB = (LinearLayout) bottomPanel.findViewById(R.id.btn_panel_b);
//		if (btnPanelB == null)
//		{
//			Log.i(LOG_ID_STRING, "Error: Button Panel element not found!");
//			return false;
//		}
//		
//		mSendMsgField = (EditText) btnPanelB.findViewById(R.id.send_msg_field);
//		if (mSendMsgField == null)
//		{
//			Log.i(LOG_ID_STRING, "Error: Message Field element not found!");
//			return false;
//		}
//		
//		mSendButton = (Button) btnPanelB.findViewById(R.id.custom_send_btn);
//		if (mSendButton != null)
//		{
//			mSendButton.setOnClickListener(this);
//		}
//		else
//		{
//			Log.i(LOG_ID_STRING, "Error: Custom Send Button element not found!");
//			return false;
//		}
		
		return true;
	}
	
	private void savePictureToFile(byte[] data) {
		final String root_sd = Environment.getExternalStorageDirectory().toString();
		final String LOG_ID_STRING = "[savePictureToFile]";
		
		/* Save the marker trace to a file */
		String savePath = root_sd + "/OreadPrototype";
		File saveDir = new File(savePath);
		
		if (!saveDir.exists())
		{
			saveDir.mkdirs();
		}
		
		Calendar calInstance = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));
		int hour = calInstance.get(Calendar.HOUR_OF_DAY);
		int min = calInstance.get(Calendar.MINUTE);
		int sec = calInstance.get(Calendar.SECOND);
		
		String hourStr = (hour < 10 ? "0" + Integer.toString(hour) : Integer.toString(hour));
		String minStr = (min < 10 ? "0" + Integer.toString(min) : Integer.toString(min));
		String secStr = (sec < 10 ? "0" + Integer.toString(sec) : Integer.toString(sec));
		
		File saveFile = null;

		saveFile = new File(saveDir, ("OREAD_Image_" + hourStr + minStr + secStr + ".jpg"));
//		try {
//			  saveFile = File.createTempFile(("OREAD_Image_" + hourStr + minStr + "-"), ".jpg", saveDir);
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
		
		try {
			if (!saveFile.createNewFile())
			{
				Log.e(LOG_ID_STRING, "Error: Failed to create save file!");
				return;
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		try {
			FileOutputStream saveFileStream = new FileOutputStream(saveFile);
			
			saveFileStream.write(data);
			
			saveFileStream.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		mLastImageFile = saveFile.getPath();
		
		Log.i(LOG_ID_STRING, "Saved! (see " + mLastImageFile +")");
	}
	
	private void saveDataToFile()
	{
		final String root_sd = Environment.getExternalStorageDirectory().toString();
		final String LOG_ID_STRING = "[saveDataToFile]";
		
		/* Check that the Marker Coordinates List is not null or empty */
		if (mArrayAdapter == null)
		{
			Log.e(LOG_ID_STRING, "Error: Non-existent data set!");
			return;
		}
		if (mArrayAdapter.isEmpty())
		{
			Log.e(LOG_ID_STRING, "Error: Non-existent data set!");
			return;
		}
		
		/* Save the marker trace to a file */
		String savePath = root_sd + "/OreadPrototype";
		File saveDir = new File(savePath);
		
		if (!saveDir.exists())
		{
			saveDir.mkdirs();
		}
		
		Calendar calInstance = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));
		int hour = calInstance.get(Calendar.HOUR_OF_DAY);
		int min = calInstance.get(Calendar.MINUTE);
		
		String hourStr = (hour < 10 ? "0" + Integer.toString(hour) : Integer.toString(hour));
		String minStr = (min < 10 ? "0" + Integer.toString(min) : Integer.toString(min));
		
		File saveFile = null;
		
		try {
			saveFile = File.createTempFile(("OREAD_DataReadout_" + hourStr + minStr + "-"), ".txt", saveDir);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		if (saveFile == null)
		{
			Log.e(LOG_ID_STRING, "Error: Failed to create save file!");
			return;
		}

		try {
			FileOutputStream saveFileStream = new FileOutputStream(saveFile);
			String outStr = "";
			
			for (int i = 0; i < mArrayAdapter.getCount(); i++)
			{
				outStr = mArrayAdapter.getItem(i).toString();
				saveFileStream.write(outStr.getBytes());
			}
			
			saveFileStream.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/* Internal Classes */
	private class InsertMessageToListTask extends AsyncTask<String, Void, String>
	{
		@SuppressWarnings("unused")
		private final String LOG_ID_STRING = "[InsertMessageToListTask]";

		@Override
		protected String doInBackground(String... params) {
			return params[0];
		}

		@Override
		protected void onPostExecute(String paramStr)
		{
			if (mArrayAdapter != null)
			{
				if (mArrayAdapter.getCount() >= 8) {
					mArrayAdapter.remove(mArrayAdapter.getItem(0));
				}
				
				mArrayAdapter.add(paramStr);
				logDataToFile(paramStr);
			}
			
		}
		
		private void logDataToFile(String paramStr) {
			final String root_sd = Environment.getExternalStorageDirectory().toString();
			final String LOG_ID_STRING = "[saveDataToFile]";
			
			/* Save the marker trace to a file */
			String savePath = root_sd + "/OreadPrototype";
			File saveDir = new File(savePath);
			
			if (!saveDir.exists())
			{
				saveDir.mkdirs();
			}

			Calendar calInstance = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));
			int hour = calInstance.get(Calendar.HOUR_OF_DAY);
			int min = calInstance.get(Calendar.MINUTE);
			int sec = calInstance.get(Calendar.SECOND);
			int msec = calInstance.get(Calendar.MILLISECOND);
			
			String hourStr = (hour < 10 ? "0" + Integer.toString(hour) : Integer.toString(hour));
			String minStr = (min < 10 ? "0" + Integer.toString(min) : Integer.toString(min));
			String secStr = (sec < 10 ? "0" + Integer.toString(sec) : Integer.toString(sec));
			String msecStr = "";
			
			if (msec < 10) {
				msecStr = "00" + Integer.toString(msec);
			} else if (msec < 100) {
				msecStr = "0" + Integer.toString(msec);
			} else {
				msecStr = Integer.toString(msec);
			}

			File saveFile = new File(savePath,"OREAD_LogData_" + hourStr + ".txt");
			
			if (!saveFile.exists()) {
				try {
					saveFile.createNewFile();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			try {
				FileOutputStream saveFileStream = new FileOutputStream(saveFile, true);
				saveFileStream.write(("[" + hourStr + "." + minStr + "." + secStr + "." + msecStr + "] ").getBytes());
				saveFileStream.write(paramStr.getBytes());
				saveFileStream.close();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			return;
		}
	}
	
	private boolean _shouldStopCmdProc = false;
	private String _defaultCmdSet[] = {
		"READ 0",
		"WAIT",
		"READ 1",
		"WAIT",
		"READ 2",
		"WAIT"
	};
	private int _runCmdIdx = 0;

	
	private class ProtoProcessCommandTask implements Runnable {
		private final String LOG_ID_STRING = "[ProtoProcessCommandTask]";

		@Override
		public void run() {
			PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
			PowerManager.WakeLock wakeLock = pm.newWakeLock(
					pm.SCREEN_DIM_WAKE_LOCK, "My wakelock");
			// This will make the screen and power stay on
			// This will release the wakelook after 1000 ms
			wakeLock.acquire();
			
			while (!_shouldStopCmdProc) {
				/*  Check that Bluetooth has been successfully initialized 
				 * 	and started */
				if ((!isBluetoothStarted) || (mKBluetooth == null))
				{
					Log.e(LOG_ID_STRING, "Error: Bluetooth was not properly initialized!");
					break;
				}
				
				String runCmdStr = _defaultCmdSet[_runCmdIdx];
				
				if ( runCmdStr.equals("WAIT") == false ) {
					/* Send the contents of the message field */
					mKBluetooth.broadcast(runCmdStr.getBytes());
				}
				
				try {
					Thread.sleep(mCmdExecInterval);
				} catch (InterruptedException e) {
					Log.i(LOG_ID_STRING, "Info: Cmd Processing Interrupted!");
					if (_shouldStopCmdProc) {
						break;
					}
				}
				_runCmdIdx++;
				
				if ((_runCmdIdx%_defaultCmdSet.length) == 0) {
					_runCmdIdx = 0;
				}
			}
			_runCmdIdx = 0;
			_shouldStopCmdProc = false;
			wakeLock.release();
		}
	}
	
	/**
	 * <b>TestDataSenderTask Class</b> </br>An asynchronous task for 
	 * sending data through an active connection 
	 */
	private class TestDataSenderTask extends AsyncTask<Void, Void, Void>
	{
		private final String LOG_ID_STRING = "[TestDataSenderTask]";
		
		@Override
		protected Void doInBackground(Void... params) {
			Log.i(LOG_ID_STRING, "Info: Started.");
			
			/* Attempt to initialize the Web Interface */
			if ( initializeWebInterface(mDataServerUrl, mImageServerUrl) == false ) {
				Log.e(LOG_ID_STRING, "ERROR: Failed to initialize Web Interface!");
				return null;
			}
			
			if (mWebInterface == null) {
				Log.e(LOG_ID_STRING, "Error: Web Interface Unavailable!");
				return null;
			}
			
			/* Upload sensor data if available */
			if (_bIsSensorDataAvailable == true) {
				if (uploadSensorData() != STATUS_OK) {
					return null;
				}
				_bIsSensorDataAvailable = false;
			}
			
			if (_bIsImageDataAvailable == true) {
				uploadImageData();
				_bIsImageDataAvailable = false;
			}
			
			/* Wait for a few milliseconds before starting the next transmission */
			try
			{
				Thread.sleep(TRANSMIT_INTERVAL);
			}
			catch (InterruptedException e)
			{
				Log.w(LOG_ID_STRING, "Warning: Thread Sleep Interrupted.");
			}
			
			mSenderTask = null;
			
			Log.i(LOG_ID_STRING, "Info: Finished.");
			
			return null;
		}
		
		private int uploadSensorData() {
			/* Load the data to be sent */
			/* Fudge the data to be sent (for testing purposes only) */
//			if (mSensorData == null) {
//				mSensorData = RandomSensorDataFactory.createType1SensorData();
//			}
			
			/* Transmit the data through the Web Interface */
			String jsonStr = Type1SensorDataEncoder.encodeToJSON(mSensorData);
			
			if (jsonStr.equals("") == false) {
				if (mWebInterface.sendJSONData("reportData", jsonStr))
				{
					Log.i(LOG_ID_STRING, "Info: JSON data sent successfully.");
				}
				if (mWebInterface.finishSending())
				{
					Log.i(LOG_ID_STRING, "Info: Message successfully closed.");
					mWebInterface = null;
				}
			}
			else
			{
				Log.w(LOG_ID_STRING, "Warning: Did not send blank JSON data.");
			}
			
			return STATUS_OK;
		}

		private int uploadImageData() {
			
			/* TODO: Load the data to be sent */
			/* Create the data to be sent (for testing purposes only) */
//			if (mSensorData == null) {
//				mSensorData = RandomSensorDataFactory.createType1SensorData();
//			}
			
			/* Transmit the data through the Web Interface */
			String jsonStr = "{\"origin\":1, \"message\":\"phone picture\", \"recordStatus\":\"ok\", \"dateRecorded\":" + System.currentTimeMillis() + "}\r\n";
			
			if (jsonStr.equals("") == false) {
				if (mWebInterface == null) {
					return STATUS_FAILED;
				}
				
				if (mWebInterface.sendFileData("imageData", jsonStr, mLastImageFile)); {
					Log.i(LOG_ID_STRING, "Info: JSON data sent successfully.");
				}
				if (mWebInterface.finishSending())
				{
					Log.i(LOG_ID_STRING, "Info: Message successfully closed.");
					mWebInterface = null;
				}
			}
			else
			{
				Log.w(LOG_ID_STRING, "Warning: Did not send blank JSON data.");
			}
			
			return STATUS_OK;
		}

		@Override
		protected void onPostExecute(Void params)
		{
			/* Check if our old sender task is already null */
			if (mSenderTask == null)
			{
				/* Check if we should begin a new sender task */
				if (shouldStopTasks == false)
				{
					mSenderTask = new TestDataSenderTask();
					mSenderTask.execute();
					
					Log.i(LOG_ID_STRING, "Info: New sender task created.");
				}
				else
				{
					/* Reset the shouldStopTasks boolean */
					shouldStopTasks = false;
				}
			}
			else
			{
				mSenderTask = null;
				
				/* Reset the shouldStopTasks boolean */
				shouldStopTasks = false;
				 
				Log.e(LOG_ID_STRING, "Error: Old sender task has not been properly closed!");
			}
		}
	}
}
