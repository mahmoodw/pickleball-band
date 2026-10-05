package com.wmahmood.pickleball;

import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;
import android.speech.tts.*;
import android.view.KeyEvent;
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
    // Node IDs are opaque: Notify can use "" for its connected band. Only null
    // means we have no route; never use String.isEmpty() to test attachment.
    private String node = null, challenge = "", activeUtterance = "";
    private final MessageOrder order = new MessageOrder();
    private final MediaRemote media = new MediaRemote();
    private long queryStarted, attachingStarted, sendAttempt;
    private Score activeScore;
    private AudioManager audio;
    private AudioFocusRequest duckFocus, pauseFocus, heldFocus;
    private AudioAttributes speechAttributes;
    private AnnouncementAudio announcements;
    private final Runnable poll = new Runnable() { public void run() { discover(); main.postDelayed(this, 8000); } };
    private final OnServiceConnectionListener serviceListener = new OnServiceConnectionListener() {
        public void onServiceConnected() { main.post(() -> { if (!destroyed) { querying = false; discover(); } }); }
        public void onServiceDisconnected() { main.post(() -> { cancelSpeech(); registered = false; attaching = false; node = null; challenge = ""; ++sendAttempt; status("Notify disconnected. Open Notify to reconnect the band."); }); }
    };
    @Override public void onCreate() {
        super.onCreate(); running = true;
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("announcer", "Game announcements", NotificationManager.IMPORTANCE_LOW));
        startForeground(1, notification("Connecting through Notify..."));
        SharedPreferences prefs = getSharedPreferences(PREFS, 0);
        prefs.edit().putBoolean("bandReceived",false).putString("messageSend","Not attempted").apply();
        if (!"notify".equals(prefs.getString("transport", ""))) {
            // IDs saved by the old Mi Fitness provider do not identify Notify nodes.
            prefs.edit().remove("selectedNode").putString("transport", "notify").apply();
        }
        audio = getSystemService(AudioManager.class);
        speechAttributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
        AudioManager.OnAudioFocusChangeListener focusChanges = change -> {
            if (change < 0) { cancelSpeech(); status("Audio interrupted. Tap Speak on the band to repeat."); }
        };
        duckFocus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(speechAttributes)
            .setOnAudioFocusChangeListener(focusChanges, main).build();
        pauseFocus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(speechAttributes)
            .setOnAudioFocusChangeListener(focusChanges, main).build();
        announcements = new AnnouncementAudio(new AnnouncementAudio.Output() {
            public boolean requestFocus(boolean pauseMusic) {
                heldFocus = pauseMusic ? pauseFocus : duckFocus;
                return audio.requestAudioFocus(heldFocus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            }
            public void releaseFocus() { if (heldFocus != null) audio.abandonAudioFocusRequest(heldFocus); heldFocus=null; }
            public boolean musicActive() { return audio.isMusicActive(); }
            public boolean fixedVolume() { return audio.isVolumeFixed(); }
            public int volume() { return audio.getStreamVolume(AudioManager.STREAM_MUSIC); }
            public int maxVolume() { return audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC); }
            public String route() { return mediaRoute(); }
            public void setVolume(int value) { audio.setStreamVolume(AudioManager.STREAM_MUSIC,value,0); }
            public boolean speak(String id, String text) {
                Bundle params = new Bundle();
                params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME,1.0f);
                return tts.speak(text,TextToSpeech.QUEUE_FLUSH,params,id) != TextToSpeech.ERROR;
            }
            public void stop() { if (tts != null) tts.stop(); }
            public void note(String text) { getSharedPreferences(PREFS,0).edit().putString("announcementAudio",text).apply(); }
        }, (delay,action) -> main.postDelayed(action,delay), new AnnouncementAudio.Listener() {
            public void started(String id) {
                if (id.equals(activeUtterance)) {
                    speechStatus("Speaking score");
                    if (activeScore != null) ack(activeScore,"speaking");
                }
            }
            public void finished(String id, String result) {
                if (!id.equals(activeUtterance)) return;
                if (activeScore != null) ack(activeScore,result);
                speechStatus(result.equals("spoken") ? "Score spoken" : result.equals("interrupted") ?
                    "Announcement interrupted. Tap Speak to repeat." : "Speech failed. Check the audio status and voice settings.");
                activeUtterance=""; activeScore=null;
            }
        });
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
            tts.setAudioAttributes(speechAttributes); tts.setSpeechRate(0.85f); ttsReady = true; speechStatus("Offline English voice ready");
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
            String wanted = getSharedPreferences(PREFS, 0).getString("selectedNode", null);
            Node chosen = null;
            for (Node n : nodes) if (n.id != null && n.id.equals(wanted)) chosen = n;
            if (chosen == null && wanted == null && nodes.size() == 1) chosen = nodes.get(0);
            if (chosen == null || chosen.id == null) {
                detach();
                status(chosen != null ? "Notify returned a band without an ID. Select your band again." :
                    nodes.isEmpty() || wanted != null ? "Notify cannot see your selected band. Open Notify to reconnect, or select a band again." : "Multiple wearables connected. Select your Band 10 in this app.");
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
                attaching = false; registered = true; challenge = UUID.randomUUID().toString(); order.reset(); media.reset();
                status("Notify listener ready. Waiting for Pickleball on your band."); hello();
            })).addOnFailureListener(error -> main.post(() -> {
                if (!id.equals(node)) return;
                attaching = false; registered = false;
                status("Band listener failed: " + error.toString() + ". If permission was denied, use Request band access.");
            }));
    }
    private void detach() {
        if (messages != null && node != null) messages.removeListener(node);
        node = null; challenge = ""; registered = false; attaching = false;
        ++sendAttempt;
    }
    private void hello() {
        try { send(new JSONObject().put("type", "hello").put("challenge", challenge).put("mediaVersion", 1)); } catch (Exception ignored) {}
    }
    private void receive(String from, byte[] bytes) {
        if (destroyed || node == null || !node.equals(from) || bytes == null || bytes.length > 8192) return;
        try {
            JSONObject p = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            getSharedPreferences(PREFS,0).edit().putBoolean("bandReceived",true).apply();
            if ("hello".equals(p.optString("type"))) {
                challenge = UUID.randomUUID().toString(); order.reset(); media.reset();
                status("Band app reached the phone. Waiting for score sync...");
                hello(); return;
            }
            if (challenge.isEmpty() || !challenge.equals(p.optString("challenge"))) { hello(); return; }
            if ("media".equals(p.optString("type"))) {
                JSONObject reply = media.receive(p, this::controlMedia);
                if (reply != null) {
                    getSharedPreferences(PREFS,0).edit().putString("media",reply.optString("message")).apply();
                    send(reply);
                }
                return;
            }
            Score score = new Score(p);
            if (!order.accept(score)) return;
            String details = "Us " + score.us + "   |   Them " + score.them + "\n" +
                (score.winner() >= 0 ? "Game over" : (score.serving == 0 ? "We serve" : "They serve") + (score.mode.equals("doubles") ? ", server " + score.server : ""));
            getSharedPreferences(PREFS, 0).edit().putString("score", score.call()).putString("details", details).apply();
            status("Band app connected");
            if (score.action.equals("sync")) {
                media.sync(score.session);
                if (activeScore != null && (!activeScore.gameId.equals(score.gameId) || activeScore.revision != score.revision)) cancelSpeech();
                ack(score, "synced"); return;
            }
            speak(score.speech(), score);
        } catch (Exception error) { status("Invalid band message: " + error.getMessage()); }
    }
    private String controlMedia(String action) {
        if (audio == null) throw new IllegalStateException("Audio unavailable");
        // Apply music-button changes to the restored music level, not a temporary boost.
        if (!activeUtterance.isEmpty()) cancelSpeech();
        if (action.equals("volumeUp") || action.equals("volumeDown")) {
            if (audio.isVolumeFixed()) return "Volume fixed on phone";
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                action.equals("volumeUp") ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, 0);
            return "Volume " + audio.getStreamVolume(AudioManager.STREAM_MUSIC) + " / " + audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        }
        int key = action.equals("next") ? KeyEvent.KEYCODE_MEDIA_NEXT :
            action.equals("previous") ? KeyEvent.KEYCODE_MEDIA_PREVIOUS : KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
        long now = SystemClock.uptimeMillis();
        audio.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, key, 0));
        audio.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, key, 0));
        // Android does not confirm the player's response to media key events.
        return action.equals("next") ? "Next track sent" : action.equals("previous") ? "Previous track sent" : "Play / pause sent";
    }
    private void speak(String text, Score score) {
        if (!ttsReady) { cancelSpeech(); speechStatus("Voice not ready. Wait, then tap Speak again."); if (score != null) ack(score, "error"); return; }
        if (activeScore != null) ack(activeScore,"interrupted");
        activeUtterance = UUID.randomUUID().toString(); activeScore = score;
        SharedPreferences prefs=getSharedPreferences(PREFS,0);
        speechStatus("Preparing: " + text);
        announcements.start(activeUtterance,text,new AnnouncementAudio.Options(
            prefs.getBoolean("announcementBoost",false), prefs.getInt("announcementVolume",75), prefs.getInt("announcementGap",500)));
        if (score != null) ack(score,"speaking");
    }
    private void cancelSpeech() {
        if (activeScore != null) ack(activeScore, "interrupted");
        activeUtterance = ""; activeScore = null;
        if (announcements != null) announcements.cancel();
    }
    private void finishSpeech(String id, boolean ok) {
        if (!destroyed && announcements != null) announcements.completed(id,ok);
    }
    private String mediaRoute() {
        List<AudioDeviceInfo> devices = Build.VERSION.SDK_INT >= 33 ? audio.getAudioDevicesForAttributes(speechAttributes) :
            Arrays.asList(audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS));
        List<String> keys=new ArrayList<>();
        for (AudioDeviceInfo device : devices) keys.add(device.getType() + ":" + device.getId());
        Collections.sort(keys);
        return String.join(",",keys);
    }
    private void ack(Score s, String result) {
        try { send(new JSONObject().put("type","ack").put("session",s.session).put("sequence",s.sequence).put("status",result)); } catch (Exception ignored) {}
    }
    private void send(JSONObject p) {
        if (messages == null || node == null) return;
        final long request=++sendAttempt;
        getSharedPreferences(PREFS,0).edit().putString("messageSend","Waiting for Notify's send result").apply();
        messages.sendMessage(node, p.toString().getBytes(StandardCharsets.UTF_8))
            .addOnSuccessListener(value -> main.post(() -> {
                if (destroyed || request != sendAttempt) return;
                getSharedPreferences(PREFS,0).edit().putString("messageSend","Accepted by Notify (band receipt not confirmed)").apply();
            })).addOnFailureListener(error -> main.post(() -> {
                if (destroyed || request != sendAttempt) return;
                getSharedPreferences(PREFS,0).edit().putString("messageSend",error.toString()).apply();
                status("Message to band failed: " + error.toString());
            }));
    }
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
        destroyed = true; running = false; cancelSpeech();
        if (announcements != null) announcements.close();
        main.removeCallbacksAndMessages(null); detach();
        if (serviceApi != null) serviceApi.unregisterServiceConnectionListener(serviceListener);
        if (tts != null) tts.shutdown();
        getSharedPreferences(PREFS,0).edit().putString("connection","Stopped. Tap Start announcer before playing.").apply();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
