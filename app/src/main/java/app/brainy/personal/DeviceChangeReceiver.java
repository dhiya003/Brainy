package app.brainy.personal;

import android.content.*;

public class DeviceChangeReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action=intent==null?null:intent.getAction();
        if(Intent.ACTION_TIME_CHANGED.equals(action)||Intent.ACTION_TIMEZONE_CHANGED.equals(action)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(action))
            ReminderEngine.restoreAll(context);
    }
}
