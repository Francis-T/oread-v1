package net.oukranos.oreadv1.fragments;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import net.oukranos.oreadv1.R;
import net.oukranos.oreadv1.interfaces.OreadServiceApi;
import net.oukranos.oreadv1.interfaces.OreadServiceListener;
import net.oukranos.oreadv1.types.CalibDataConfig;
import net.oukranos.oreadv1.types.CalibrationData;
import net.oukranos.oreadv1.types.WaterQualityData;
import net.oukranos.oreadv1.util.OreadLogger;
import android.annotation.TargetApi;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.RemoteException;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class FragmentCalibration extends Fragment implements OnItemSelectedListener {
	/* Get an instance of the OreadLogger class to handle logging */
	private static final OreadLogger OLog = OreadLogger.getInstance();
	
	private final String root_sd = Environment.getExternalStorageDirectory().toString();
	private final String loadPath = root_sd + "/OreadPrototype/";
	
	private View _viewRef = null;
	private OreadServiceApi _serviceAPI = null;
	private CalibrationData _calibData = null;
	private CalibDataConfig _calibDataConfig = null;
	private boolean _isListening = false;
	
	private long _lastChange = 0;
	private long _readStart = 0;
	@SuppressWarnings("unused")
	private double _currentValue = 0.0;
	private String _calibParamStr = "";
	
	private Spinner spn_calibMode = null;
	private TextView lbl_instrHead = null;
	private TextView lbl_instrBody = null;
	private TextView lbl_sensRead = null;
	private TextView lbl_variance = null;
	private TextView lbl_respTime = null;
	private TextView lbl_calibParam = null;
	private TextView lbl_totalTime = null;
	
	public FragmentCalibration() {
		_calibDataConfig = CalibDataConfig.getInstance(loadPath + "calib-config.xml");
		return;
	}
	
	public FragmentCalibration(OreadServiceApi api) {
		_calibDataConfig = CalibDataConfig.getInstance("calib-config.xml");
		this._serviceAPI = api;
		return;
	}
	
	public void setServiceHandle(OreadServiceApi api) {
		this._serviceAPI = api;
		return;
	}

	@Override
	public void onDestroyView() {
		// TODO Auto-generated method stub
		if (this._serviceAPI != null) {
			if (this._isListening) {
				try {
					_serviceAPI.removeListener(_listener);
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		
		super.onDestroyView();
	}

	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		if (container == null) {
			// We have different layouts, and in one of them this
			// fragment's containing frame doesn't exist. The fragment
			// may still be created from its saved state, but there is
			// no reason to try to create its view hierarchy because it
			// won't be displayed. Note this is not needed -- we could
			// just run the code below, where we would create and return
			// the view hierarchy; it would just never be used.
			return null;
		}
		_viewRef = inflater.inflate(R.layout.frag_calib_instr_layout, container, false);
		
		Button btn_calibrate = (Button) _viewRef.findViewById(R.id.btn_confirm_calib);
		btn_calibrate.setOnClickListener( new View.OnClickListener() {
				@Override
				public void onClick(View arg0) {
					if (_serviceAPI != null) {
						try {
							/* Build the calibration string part by part */
							String calibStr = _calibData.getCommand();
							calibStr += "?";
							calibStr += _calibData.getParamPrefix();
							if (_calibData.shouldAllowParams()) {
								calibStr += ",";
								calibStr += _calibParamStr;
							}
							
							_serviceAPI.runCommand("system.main.runTask", calibStr);
							
						} catch (RemoteException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
			}
		);
		
		Button btn_conn = (Button) _viewRef.findViewById(R.id.btn_connect);
		btn_conn.setOnClickListener( new View.OnClickListener() {
				@Override
				public void onClick(View arg0) {
					if (_serviceAPI != null) {
						try {
							_serviceAPI.runCommand("system.main.initSubControllers", "");
							_serviceAPI.runCommand("system.main.runTask", "comm.bluetooth.start");
							_serviceAPI.runCommand("system.main.runTask", "comm.bluetooth.connectByName?HC-05");
							_serviceAPI.runCommand("system.main.runTask", "sensors.water_quality.start");
						} catch (RemoteException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
			}
		);
		
		Button btn_startRead = (Button) _viewRef.findViewById(R.id.btn_cont_read_start);
		btn_startRead.setOnClickListener( new View.OnClickListener() {
				@Override
				public void onClick(View arg0) {
					if (_serviceAPI != null) {
						try {
							if (_isListening == false) {
								_serviceAPI.addListener(_listener);
								
								new RetrieveDataTask().execute();
								
								_isListening = true;
								_readStart = System.currentTimeMillis();
								_lastChange = System.currentTimeMillis();
								if (previousValues != null) {
									previousValues.clear();
								}
							}
						} catch (RemoteException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
			}
		);
		
		Button btn_stopRead = (Button) _viewRef.findViewById(R.id.btn_cont_read_stop);
		btn_stopRead.setOnClickListener( new View.OnClickListener() {
				@Override
				public void onClick(View arg0) {
					if (_serviceAPI != null) {
						try {
							_serviceAPI.removeListener(_listener);
							_isListening = false;
						} catch (RemoteException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
			}
		);
		
		Button btn_disc = (Button) _viewRef.findViewById(R.id.btn_disconnect);
		btn_disc.setOnClickListener( new View.OnClickListener() {
				@Override
				public void onClick(View arg0) {
					if (_serviceAPI != null) {
						try {
							_serviceAPI.runCommand("system.main.destSubControllers", "");
						} catch (RemoteException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
			}
		);
		
		Button btn_options = (Button) _viewRef.findViewById(R.id.btn_opts);
		btn_options.setOnClickListener( new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {

				/* Create an AlertDialog allowing the user to change the calibration value */
				final EditText calibParamInput = new EditText(v.getContext());
				
				calibParamInput.setTextSize(14);
				
				final LinearLayout textPanel = new LinearLayout(v.getContext());
				textPanel.setOrientation(LinearLayout.VERTICAL);
				textPanel.addView(calibParamInput);
				
				new AlertDialog.Builder(v.getContext())
			    .setTitle("Set the calibration value")
			    .setMessage("Enter the calibration value:")
			    .setView(textPanel)
			    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
			        public void onClick(DialogInterface dialog, int whichButton) {
			        	_calibParamStr = calibParamInput.getText().toString();
			        	if (lbl_calibParam != null) {
			        		lbl_calibParam.setText(_calibParamStr);
			        	}
			        }
			    }).setNegativeButton("Clear", new DialogInterface.OnClickListener() {
			        public void onClick(DialogInterface dialog, int whichButton) {
			        	_calibParamStr = "";
			        }
			    }).show();
				
			}
		} );
		
		lbl_instrHead = (TextView) _viewRef.findViewById(R.id.lbl_instr_head);
		lbl_instrBody = (TextView) _viewRef.findViewById(R.id.lbl_instr_body);
		lbl_sensRead = (TextView) _viewRef.findViewById(R.id.txt_value);
		lbl_variance = (TextView) _viewRef.findViewById(R.id.txt_shift);
		lbl_respTime = (TextView) _viewRef.findViewById(R.id.txt_resp_time);
		lbl_totalTime = (TextView) _viewRef.findViewById(R.id.txt_total_time);
		lbl_calibParam = (TextView) _viewRef.findViewById(R.id.txt_calib_param);
		
		spn_calibMode = (Spinner) _viewRef.findViewById(R.id.spn_calib_mode);
		spn_calibMode.setOnItemSelectedListener(this);
		
		return _viewRef;
	}

	@Override
	public void onItemSelected(AdapterView<?> parent, View view,
			int position, long id) {
		// TODO Auto-generated method stub
		if (position == 0) {
			return;
		}
		
		String data = (String) spn_calibMode.getItemAtPosition(position);
		if (_calibDataConfig != null) {
			_calibData = _calibDataConfig.getMatch(data);
		} else {
			_calibData = new CalibrationData("Unknown", "Unknown", "Unknown");
		}
		
		/* Clear the list of previously obtained values */
		if (previousValues != null) {
			previousValues.clear();
		}

		if (_calibData != null) {
			lbl_instrHead.setText(_calibData.getTitle());
			lbl_instrBody.setText(_calibData.getInstructions());
		}
		
		//_viewRef.findViewById(R.id.fragment_calib_layout).invalidate();
	}

	@Override
	public void onNothingSelected(AdapterView<?> parent) {
		// TODO Auto-generated method stub
		
	}
	
	private void storeReadValue(double d) {
		if (previousValues != null) {
			
			/* Remove the oldest value if we would exceed 20 items */
			if (previousValues.size() + 1 > 10) {
				previousValues.remove(0);
			}
			
			previousValues.add(d);
		}
		
		return;
	}
	
	private double getVariance() {
		double average = 0.0;
		double total = previousValues.size();
		
		for (double d : previousValues) {
			average += d;
		}
		
		if (total > 0) {
			average /= total;
			Log.d("TEST", "average: " + Double.toString(average));
		} else {
			return -1.0;
		}
		
		double variance = 0.0;

		for (double d : previousValues) {
			variance += (d - average)*(d - average);
		}
		
		if (total > 0) {
			Log.d("TEST", "variance: " + Double.toString(variance));
			variance /= total;
		} else {
			return -1.0;
		}
		
		return variance;
	}

	/**
	 * This inner class provides a cleaner interface for the listener stub used
	 * with the OreadService
	 */
	WaterQualityData d = null;
	List<Double> previousValues = new ArrayList<Double>();
	private OreadServiceListener.Stub _listener = new OreadServiceListener.Stub() {
		@Override
		public void handleWaterQualityData() throws RemoteException {
			OLog.info("Handling Water Quality Data from fragment...");
			d = null;

			if (_serviceAPI == null) {
				return;
			}
			
			try {
				d = _serviceAPI.getData();
			} catch (RemoteException e) {
				OLog.err("Failed to get data");
			}
			
			OLog.info("SensorData size: ");
			OLog.info(  "   pH: " + d.pH +
						"  DO2: " + d.dissolved_oxygen +
						" COND: " + d.conductivity + 
						" TEMP: " + d.temperature + 
						"  TDS: " + d.tds + 
						"  SAL: " + d.salinity );
			
			new AsyncTask<Void, Void, Void>() {
				@Override
				protected Void doInBackground(Void... params) {
					return null;
				}
				
				@Override
				protected void onPostExecute(Void params) {
					if (d == null) {
						OLog.err("Water Quality Data Unavailable");
						return;
					}

					final double pH_swingRange = 0.1;
					final double do_swingRange = 0.1;
					final double ec_swingRange = 100;
					final double tm_swingRange = 0.1;
					double calcVariance = 0.0;
					
					if (_calibData != null) {
						String sensValStr = "";
						if (_calibData.getSensor().equals("pH")) {
							sensValStr = Double.toString(d.pH); 
							
							lbl_sensRead.setText(sensValStr);

							_currentValue = d.pH;
							storeReadValue(d.pH);
							calcVariance = getVariance();
							if ( calcVariance > pH_swingRange ) {
								_lastChange = System.currentTimeMillis();
							}
							
							logToFile("pH: " + sensValStr);
							
						} else if (_calibData.getSensor().equals("DO2")) {
							sensValStr = Double.toString(d.dissolved_oxygen) + " " + _calibData.getUnits(); 
							
							lbl_sensRead.setText(sensValStr);

							_currentValue = d.dissolved_oxygen;
							storeReadValue(d.dissolved_oxygen);
							calcVariance = getVariance();
							if ( calcVariance > do_swingRange ) {
								_lastChange = System.currentTimeMillis();
							}
							
							logToFile("DO: " + sensValStr);
							
						} else if (_calibData.getSensor().equals("EC")) {
							sensValStr = Double.toString(d.conductivity) + " " + _calibData.getUnits(); 
							
							lbl_sensRead.setText(sensValStr);

							_currentValue = d.conductivity;
							storeReadValue(d.conductivity);
							calcVariance = getVariance();
							if ( calcVariance > ec_swingRange ) {
								_lastChange = System.currentTimeMillis();
							}
							
							logToFile("EC: " + sensValStr);
							
						} else if (_calibData.getSensor().equals("TEMP")) {
							sensValStr = Double.toString(d.temperature) + " " + _calibData.getUnits(); 
							
							lbl_sensRead.setText(sensValStr);
							
							_currentValue = d.temperature;
							storeReadValue(d.temperature);
							calcVariance = getVariance();
							if ( calcVariance > tm_swingRange ) {
								_lastChange = System.currentTimeMillis();
							}
							
							logToFile(sensValStr);
						}

						Calendar cal = Calendar.getInstance();
						
						lbl_variance.setText( Float.toString((float) calcVariance) + 
								" " + _calibData.getUnits() );
						
						cal.setTimeInMillis( System.currentTimeMillis() - _readStart );

						int min = cal.get(Calendar.MINUTE);
						int secs = cal.get(Calendar.SECOND);
						String minStr = (min > 9 ? "" : "0") + Integer.toString(min);
						String secStr = (secs > 9 ? "" : "0") + Integer.toString(secs);
						
						lbl_totalTime.setText( minStr + ":" + secStr );
						
						cal.setTimeInMillis( _lastChange - _readStart );

						min = cal.get(Calendar.MINUTE);
						secs = cal.get(Calendar.SECOND);
						minStr = (min > 9 ? "" : "0") + Integer.toString(min);
						secStr = (secs > 9 ? "" : "0") + Integer.toString(secs);
						
						lbl_respTime.setText( minStr + ":" + secStr );
					}
					
					OLog.info("Updated UI");
					return;
				}
			}.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
			
			return;
		}
	};
	
	private class RetrieveDataTask extends AsyncTask<Void, Void, Void> {

		@Override
		protected Void doInBackground(Void... params) {
			while (_isListening) {
				try {
					if (_calibData != null) {
						if (_calibData.getSensor().equals("pH")) {
							_serviceAPI.runCommand("system.main.runTask", "sensors.water_quality.readPH");
						} else if (_calibData.getSensor().equals("DO2")) {
							_serviceAPI.runCommand("system.main.runTask", "sensors.water_quality.readDO");
						} else if (_calibData.getSensor().equals("EC")) {
							_serviceAPI.runCommand("system.main.runTask", "sensors.water_quality.readEC");
						} else if (_calibData.getSensor().equals("TEMP")) {
							_serviceAPI.runCommand("system.main.runTask", "sensors.water_quality.readTM");
						}
					}
					_serviceAPI.runCommand("system.main.wait", "500");
					_serviceAPI.runCommand("system.main.receiveData", "");
					try {
						Thread.sleep(1500);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			// TODO Auto-generated method stub
			super.onPostExecute(result);
		}
		
	}
	

	public void logToFile(String message) {
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
		saveFile = new File(saveDir, ("OREAD_Readings.txt"));
		
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

	public OreadServiceApi getServiceHandle() {
		return this._serviceAPI;
	}
	
}
