package app.brainy.personal;

import android.app.*;
import android.content.*;
import android.os.Build;
import org.json.*;
import java.text.*;
import java.util.*;

public final class ReminderEngine {
    static final String PREFS = "brainy_native";
    static final String TASKS = "tasks";
    static final String CHANNEL = "brainy_reminders";

    private ReminderEngine() {}

    public static void saveTask(Context c, JSONObject task) {
        try {
            JSONObject all = tasks(c);
            all.put(task.getString("id"), task);
            c.getSharedPreferences(PREFS, 0).edit().putString(TASKS, all.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static JSONObject tasks(Context c) {
        try { return new JSONObject(c.getSharedPreferences(PREFS, 0).getString(TASKS, "{}")); }
        catch (Exception e) { return new JSONObject(); }
    }

    public static JSONObject task(Context c, String id) {
        return tasks(c).optJSONObject(id);
    }

    public static void complete(Context c, String id) {
        try {
            JSONObject all = tasks(c), t = all.optJSONObject(id);
            if (t != null) { t.put("done", true); all.put(id, t); }
            c.getSharedPreferences(PREFS, 0).edit().putString(TASKS, all.toString()).apply();
            cancelTask(c, id);
        } catch (Exception ignored) {}
    }

    public static int requestCode(String id, String kind) {
        return Math.abs((id + ":" + kind).hashCode());
    }

    public static void scheduleJourney(Context c, JSONObject task) {
        saveTask(c, task);
        if (task.optBoolean("done", false)) return;
        long due = task.optLong("dueAtMs", 0);
        if (due <= 0) return;
        Calendar d = Calendar.getInstance(); d.setTimeInMillis(due);
        Calendar previous = (Calendar)d.clone(); previous.add(Calendar.DAY_OF_MONTH, -1); previous.set(Calendar.HOUR_OF_DAY, 20); previous.set(Calendar.MINUTE, 0); previous.set(Calendar.SECOND, 0);
        Calendar morning = (Calendar)d.clone(); morning.set(Calendar.HOUR_OF_DAY, 8); morning.set(Calendar.MINUTE, 0); morning.set(Calendar.SECOND, 0);
        schedule(c, task, "prepare", previous.getTimeInMillis(), "Prepare for tomorrow", "Preparation reminder");
        schedule(c, task, "morning", morning.getTimeInMillis(), "On today’s agenda", "Morning reminder");
        schedule(c, task, "near30", due - 30*60_000L, "Coming up in 30 minutes", "Check the time and prepare");
        schedule(c, task, "near5", due - 5*60_000L, "Starting in 5 minutes", "Be ready");
        schedule(c, task, "due", due, task.optString("title", "Responsibility due"), "Due now");
        schedule(c, task, "followup", due + 30*60_000L, "Did you complete this?", "Completion follow-up");
    }

    public static void schedule(Context c, JSONObject task, String kind, long at, String prefix, String stage) {
        if (at <= System.currentTimeMillis()) return;
        String id = task.optString("id");
        Intent i = new Intent(c, NotificationReceiver.class)
            .putExtra("taskId", id).putExtra("kind", kind).putExtra("prefix", prefix).putExtra("stage", stage);
        int code = requestCode(id, kind);
        PendingIntent p = PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager a = c.getSystemService(AlarmManager.class);
        if (Build.VERSION.SDK_INT < 31 || a.canScheduleExactAlarms()) a.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
        else a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
    }

    public static void cancelTask(Context c, String id) {
        String[] kinds={"prepare","morning","near30","near5","due","followup","snooze","reschedule"};
        AlarmManager a=c.getSystemService(AlarmManager.class);
        for(String kind:kinds){
            Intent i=new Intent(c,NotificationReceiver.class);
            PendingIntent p=PendingIntent.getBroadcast(c,requestCode(id,kind),i,PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);
            if(p!=null){a.cancel(p);p.cancel();}
        }
    }

    public static void scheduleDaily(Context c, String kind, int hour) {
        Calendar n=Calendar.getInstance(), at=Calendar.getInstance();
        at.set(Calendar.HOUR_OF_DAY,hour);at.set(Calendar.MINUTE,0);at.set(Calendar.SECOND,0);at.set(Calendar.MILLISECOND,0);
        if(at.before(n))at.add(Calendar.DAY_OF_MONTH,1);
        JSONObject pseudo=new JSONObject();
        try{pseudo.put("id","daily");pseudo.put("title",kind.equals("briefing")?"Morning briefing":"Evening review");}
        catch(Exception ignored){}
        schedule(c,pseudo,kind,at.getTimeInMillis(),kind.equals("briefing")?"Good morning":"Evening review",kind);
    }

    public static String agenda(Context c, boolean tomorrow) {
        JSONObject all=tasks(c); int count=0, overdue=0; StringBuilder names=new StringBuilder();
        Calendar start=Calendar.getInstance(); start.set(Calendar.HOUR_OF_DAY,0);start.set(Calendar.MINUTE,0);start.set(Calendar.SECOND,0);start.set(Calendar.MILLISECOND,0);
        if(tomorrow)start.add(Calendar.DAY_OF_MONTH,1);
        long from=start.getTimeInMillis(), to=from+24*60*60_000L, now=System.currentTimeMillis();
        Iterator<String> keys=all.keys();
        while(keys.hasNext()){JSONObject t=all.optJSONObject(keys.next());if(t==null||t.optBoolean("done"))continue;long due=t.optLong("dueAtMs");
            if(!tomorrow&&due>0&&due<now)overdue++;
            if(due>=from&&due<to){count++;if(names.length()<100){if(names.length()>0)names.append(", ");names.append(t.optString("title"));}}
        }
        if(count==0)return tomorrow?"Nothing planned for tomorrow.":(overdue>0?overdue+" overdue responsibilities need recovery.":"Your agenda is clear.");
        return count+" "+(count==1?"responsibility":"responsibilities")+": "+names+(overdue>0?". "+overdue+" overdue.":"");
    }

    public static void restoreAll(Context c) {
        JSONObject all=tasks(c);Iterator<String> keys=all.keys();
        while(keys.hasNext()){JSONObject t=all.optJSONObject(keys.next());if(t!=null&&!t.optBoolean("done"))scheduleJourney(c,t);}
        scheduleDaily(c,"briefing",8);scheduleDaily(c,"review",19);
    }
}
