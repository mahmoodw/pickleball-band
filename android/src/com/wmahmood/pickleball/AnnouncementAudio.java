package com.wmahmood.pickleball;

/** Main-thread audio sequencing, with no Android dependencies for timing tests. */
final class AnnouncementAudio {
    static final class Options {
        static final int MIN_GAP_MS=250, MAX_GAP_MS=1500, GAP_STEP_MS=25;
        final boolean boost, pauseMusic;
        final int percent, gapMs;
        Options(boolean boost, boolean pauseMusic, int percent, int gapMs) {
            this.boost = boost; this.pauseMusic = pauseMusic;
            this.percent = Math.max(20, Math.min(100, percent));
            this.gapMs = Math.max(MIN_GAP_MS, Math.min(MAX_GAP_MS, gapMs));
        }
    }
    interface Output {
        boolean requestFocus(boolean pauseMusic);
        void releaseFocus();
        boolean musicActive();
        boolean fixedVolume();
        int volume();
        int maxVolume();
        String route();
        void setVolume(int value);
        boolean speak(String id, String text);
        void stop();
        void note(String text);
    }
    interface Scheduler { void after(int milliseconds, Runnable action); }
    interface Listener {
        void started(String id);
        void finished(String id, String result);
    }
    private static final class Request {
        final String id, text;
        final Options options;
        Request(String id, String text, Options options) { this.id=id; this.text=text; this.options=options; }
    }
    private final Output output;
    private final Scheduler scheduler;
    private final Listener listener;
    private Request current, pending;
    private boolean closing, closed, focused;
    private int generation, original=-1, applied=-1;
    private String boostedRoute="";

    AnnouncementAudio(Output output, Scheduler scheduler, Listener listener) {
        this.output=output; this.scheduler=scheduler; this.listener=listener;
    }
    void start(String id, String text, Options options) {
        if (closed) return;
        Request request=new Request(id,text,options);
        if (current != null) {
            if (pending != null) listener.finished(pending.id,"interrupted");
            pending=request;
            end("interrupted",true);
        } else begin(request);
    }
    private void begin(Request request) {
        current=request; closing=false;
        final int token=++generation;
        try {
            focused=output.requestFocus(request.options.pauseMusic);
            if (!focused) { output.note("Audio busy; announcement not started"); end("error",false); return; }
            output.note(request.options.pauseMusic ? "Waiting for music to pause" : request.options.boost ?
                "Ducking requested; music may also get louder during boost" : "Using normal media volume; music may duck");
            int delay=request.options.pauseMusic ? 100 : 150;
            scheduler.after(delay, () -> prepare(token,delay));
        } catch (RuntimeException error) { output.note("Could not prepare announcement audio"); end("error",true); }
    }
    private boolean live(int token) { return !closed && current != null && !closing && token==generation; }
    private void prepare(int token, int waited) {
        if (!live(token)) return;
        try {
            if (!current.options.boost && !current.options.pauseMusic) { speak(token); return; }
            if (current.options.pauseMusic && output.musicActive()) {
                if (waited < 1500) scheduler.after(100, () -> prepare(token,waited+100));
                else { output.note(current.options.boost ? "Music kept playing; volume boost skipped" : "Music kept playing; using normal media volume"); speak(token); }
                return;
            }
            // Allow a pause to drain buffered music, or give ducking time to settle.
            // A focus grant does not confirm how much another player's audio ducks.
            scheduler.after(current.options.gapMs, () -> boost(token));
        } catch (RuntimeException error) { output.note("Audio check failed; volume unchanged"); end("error",true); }
    }
    private void boost(int token) {
        if (!live(token)) return;
        try {
            if (!current.options.boost) { speak(token); return; }
            if (current.options.pauseMusic && output.musicActive()) { output.note("Music resumed; volume boost skipped"); speak(token); return; }
            int base=output.volume(), max=output.maxVolume();
            int target=Math.max(base, Math.min(max, Math.round(max*current.options.percent/100f)));
            String route=output.route();
            if (base==0 || output.fixedVolume() || target<=base || route.isEmpty()) {
                output.note(base==0 ? "Media muted; volume unchanged" : "Using current media volume");
                speak(token); return;
            }
            original=base; applied=target; boostedRoute=route;
            output.setVolume(target);
            if (!route.equals(output.route())) { output.note("Audio output changed; announcement cancelled"); end("interrupted",true); return; }
            applied=output.volume();
            output.note("Announcement volume " + applied + " / " + max + (current.options.pauseMusic ?
                "; music volume will be restored" : "; ducking requested, background level depends on player"));
            // Absolute-volume commands can reach a speaker after the local slider changes.
            scheduler.after(current.options.gapMs, () -> speak(token));
        } catch (RuntimeException error) { output.note("Volume boost unavailable"); end("error",true); }
    }
    private void speak(int token) {
        if (!live(token)) return;
        try {
            if (original>=0 && !boostedRoute.equals(output.route())) {
                output.note("Audio output changed; announcement cancelled"); end("interrupted",true); return;
            }
            if (original>=0 && current.options.pauseMusic && output.musicActive()) {
                restore(); output.note("Music resumed; volume boost skipped");
                scheduler.after(current.options.gapMs, () -> speak(token)); return;
            }
            if (!output.speak(current.id,current.text)) { end("error",true); return; }
            listener.started(current.id);
            scheduler.after(12000, () -> { if (live(token)) { output.note("Speech timed out; restoring music volume"); end("error",true); } });
            watchRoute(token);
        } catch (RuntimeException error) { output.note("Speech unavailable; restoring music volume"); end("error",true); }
    }
    private void watchRoute(int token) {
        if (!live(token) || original<0) return;
        scheduler.after(150, () -> {
            if (!live(token) || original<0) return;
            try {
                if (!boostedRoute.equals(output.route())) {
                    output.note("Audio output changed; announcement cancelled"); end("interrupted",true);
                } else watchRoute(token);
            } catch (RuntimeException error) { end("error",true); }
        });
    }
    void completed(String id, boolean ok) {
        if (current != null && current.id.equals(id) && !closing) end(ok ? "spoken" : "error",!ok);
    }
    void cancel() {
        if (pending != null) { listener.finished(pending.id,"interrupted"); pending=null; }
        end("interrupted",true);
    }
    private void restore() {
        if (original<0) return;
        try {
            // Keep a manual volume change, and never write an old speaker's volume
            // into a newly selected output. This is a lease on one unchanged level.
            if (boostedRoute.equals(output.route()) && output.volume()==applied) output.setVolume(original);
            else output.note("Output or volume changed; automatic volume restore skipped");
        } catch (RuntimeException error) { output.note("Could not restore volume; check the phone media slider"); }
        finally { original=-1; applied=-1; boostedRoute=""; }
    }
    private void end(String result, boolean stop) {
        if (current==null || closing) return;
        closing=true; final int token=++generation;
        if (stop) { try { output.stop(); } catch (RuntimeException ignored) {} }
        restore();
        // Keep focus until the restored level has had time to reach the speaker.
        int delay=focused && (current.options.boost || current.options.pauseMusic) ? current.options.gapMs : 0;
        scheduler.after(delay, () -> {
            if (closed || token!=generation || current==null) return;
            release();
            String id=current.id; current=null; closing=false;
            listener.finished(id,result);
            Request next=pending; pending=null;
            if (next!=null) begin(next);
        });
    }
    private void release() {
        if (focused) { try { output.releaseFocus(); } catch (RuntimeException ignored) {} }
        focused=false;
    }
    void close() {
        closed=true; ++generation; pending=null;
        try { output.stop(); } catch (RuntimeException ignored) {}
        restore(); release(); current=null;
    }
}
