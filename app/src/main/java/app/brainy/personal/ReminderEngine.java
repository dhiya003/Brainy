package app.brainy.personal;

import android.app.*;
import android.content.*;
import android.os.Build;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class ReminderEngine {
    static final String PREFS="brainy_native",TASKS="tasks",DELIVERIES="deliveries",CHANNEL="brainy_reminders";
    private static final String[] KINDS={"prepare","morning","near30","near5","lead","due","followup","snooze","reschedule","recovery"};
    private ReminderEngine(){}

    private static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,0);}
    public static JSONObject tasks(Context c){try{return new JSONObject(prefs(c).getString(TASKS,"{}"));}catch(Exception e){return new JSONObject();}}
    public static JSONObject task(Context c,String id){return tasks(c).optJSONObject(id);}
    private static void persist(Context c,JSONObject all){prefs(c).edit().putString(TASKS,all.toString()).commit();}
    public static void saveTask(Context c,JSONObject task){try{JSONObject all=tasks(c);all.put(task.getString("id"),task);persist(c,all);}catch(Exception ignored){}}
    private static String iso(long time){return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",Locale.US).format(new Date(time));}

    public static void delete(Context c,String id){cancelTask(c,id);JSONObject all=tasks(c);all.remove(id);persist(c,all);}
    public static void complete(Context c,String id){
        try{
            JSONObject all=tasks(c),t=all.optJSONObject(id);if(t==null)return;cancelTask(c,id);long now=System.currentTimeMillis();t.put("lastCompletedAt",now);t.put("lastAction","completed");
            String recurrence=t.optString("recurrence","").toLowerCase();
            if(!recurrence.isEmpty()&&!"null".equals(recurrence)){
                Calendar next=Calendar.getInstance();next.setTimeInMillis(t.optLong("dueAtMs",now));
                do{advance(next,recurrence);}while(next.getTimeInMillis()<=now);
                t.put("dueAtMs",next.getTimeInMillis());t.put("dueAt",iso(next.getTimeInMillis()));t.put("done",false);t.remove("snoozedUntil");all.put(id,t);persist(c,all);scheduleJourney(c,t);
            }else{t.put("done",true);t.remove("snoozedUntil");all.put(id,t);persist(c,all);}
        }catch(Exception ignored){}
    }
    private static void advance(Calendar c,String recurrence){if(recurrence.contains("day"))c.add(Calendar.DAY_OF_MONTH,1);else if(recurrence.contains("week"))c.add(Calendar.WEEK_OF_YEAR,1);else if(recurrence.contains("month"))c.add(Calendar.MONTH,1);else if(recurrence.contains("year"))c.add(Calendar.YEAR,1);else c.add(Calendar.DAY_OF_MONTH,1);}
    public static void recordAction(Context c,String id,String action,long value){try{JSONObject all=tasks(c),t=all.optJSONObject(id);if(t==null)return;t.put("lastAction",action);t.put("lastActionAt",System.currentTimeMillis());if(value>0&&"snoozed".equals(action))t.put("snoozedUntil",value);if(value>0&&"rescheduled".equals(action)){t.put("dueAtMs",value);t.put("dueAt",iso(value));}all.put(id,t);persist(c,all);}catch(Exception ignored){}}

    public static int requestCode(String id,String kind){return Math.abs((id+":"+kind).hashCode());}
    public static void scheduleJourney(Context c,JSONObject task){
        saveTask(c,task);if(task.optBoolean("done"))return;long due=task.optLong("dueAtMs");
        if(due<=0)return;cancelTask(c,task.optString("id"));long now=System.currentTimeMillis();
        if(due<=now){schedule(c,task,"recovery",now+3_000L,"This is overdue","Missed reminder recovery");return;}
        Calendar d=Calendar.getInstance();d.setTimeInMillis(due);Calendar previous=(Calendar)d.clone();previous.add(Calendar.DAY_OF_MONTH,-1);previous.set(Calendar.HOUR_OF_DAY,20);previous.set(Calendar.MINUTE,0);previous.set(Calendar.SECOND,0);previous.set(Calendar.MILLISECOND,0);
        Calendar morning=(Calendar)d.clone();morning.set(Calendar.HOUR_OF_DAY,8);morning.set(Calendar.MINUTE,0);morning.set(Calendar.SECOND,0);morning.set(Calendar.MILLISECOND,0);
        schedule(c,task,"prepare",previous.getTimeInMillis(),"Prepare for tomorrow","Preparation reminder");schedule(c,task,"morning",morning.getTimeInMillis(),"On today’s agenda","Morning reminder");
        int lead=task.optInt("preferredLeadMinutes",30);schedule(c,task,"lead",due-lead*60_000L,"Coming up in "+lead+" minutes","Preparation reminder");
        if(lead!=5)schedule(c,task,"near5",due-5*60_000L,"Starting in 5 minutes","Be ready");
        schedule(c,task,"due",due,task.optString("title","Responsibility due"),"Due now");schedule(c,task,"followup",due+30*60_000L,"Did you complete this?","Completion follow-up");
    }
    public static void schedule(Context c,JSONObject task,String kind,long at,String prefix,String stage){
        if(at<=System.currentTimeMillis())return;String id=task.optString("id");
        Intent i=new Intent(c,NotificationReceiver.class).putExtra("taskId",id).putExtra("kind",kind).putExtra("prefix",prefix).putExtra("stage",stage).putExtra("scheduledAt",at);
        PendingIntent p=PendingIntent.getBroadcast(c,requestCode(id,kind),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);AlarmManager a=c.getSystemService(AlarmManager.class);
        if(Build.VERSION.SDK_INT<31||a.canScheduleExactAlarms())a.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,p);else a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,p);
        if(!"daily".equals(id)){try{JSONObject scheduled=task.optJSONObject("scheduledAlarms");if(scheduled==null)scheduled=new JSONObject();scheduled.put(kind,at);task.put("scheduledAlarms",scheduled);saveTask(c,task);}catch(Exception ignored){}}
    }
    public static void cancelTask(Context c,String id){AlarmManager a=c.getSystemService(AlarmManager.class);for(String kind:KINDS){PendingIntent p=PendingIntent.getBroadcast(c,requestCode(id,kind),new Intent(c,NotificationReceiver.class),PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);if(p!=null){a.cancel(p);p.cancel();}}}

    public static boolean shouldDeliver(Context c,String id,String kind,long scheduledAt){String key=id+":"+kind+":"+scheduledAt;android.content.SharedPreferences p=prefs(c);if(p.getLong("delivery:"+key,0)>0)return false;p.edit().putLong("delivery:"+key,System.currentTimeMillis()).apply();return true;}
    public static void scheduleDaily(Context c,String kind,int hour){Calendar at=Calendar.getInstance();at.set(Calendar.HOUR_OF_DAY,hour);at.set(Calendar.MINUTE,0);at.set(Calendar.SECOND,0);at.set(Calendar.MILLISECOND,0);if(at.getTimeInMillis()<=System.currentTimeMillis())at.add(Calendar.DAY_OF_MONTH,1);JSONObject p=new JSONObject();try{p.put("id","daily");}catch(Exception ignored){}schedule(c,p,kind,at.getTimeInMillis(),kind.equals("briefing")?"Good morning":"Evening review",kind);}

    public static String agenda(Context c,boolean tomorrow){JSONObject all=tasks(c);int count=0,overdue=0;StringBuilder names=new StringBuilder();Calendar start=Calendar.getInstance();start.set(Calendar.HOUR_OF_DAY,0);start.set(Calendar.MINUTE,0);start.set(Calendar.SECOND,0);start.set(Calendar.MILLISECOND,0);if(tomorrow)start.add(Calendar.DAY_OF_MONTH,1);long from=start.getTimeInMillis(),to=from+86400000L,now=System.currentTimeMillis();Iterator<String> keys=all.keys();while(keys.hasNext()){JSONObject t=all.optJSONObject(keys.next());if(t==null||t.optBoolean("done"))continue;long due=t.optLong("dueAtMs");if(!tomorrow&&due>0&&due<now)overdue++;if(due>=from&&due<to){count++;if(names.length()<100){if(names.length()>0)names.append(", ");names.append(t.optString("title"));}}}if(count==0)return tomorrow?"Nothing planned for tomorrow.":(overdue>0?overdue+" overdue responsibilities need recovery.":"Your agenda is clear.");return count+" "+(count==1?"responsibility":"responsibilities")+": "+names+(overdue>0?". "+overdue+" overdue.":"");}
    public static String snoozed(Context c){JSONArray result=new JSONArray();JSONObject all=tasks(c);long now=System.currentTimeMillis();Iterator<String> keys=all.keys();while(keys.hasNext()){JSONObject t=all.optJSONObject(keys.next());if(t!=null&&t.optLong("snoozedUntil")>now)result.put(t);}return result.toString();}
    public static String diagnostics(Context c){try{JSONObject d=new JSONObject();AlarmManager a=c.getSystemService(AlarmManager.class);d.put("taskCount",tasks(c).length());d.put("exactAlarmAllowed",Build.VERSION.SDK_INT<31||a.canScheduleExactAlarms());d.put("lastRestoreAt",prefs(c).getLong("lastRestoreAt",0));d.put("forceStopLimitation","After Android Force stop, alarms cannot resume until Brainy is opened again.");return d.toString();}catch(Exception e){return "{}";}}
    public static void restoreAll(Context c){prefs(c).edit().putLong("lastRestoreAt",System.currentTimeMillis()).apply();JSONObject all=tasks(c);Iterator<String> keys=all.keys();while(keys.hasNext()){JSONObject t=all.optJSONObject(keys.next());if(t!=null&&!t.optBoolean("done"))scheduleJourney(c,t);}scheduleDaily(c,"briefing",8);scheduleDaily(c,"review",19);}
}
