package net.oukranos.oreadv1;

import net.oukranos.oreadv1.util.OLog;

import android.app.AlarmManager;
import android.app.PendingIntent;

import java.util.Calendar;

public class OreadServiceWakeReceiver extends WakefulBroadcastReceiver {
    private static final long RECEIVER_WAKE_INTERVAL = (5 * 60 * 1000); /* 5 min interval */
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
        /* Prepare and start the service */
        Intent oreadService = new Intent(context, OreadService.class.getName());

        /* Indicate the originator of the intent -- this basically allows the
         *  OreadService to automatically start the MainController regardless
         *  of whether there are no clients bound to it */
        oreadService.putExtra("net.oukranos.oreadv1.EXTRA_ORIGIN_NAME",
                OreadServiceWakeReceiver.class.getName());
        oreadService.putExtra("net.oukranos.oreadv1.EXTRA_DIRECTIVE",
                "RunContinuous");

        ComponentName cn = startWakefulService(context, intent);
		if (cn == null) {
			OLog.err("Failed to start service: " + OreadService.class.getName());
		} else {
			OLog.info("Started service: " + cn.getShortClassName());
		}

        return;
    }

    /**
     * Sets the 'wake signal' alarm
     * @param context
     **/
    public void setAlarm(Context context) {
        _wakeAlarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent alarmRcvrIntent = new Intent(context, OreadServiceWakeReceiver.class);
        _wakeAlarmIntent = PendingIntent.getBroadcast(context, 0, alarmRcvrIntent, 0);

        /* Set the wake alarm to be triggered in fifteen minutes */
        _wakeAlarm.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + INTERVAL_FIFTEEN_MINUTES,
                _wakeAlarmIntent);

        return;
    }

    /**
     * Cancels the existing 'wake signal' alarm
     * @param context
     **/
    public void cancelAlarm(Context context) {
        if (_wakeAlarm != null) {
            _wakeAlarm.cancel(_wakeAlarmIntent);
        }
        _wakeAlarm = null;
        return;
    }
}

