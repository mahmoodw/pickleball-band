package com.wmahmood.pickleball;

/** Offline styles adjust an installed TTS voice; they do not clone a speaker. */
final class AnnouncerVoice {
    static final String STYLE_KEY="announcerVoiceStyle", VOICE_KEY="announcerVoiceName", ENGINE_KEY="announcerVoiceEngine";
    static final AnnouncerVoice[] STYLES={
        new AnnouncerVoice("classic","Classic",1.0f,0.85f),
        new AnnouncerVoice("narrator","Deep narrator",0.72f,0.78f),
        new AnnouncerVoice("frog","Playful frog",1.35f,0.95f)
    };
    final String id, label;
    final float pitch, rate;
    private AnnouncerVoice(String id,String label,float pitch,float rate) {
        this.id=id; this.label=label; this.pitch=pitch; this.rate=rate;
    }
    static AnnouncerVoice find(String id) {
        for (AnnouncerVoice style : STYLES) if (style.id.equals(id)) return style;
        return STYLES[0];
    }
}
