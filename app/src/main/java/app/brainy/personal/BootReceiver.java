package app.brainy.personal;

import android.content.*;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        // The web app resynchronizes persisted responsibilities when it next opens.
        // A future worker will restore every exact alarm directly from encrypted native storage.
    }
}
