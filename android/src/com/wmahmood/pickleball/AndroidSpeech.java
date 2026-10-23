package com.wmahmood.pickleball;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.*;
import java.util.*;

/** Original offline English announcer, using the phone's default TTS engine. */
final class AndroidSpeech {
    private TextToSpeech tts;
    private boolean ready, closed;
    private String active="", status="Android voice is starting. Try again in a moment.";
    AndroidSpeech(Context context,Handler main,AudioAttributes attributes,ClipPlayback.Listener listener,Runnable changed) {
        tts=new TextToSpeech(context,result -> main.post(() -> {
            if (closed) return;
            try {
                if (result!=TextToSpeech.SUCCESS) throw new IllegalStateException();
                Voice selected=tts.getDefaultVoice();
                if (!eligible(selected)) {
                    List<Voice> voices=new ArrayList<>();
                    if (tts.getVoices()!=null) for (Voice voice : tts.getVoices()) if (eligible(voice)) voices.add(voice);
                    voices.sort(Comparator.comparing((Voice voice) -> !voice.getLocale().equals(Locale.US))
                        .thenComparing(Voice::getName));
                    selected=voices.isEmpty() ? null : voices.get(0);
                }
                if (selected==null || tts.setVoice(selected)!=TextToSpeech.SUCCESS) throw new IllegalStateException();
                tts.setAudioAttributes(attributes);
                if (tts.setSpeechRate(.85f)!=TextToSpeech.SUCCESS) throw new IllegalStateException();
                ready=true; status="Android default voice ready";
            } catch (RuntimeException error) {
                status="Android voice unavailable. Install an offline English voice in Android text-to-speech settings, then restart the announcer.";
            }
            changed.run();
        }));
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            public void onStart(String id) {}
            public void onDone(String id) { finished(id,true); }
            public void onError(String id) { finished(id,false); }
            private void finished(String id,boolean ok) { main.post(() -> {
                if (closed || !active.equals(id)) return;
                active=""; listener.finished(id,ok);
            }); }
        });
    }
    private static boolean eligible(Voice voice) {
        return voice!=null && voice.getLocale().getLanguage().equals("en") && !voice.isNetworkConnectionRequired() &&
            (voice.getFeatures()==null || !voice.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED));
    }
    boolean ready() { return ready && !closed; }
    String status() { return status; }
    boolean speak(String id,String text) {
        if (!ready()) return false;
        stop(); active=id;
        Bundle params=new Bundle(); params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME,1f);
        if (tts.speak(text,TextToSpeech.QUEUE_FLUSH,params,id)==TextToSpeech.ERROR) { active=""; return false; }
        return true;
    }
    void stop() { active=""; if (tts!=null) tts.stop(); }
    void close() { closed=true; ready=false; active=""; if (tts!=null) { tts.stop(); tts.shutdown(); } }
}
