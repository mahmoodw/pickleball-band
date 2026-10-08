package com.wmahmood.pickleball;

import java.util.*;

/** Exercises ordering against an explicit clock, without Android or real-time sleeps. */
public final class AnnouncementAudioTest {
    private static int checks;
    private static void check(boolean ok,String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static final AnnouncementAudio.Options BOOST=new AnnouncementAudio.Options(true,true,75,500);
    static final class Timer implements AnnouncementAudio.Scheduler {
        static final class Task {
            final long time, order; final Runnable action;
            Task(long time,long order,Runnable action) { this.time=time; this.order=order; this.action=action; }
        }
        long now, order;
        final PriorityQueue<Task> tasks=new PriorityQueue<>(Comparator.comparingLong((Task t)->t.time).thenComparingLong(t->t.order));
        public void after(int ms,Runnable action) { tasks.add(new Task(now+ms,++order,action)); }
        void until(long time) {
            while (!tasks.isEmpty() && tasks.peek().time<=time) { Task task=tasks.remove(); now=task.time; task.action.run(); }
            now=time;
        }
    }
    static final class Harness implements AnnouncementAudio.Output, AnnouncementAudio.Listener {
        final Timer timer=new Timer();
        final List<String> events=new ArrayList<>(), notes=new ArrayList<>(), spoken=new ArrayList<>(), finished=new ArrayList<>();
        final AnnouncementAudio audio=new AnnouncementAudio(this,timer,this);
        int volume=40, maximum=100, failSetCount;
        boolean music=true, pauseWorks=true, focusWorks=true, fixed, speechWorks=true, speechThrows;
        String route="speaker";
        void event(String text) { events.add(timer.now+":"+text); }
        public boolean requestFocus(boolean pause) {
            event(pause ? "pause" : "duck");
            if (focusWorks && pause && pauseWorks) music=false;
            return focusWorks;
        }
        public void releaseFocus() { event("release@"+volume); }
        public boolean musicActive() { return music; }
        public boolean fixedVolume() { return fixed; }
        public int volume() { return volume; }
        public int maxVolume() { return maximum; }
        public String route() { return route; }
        public void setVolume(int value) {
            volume=value; event("volume="+value);
            if (failSetCount>0) { failSetCount--; throw new IllegalStateException("volume service failed after write"); }
        }
        public boolean speak(String id,String text) {
            event("speak="+id+"@"+volume); spoken.add(id);
            if (speechThrows) throw new IllegalStateException("TTS failed");
            return speechWorks;
        }
        public void stop() { event("stop"); }
        public void note(String text) { notes.add(text); }
        public void started(String id) { event("started="+id); }
        public void finished(String id,String result) { finished.add(id+":"+result); }
        void start(String id) { audio.start(id,"Zero. Zero. Two.",BOOST); }
        boolean wroteVolume() { return events.stream().anyMatch(e->e.contains(":volume=")); }
        boolean released() { return events.stream().anyMatch(e->e.contains(":release@")); }
    }
    public static void main(String[] args) {
        Harness h=new Harness(); h.start("a");
        check(h.events.equals(Arrays.asList("0:pause")),"Volume changed before requesting a pause");
        h.timer.until(599); check(h.volume==40 && h.spoken.isEmpty(),"Did not wait for buffered music to drain");
        h.timer.until(600); check(h.volume==75 && h.spoken.isEmpty(),"Missing speaker-volume settling gap");
        h.timer.until(1100); check(h.spoken.equals(Arrays.asList("a")),"Voice did not start after the boost");
        h.audio.completed("a",true);
        check(h.volume==40 && !h.released(),"Music resumed before volume restoration");
        h.timer.until(1599); check(!h.released(),"Restoration settling gap was skipped");
        h.timer.until(1600); check(h.events.contains("1600:release@40") && h.finished.contains("a:spoken"),"Wrong restored level or completion");

        h=new Harness(); h.pauseWorks=false; h.start("ignored-pause"); h.timer.until(1500);
        check(!h.wroteVolume() && h.spoken.size()==1,"Player ignoring focus was amplified");
        check(h.notes.contains("Music kept playing; volume boost skipped"),"Missing skipped-boost explanation");

        h=new Harness(); h.pauseWorks=false; h.start("slow-pause"); h.timer.until(600); h.music=false;
        h.timer.until(1199); check(h.volume==40,"Slow player did not receive a full drain gap");
        h.timer.until(1200); check(h.volume==75,"Slow pause never enabled the boost");

        h=new Harness(); h.start("restart-before-boost"); h.timer.until(300); h.music=true; h.timer.until(600);
        check(!h.wroteVolume() && h.spoken.size()==1,"Music restarting during the drain gap was amplified");

        h=new Harness(); h.start("restart-after-boost"); h.timer.until(700); h.music=true; h.timer.until(1100);
        check(h.volume==40 && h.spoken.isEmpty(),"Resumed music was not restored before speech");
        h.timer.until(1600); check(h.events.contains("1600:speak=restart-after-boost@40"),"Fallback speech did not use normal volume");

        h=new Harness(); h.start("cancel-before-boost"); h.timer.until(300); h.audio.cancel(); h.timer.until(2000);
        check(!h.wroteVolume() && h.spoken.isEmpty() && h.released(),"Cancelled preparation still raised volume or spoke");

        h=new Harness(); h.start("cancel-after-boost"); h.timer.until(650); h.audio.cancel();
        check(h.volume==40,"Cancellation did not immediately restore the normal level");
        h.timer.until(2000); check(h.spoken.isEmpty() && h.released(),"Stale start survived cancellation");

        h=new Harness(); h.start("a"); h.timer.until(1100); h.start("b");
        check(h.volume==40,"Replacement captured an already boosted baseline");
        h.timer.until(1200); h.start("c"); h.audio.completed("a",true); h.timer.until(2700);
        check(h.spoken.equals(Arrays.asList("a","c")),"Rapid score changes replayed an obsolete announcement");
        h.audio.completed("a",true); check(h.volume==75,"Stale completion restored the current call's volume");
        h.audio.completed("c",true); h.timer.until(3200);
        check(h.volume==40 && h.finished.contains("c:spoken"),"Replacement lost the original music volume");

        for(int failure=0;failure<3;failure++) {
            h=new Harness(); h.speechWorks=failure!=0; h.speechThrows=failure==1;
            h.start("failed"); h.timer.until(1100);
            if (failure==2) h.audio.completed("failed",false);
            check(h.volume==40,"TTS failure left volume raised: "+failure);
            h.timer.until(1600); check(h.finished.contains("failed:error") && h.released(),"TTS failure leaked focus: "+failure);
        }
        h=new Harness(); h.failSetCount=1; h.start("volume-failure"); h.timer.until(1100);
        check(h.volume==40 && h.spoken.isEmpty() && h.released(),"Partially failed volume write was not restored");

        h=new Harness(); h.focusWorks=false; h.start("busy"); h.timer.until(2000);
        check(!h.wroteVolume() && h.spoken.isEmpty() && h.finished.contains("busy:error"),"Denied focus changed audio");

        h=new Harness(); h.start("hung"); h.timer.until(13100);
        check(h.volume==40,"Missing TTS callback left volume raised");
        h.timer.until(13600); check(h.released() && h.finished.contains("hung:error"),"Watchdog did not release focus");
        h.audio.completed("hung",true); check(!h.finished.contains("hung:spoken"),"Late TTS completion changed the result");

        h=new Harness(); h.start("manual"); h.timer.until(1100); h.volume=55; h.audio.completed("manual",true); h.timer.until(1600);
        check(h.volume==55,"Restoration overwrote a manual volume adjustment");

        h=new Harness(); h.start("route-change"); h.timer.until(1100); h.route="headphones"; h.volume=20; h.timer.until(1750);
        check(h.volume==20 && h.finished.contains("route-change:interrupted"),"Old speaker level was written into a new output");

        h=new Harness(); h.volume=0; h.start("muted"); h.timer.until(1100);
        check(h.volume==0 && !h.wroteVolume(),"Boost unmuted media");
        h=new Harness(); h.fixed=true; h.start("fixed"); h.timer.until(1100);
        check(!h.wroteVolume(),"Changed a fixed-volume output");
        h=new Harness(); h.volume=80; h.start("already-loud"); h.timer.until(1100);
        check(h.volume==80 && !h.wroteVolume(),"Announcement target reduced an already higher volume");

        h=new Harness(); h.audio.start("normal","Score",new AnnouncementAudio.Options(false,false,75,500));
        check(h.events.contains("0:duck") && !h.wroteVolume() && h.spoken.size()==1,"Default audio behavior regressed");
        h.audio.completed("normal",true); h.timer.until(0); check(h.released(),"Unboosted call kept focus unnecessarily");

        h=new Harness(); h.start("closing"); h.timer.until(700); h.audio.close(); h.timer.until(20000);
        check(h.volume==40 && h.released() && h.spoken.isEmpty(),"Service shutdown leaked volume or delayed speech");
        h.start("after-close"); h.timer.until(30000); check(h.spoken.isEmpty(),"Closed controller restarted");

        // 350 ms is preserved exactly at every phase, not rounded to 250 or 500.
        h=new Harness(); h.audio.start("fine-pause","Score",new AnnouncementAudio.Options(true,true,75,350));
        h.timer.until(449); check(h.volume==40 && h.spoken.isEmpty(),"Fine pause gap ended early");
        h.timer.until(450); check(h.volume==75 && h.spoken.isEmpty(),"Fine pause gap was rounded");
        h.timer.until(800); check(h.spoken.size()==1,"Fine speaker-settling gap was not applied");
        h.audio.completed("fine-pause",true); h.timer.until(1149);
        check(h.volume==40 && !h.released(),"Fine restore gap ended early");
        h.timer.until(1150); check(h.released(),"Fine restore gap was rounded");

        AnnouncementAudio.Options duck=new AnnouncementAudio.Options(true,false,75,350);
        h=new Harness(); h.audio.start("duck-boost","Score",duck);
        check(h.events.equals(Arrays.asList("0:duck","0:volume=75","0:speak=duck-boost@75","0:started=duck-boost")) && h.music,
            "Duck mode must request focus, boost and start speech immediately in order");
        h.audio.completed("duck-boost",true);
        check(h.volume==40 && !h.released(),"Unducked music before restoring its level");
        h.timer.until(0); check(h.events.contains("0:release@40") && h.finished.contains("duck-boost:spoken"),"Ducked completion added a settling delay");
        for(boolean boost : new boolean[]{false,true}) for(int gap : new int[]{250,350,1500}) {
            h=new Harness(); h.audio.start("immediate","Score",new AnnouncementAudio.Options(boost,false,75,gap));
            check(h.spoken.size()==1 && h.volume==(boost ? 75 : 40),"Saved pause gap delayed a ducked call: "+gap);
            h.audio.completed("immediate",true); h.timer.until(0);
            check(h.volume==40 && h.released(),"Saved pause gap delayed ducked restoration: "+gap);
        }

        h=new Harness(); h.audio.start("pause-only","Score",new AnnouncementAudio.Options(false,true,75,350));
        h.timer.until(450);
        check(h.events.contains("0:pause") && !h.music && !h.wroteVolume() && h.spoken.size()==1,"Pause option was coupled to volume boost");
        h.audio.completed("pause-only",true); h.timer.until(800); check(h.released(),"Pause-only call leaked focus");

        h=new Harness(); h.pauseWorks=false;
        h.audio.start("ignored-pause-only","Score",new AnnouncementAudio.Options(false,true,75,350)); h.timer.until(1500);
        check(h.spoken.size()==1 && !h.wroteVolume(),"Ignored pause-only request hung the call");

        h=new Harness(); h.audio.start("duck-cancel","Score",duck); h.timer.until(550); h.audio.cancel();
        check(h.volume==40,"Ducked cancellation did not restore volume");
        h.timer.until(550); check(h.spoken.size()==1 && h.released(),"Ducked cancellation delayed cleanup or replayed speech");

        for(int failure=0;failure<3;failure++) {
            h=new Harness(); h.speechWorks=failure!=0; h.speechThrows=failure==1;
            h.audio.start("duck-failed","Score",duck); h.timer.until(850);
            if(failure==2) h.audio.completed("duck-failed",false);
            check(h.volume==40,"Ducked TTS failure left volume raised: "+failure);
            h.timer.until(1200); check(h.released() && h.finished.contains("duck-failed:error"),"Ducked TTS failure leaked focus: "+failure);
        }
        h=new Harness(); h.focusWorks=false; h.audio.start("duck-denied","Score",duck); h.timer.until(2000);
        check(!h.wroteVolume() && h.spoken.isEmpty(),"Denied duck focus still boosted volume");

        h=new Harness(); h.audio.start("duck-route","Score",duck); h.timer.until(850);
        h.route="headphones"; h.volume=20; h.timer.until(1350);
        check(h.volume==20 && h.finished.contains("duck-route:interrupted"),"Ducked route change overwrote the new output");

        h=new Harness(); h.audio.start("duck-manual","Score",duck); h.timer.until(850); h.volume=55;
        h.audio.completed("duck-manual",true); h.timer.until(1200);
        check(h.volume==55 && h.released(),"Ducked completion overwrote a manual volume adjustment");

        h=new Harness(); h.audio.start("duck-old","Score",duck); h.timer.until(850); h.start("pause-new");
        check(h.volume==40,"Switching music modes captured a boosted baseline");
        h.timer.until(1950); check(h.events.contains("850:pause") && h.events.contains("1950:speak=pause-new@75"),"Replacement did not use the new music mode");
        h.audio.completed("duck-old",true); check(h.volume==75,"Stale duck completion changed the replacement volume");
        h.audio.completed("pause-new",true); h.timer.until(2450); check(h.volume==40 && h.released(),"Replacement did not restore baseline");

        h=new Harness(); h.audio.start("duck-timeout","Score",duck); h.timer.until(12000);
        check(h.volume==40 && h.released() && h.finished.contains("duck-timeout:error"),"Ducked timeout left volume or focus raised");
        h=new Harness(); h.audio.start("duck-close","Score",duck); h.timer.until(550); h.audio.close(); h.timer.until(20000);
        check(h.volume==40 && h.released() && h.spoken.size()==1,"Ducked service shutdown leaked volume or replayed speech");
        for(int level : new int[]{0,80}) {
            h=new Harness(); h.volume=level; h.audio.start("duck-unchanged","Score",duck); h.timer.until(850);
            check(h.volume==level && !h.wroteVolume(),"Ducked boost changed muted/already-higher media");
        }
        for(int gap : new int[]{275,325,350,375,450}) {
            check(new AnnouncementAudio.Options(true,false,75,gap).gapMs==gap,"Fine gap was rounded: "+gap);
        }
        AnnouncementAudio.Options bounds=new AnnouncementAudio.Options(true,true,999,9999);
        check(bounds.percent==100 && bounds.gapMs==1500,"Upper settings limits not enforced");
        bounds=new AnnouncementAudio.Options(true,true,-1,-1);
        check(bounds.percent==20 && bounds.gapMs==250,"Lower settings limits not enforced");
        System.out.println(checks+" announcement audio sequencing checks passed");
    }
}
