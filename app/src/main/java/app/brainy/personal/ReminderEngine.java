package app.brainy.personal;

import android.app.*;
import android.content.*;
import android.os.Build;
import org.json.*;
import java.util.*;

public final class ReminderEngine {
    static final String PREFS="brainy_native",TASKS="tasks",CHANNEL="brainy_reminders";
    private ReminderEngine(){}
    public static JSONObject tasks(Context c){try{return new JSONObject(c.getSharedPreferences(PREFS,0).getString(TASKS,"{}"));}catch(Exception e){return new JSONObject();}}
    public static JSONObject task(Context c,String id){return tasks(c).optJSONObject(id);}
    private static void persist(Context c,JSONObject all){c.getSharedPreferences(PREFS,0).edit().putString(TASKS,all.toString()).commit();}
    public static void saveTask(Context c,JSONObject task){try{JSONObject all=tasks(c);all.put(task.getString("id"),task);persist(c,all);}catch(Exception ignored){}}
    public static void delete(Context c,String id){cancelTask(c,id);JSONObject all=tasks(c);all.remove(id);persist(c,all);}
    public static void complete(Context c,String id){
        try{JSONObject all=tasks(c),t=all.optJSONObject(id);if(t==null)return;cancelTask(c,id);t.put("lastCompletedAt",System.currentTimeMillis());
            String recurrence=t.optString("recurrence","").toLowerCase();
            if(!recurrence.isEmpty()&&!"null".equals(recurrence)){Calendar next=Calendar.getInstance();next.setTimeInMillis(t.optLong("dueAtMs",System.currentTimeMillis()));
                if(recurrence.contains("day"))next.add(Calendar.DAY_OF_MONTH,1);else if(recurrence.contains("week"))next.add(Calendar.WEEK_OF_YEAR,1);else if(recurrence.contains("month"))next.add(Calendar.MONTH,1);else if(recurrence.contains("year"))next.add(Calendar.YEAR,1);else next.add(Calendar.DAY_OF_MONTH,1);
                t.put("dueAtMs",next.getTimeInMillis());t.put("dueAt",new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",Locale.US).format(next.getTime()));t.put("done",false);all.put(id,t);persist(c,all);scheduleJourney(c,t);
            }else{t.put("done",true);all.put(id,t);persist(c,all);}
        }catch(Exception ignored){}
    }
    public static int requestCode(String id,String kind){return Math.abs((id+":"+kind).hashCode());}
    public static void scheduleJourney(Context c,JSONObject task){saveTask(c,task);if(task.optBoolean("done"))return;long due=task.optLong("dueAtMs");if(due<=0)return;
        Calendar d=Calendar.getInstance();d.setTimeInMillis(due);Calendar previous=(Calendar)d.clone();previous.add(Calendar.DAY_OF_MONTH,-1);previous.set(Calendar.HOUR_OF_DAY,20);previous.set(Calendar.MINUTE,0);previous.set(Calendar.SECOND,0);
        Calendar morning=(Calendar)d.clone();morning.set(Calendar.HOUR_OF_DAY,8);morning.set(Calendar.MINUTE,0);morning.set(Calendar.SECOND,0);
        schedule(c,task,"prepare",previous.getTimeInMillis(),"Prepare for tomorrow","Preparation reminder");schedule(c,task,"morning",morning.getTimeInMillis(),"On today’s agenda","Morning reminder");
        schedule(c,task,"near30",due-30*60_000L,"Coming up in 30 minutes","Check the time and prepare");schedule(c,task,"near5",due-5*60_000L,"Starting in 5 minutes","Be ready");
        schedule(c,task,"due",due,task.optString("title","Responsibility due"),"Due now");schedule(c,task,"followup",due+30*60_000L,"Did you complete this?","Completion follow-up");
    }
    public static void schedule(Context c,JSONObject task,String kind,long at,String prefix,String stage){if(at<=System.currentTimeMillis())return;String id=task.optString("id");
        Intent i=new Intent(c,NotificationReceiver.class).putExtra("taskId",id).putExtra("kind",kind).putExtra("prefix",prefix).putExtra("stage",stage);
        PendingIntent p=PendingIntent.getBroadcast(c,requestCode(id,kind),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);AlarmManager a=c.getSystemService(AlarmManager.class);
        if(Build.VERSION.SDK_INT<31||a.canScheduleExactAlarms())a.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,p);else a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,p);
    }
    public static void cancelTask(Context c,String id){String[] kinds={"prepare","morning","near30","near5","due","followup","snooze","reschedule"};AlarmManager a=c.getSystemService(AlarmManager.class);
        for(String kind:kinds){PendingIntent p=PendingIntent.getBroadcast(c,requestCode(id,kind),new Intent(c,NotificationReceiver.class),PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);if(p!=null){a.cancel(p);p.cancel();}}}
    public static void scheduleDaily(Context c,String kind,int hour){Calendar at=Calendar.getInstance();at.set(Calendar.HOUR_OF_DAY,hour);at.set(Calendar.MINUTE,0);at.set(Calendar.SECOND,0);at.set(Calendar.MILLISECOND,0);if(at.getTimeInMillis()<=System.currentTimeMillis())at.add(Calendar.DAY_OF_MONTH,1);
        JSONObject p=new JSONObject();try{p.put("id","daily");}catch(Exception ignored){}schedule(c,p,kind,at.getTimeInMillis(),kind.equals("briefing")?"Good morning":"Evening review",kind);}
    public static String agenda(Context c,boolean tomorrow){JSONObject all=tasks(c);int count=0,overdue=0;StringBuilder names=new StringBuilder();Calendar start=Calendar.getInstance();start.set(Calendar.HOUR_OF_DAY,0);start.set(Calendar.MINUTE,0);start.set(Calendar.SECOND,0);start.set(Calendar.MILLISECOND,0);if(tomorrow)start.add(Calendar.DAY_OF_MONTH,1);
        long from=start.getTimeInMillis(),to=from+86400000L,now=System.currentTimeMillis();Iterator<String> keys=all.keys();while(keys.hasNext()){JSONObject t=all.optJSONObject(keys.next());if(t==null||t.optBoolean("done"))continue;long due=t.optLong("dueAtMs");if(!tomorrow&&due>0&&due<now)overdue++;if(due>=from&&due<to){count++;if(names.length()<100){if(names.length()>0)names.append(", ");names.append(t.optString("title"));}}}
        if(count==0)return tomorrow?"Nothing planned for tomorrow.":(overdue>0?overdue+" overdue responsibilities need recovery.":"Your agenda is clear.");return count+" "+(count==1?"responsibility":"responsibilities")+": "+names+(overdue>0?". "+overdue+" overdue.":"");}
    public static void restoreAll(Context c){JSONObject all=tasks(c);Iterator<String> keys=all.keys();while(keys.hasNext()){JSONObject t=all.optJSONObject(keys.next());if(t!=null&&!t.optBoolean("done"))scheduleJourney(c,t);}scheduleDaily(c,"briefing",8);scheduleDaily(c,"review",19);}
}
