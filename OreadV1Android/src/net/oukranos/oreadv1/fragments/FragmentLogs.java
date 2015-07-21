package net.oukranos.oreadv1.fragments;

import net.oukranos.oreadv1.R;
import net.oukranos.oreadv1.interfaces.OreadServiceApi;
import net.oukranos.oreadv1.interfaces.OreadServiceListener;
import net.oukranos.oreadv1.types.ControllerState;
import net.oukranos.oreadv1.types.OreadServiceControllerStatus;
import net.oukranos.oreadv1.util.OreadLogger;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.View.OnTouchListener;
import android.widget.TextView;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class FragmentLogs extends Fragment {
	/* Get an instance of the OreadLogger class to handle logging */
	private static final OreadLogger OLog = OreadLogger.getInstance();
	
	private View _viewRef = null;
	private OreadServiceApi _serviceAPI = null;
	
	private LogFragmentSvcListener _listener = null;
	private Activity _parent = null;
	private TextView _logView = null;
	
	public FragmentLogs() {
		return;
	}
	
	public FragmentLogs(OreadServiceApi api) {
		this._serviceAPI = api;
		return;
	}

	@Override
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
		_viewRef = inflater.inflate(R.layout.frag_logs, container, false);

		_parent = this.getActivity();

		/* Setup the double tap listener */
		final GestureDetector gestureDetect = 
				new GestureDetector(_parent, new DoubleTapListener());
		
		_logView = (TextView) _viewRef.findViewById(R.id.txt_log_full);
		_logView.setOnTouchListener( new OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				// TODO Auto-generated method stub
				if (gestureDetect.onTouchEvent(event)) {
					return false;
				}
				return true;
			}
		});
		
		updateLogScreen();
		
		return _viewRef;
	}

	@Override
	public void onDestroyView() {
		if (this._serviceAPI != null) {
			if (_listener != null) {
				try {
					_serviceAPI.removeListener(_listener);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}
		}
		
		super.onDestroyView();
	}

	@Override
  	public void onActivityCreated(Bundle savedInstanceState) {
	  	super.onActivityCreated(savedInstanceState);
	  	return;
  	}
	
	private void updateLogScreen() {
		String logText = "";
		
		/* TODO HACKY */
		if (_serviceAPI != null) {
			try {
				logText = _serviceAPI.getLogs(20);
			} catch (RemoteException e1) {
				e1.printStackTrace();
			}
		} else {
			OLog.err("Service Unavailable");
		}
		
		_logView.setText(logText);
		return;
	}
	
	private class DoubleTapListener extends SimpleOnGestureListener {
		@Override
		public boolean onDoubleTapEvent(MotionEvent e) {
			if (_parent != null) {
				updateLogScreen();
			}
			return super.onDoubleTapEvent(e);
		}
		
	}
	  
	/********************/
	/** Public Methods **/
	/********************/
	public OreadServiceApi getServiceHandle() {
		return this._serviceAPI;
	}
	
	public void setServiceHandle(OreadServiceApi api) {
		this._serviceAPI = api;
		return;
	}
  
	/*********************/
	/** Private Methods **/
	/*********************/
  	private void startService() {
		if (_serviceAPI != null) {
			
			if (_listener == null) {
				_listener = new LogFragmentSvcListener();
			}
			
			try {
				OreadServiceControllerStatus cs = _serviceAPI.getStatus();
				if (cs == null) {
					_serviceAPI.start();
					_serviceAPI.addListener(_listener);
				} else if (cs.getState() == ControllerState.UNKNOWN) {
					_serviceAPI.start();
					_serviceAPI.addListener(_listener);
				}
				
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		} else {
			OLog.err("Service Unavailable");
		}
		
		return;
  	}
  	
  	private void stopService() {
		if (_serviceAPI != null) {
			try {
				_serviceAPI.stop();
				
				if (_listener != null) {
					_serviceAPI.removeListener(_listener);
				}
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		} else {
			OLog.err("Service Unavailable");
		}
		
		return;
  	}
			
    private class LogFragmentSvcListener extends OreadServiceListener.Stub {
		@Override
		public void handleWaterQualityData() throws RemoteException {
			/* Do Nothing */
			return;
		}
	}
}
