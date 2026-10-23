package com.wmahmood.pickleball;

import java.util.*;

public final class AnnouncerVoiceTest {
    static int checks;
    static void check(boolean ok) { checks++; if (!ok) throw new AssertionError("Voice check " + checks); }
    public static void main(String[] args) {
        check(AnnouncerVoice.find(null).id.equals("classic"));
        check(AnnouncerVoice.find("deleted-profile").id.equals("classic"));
        check(AnnouncerVoice.find("classic").pitch==1.0f && AnnouncerVoice.find("classic").rate==0.85f);
        check(AnnouncerVoice.find("narrator").pitch<1.0f && AnnouncerVoice.find("narrator").rate<0.85f);
        check(AnnouncerVoice.find("frog").pitch>1.0f && AnnouncerVoice.find("frog").rate>0.85f);
        check(OfflineVoices.eligible(Locale.US,false,null));
        check(OfflineVoices.eligible(Locale.UK,false,Collections.emptySet()));
        check(!OfflineVoices.eligible(Locale.US,true,Collections.emptySet()));
        check(!OfflineVoices.eligible(Locale.US,false,Collections.singleton("notInstalled")));
        check(!OfflineVoices.eligible(Locale.FRENCH,false,null));
        check(!OfflineVoices.eligible(null,false,null));
        System.out.println(checks+" offline voice checks passed");
    }
}
