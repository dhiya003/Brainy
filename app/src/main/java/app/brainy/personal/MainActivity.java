package app.brainy.personal;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.webkit.*;
import android.util.Base64;
import android.graphics.BitmapFactory;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import org.json.*;
import java.util.*;

public class MainActivity extends Activity {
    private static final int FILE_PICKER=201, VOICE=202;
    private WebView webView; private ValueCallback<Uri[]> fileCallback; private String pendingText; private Uri pendingImage;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); createNotificationChannel(); ReminderEngine.restoreAll(this);
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},100);
        webView=new WebView(this); WebSettings s=webView.getSettings();
        s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setDatabaseEnabled(true);s.setAllowFileAccess(true);
        webView.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String url){deliverShare();}});
        webView.setWebChromeClient(new WebChromeClient(){@Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> cb,FileChooserParams p){
            if(fileCallback!=null)fileCallback.onReceiveValue(null);fileCallback=cb;
            Intent i=new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(Intent.createChooser(i,"Choose image or screenshot"),FILE_PICKER);return true;}});
        webView.addJavascriptInterface(new BrainyBridge(this),"BrainyAndroid");setContentView(webView);captureShare(getIntent());webView.loadUrl("file:///android_asset/index.html");
    }

    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);captureShare(i);deliverShare();}
    private void captureShare(Intent i){
        if(i==null)return;String action=i.getAction();
        if(Intent.ACTION_SEND.equals(action)){pendingText=i.getStringExtra(Intent.EXTRA_TEXT);pendingImage=i.getParcelableExtra(Intent.EXTRA_STREAM);}
        else if(Intent.ACTION_SEND_MULTIPLE.equals(action)){ArrayList<Uri> list=i.getParcelableArrayListExtra(Intent.EXTRA_STREAM);if(list!=null&&!list.isEmpty())pendingImage=list.get(0);}
    }
    private void deliverShare(){
        if(webView==null)return;
        if(pendingText!=null){String q=JSONObject.quote(pendingText);webView.evaluateJavascript("window.receiveShare("+q+",'text')",null);pendingText=null;}
        if(pendingImage!=null){Uri u=pendingImage;pendingImage=null;try{runOcr(InputImage.fromFilePath(this,u));}catch(Exception e){webView.evaluateJavascript("window.ocrResult('',true)",null);}}
    }
    public void startVoice(){
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_PROMPT,"Tell Brainy what to remember");
        try{startActivityForResult(i,VOICE);}catch(Exception e){webView.evaluateJavascript("window.voiceResult('',true)",null);}
    }
    public void ocrBase64(String data){
        try{String raw=data.substring(data.indexOf(',')+1);byte[] bytes=Base64.decode(raw,Base64.DEFAULT);runOcr(InputImage.fromBitmap(BitmapFactory.decodeByteArray(bytes,0,bytes.length),0));}
        catch(Exception e){webView.evaluateJavascript("window.ocrResult('',true)",null);}
    }
    private void runOcr(InputImage image){
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image)
            .addOnSuccessListener(t->webView.evaluateJavascript("window.ocrResult("+JSONObject.quote(t.getText())+",false)",null))
            .addOnFailureListener(e->webView.evaluateJavascript("window.ocrResult('',true)",null));
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==FILE_PICKER){Uri[] value=null;if(result==RESULT_OK&&data!=null&&data.getData()!=null)value=new Uri[]{data.getData()};if(fileCallback!=null)fileCallback.onReceiveValue(value);fileCallback=null;}
        else if(request==VOICE){String text="";if(result==RESULT_OK&&data!=null){ArrayList<String> r=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);if(r!=null&&!r.isEmpty())text=r.get(0);}webView.evaluateJavascript("window.voiceResult("+JSONObject.quote(text)+","+(text.isEmpty()?"true":"false")+")",null);}
    }
    private void createNotificationChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel ch=new NotificationChannel(ReminderEngine.CHANNEL,"Brainy reminders",NotificationManager.IMPORTANCE_HIGH);ch.setDescription("Preparation, due, follow-up and daily briefing reminders");ch.enableVibration(true);getSystemService(NotificationManager.class).createNotificationChannel(ch);}}
    @Override public void onBackPressed(){if(webView!=null&&webView.canGoBack())webView.goBack();else super.onBackPressed();}
}

class BrainyBridge {
    private final MainActivity a; BrainyBridge(MainActivity activity){a=activity;}
    @JavascriptInterface public boolean canScheduleExactAlarms(){return Build.VERSION.SDK_INT<31||a.getSystemService(AlarmManager.class).canScheduleExactAlarms();}
    @JavascriptInterface public void requestExactAlarmPermission(){if(Build.VERSION.SDK_INT>=31){Intent i=new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+a.getPackageName()));i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);a.startActivity(i);}}
    @JavascriptInterface public int scheduleNotification(String title,String body,long at,String actionUrl){String id="test-"+UUID.randomUUID();JSONObject t=new JSONObject();try{t.put("id",id);t.put("title",title);t.put("description",body);t.put("dueAtMs",at);t.put("actionUrl",actionUrl);ReminderEngine.saveTask(a,t);ReminderEngine.schedule(a,t,"due",at,title,"Test");}catch(Exception ignored){}return ReminderEngine.requestCode(id,"due");}
    @JavascriptInterface public void scheduleJourney(String json){try{ReminderEngine.scheduleJourney(a,new JSONObject(json));}catch(Exception ignored){}}
    @JavascriptInterface public void completeTask(String id){ReminderEngine.complete(a,id);}
    @JavascriptInterface public void deleteTask(String id){ReminderEngine.delete(a,id);}
    @JavascriptInterface public String nativeAgenda(boolean tomorrow){return ReminderEngine.agenda(a,tomorrow);}
    @JavascriptInterface public String nativeTasks(){return ReminderEngine.tasks(a).toString();}
    @JavascriptInterface public String snoozedTasks(){return ReminderEngine.snoozed(a);}
    @JavascriptInterface public String reminderDiagnostics(){return ReminderEngine.diagnostics(a);}
    @JavascriptInterface public boolean isIgnoringBatteryOptimizations(){if(Build.VERSION.SDK_INT<23)return true;PowerManager p=(PowerManager)a.getSystemService(Context.POWER_SERVICE);return p!=null&&p.isIgnoringBatteryOptimizations(a.getPackageName());}
    @JavascriptInterface public void openBatteryOptimizationSettings(){a.runOnUiThread(()->{try{a.startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(Exception e){a.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}});}
    @JavascriptInterface public void startVoiceInput(){a.runOnUiThread(a::startVoice);}
    @JavascriptInterface public void recognizeImage(String data){a.runOnUiThread(()->a.ocrBase64(data));}
    @JavascriptInterface public void openExternal(String url){
        try{
            Uri uri=Uri.parse(url); Intent i=new Intent(Intent.ACTION_VIEW,uri); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if("upi".equalsIgnoreCase(uri.getScheme())){
                i.setPackage("com.google.android.apps.nbu.paisa.user");
                try{a.startActivity(i);return;}catch(Exception unavailable){i.setPackage(null);}
            }
            a.startActivity(i);
        }catch(Exception ignored){}
    }
}
