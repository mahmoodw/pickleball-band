package com.wmahmood.pickleball;

import android.app.*;
import android.content.*;
import android.os.*;
import org.json.JSONObject;
import java.io.*;

/** Explicitly started phone recorder; no native band workout is launched. */
public class WorkoutService extends Service {
    static WorkoutService active;
    static final String PREFS="workout-lab";
    private final Handler main=new Handler(Looper.getMainLooper());
    private WorkoutSession session;
    private HeartRateClient heart;
    private String hrStatus="No heart-rate source selected", error="";
    private long lastHr=-1, lastSavedHr=-1, lastBand=-1, samples, bandQueries, scores, hiddenSamples;
    private int bpm;
    private boolean stopped, bandVisible, haveVisibility;
    private final Runnable tick=new Runnable() { public void run() {
        if (session==null || stopped) return;
        record("tick",""); saveStatus(); main.postDelayed(this,5000);
    }};
    static File directory(Context context) { return new File(context.getFilesDir(),"workouts"); }
    static long now() { return SystemClock.elapsedRealtime(); }
    @Override public void onCreate() {
        super.onCreate(); active=this;
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("workout","Workout recording",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,10,new Intent(this,WorkoutActivity.class),PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,11,new Intent(this,WorkoutService.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE);
        startForeground(2,new Notification.Builder(this,"workout").setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("Pickleball Lab recording").setContentText("Session timing and available heart rate are saved on this phone.")
            .setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"Finish session",stop).build()).build());
    }
    @Override public int onStartCommand(Intent intent,int flags,int id) {
        String action=intent==null ? "" : intent.getAction();
        if ("stop".equals(action)) { finish("Finished"); return START_NOT_STICKY; }
        if (session==null) {
            try {
                session=new WorkoutSession(directory(this),System.currentTimeMillis(),now());
                getSharedPreferences(PREFS,0).edit().putString("latest",session.file.getName()).apply();
                main.post(tick); connectHeart();
            } catch (IOException e) {
                error="Cannot create workout file";
                getSharedPreferences(PREFS,0).edit().putString("report",error).putString("state","Save failed").apply();
                finish("Save failed");
            }
        } else if ("retry".equals(action)) connectHeart();
        saveStatus(); return START_NOT_STICKY;
    }
    private void connectHeart() {
        if (heart!=null) heart.close(); heart=null; lastHr=-1; bpm=0;
        String address=getSharedPreferences(PREFS,0).getString("source","");
        if (address.isEmpty()) { hrStatus="No heart-rate source selected. Timer still records."; return; }
        heart=new HeartRateClient(this,new HeartRateClient.Listener() {
            public void status(String value) { hrStatus=value; saveStatus(); }
            public void sample(int value) {
                long time=now(); bpm=value; lastHr=time;
                if (lastSavedHr>=0 && time-lastSavedHr<1000) return;
                if (record("heartRate",Integer.toString(value))) {
                    lastSavedHr=time; samples++; if (haveVisibility && !bandVisible) hiddenSamples++;
                }
                saveStatus();
            }
        });
        heart.connect(address);
    }
    private boolean record(String event,String value) {
        if (session==null || stopped || !error.isEmpty()) return false;
        try { session.record(now(),event,value); return true; }
        catch (IOException e) {
            error="Workout storage failed. Finish and export the partial session.";
            // Preserve the existing file and expose the failure; never claim new samples saved.
            main.removeCallbacks(tick); saveStatus(); return false;
        }
    }
    static JSONObject snapshot(Context context, boolean visible) throws Exception {
        WorkoutService recorder=active;
        JSONObject out=new JSONObject().put("running",recorder!=null && recorder.session!=null && !recorder.stopped);
        if (recorder==null || recorder.session==null) return out.put("elapsed",0).put("samples",0).put("bpm",0).put("hrAge",-1).put("message","Start session on phone");
        recorder.lastBand=now(); recorder.bandQueries++;
        if (!recorder.haveVisibility || recorder.bandVisible!=visible) recorder.record("bandVisible",Boolean.toString(visible));
        recorder.bandVisible=visible; recorder.haveVisibility=true;
        recorder.record("bandPing",visible ? "visible" : "hidden");
        recorder.saveStatus();
        long age=recorder.lastHr<0 ? -1 : now()-recorder.lastHr;
        return out.put("elapsed",recorder.session.elapsed(now())/1000).put("samples",recorder.samples)
            .put("bpm",HeartRateMeasurement.fresh(recorder.lastHr,now()) ? recorder.bpm : 0).put("hrAge",age<0 ? -1 : age/1000)
            .put("message",!recorder.error.isEmpty() ? "Storage error on phone" : age<0 ? "Waiting for heart rate" : age>15000 ? "Heart rate is stale" : "Heart rate received");
    }
    static void score(Score value) {
        WorkoutService recorder=active;
        if (recorder==null) return;
        recorder.scores++; recorder.lastBand=now();
        recorder.record("score",value.action+" "+value.call()); recorder.saveStatus();
    }
    private void saveStatus() {
        if (session==null) return;
        getSharedPreferences(PREFS,0).edit().putString("report",report()).putString("state",stopped ? "Finished" : "Recording").apply();
    }
    String report() {
        long age=lastHr<0 ? -1 : Math.max(0,(now()-lastHr)/1000);
        return "Session: "+(stopped ? "Finished" : "Recording")+"\nElapsed: "+(session==null ? 0 : session.elapsed(now())/1000)+" s"+
            "\nHeart-rate link: "+hrStatus+"\nHeart rate: "+(HeartRateMeasurement.fresh(lastHr,now()) ? bpm+" bpm" : "No fresh reading")+
            "\nLast HR sample age: "+(age<0 ? "none" : age+" s")+"\nSaved HR samples: "+samples+
            "\nSamples while band last reported hidden: "+hiddenSamples+"\nBand status requests: "+bandQueries+
            "\nScore messages received: "+scores+"\nLast band message age: "+(lastBand<0 ? "none" : (now()-lastBand)/1000+" s")+
            (error.isEmpty() ? "" : "\n"+error);
    }
    private void finish(String reason) {
        if (!stopped) record("finish",reason);
        stopped=true; main.removeCallbacksAndMessages(null);
        if (heart!=null) heart.close(); heart=null;
        saveStatus(); stopSelf();
    }
    @Override public void onDestroy() {
        if (!stopped) { record("interrupted","Recorder stopped by Android"); stopped=true; saveStatus(); }
        main.removeCallbacksAndMessages(null); if (heart!=null) heart.close();
        if (active==this) active=null;
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
