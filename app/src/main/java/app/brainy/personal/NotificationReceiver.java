package app.brainy.personal;

import android.app.*;
import android.content.*;
import android.net.Uri;
import androidx.core.app.NotificationCompat;

public class NotificationReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        int id = intent.getIntExtra("id", 1);
        String title = intent.getStringExtra("title");
        String body = intent.getStringExtra("body");
        String actionUrl = intent.getStringExtra("actionUrl");

        Intent open = new Intent(context, MainActivity.class);
        if (actionUrl != null && !actionUrl.isEmpty()) open.setData(Uri.parse(actionUrl));
        PendingIntent content = PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(context, "brainy_reminders")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title == null ? "Brainy" : title)
            .setContentText(body == null ? "You have a responsibility due." : body)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(content)
            .build();
        context.getSystemService(NotificationManager.class).notify(id, notification);
    }
}
