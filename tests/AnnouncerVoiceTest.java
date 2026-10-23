package com.wmahmood.pickleball;

import java.io.*;
import java.util.*;

public final class AnnouncerVoiceTest {
    static int checks;
    static void check(boolean ok) { checks++; if (!ok) throw new AssertionError("Voice check " + checks); }
    static void rejects(String text) {
        boolean failed=false;
        try { VoiceClips.plan(text); } catch (IllegalArgumentException expected) { failed=true; }
        check(failed);
    }
    public static void main(String[] args) throws Exception {
        check(AnnouncerVoice.VOICES.length==3);
        check(AnnouncerVoice.find("morgan").label.equals("Morgan Freeman"));
        check(AnnouncerVoice.find("kermit").label.equals("Kermit"));
        check(AnnouncerVoice.find("android").label.equals("Android default"));
        check(!AnnouncerVoice.find("android").recorded());
        check(AnnouncerVoice.find("morgan").recorded() && AnnouncerVoice.find("kermit").recorded());
        check(AnnouncerVoice.find(null).id.equals("morgan"));
        check(AnnouncerVoice.find("frog").id.equals("morgan"));
        check(VoiceClips.plan("You just got pickled! Turnover. Four. Two. One.")
            .equals(Arrays.asList("pickled","turnover","n04","n02","n01")));
        check(VoiceClips.plan("Correction. Game. twenty two to twenty. They win.")
            .equals(Arrays.asList("correction","game","n22","to","n20","they_win")));
        check(VoiceClips.plan("zero. zero.").equals(Arrays.asList("n00","n00")));
        for (int i=0;i<100;i++) check(VoiceClips.plan(Score.word(i)+".").equals(Collections.singletonList(String.format(Locale.ROOT,"n%02d",i))));
        // Every score pair, serving side, server number and event must have recordings.
        for (int us=0;us<100;us++) for (int them=0;them<100;them++) for (int side=0;side<2;side++) {
            Score score=new Score(ScoreTest.packet("rally",1,0,us,them,side,1+us%2).put("pickled",true).put("turnover",true));
            if (VoiceClips.plan(score.speech()).isEmpty()) throw new AssertionError("Uncovered score");
        }
        check(true);
        for (String text : new String[]{"", "Hello", "../voice", "Game to zero", "zero to unknown"}) rejects(text);
        rejects(null);
        List<String> opened=new ArrayList<>();
        byte[] result=VoiceClips.assemble("kermit",VoiceClips.plan("One. Two."),path -> {
            opened.add(path); return new ByteArrayInputStream(new byte[]{1,2,3,4});
        });
        check(opened.equals(Arrays.asList("voices/kermit/n01.pcm","voices/kermit/n02.pcm")));
        check(result.length==8+VoiceClips.RATE*2*160/1000);
        check(result[0]==1 && result[3]==4 && result[4]==0 && result[result.length-1]==4);
        for (byte[] bad : new byte[][]{new byte[0],new byte[3],new byte[VoiceClips.MAX_CLIP_BYTES+2]}) {
            boolean failed=false;
            try { VoiceClips.assemble("morgan",VoiceClips.plan("one"),path -> new ByteArrayInputStream(bad)); }
            catch (IOException expected) { failed=true; }
            check(failed);
        }
        boolean rejectedAndroid=false;
        try { VoiceClips.assemble("android",VoiceClips.plan("one"),path -> { throw new AssertionError("TTS must not load model clips"); }); }
        catch (IOException expected) { rejectedAndroid=true; } check(rejectedAndroid);
        boolean failed=false;
        try { VoiceClips.assemble("morgan",VoiceClips.plan("one"),path -> { throw new IOException("Missing clip"); }); }
        catch (IOException expected) { failed=true; } check(failed);
        System.out.println(checks+" recorded voice checks passed (including all 20,000 score/serve pairs)");
    }
}
