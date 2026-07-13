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
            ReminderEngine.schedule(c,task,"snooze",System.currentTimeMillis()+10*60_000L,"Snoozed reminder","Snoozed 10 minutes");
            c.getSystemService(NotificationManager.class).cancelAll();
        }else if(task!=null&&"app.brainy.RESCHEDULE".equals(action)){
            try{long due=System.currentTimeMillis()+60*60_000L;task.put("dueAtMs",due);ReminderEngine.cancelTask(c,taskId);ReminderEngine.scheduleJourney(c,task);}
            catch(Exception ignored){}
            c.getSystemService(NotificationManager.class).cancelAll();
        }else if("app.brainy.OPEN".equals(action)&&task!=null){
            String url=task.optString("actionUrl","");
            if(!url.isEmpty()){try{Intent open=new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url));open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);c.startActivity(open);}catch(Exception ignored){}}
        }
    }
}
