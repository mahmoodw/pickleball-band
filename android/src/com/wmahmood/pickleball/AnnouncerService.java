package com.wmahmood.pickleball;

import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;
import android.speech.tts.*;
import com.xiaomi.xms.wearable.Wearable;
import com.xiaomi.xms.wearable.node.Node;
import com.xiaomi.xms.wearable.message.MessageApi;
import com.xiaomi.xms.wearable.service.OnServiceConnectionListener;
import com.xiaomi.xms.wearable.service.ServiceApi;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class AnnouncerService extends Service {
    static final String PREFS = "pickleball";
    static volatile boolean running;
    private final Handler main = new Handler(Looper.getMainLooper());
    private MessageApi messages;
    private ServiceApi serviceApi;
    private TextToSpeech tts;
    private boolean ttsReady, querying, attaching, registered, destroyed;
    private String node = "", challenge = "", activeUtterance = "";
    private final MessageOrder order = new MessageOrder();
    private long queryStarted, attachingStarted;
    private Score activeScore;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private final Runnable poll = new Runnable() { public void run() { discover(); main.postDelayed(this, 8000); } };
    private final OnServiceConnectionListener serviceListener = new OnServiceConnectionListener() {
        public void onServiceConnected() { main.post(() -> { if (!destroyed) { querying = false; discover(); } }); }
        public void onServiceDisconnected() { main.post(() -> { cancelSpeech(); registered = false; attaching = false; node = ""; challenge = ""; status("Notify disconnected. Open Notify to reconnect the band."); }); }
    };
    @Override public void onCreate() {
        super.onCreate(); running = true;
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("announcer", "Game announcements", NotificationManager.IMPORTANCE_LOW));
        startForeground(1, notification("Connecting through Notify..."));
        SharedPreferences prefs = getSharedPreferences(PREFS, 0);
        if (!"notify".equals(prefs.getString("transport", ""))) {
            // IDs saved by the old Mi Fitness provider do not identify Notify nodes.
            prefs.edit().remove("selectedNode").putString("transport", "notify").apply();
        }
        audio = getSystemService(AudioManager.class);
        AudioAttributes attrs = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener(change -> { if (change < 0) { cancelSpeech(); status("Audio interrupted. Tap Speak on the band to repeat."); } }, main).build();
        tts = new TextToSpeech(this, result -> main.post(() -> {
            if (destroyed) return;
            if (result != TextToSpeech.SUCCESS || tts.setLanguage(Locale.US) < 0) { speechStatus("English speech unavailable. Install an English voice in Android text-to-speech settings."); return; }
            Voice offline = null;
            if (tts.getVoices() != null) for (Voice voice : tts.getVoices()) {
                if (voice.getLocale().getLanguage().equals("en") && !voice.isNetworkConnectionRequired() &&
                    (voice.getFeatures() == null || !voice.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED))) {
                    if (offline == null || voice.getLocale().equals(Locale.US)) offline = voice;
                }
            }
            if (offline == null || tts.setVoice(offline) != TextToSpeech.SUCCESS) { speechStatus("Install an offline English voice in Android text-to-speech settings, then restart the announcer."); return; }
            tts.setAudioAttributes(attrs); tts.setSpeechRate(0.85f); ttsReady = true; speechStatus("Offline English voice ready");
        }));
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            public void onStart(String id) {}
            public void onDone(String id) { main.post(() -> finishSpeech(id, true)); }
            public void onError(String id) { main.post(() -> finishSpeech(id, false)); }
        });
        main.post(poll);
    }
    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (intent != null && "stop".equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if (intent != null && "test".equals(intent.getAction())) speak("Zero. Zero. Two.", null);
        return START_NOT_STICKY;
    }
    private void discover() {
        if (destroyed) return;
        if (NotifyBridge.availablePackage(this).isEmpty()) {
            detach(); status(NotifyBridge.missingMessage(this)); return;
        }
        if (messages == null) {
            try {
                messages = Wearable.getMessageApi(this);
                serviceApi = Wearable.getServiceApi(this);
                serviceApi.registerServiceConnectionListener(serviceListener);
            } catch (RuntimeException error) {
                messages = null; status("Notify service access failed: " + error.getMessage()); return;
            }
        }
        long now = android.os.SystemClock.elapsedRealtime();
        if (querying && now - queryStarted < 15000) return;
        if (attaching && now - attachingStarted < 15000) return;
        if (attaching) { attaching = false; status("Band listener timed out. Open Notify and check band access."); }
        querying = true; queryStarted = now;
        final long generation = queryStarted;
        Wearable.getNodeApi(this).getConnectedNodes().addOnSuccessListener(nodes -> main.post(() -> {
            if (destroyed || generation != queryStarted) return;
            querying = false;
            String wanted = getSharedPreferences(PREFS, 0).getString("selectedNode", "");
            Node chosen = null;
            for (Node n : nodes) if (n.id.equals(wanted)) chosen = n;
            if (chosen == null && wanted.isEmpty() && nodes.size() == 1) chosen = nodes.get(0);
            if (chosen == null) {
                detach();
                status(nodes.isEmpty() || !wanted.isEmpty() ? "Notify cannot see your selected band. Open Notify to reconnect, or select a band again." : "Multiple wearables connected. Select your Band 10 in this app.");
                return;
            }
            if (!chosen.id.equals(node) || !registered) attach(chosen.id);
        })).addOnFailureListener(error -> main.post(() -> {
            if (destroyed || generation != queryStarted) return;
            querying = false; detach(); status("Notify connection failed: " + error.getMessage());
        }));
        main.postDelayed(() -> {
            if (!destroyed && querying && queryStarted == generation) status("Waiting for Notify's Interconnect service. Open Notify, confirm the band is connected, then retry.");
        }, 6000);
    }
    private void attach(String id) {
        detach(); node = id; attaching = true; attachingStarted = android.os.SystemClock.elapsedRealtime();
        status("Connecting to the band app...");
        messages.addListener(id, (from, bytes) -> main.post(() -> receive(from, bytes)))
            .addOnSuccessListener(value -> main.post(() -> {
                if (destroyed || !id.equals(node)) return;
                attaching = false; registered = true; challenge = UUID.randomUUID().toString(); order.reset();
                hello(); status("Band connected. Open Pickleball on your band.");
            })).addOnFailureListener(error -> main.post(() -> {
                if (!id.equals(node)) return;
                attaching = false; registered = false;
                status("Band messages unavailable. Use Grant band access, then retry. " + error.getMessage());
            }));
    }
    private void detach() {
        if (messages != null && !node.isEmpty()) messages.removeListener(node);
        node = ""; challenge = ""; registered = false; attaching = false;
    }
    private void hello() {
        try { send(new JSONObject().put("type", "hello").put("challenge", challenge)); } catch (Exception ignored) {}
    }
    private void receive(String from, byte[] bytes) {
        if (destroyed || !from.equals(node) || bytes.length > 8192) return;
        try {
            JSONObject p = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            if ("hello".equals(p.optString("type"))) {
                challenge = UUID.randomUUID().toString(); order.reset();
                hello(); return;
            }
            if (challenge.isEmpty() || !challenge.equals(p.optString("challenge"))) { hello(); return; }
            Score score = new Score(p);
            if (!order.accept(score)) return;
            String details = "Us " + score.us + "   |   Them " + score.them + "\n" +
                (score.winner() >= 0 ? "Game over" : (score.serving == 0 ? "We serve" : "They serve") + (score.mode.equals("doubles") ? ", server " + score.server : ""));
            getSharedPreferences(PREFS, 0).edit().putString("score", score.call()).putString("details", details).apply();
            status("Band app connected");
            if (score.action.equals("sync")) {
                if (activeScore != null && (!activeScore.gameId.equals(score.gameId) || activeScore.revision != score.revision)) cancelSpeech();
                ack(score, "synced"); return;
            }
            speak(score.speech(), score);
        } catch (Exception error) { status("Invalid band message: " + error.getMessage()); }
    }
    private void speak(String text, Score score) {
        cancelSpeech();
        if (!ttsReady) { speechStatus("Voice not ready. Wait, then tap Speak again."); if (score != null) ack(score, "error"); return; }
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            speechStatus("Audio is busy. Tap Speak to retry."); if (score != null) ack(score, "error"); return;
        }
        activeUtterance = UUID.randomUUID().toString(); activeScore = score;
        speechStatus("Speaking: " + text);
        int result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, new Bundle(), activeUtterance);
        if (result == TextToSpeech.ERROR) finishSpeech(activeUtterance, false);
        else if (score != null) ack(score, "speaking");
    }
    private void cancelSpeech() {
        if (activeScore != null) ack(activeScore, "interrupted");
        activeUtterance = ""; activeScore = null;
        if (tts != null) tts.stop();
        if (audio != null && focus != null) audio.abandonAudioFocusRequest(focus);
    }
    private void finishSpeech(String id, boolean ok) {
        if (destroyed || !id.equals(activeUtterance)) return;
        if (activeScore != null) ack(activeScore, ok ? "spoken" : "error");
        speechStatus(ok ? "Score spoken" : "Speech failed. Check your Android voice settings.");
        activeUtterance = ""; activeScore = null; audio.abandonAudioFocusRequest(focus);
    }
    private void ack(Score s, String result) {
        try { send(new JSONObject().put("type","ack").put("session",s.session).put("sequence",s.sequence).put("status",result)); } catch (Exception ignored) {}
    }
    private void send(JSONObject p) { if (messages != null && !node.isEmpty()) messages.sendMessage(node, p.toString().getBytes(StandardCharsets.UTF_8)); }
    private void speechStatus(String text) { getSharedPreferences(PREFS,0).edit().putString("speech",text).apply(); }
    private void status(String text) {
        if (destroyed) return;
        getSharedPreferences(PREFS,0).edit().putString("connection", text).apply();
        getSystemService(NotificationManager.class).notify(1, notification(text));
    }
    private Notification notification(String text) {
        PendingIntent open = PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this,1,new Intent(this,AnnouncerService.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,"announcer").setContentTitle("Pickleball announcer").setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentIntent(open).setOngoing(true)
            .addAction(new Notification.Action.Builder(null,"Stop",stop).build()).build();
    }
    @Override public void onDestroy() {
        destroyed = true; running = false; main.removeCallbacksAndMessages(null); cancelSpeech(); detach();
        if (serviceApi != null) serviceApi.unregisterServiceConnectionListener(serviceListener);
        if (tts != null) tts.shutdown();
        getSharedPreferences(PREFS,0).edit().putString("connection","Stopped. Tap Start announcer before playing.").apply();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
