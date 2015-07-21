package net.oukranos.oreadv1;

import net.oukranos.oreadv1.util.OreadLogger;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.support.v4.content.WakefulBroadcastReceiver;

public class OreadServiceWakeReceiver extends WakefulBroadcastReceiver {
	/* Get an instance of the OreadLogger class to handle logging */
	private static final OreadLogger OLog = OreadLogger.getInstance();
	
	public static final String EXTRA_ORIGINATOR = "net.oukranos.oreadv1.EXTRA_ORIGINATOR";
	public static final String EXTRA_DIRECTIVE = "net.oukranos.oreadv1.EXTRA_DIRECTIVE";
	public static final String ORIGINATOR_ID = OreadServiceWakeReceiver.class.getName();
	public static final String DIRECTIVE_ID = "run";
	
	private AlarmManager _wakeAlarm = null;
	private PendingIntent _wakeAlarmIntent = null;
	
    public OreadServiceWakeReceiver() {
        return;
    }

	@Override
	/**
	 *  Receives the 'wake signal' from dispatched by the alarm
	 *  @param context
	 *  @param intent
	 **/
	public void onReceive(Context context, Intent intent) {
		OLog.info("OreadServiceWakeReceiver onReceive() started");
		
		Intent oreadService = new Intent(OreadService.class.getName());
		oreadService.putExtra(EXTRA_ORIGINATOR, ORIGINATOR_ID);
		oreadService.putExtra(EXTRA_DIRECTIVE, DIRECTIVE_ID);
		
		//ComponentName cn = startWakefulService(context, oreadService);
		ComponentName cn = context.startService(oreadService);
		if (cn == null) {
			OLog.err("Failed to start service: " + OreadService.class.getName());
		} else {
			OLog.info("Started service: " + cn.getShortClassName());
		}
		
		OLog.info("OreadServiceWakeReceiver onReceive() finished");
		return;
	}
	
	public void setAlarm(Context context, long interval) {
		if (context == null) {
			OLog.err("Set alarm failed: Invalid context param");
			return;
		}
		
		_wakeAlarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		Intent recvTriggerIntent = new Intent(context, OreadServiceWakeReceiver.class);
		_wakeAlarmIntent = PendingIntent.getBroadcast(context, 0, recvTriggerIntent, 0);
		
		/* Set the alarm to trigger once after a fifteen minute interval */
		_wakeAlarm.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, 
				SystemClock.elapsedRealtime() + interval, 
				_wakeAlarmIntent);
		
		OLog.info("OreadService wake trigger set to " + interval + "ms from now.");
		return;
	}
	
	public void cancelAlarm(Context context) {
		if (context == null) {
			OLog.warn("Cancel alarm warning: Invalid context param");
		}
		
		if (_wakeAlarm == null) {
			_wakeAlarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		}
		
		if (_wakeAlarmIntent == null) {
			Intent recvTriggerIntent = new Intent(context, OreadServiceWakeReceiver.class);
			_wakeAlarmIntent = PendingIntent.getBroadcast(context, 0, recvTriggerIntent, 0);
		}
		
		_wakeAlarm.cancel(_wakeAlarmIntent);
		
		OLog.info("WakeAlarm cancelled");
		return;
	}

}
