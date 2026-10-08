package com.wmahmood.pickleball;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.xiaomi.xms.wearable.Wearable;
import com.xiaomi.xms.wearable.auth.Permission;
import com.xiaomi.xms.wearable.node.Node;

public class MainActivity extends Activity {
    private TextView score, details, connection, speech, music, announcementAudio, feedback;
    private final BandSelection selection = new BandSelection();
    private long accessRequest;
    private boolean accessPending;
    private String accessStatus = "Not requested (separate from band selection)";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() { public void run() { render(); handler.postDelayed(this, 1000); } };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(7,21,16)); getWindow().setNavigationBarColor(Color.rgb(7,21,16));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(24),dp(36),dp(24),dp(28)); body.setBackgroundColor(Color.rgb(7,21,16));
        scroll.setOnApplyWindowInsetsListener((view,insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        scroll.addView(body); setContentView(scroll);
        label(body,"PICKLEBALL",20,Color.rgb(186,247,94));
        label(body,"Your wrist keeps score.\nYour phone makes the call.",20,Color.WHITE);
        score = label(body,"—",54,Color.rgb(186,247,94));
        details = label(body,"Open Pickleball on your band to sync the score.",17,Color.WHITE);
        connection = label(body,"",15,Color.rgb(164,183,172));
        speech = label(body,"",15,Color.rgb(164,183,172));
        music = label(body,"",15,Color.rgb(164,183,172));
        announcementAudio = label(body,"",15,Color.rgb(164,183,172));
        button(body,"Start announcer",() -> {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},1);
            startForegroundService(new Intent(this,AnnouncerService.class)); render();
        });
        button(body,"Test phone voice",() -> {
            if (!AnnouncerService.running) { feedback.setText("Start the announcer first, then test the voice."); return; }
            startService(new Intent(this,AnnouncerService.class).setAction("test"));
        });
        button(body,"Announcement volume",this::announcementSettings);
        button(body,"Select band",this::selectBand);
        button(body,"Open Notify",() -> {
            Intent launch = NotifyBridge.launchIntent(this);
            if (launch != null) startActivity(launch); else feedback.setText(NotifyBridge.missingMessage(this));
        });
        button(body,"Voice settings",() -> {
            try { startActivity(new Intent("com.android.settings.TTS_SETTINGS")); }
            catch (Exception e) { feedback.setText("Open Android Settings and search for Text-to-speech."); }
        });
        button(body,"Stop announcer",() -> { stopService(new Intent(this,AnnouncerService.class)); render(); });
        button(body,"Request band access (if needed)",this::grantAccess);
        button(body,"Copy connection details",this::copyDiagnostics);
        feedback = label(body,"",14,Color.rgb(230,198,132));
        label(body,"Connect your band in Notify for Xiaomi and keep Notify running. Select your band, then start the announcer. Only use Request band access if a permission error is reported. Mi Fitness and Tasker are not required. Use Announcement volume to choose a louder voice, music pause or ducking, and a speaker settling gap. Audio follows your phone's media output.\n\nVersion 0.1.11. The band opens on two large rally buttons. Tap the middle serve/score area to speak; hold it to undo. Swipe right for live score, Undo, Speak and Correct; swipe right again for settings. Swipe left from the rally buttons for music. Start playback in your phone music app first.",14,Color.rgb(164,183,172));
    }
    private TextView label(LinearLayout parent,String text,int sp,int color) {
        TextView v=new TextView(this); v.setText(text); v.setTextSize(sp); v.setTextColor(color); v.setPadding(0,dp(10),0,dp(14)); parent.addView(v); return v;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void button(LinearLayout parent,String title,Runnable action) {
        Button b=new Button(this); b.setText(title); b.setAllCaps(false); b.setOnClickListener(v -> action.run());
        parent.addView(b,new LinearLayout.LayoutParams(-1,dp(64)));
    }
    private void render() {
        SharedPreferences p=getSharedPreferences(AnnouncerService.PREFS,0);
        score.setText(p.getString("score","—")); details.setText(p.getString("details","Open Pickleball on your band to sync the score."));
        connection.setText(AnnouncerService.running ? p.getString("connection","Connecting...") : "Announcer stopped");
        speech.setText(p.getString("speech","Use Test phone voice before your first game."));
        music.setText("Music: " + (AnnouncerService.running ? p.getString("media","Start music on your phone, then swipe left on the band's Play page.") : "Start the announcer to enable band controls."));
        announcementAudio.setText("Announcement volume: " + (p.getBoolean("announcementBoost",false) ?
            p.getInt("announcementVolume",75) + "% when the normal level is lower" : "Same as music") +
            (p.getBoolean("announcementPauseMusic",p.getBoolean("announcementBoost",false)) ? "; pause music requested" : "; ducking requested") +
            (p.getBoolean("announcementPauseMusic",p.getBoolean("announcementBoost",false)) ?
                "\nSpeaker settling gap: " + p.getInt("announcementGap",500) + " ms" : "\nNo added announcement delay") +
            (AnnouncerService.running ? "\n" + p.getString("announcementAudio","Ready to test") : ""));
    }
    private void announcementSettings() {
        SharedPreferences prefs=getSharedPreferences(AnnouncerService.PREFS,0);
        LinearLayout form=new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(20),dp(12),dp(20),dp(12));
        form.setBackgroundColor(Color.rgb(7,21,16));
        Switch boost=new Switch(this); boost.setText("Raise volume for announcements"); boost.setTextColor(Color.WHITE);
        boost.setChecked(prefs.getBoolean("announcementBoost",false)); form.addView(boost);
        Switch pause=new Switch(this); pause.setText("Pause music during announcements"); pause.setTextColor(Color.WHITE);
        // Before this setting existed, boosted calls paused and normal calls ducked.
        pause.setChecked(prefs.getBoolean("announcementPauseMusic",prefs.getBoolean("announcementBoost",false))); form.addView(pause);
        TextView modeHint=label(form,"",14,Color.rgb(164,183,172));
        TextView volumeLabel=label(form,"",17,Color.WHITE);
        SeekBar volume=new SeekBar(this); volume.setMax(16);
        volume.setProgress((Math.max(20,Math.min(100,prefs.getInt("announcementVolume",75)))-20)/5);
        form.addView(volume,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView gapLabel=label(form,"",17,Color.WHITE);
        final int minGap=AnnouncementAudio.Options.MIN_GAP_MS, gapStep=AnnouncementAudio.Options.GAP_STEP_MS;
        SeekBar gap=new SeekBar(this); gap.setMax((AnnouncementAudio.Options.MAX_GAP_MS-minGap)/gapStep);
        gap.setProgress(Math.max(0,Math.min(gap.getMax(),Math.round((prefs.getInt("announcementGap",500)-minGap)/(float)gapStep))));
        form.addView(gap,new LinearLayout.LayoutParams(-1,dp(48)));
        LinearLayout gapSteps=new LinearLayout(this); gapSteps.setOrientation(LinearLayout.HORIZONTAL);
        Button less=new Button(this), more=new Button(this);
        less.setText("− 25 ms"); more.setText("+ 25 ms"); less.setAllCaps(false); more.setAllCaps(false);
        less.setOnClickListener(v -> gap.setProgress(gap.getProgress()-1));
        more.setOnClickListener(v -> gap.setProgress(gap.getProgress()+1));
        gapSteps.addView(less,new LinearLayout.LayoutParams(0,dp(48),1));
        gapSteps.addView(more,new LinearLayout.LayoutParams(0,dp(48),1)); form.addView(gapSteps);
        Runnable labels=() -> {
            volumeLabel.setText("Announcement volume: " + (20+volume.getProgress()*5) + "%");
            gapLabel.setText("Speaker settling gap: " + (minGap+gap.getProgress()*gapStep) + " ms" + (pause.isChecked() ? "" : " (pause only)"));
            boolean settling=pause.isChecked();
            volume.setEnabled(boost.isChecked()); gap.setEnabled(settling);
            less.setEnabled(settling && gap.getProgress()>0); more.setEnabled(settling && gap.getProgress()<gap.getMax());
            modeHint.setText(pause.isChecked() ?
                "Music is asked to pause. With volume boost on, the boost is skipped if music keeps playing. The normal level returns before focus is released." :
                boost.isChecked() ? "Music is asked to duck while the voice is boosted, with no added delay. Android and your player control how low it goes; music may also get louder. Some players pause even with ducking. Try a modest boost with Save & test." :
                "Music is asked to duck at your normal media volume, with no added delay. Android and your player control the amount; some players pause instead.");
        };
        SeekBar.OnSeekBarChangeListener changes=new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar,int value,boolean fromUser) { labels.run(); }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {}
        };
        volume.setOnSeekBarChangeListener(changes); gap.setOnSeekBarChangeListener(changes);
        boost.setOnCheckedChangeListener((button,checked) -> labels.run());
        pause.setOnCheckedChangeListener((button,checked) -> labels.run()); labels.run();
        label(form,"Pause mode uses the gap before a boost, after changing volume and before releasing music. Adjust in 25 ms steps; try 350 ms. With Pause off, speech starts without these waits; TTS and Bluetooth still have their own latency. Media stays muted at zero volume.",14,Color.rgb(164,183,172));
        Runnable save=() -> {
            prefs.edit().putBoolean("announcementBoost",boost.isChecked()).putBoolean("announcementPauseMusic",pause.isChecked())
                .putInt("announcementVolume",20+volume.getProgress()*5).putInt("announcementGap",minGap+gap.getProgress()*gapStep).apply();
            render();
        };
        ScrollView scroll=new ScrollView(this); scroll.addView(form);
        new AlertDialog.Builder(this).setTitle("Announcement volume").setView(scroll)
            .setPositiveButton("Save",(dialog,which) -> save.run())
            .setNeutralButton("Save & test",(dialog,which) -> {
                save.run();
                if (AnnouncerService.running) startService(new Intent(this,AnnouncerService.class).setAction("test"));
                else feedback.setText("Settings saved. Start the announcer, then tap Test phone voice.");
            }).setNegativeButton("Cancel",null).show();
    }
    private void selectBand() {
        if (NotifyBridge.availablePackage(this).isEmpty()) { feedback.setText(NotifyBridge.missingMessage(this)); return; }
        accessRequest++; accessPending = false;
        final long request = selection.begin();
        feedback.setText(selection.status());
        handler.postDelayed(() -> { if (selection.timedOut(request)) feedback.setText(selection.status()); },8000);
        Wearable.getNodeApi(this).getConnectedNodes().addOnSuccessListener(nodes -> runOnUiThread(() -> {
            if (!selection.discovered(request, nodes.size())) return;
            feedback.setText(selection.status());
            if (nodes.isEmpty()) return;
            String[] names=new String[nodes.size()]; for(int i=0;i<nodes.size();i++) names[i]=nodes.get(i).name;
            new AlertDialog.Builder(this).setTitle("Choose your band").setItems(names,(d,index) -> {
                Node node=nodes.get(index);
                if (node.id == null) { cancelSelection(); feedback.setText("Notify returned a band without an ID. Open Notify, then select your band again."); return; }
                if (!selection.select(request)) return;
                getSharedPreferences(AnnouncerService.PREFS,0).edit().putString("selectedNode",node.id).putString("transport","notify").apply();
                feedback.setText(selection.status());
            }).setNegativeButton("Cancel",(d,which) -> cancelSelection()).setOnCancelListener(d -> cancelSelection()).show();
        })).addOnFailureListener(error -> runOnUiThread(() -> {
            if (selection.failed(request, error.toString())) feedback.setText(selection.status());
        }));
    }
    private void cancelSelection() { selection.cancel(); feedback.setText(selection.status()); }
    private void grantAccess() {
        String node=getSharedPreferences(AnnouncerService.PREFS,0).getString("selectedNode",null);
        if (node == null) { feedback.setText("Select your band first."); return; }
        if (NotifyBridge.availablePackage(this).isEmpty()) { feedback.setText(NotifyBridge.missingMessage(this)); return; }
        final long request=++accessRequest;
        accessPending=true;
        accessStatus="Waiting for Notify's device-management permission response...";
        feedback.setText(accessStatus);
        handler.postDelayed(() -> {
            if (request != accessRequest || !accessPending) return;
            accessPending=false;
            accessStatus="Notify did not answer the permission request. This does not mean your band is disconnected. Try Start announcer; if messages report a permission error, copy connection details.";
            feedback.setText(accessStatus);
        },10000);
        Wearable.getAuthApi(this).requestPermission(node,Permission.DEVICE_MANAGER)
            .addOnSuccessListener(result -> runOnUiThread(() -> {
                if (request != accessRequest || isDestroyed()) return;
                accessPending=false;
                boolean granted=false;
                if (result != null) for (Permission permission : result) if (Permission.DEVICE_MANAGER.getName().equals(permission.getName())) granted=true;
                accessStatus=granted ? "Band access granted. Start the announcer and open the band app." : "Band access was not granted. Check the authorization prompt in Notify.";
                feedback.setText(accessStatus);
            }))
            .addOnFailureListener(error -> runOnUiThread(() -> {
                if (request != accessRequest || isDestroyed()) return;
                accessPending=false;
                accessStatus="Access request failed: "+error.toString();
                feedback.setText(accessStatus);
            }));
    }
    private void copyDiagnostics() {
        SharedPreferences p=getSharedPreferences(AnnouncerService.PREFS,0);
        StringBuilder text=new StringBuilder("Pickleball 0.1.11 / Android API ").append(Build.VERSION.SDK_INT);
        for (String name : NotifyBridge.PACKAGES) {
            try { text.append("\n").append(name).append(" ").append(getPackageManager().getPackageInfo(name,0).versionName); }
            catch (android.content.pm.PackageManager.NameNotFoundException ignored) {}
        }
        text.append("\nNotify service visible: ").append(!NotifyBridge.availablePackage(this).isEmpty());
        text.append("\nAnnouncer running: ").append(AnnouncerService.running);
        text.append("\nConnection: ").append(p.getString("connection","Not started"));
        text.append("\nVoice: ").append(p.getString("speech","Not started"));
        text.append("\nMusic: ").append(p.getString("media","No controls sent"));
        text.append("\nAnnouncement boost: ").append(p.getBoolean("announcementBoost",false));
        text.append("\nPause music: ").append(p.getBoolean("announcementPauseMusic",p.getBoolean("announcementBoost",false)));
        text.append("\nAnnouncement volume: ").append(p.getInt("announcementVolume",75)).append("%");
        text.append("\nSpeaker settling gap: ").append(p.getInt("announcementGap",500)).append(" ms");
        text.append("\nAnnouncement audio: ").append(p.getString("announcementAudio","Not started"));
        text.append("\nSelection: ").append(selection.status());
        text.append("\nBand saved: ").append(p.contains("selectedNode"));
        String selectedId=p.getString("selectedNode",null);
        text.append("\nSaved band ID kind: ").append(selectedId == null ? "Absent" : selectedId.isEmpty() ? "Empty (valid Notify route)" : "Nonempty");
        text.append("\nPermission request: ").append(accessStatus);
        text.append("\nLast message send: ").append(p.getString("messageSend","Not attempted"));
        text.append("\nBand message received: ").append(p.getBoolean("bandReceived",false));
        getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Pickleball connection",text.toString()));
        feedback.setText("Connection details copied. Paste them into your bug report or chat.");
    }
    @Override public void onResume() { super.onResume(); handler.post(refresh); }
    @Override public void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
    @Override public void onDestroy() { selection.cancel(); accessRequest++; handler.removeCallbacksAndMessages(null); super.onDestroy(); }
}
