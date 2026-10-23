package com.wmahmood.pickleball;

/** The only selectable voices are the bundled recordings rendered through RVC. */
final class AnnouncerVoice {
    static final String KEY="recordedAnnouncerVoice";
    static final AnnouncerVoice[] VOICES={
        new AnnouncerVoice("morgan","Morgan Freeman"),
        new AnnouncerVoice("kermit","Kermit")
    };
    final String id, label;
    private AnnouncerVoice(String id,String label) {
        this.id=id; this.label=label;
    }
    static AnnouncerVoice find(String id) {
        for (AnnouncerVoice voice : VOICES) if (voice.id.equals(id)) return voice;
        return VOICES[0];
    }
}
