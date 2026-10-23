package com.wmahmood.pickleball;

import java.util.*;

public final class ClipPlaybackTest {
    static int checks;
    static void check(boolean ok) { checks++; if (!ok) throw new AssertionError("Playback check " + checks); }
    static class Harness implements ClipPlayback.Player {
        final List<ClipPlayback.Loaded> loads=new ArrayList<>();
        final List<Runnable> completions=new ArrayList<>();
        final List<String> finished=new ArrayList<>(), voices=new ArrayList<>();
        final ClipPlayback playback=new ClipPlayback((voice,text,callback) -> { voices.add(voice); loads.add(callback); },
            this,(id,ok) -> finished.add(id+":"+ok));
        boolean playing, fail;
        public void play(byte[] pcm,Runnable done) { if (fail) throw new IllegalStateException(); playing=true; completions.add(done); }
        public void stop() { playing=false; }
    }
    public static void main(String[] args) {
        Harness h=new Harness();
        check(h.playback.speak("old","morgan","Zero."));
        check(h.playback.speak("new","kermit","One."));
        h.loads.get(0).ready(new byte[2]);
        check(!h.playing && h.completions.isEmpty());
        h.loads.get(1).ready(new byte[2]);
        check(h.playing && h.voices.equals(Arrays.asList("morgan","kermit")));
        h.completions.get(0).run(); h.completions.get(0).run();
        check(!h.playing && h.finished.equals(Collections.singletonList("new:true")));
        h.playback.speak("cancel-load","morgan","Zero."); h.playback.stop(); h.loads.get(2).ready(new byte[2]);
        check(!h.playing && h.finished.size()==1 && h.completions.size()==1);
        h.playback.speak("cancel-playing","morgan","Zero."); h.loads.get(3).ready(new byte[2]);
        h.playback.speak("replacement","kermit","One.");
        h.completions.get(1).run(); check(h.finished.size()==1);
        h.loads.get(4).ready(new byte[2]); h.completions.get(2).run();
        check(h.finished.get(1).equals("replacement:true"));
        h.playback.speak("missing","morgan","Zero."); h.loads.get(5).ready(null);
        check(h.finished.get(2).equals("missing:false") && !h.playing);
        h.fail=true; h.playback.speak("device-error","morgan","Zero."); h.loads.get(6).ready(new byte[2]);
        check(h.finished.get(3).equals("device-error:false"));
        h.fail=false; h.playback.speak("shutdown","morgan","Zero."); h.playback.close();
        h.loads.get(7).ready(new byte[2]); check(!h.playing && h.finished.size()==4);
        check(!h.playback.speak("after-close","kermit","Zero."));
        System.out.println(checks+" clip playback lifecycle checks passed");
    }
}
