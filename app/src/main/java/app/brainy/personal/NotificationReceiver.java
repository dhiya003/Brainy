package app.brainy.personal;

import android.app.*;
import android.content.*;
import androidx.core.app.NotificationCompat;
import org.json.*;

public class NotificationReceiver extends BroadcastReceiver {
    private PendingIntent action(Context c,String action,String taskId,String kind,int offset){
        Intent i=new Intent(c,ReminderActionReceiver.class).setAction(action).putExtra("taskId",taskId).putExtra("kind",kind);
        return PendingIntent.getBroadcast(c,ReminderEngine.requestCode(taskId,kind)+offset,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }

    @Override public void onReceive(Context c, Intent intent) {
        String taskId=intent.getStringExtra("taskId"),kind=intent.getStringExtra("kind");
        if(taskId==null)return;
        long scheduledAt=intent.getLongExtra("scheduledAt",0);
        if(!ReminderEngine.shouldDeliver(c,taskId,kind==null?"reminder":kind,scheduledAt))return;
        String title,body; JSONObject task=ReminderEngine.task(c,taskId);
        boolean daily="daily".equals(taskId);
        if(daily){
            boolean morning="briefing".equals(kind);
            title=morning?"Good morning — your briefing":"Evening review";
            body=ReminderEngine.agenda(c,!morning);
            ReminderEngine.scheduleDaily(c,kind,morning?8:19);
        }else{
            if(task==null||task.optBoolean("done"))return;
            String prefix=intent.getStringExtra("prefix");
            title=(prefix==null?task.optString("title","Brainy"):prefix);
            body=task.optString("title","Responsibility due");
            String stage=intent.getStringExtra("stage");if(stage!=null)body=body+" · "+stage;
        }
        int id=ReminderEngine.requestCode(taskId,kind==null?"reminder":kind);
        Intent openApp=new Intent(c,MainActivity.class).putExtra("taskId",taskId);
        PendingIntent content=PendingIntent.getActivity(c,id,openApp,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b=new NotificationCompat.Builder(c,ReminderEngine.CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(body)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body)).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true).setContentIntent(content);
        if(!daily){
            b.addAction(0,"Complete",action(c,"app.brainy.COMPLETE",taskId,kind,1))
             .addAction(0,"Snooze 10m",action(c,"app.brainy.SNOOZE",taskId,kind,2))
             .addAction(0,"Reschedule +1h",action(c,"app.brainy.RESCHEDULE",taskId,kind,3));
            if(task!=null&&!task.optString("actionUrl","").isEmpty())
                b.addAction(0,task.optString("actionLabel","Open app"),action(c,"app.brainy.OPEN",taskId,kind,4));
        }
        c.getSystemService(NotificationManager.class).notify(id,b.build());
    }
}
