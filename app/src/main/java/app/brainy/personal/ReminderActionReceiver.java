package app.brainy.personal;

import android.app.*;
import android.content.*;
import org.json.*;

public class ReminderActionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        String taskId=i.getStringExtra("taskId"), action=i.getAction();
        if(taskId==null||action==null)return;
        JSONObject task=ReminderEngine.task(c,taskId);
        if("app.brainy.COMPLETE".equals(action)){
            ReminderEngine.complete(c,taskId);
            c.getSystemService(NotificationManager.class).cancel(ReminderEngine.requestCode(taskId,i.getStringExtra("kind")));
        }else if(task!=null&&"app.brainy.SNOOZE".equals(action)){
            long at=System.currentTimeMillis()+10*60_000L;
            ReminderEngine.recordAction(c,taskId,"snoozed",at);
            JSONObject updated=ReminderEngine.task(c,taskId);
            ReminderEngine.schedule(c,updated==null?task:updated,"snooze",at,"Snoozed reminder","Snoozed 10 minutes");
            c.getSystemService(NotificationManager.class).cancel(ReminderEngine.requestCode(taskId,i.getStringExtra("kind")));
        }else if(task!=null&&"app.brainy.RESCHEDULE".equals(action)){
            try{long due=System.currentTimeMillis()+60*60_000L;ReminderEngine.recordAction(c,taskId,"rescheduled",due);JSONObject updated=ReminderEngine.task(c,taskId);ReminderEngine.cancelTask(c,taskId);if(updated!=null)ReminderEngine.scheduleJourney(c,updated);}
            catch(Exception ignored){}
            c.getSystemService(NotificationManager.class).cancel(ReminderEngine.requestCode(taskId,i.getStringExtra("kind")));
        }else if("app.brainy.OPEN".equals(action)&&task!=null){
            String url=task.optString("actionUrl","");
            if(!url.isEmpty()){try{android.net.Uri uri=android.net.Uri.parse(url);Intent open=new Intent(Intent.ACTION_VIEW,uri);open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);if("upi".equalsIgnoreCase(uri.getScheme()))open.setPackage("com.google.android.apps.nbu.paisa.user");try{c.startActivity(open);}catch(Exception unavailable){open.setPackage(null);c.startActivity(open);}ReminderEngine.recordAction(c,taskId,"opened_external",0);}catch(Exception ignored){}}
        }
    }
}
