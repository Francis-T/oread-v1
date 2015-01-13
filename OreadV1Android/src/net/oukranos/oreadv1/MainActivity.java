package net.oukranos.oreadv1;

import java.util.ArrayList;
import java.util.List;

import net.oukranos.oreadv1.controller.MainController;
import net.oukranos.oreadv1.interfaces.MainControllerEventHandler;
import net.oukranos.oreadv1.types.ControllerState;
import net.oukranos.oreadv1.types.Status;
import net.oukranos.oreadv1.types.WaterQualityData;
import net.oukranos.oreadv1.util.OLog;
import net.oukranos.oreadv1.util.SystemUiHider;

import android.annotation.TargetApi;
import android.app.Activity;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ListView;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 * 
 * @see SystemUiHider
 */
public class MainActivity extends Activity implements MainControllerEventHandler {
	/**
	 * Whether or not the system UI should be auto-hidden after
	 * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
	 */
	private static final boolean AUTO_HIDE = true;

	/**
	 * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
	 * user interaction before hiding the system UI.
	 */
	private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

	/**
	 * If set, will toggle the system UI visibility upon interaction. Otherwise,
	 * will show the system UI visibility upon interaction.
	 */
	private static final boolean TOGGLE_ON_CLICK = true;

	/**
	 * The flags to pass to {@link SystemUiHider#getInstance}.
	 */
	private static final int HIDER_FLAGS = SystemUiHider.FLAG_HIDE_NAVIGATION;

	/**
	 * The instance of the {@link SystemUiHider} for this activity.
	 */
	private SystemUiHider mSystemUiHider;
	
	private MainController _mainController = null;
	private PullDataTask _pullDataTask = null;
	private SensorDataAdapter _sensorDataAdapter = null;
	private List<WaterQualityData> _sensorData = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		if (_mainController == null) {
			_mainController = MainController.getInstance(this);
			_mainController.setEventHandler(this);
			_mainController.initialize();
		}

		setContentView(R.layout.activity_main);

		final View controlsView = findViewById(R.id.fullscreen_content_controls);
		final View contentView = findViewById(R.id.fullscreen_content);
		final View listView = contentView.findViewById(R.id.sensor_data_list);
		
		if (listView == null) {
			OLog.warn("ListView is NULL");
			_mainController.destroy();
			_mainController = null;
			return;
		}
		
		_sensorData = new ArrayList<WaterQualityData>();

		// Set up an instance of SystemUiHider to control the system UI for
		// this activity.
		mSystemUiHider = SystemUiHider.getInstance(this, contentView,
				HIDER_FLAGS);
		mSystemUiHider.setup();
		mSystemUiHider
				.setOnVisibilityChangeListener(new SystemUiHider.OnVisibilityChangeListener() {
					// Cached values.
					int mControlsHeight;
					int mShortAnimTime;

					@Override
					@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
					public void onVisibilityChange(boolean visible) {
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
							// If the ViewPropertyAnimator API is available
							// (Honeycomb MR2 and later), use it to animate the
							// in-layout UI controls at the bottom of the
							// screen.
							if (mControlsHeight == 0) {
								mControlsHeight = controlsView.getHeight();
							}
							if (mShortAnimTime == 0) {
								mShortAnimTime = getResources().getInteger(
										android.R.integer.config_shortAnimTime);
							}
							controlsView
									.animate()
									.translationY(visible ? 0 : mControlsHeight)
									.setDuration(mShortAnimTime);
						} else {
							// If the ViewPropertyAnimator APIs aren't
							// available, simply show or hide the in-layout UI
							// controls.
							controlsView.setVisibility(visible ? View.VISIBLE
									: View.GONE);
						}

						if (visible && AUTO_HIDE) {
							// Schedule a hide().
							delayedHide(AUTO_HIDE_DELAY_MILLIS);
						}
					}
				});

		// Set up the user interaction to manually show or hide the system UI.
		contentView.setOnLongClickListener(new View.OnLongClickListener() {
			@Override
			public boolean onLongClick(View view) {
				if (TOGGLE_ON_CLICK) {
					mSystemUiHider.toggle();
				} else {
					mSystemUiHider.show();
				}
				return false;
			}
		});
		
		_sensorDataAdapter = new SensorDataAdapter(this, _sensorData);
		((ListView) listView).setAdapter(_sensorDataAdapter);
		OLog.info("Adapter set");

		// Upon interacting with UI controls, delay any scheduled hide()
		// operations to prevent the jarring behavior of controls going away
		// while interacting with the UI.
		findViewById(R.id.start_button).setOnTouchListener(mDelayHideTouchListener);
		findViewById(R.id.stop_button).setOnTouchListener(mDelayHideTouchListener);
		
		findViewById(R.id.start_button).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (_mainController.getState() == ControllerState.UNKNOWN) {
					_mainController.start();
				}
			}
		});

		findViewById(R.id.stop_button).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (_mainController.getState() == ControllerState.READY) {
					_mainController.stop();
				}
			}
		});
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);

		// Trigger the initial hide() shortly after the activity has been
		// created, to briefly hint to the user that UI controls
		// are available.
		delayedHide(100);
		
		return;	
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		if (_mainController == null) {
			_mainController = MainController.getInstance(this);
			_mainController.initialize();
		}
		
		return;
	}
	
	@Override
	protected void onPause() {
		if (_mainController.getState() == ControllerState.READY) {
			_mainController.stop();
		}
		super.onPause();
		return;
	}
	
	@Override
	protected void onStop() {
		if (_mainController != null) {
			if (_mainController.getState() == ControllerState.READY) {
				_mainController.stop();
			}
			_mainController.destroy();
		}
		
		super.onStop();
		return;
	}

	@Override
	public void onDataAvailable() {
		/* Start a pull data task */
		if (_pullDataTask != null) {
			OLog.err("An old pull data task still exists");
			return;
		}
		
		_pullDataTask = new PullDataTask();
		_pullDataTask.execute();
		
		return;
	}

	/**
	 * Touch listener to use for in-layout UI controls to delay hiding the
	 * system UI. This is to prevent the jarring behavior of controls going away
	 * while interacting with activity UI.
	 */
	View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
		@Override
		public boolean onTouch(View view, MotionEvent motionEvent) {
			if (AUTO_HIDE) {
				delayedHide(AUTO_HIDE_DELAY_MILLIS);
			}
			return false;
		}
	};

	Handler mHideHandler = new Handler();
	Runnable mHideRunnable = new Runnable() {
		@Override
		public void run() {
			mSystemUiHider.hide();
		}
	};

	/**
	 * Schedules a call to hide() in [delay] milliseconds, canceling any
	 * previously scheduled calls.
	 */
	private void delayedHide(int delayMillis) {
		mHideHandler.removeCallbacks(mHideRunnable);
		mHideHandler.postDelayed(mHideRunnable, delayMillis);
	}
	
	/* Inner classes */
	private class PullDataTask extends AsyncTask<Void, Void, TaskStatus> {

		@Override
		protected TaskStatus doInBackground(Void... params) {
			OLog.info("Pull data task started.");
			if (_mainController != null) {
				if (_sensorData == null) {
					OLog.err("Sensor data list is null");
					return TaskStatus.FAILED;
				}
				
				if (_sensorDataAdapter == null) {
					OLog.err("Sensor data adapter is null");
					return TaskStatus.FAILED;
				}
				
				/* Pull data from the MainController */
				WaterQualityData data = new WaterQualityData(0); // TODO This ID should be a variable instead
				if ( _mainController.getData(data) != net.oukranos.oreadv1.types.Status.OK) {
					OLog.err("Failed to pull data");
					return TaskStatus.FAILED;
				}
				_sensorData.add(data);
				
				
				if (_sensorData.size() > 5) {
					/* If we have more than ten elements in the list, discard the first element */ 
					_sensorData.remove(0);
				}
				
				WaterQualityData d = _sensorData.get(_sensorData.size()-1);
				OLog.info("SensorData size: " + _sensorData.size());
				OLog.info(  "   pH: " + d.pH +
							"  DO2: " + d.dissolved_oxygen +
							" COND: " + d.conductivity + 
							" TEMP: " + d.temperature);
			}
			
			if (_pullDataTask != null) {
				_pullDataTask = null;
			}

			OLog.info("Pull data task finished.");
			return TaskStatus.OK;
		}
		
		protected void onPostExecute(TaskStatus result) {
			if (result == TaskStatus.OK) {
				if (_sensorDataAdapter != null) {
					_sensorDataAdapter.notifyDataSetChanged();
				}
			}
		}
		
	}
	
	private enum TaskStatus {
		UNKNOWN, OK, ALREADY_STARTED, FAILED
	}
}
