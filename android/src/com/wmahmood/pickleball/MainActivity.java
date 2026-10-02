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
    private TextView score, details, connection, speech, feedback;
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
        button(body,"Start announcer",() -> {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},1);
            startForegroundService(new Intent(this,AnnouncerService.class)); render();
        });
        button(body,"Test phone voice",() -> {
            if (!AnnouncerService.running) { feedback.setText("Start the announcer first, then test the voice."); return; }
            startService(new Intent(this,AnnouncerService.class).setAction("test"));
        });
        button(body,"Grant band access / select band",this::grant);
        button(body,"Open Mi Fitness",() -> {
            Intent launch = getPackageManager().getLaunchIntentForPackage("com.xiaomi.wearable");
            if (launch == null) launch = getPackageManager().getLaunchIntentForPackage("com.mi.health");
            if (launch != null) startActivity(launch); else feedback.setText("Mi Fitness is required for Xiaomi's official connection.");
        });
        button(body,"Voice settings",() -> {
            try { startActivity(new Intent("com.android.settings.TTS_SETTINGS")); }
            catch (Exception e) { feedback.setText("Open Android Settings and search for Text-to-speech."); }
        });
        button(body,"Stop announcer",() -> { stopService(new Intent(this,AnnouncerService.class)); render(); });
        feedback = label(body,"",14,Color.rgb(230,198,132));
        label(body,"Install the matching band app using Notify. Xiaomi's official connection requires Mi Fitness to see the band and grant this app access. Keep the announcer running during play. Audio follows your phone's media volume and output.\n\nPrototype: connection and speech with the phone locked still need testing on your Band 10.",14,Color.rgb(164,183,172));
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
    }
    private void grant() {
        feedback.setText("Looking for bands in Mi Fitness...");
        handler.postDelayed(() -> { if (feedback.getText().toString().equals("Looking for bands in Mi Fitness...")) feedback.setText("Mi Fitness is not responding. Open it, confirm the band is connected, then retry."); },8000);
        Wearable.getNodeApi(this).getConnectedNodes().addOnSuccessListener(nodes -> runOnUiThread(() -> {
            if (nodes.isEmpty()) { feedback.setText("Mi Fitness sees no connected band. Open Mi Fitness first."); return; }
            String[] names=new String[nodes.size()]; for(int i=0;i<nodes.size();i++) names[i]=nodes.get(i).name;
            new AlertDialog.Builder(this).setTitle("Choose your band").setItems(names,(d,index) -> {
                Node node=nodes.get(index);
                getSharedPreferences(AnnouncerService.PREFS,0).edit().putString("selectedNode",node.id).apply();
                Wearable.getAuthApi(this).requestPermission(node.id,Permission.DEVICE_MANAGER)
                    .addOnSuccessListener(result -> runOnUiThread(() -> feedback.setText("Access request completed. Start the announcer and open the band app.")))
                    .addOnFailureListener(error -> runOnUiThread(() -> feedback.setText("Access request failed: "+error.getMessage())));
            }).setNegativeButton("Cancel",null).show();
        })).addOnFailureListener(error -> runOnUiThread(() -> feedback.setText("Mi Fitness error: "+error.getMessage())));
    }
    @Override public void onResume() { super.onResume(); handler.post(refresh); }
    @Override public void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
    @Override public void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }
}
