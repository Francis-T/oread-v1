package net.oukranos.oreadv1;

import net.oukranos.oreadv1.fragments.FragmentCalibration;
import net.oukranos.oreadv1.fragments.FragmentGuidedCalibration;
import net.oukranos.oreadv1.fragments.FragmentLogs;
import net.oukranos.oreadv1.fragments.FragmentReadings;
import net.oukranos.oreadv1.interfaces.OreadServiceApi;
import net.oukranos.oreadv1.interfaces.OreadServiceListener;
import net.oukranos.oreadv1.types.Status;
import net.oukranos.oreadv1.util.OreadLogger;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class MainActivity extends Activity {
	/* Get an instance of the OreadLogger class to handle logging */
	private static final OreadLogger OLog = OreadLogger.getInstance();
	
	private FragmentCalibration _calibFragment = null;
	private FragmentReadings _readFragment = null;
	private FragmentLogs _logFragment = null;
	private FragmentGuidedCalibration _guidedCalibFragment = null;
	OreadFragment _currentFragment = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_main);
		

		/* Start the service */
		Intent oreadServiceIntent = new Intent(OreadService.class.getName());
		ComponentName cn = startService(oreadServiceIntent);
		if (cn == null) {
			OLog.err("Failed to start service: " + OreadService.class.getName());
		} else {
			OLog.info("Started service: " + cn.getShortClassName());
		}
		
		/* Setup the swipe listener */
		View view = this.findViewById(R.id.main_activity_layout);
		final GestureDetector gestureDetect = new GestureDetector(this, new SwipeListener());
		view.setOnTouchListener( new OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				// TODO Auto-generated method stub
				if (gestureDetect.onTouchEvent(event)) {
					return false;
				}
				return true;
			}
		});
		
		if ( this.establishServiceConnection() == Status.FAILED ) {
			OLog.err("Failed to bind to the service");
			
			_serviceConn = null;
			_listener = null;
			
			this.finish();
			return;
		} else {
			OLog.info("onCreate successful");
		}
		
		return;
	}
	
	@Override
	public void onResume() {
		super.onResume();

		if ( this.establishServiceConnection() == Status.FAILED ) {
			OLog.err("Failed to bind to the service");
			
			_serviceConn = null;
			_listener = null;
			
			this.finish();
			return;
		}
		
		if ( this.loadFragment(OreadFragment.READING) != Status.OK ) {
			OLog.err("Failed to load fragment");
			return;
		}
		
		return;
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		this.releaseServiceConnection();
		
		return;
	}

	/**********************************************************************/
	/**  Private Methods                                                 **/
	/**********************************************************************/
	private Status loadFragment(OreadFragment id) {
		FragmentManager fm = getFragmentManager();
		
		FragmentTransaction ft = fm.beginTransaction();
		
		switch (id) {
			case LOGGING:
				if (_logFragment == null) {
					_logFragment = new FragmentLogs();
				}
				
				if (_logFragment.getServiceHandle() == null) {
					_logFragment.setServiceHandle(_serviceAPI);
				}
				
				ft.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right);
				ft.replace(R.id.placeholder, _logFragment, "YEAAART");
				_currentFragment = OreadFragment.LOGGING;
				
				break;
			case READING:
				if (_readFragment == null) {
					_readFragment = new FragmentReadings();
				}
				
				if (_readFragment.getServiceHandle() == null) {
					_readFragment.setServiceHandle(_serviceAPI);
				}
				
				ft.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right);
				if (_currentFragment == OreadFragment.CALIBRATION) {
					ft.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right);
				} else {
					ft.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left);
				}
				ft.replace(R.id.placeholder, _readFragment, "YEAAART");
				_currentFragment = OreadFragment.READING;
				
				break;
			case CALIBRATION:
				if (_calibFragment == null) {
					_calibFragment = new FragmentCalibration();
				}
				
				if (_calibFragment.getServiceHandle() == null) {
					_calibFragment.setServiceHandle(_serviceAPI);
				}
				if (_currentFragment == OreadFragment.GUIDED_CALIBRATION) {
					ft.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right);
				} else {
					ft.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left);
				}
				ft.replace(R.id.placeholder, _calibFragment, "YEAAART");
				_currentFragment = OreadFragment.CALIBRATION;
				
				break;
			case GUIDED_CALIBRATION:
				if (_guidedCalibFragment == null) {
					_guidedCalibFragment = new FragmentGuidedCalibration();
				}
				
				if (_guidedCalibFragment.getServiceHandle() == null) {
					_guidedCalibFragment.setServiceHandle(_serviceAPI);
				}
				
				ft.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left);
				ft.replace(R.id.placeholder, _guidedCalibFragment, "YEAAART");
				_currentFragment = OreadFragment.GUIDED_CALIBRATION;
				
				break;
			default:
				break;
		}
		
		ft.commit();
		
		return Status.OK;
	}
	
	/**********************************************************************/
	/**  Private Inner Classes                                           **/
	/**********************************************************************/
	private class SwipeListener extends SimpleOnGestureListener {
		private static final int THRESHOLD_SWIPE_DISTANCE = 100;
		
		@Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
                float velocityY) {
			float startX = e1.getX();
			float endX = e2.getX();
			float dist = getAbsDistance(startX, endX);
			
			// Rightward swipe
			if ( ( startX > endX ) && (dist > THRESHOLD_SWIPE_DISTANCE) ) {
				OreadFragment fragId = getNextFragment(_currentFragment);
				if (fragId != null) {
					loadFragment(fragId);
				}
			}
			
			// Leftward swipe
			if ( ( startX < endX ) && (dist > THRESHOLD_SWIPE_DISTANCE) ) {
				OreadFragment fragId = getPrevFragment(_currentFragment);
				if (fragId != null) {
					loadFragment(fragId);
				}
			}
			
			
        	return super.onFling(e1, e2, velocityX, velocityY);
        }
		
		private float getAbsDistance(float x1, float x2) {
			return (float) Math.sqrt((x1-x2)*(x1-x2));
		}
	}
	
	private OreadFragment getPrevFragment(OreadFragment current) {
		switch (current) {
			case READING:
				return OreadFragment.LOGGING;
			case CALIBRATION:
				return OreadFragment.READING;
			case GUIDED_CALIBRATION:
				return OreadFragment.CALIBRATION;
			default:
				break;
		}
		
		return OreadFragment.UNKNOWN;
	}
	
	private OreadFragment getNextFragment(OreadFragment current) {
		switch (current) {
			case LOGGING:
				return OreadFragment.READING;
			case READING:
				return OreadFragment.CALIBRATION;
			case CALIBRATION:
				return OreadFragment.GUIDED_CALIBRATION;
			default:
				break;
		}
		
		return OreadFragment.UNKNOWN;
	}
	
	/**********************************************************************/
	/**  Private API Endpoint Methods                                    **/
	/**********************************************************************/
	
	/**
	 * Binds to the OreadService if not yet bound or the previous bindings
	 * have been severed (e.g. through unloading of the MainActivity etc.)
	 * @return the Status
	 */
	private Status establishServiceConnection() {
		Intent oreadServiceIntent = new Intent(OreadService.class.getName());
		if (bindService(oreadServiceIntent, _serviceConn, 0) == false) {
			return Status.FAILED;
		}

		if (_calibFragment != null) {
			if (_calibFragment.getServiceHandle() == null) {
				_calibFragment.setServiceHandle(_serviceAPI);
			}
		}

		if (_readFragment != null) {
			if (_readFragment.getServiceHandle() == null) {
				_readFragment.setServiceHandle(_serviceAPI);
			}
		}
		
		return Status.OK;
	}

	/**
	 * Releases the service connection
	 * @return the Status
	 */
	private void releaseServiceConnection() {
		if ( this.establishServiceConnection() == Status.FAILED ) {
			OLog.err("Failed to bind to the service");
			
			_serviceConn = null;
			_listener = null;
			
			this.finish();
			return;
		}
		
		if (_serviceAPI != null) {
//			try {
//				_serviceAPI.stop();
//			} catch (RemoteException e) {
//				e.printStackTrace();
//			}
			
			if (_listener != null) {
				try {
					_serviceAPI.removeListener(_listener);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}
			
			_serviceAPI = null;
		}
		
		if (_serviceConn != null) {
			unbindService(_serviceConn);
			_serviceConn = null;
		}
		
		_listener = null;
		
		return;
	}
	
	/**********************************************************************/
	/**  API endpoint                                                    **/
	/**********************************************************************/
	private OreadServiceApi _serviceAPI = null;
	/**
	 * This inner class provides a cleaner interface for the listener stub used
	 * with the OreadService
	 */
	private OreadServiceListener.Stub _listener = new OreadServiceListener.Stub() {
		@Override
		public void handleWaterQualityData() throws RemoteException {
			return;
		}
	};
	
	private ServiceConnection _serviceConn = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			OLog.info("Connected to service");
			_serviceAPI = OreadServiceApi.Stub.asInterface(service);
			
			if (_calibFragment != null) {
				_calibFragment.setServiceHandle(_serviceAPI);
			}

			if (_readFragment != null) {
				_readFragment.setServiceHandle(_serviceAPI);
			}
			
			try {
				_serviceAPI.addListener(_listener);
			} catch (RemoteException e) {
				OLog.err("Failed to connect to service");
			}
			
			// TODO
		}
		
		@Override
		public void onServiceDisconnected(ComponentName name) {
			OLog.info("Disconnected from service");
			
		}
	};
	
	public enum OreadFragment {
		UNKNOWN,
		LOGGING,
		READING,
		CALIBRATION,
		GUIDED_CALIBRATION
	}
	
}
