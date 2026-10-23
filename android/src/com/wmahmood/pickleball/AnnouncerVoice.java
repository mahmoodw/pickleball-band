package com.wmahmood.pickleball;

/** Bundled model voices and the original Android English announcer. */
final class AnnouncerVoice {
    static final String KEY="recordedAnnouncerVoice";
    static final AnnouncerVoice[] VOICES={
        new AnnouncerVoice("morgan","Morgan Freeman"),
        new AnnouncerVoice("kermit","Kermit"),
        new AnnouncerVoice("android","Android default")
    };
    final String id, label;
    private AnnouncerVoice(String id,String label) {
        this.id=id; this.label=label;
    }
    boolean recorded() { return !id.equals("android"); }
    String description() { return label + (recorded() ? " · bundled model audio" : " · offline English TTS"); }
    static AnnouncerVoice find(String id) {
        for (AnnouncerVoice voice : VOICES) if (voice.id.equals(id)) return voice;
        return VOICES[0];
    }
}
