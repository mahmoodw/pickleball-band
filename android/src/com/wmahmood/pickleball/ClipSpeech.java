package com.wmahmood.pickleball;

import android.content.res.AssetManager;
import android.media.*;
import android.os.Handler;
import java.util.concurrent.*;

/** Loads packaged model-generated speech on a worker and plays one complete call. */
final class ClipSpeech {
    private final ExecutorService loader=Executors.newSingleThreadExecutor();
    private final ClipPlayback playback;
    private AudioTrack track;
    ClipSpeech(AssetManager assets,Handler main,AudioAttributes attributes,ClipPlayback.Listener listener) {
        playback=new ClipPlayback((voice,text,callback) -> loader.execute(() -> {
            byte[] result;
            try { result=VoiceClips.assemble(voice,VoiceClips.plan(text),assets::open); }
            catch (Exception error) { result=null; }
            final byte[] pcm=result;
            main.post(() -> callback.ready(pcm));
        }),new ClipPlayback.Player() {
            public void play(byte[] pcm,Runnable done) {
                track=new AudioTrack.Builder().setAudioAttributes(attributes)
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(VoiceClips.RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.length).build();
                if (track.getState()==AudioTrack.STATE_UNINITIALIZED || track.write(pcm,0,pcm.length)!=pcm.length)
                    throw new IllegalStateException("Could not load announcement audio");
                AudioTrack current=track;
                current.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
                    public void onMarkerReached(AudioTrack source) { if (track==current) done.run(); }
                    public void onPeriodicNotification(AudioTrack source) {}
                },main);
                if (current.setNotificationMarkerPosition(pcm.length/2)!=AudioTrack.SUCCESS)
                    throw new IllegalStateException("Could not track announcement completion");
                current.setVolume(1.0f);
                current.play();
            }
            public void stop() {
                AudioTrack old=track; track=null;
                if (old!=null) {
                    try { old.stop(); } catch (IllegalStateException ignored) {}
                    old.release();
                }
            }
        },listener);
    }
    boolean speak(String id,String voice,String text) { return playback.speak(id,voice,text); }
    void stop() { playback.stop(); }
    void close() { playback.close(); loader.shutdownNow(); }
}
