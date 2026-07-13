package app.brainy.personal;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.webkit.*;
import org.json.*;
import java.util.UUID;

public class MainActivity extends Activity {
    private WebView webView;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        createNotificationChannel();
        ReminderEngine.restoreAll(this);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},100);
        webView=new WebView(this);WebSettings s=webView.getSettings();
        s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setDatabaseEnabled(true);s.setAllowFileAccess(true);
        webView.setWebViewClient(new WebViewClient());webView.addJavascriptInterface(new BrainyBridge(this),"BrainyAndroid");
        setContentView(webView);webView.loadUrl("file:///android_asset/index.html");
    }
    private void createNotificationChannel(){
        if(Build.VERSION.SDK_INT>=26){NotificationChannel ch=new NotificationChannel(ReminderEngine.CHANNEL,"Brainy reminders",NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Preparation, due, follow-up and daily briefing reminders");ch.enableVibration(true);getSystemService(NotificationManager.class).createNotificationChannel(ch);}
    }
    @Override public void onBackPressed(){if(webView!=null&&webView.canGoBack())webView.goBack();else super.onBackPressed();}
}

class BrainyBridge {
    private final Context c; BrainyBridge(Context context){c=context;}
    @JavascriptInterface public boolean canScheduleExactAlarms(){return Build.VERSION.SDK_INT<31||c.getSystemService(AlarmManager.class).canScheduleExactAlarms();}
    @JavascriptInterface public void requestExactAlarmPermission(){if(Build.VERSION.SDK_INT>=31){Intent i=new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:"+c.getPackageName()));i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);c.startActivity(i);}}
    @JavascriptInterface public int scheduleNotification(String title,String body,long at,String actionUrl){
        String id="test-"+UUID.randomUUID();JSONObject t=new JSONObject();try{t.put("id",id);t.put("title",title);t.put("description",body);t.put("dueAtMs",at);t.put("actionUrl",actionUrl);t.put("actionLabel","Open app");ReminderEngine.saveTask(c,t);ReminderEngine.schedule(c,t,"due",at,title,"Test");}catch(Exception ignored){}return ReminderEngine.requestCode(id,"due");
    }
    @JavascriptInterface public void scheduleJourney(String json){try{ReminderEngine.scheduleJourney(c,new JSONObject(json));}catch(Exception ignored){}}
    @JavascriptInterface public void completeTask(String id){ReminderEngine.complete(c,id);}
    @JavascriptInterface public String nativeAgenda(boolean tomorrow){return ReminderEngine.agenda(c,tomorrow);}
    @JavascriptInterface public void cancelNotification(int id){}
    @JavascriptInterface public void openExternal(String url){try{Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse(url));i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);c.startActivity(i);}catch(Exception ignored){}}
}
