package com.wmahmood.pickleball;

/** Main-thread lifecycle. Late loads/completions never play or finish a newer call. */
final class ClipPlayback {
    interface Loaded { void ready(byte[] pcm); }
    interface Loader { void load(String voice,String text,Loaded callback); }
    interface Player { void play(byte[] pcm,Runnable done); void stop(); }
    interface Listener { void finished(String id,boolean ok); }
    private final Loader loader;
    private final Player player;
    private final Listener listener;
    private int generation;
    private boolean closed;
    ClipPlayback(Loader loader,Player player,Listener listener) {
        this.loader=loader; this.player=player; this.listener=listener;
    }
    boolean speak(String id,String voice,String text) {
        if (closed) return false;
        stop(); final int token=generation;
        try {
            loader.load(voice,text,pcm -> {
                if (closed || token!=generation) return;
                if (pcm==null) { finish(token,id,false); return; }
                try { player.play(pcm,() -> finish(token,id,true)); }
                catch (RuntimeException error) { finish(token,id,false); }
            });
        } catch (RuntimeException error) { finish(token,id,false); }
        return true;
    }
    private void finish(int token,String id,boolean ok) {
        if (closed || token!=generation) return;
        stop(); listener.finished(id,ok);
    }
    void stop() { ++generation; try { player.stop(); } catch (RuntimeException ignored) {} }
    void close() { stop(); closed=true; }
}
