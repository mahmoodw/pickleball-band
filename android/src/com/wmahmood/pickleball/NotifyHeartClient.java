package com.wmahmood.pickleball;

import android.content.*;
import android.os.Build;

/** Passive Android event listener. Owns no Bluetooth connection and sends no
 * heart-monitor or workout start/stop commands. */
final class NotifyHeartClient {
    interface Listener { void sample(int bpm); void changed(); }
    final NotifyHeartEvents events=new NotifyHeartEvents();
    private final Context context;
    private final Listener listener;
    private boolean registered;
    private String status="Not registered", probe="Not requested";
    private final BroadcastReceiver receiver=new BroadcastReceiver() {
        @Override public void onReceive(Context c,Intent intent) {
            if (!registered || intent==null) return;
            Object value=null;
            try { if (intent.hasExtra("value")) value=intent.getExtras().get("value"); }
            catch (RuntimeException ignored) {}
            int bpm=events.receive(intent.getAction(),value,isInitialStickyBroadcast());
            if (bpm>0) listener.sample(bpm);
            listener.changed();
        }
    };
    NotifyHeartClient(Context context,Listener listener) { this.context=context; this.listener=listener; }
    void start() {
        IntentFilter filter=new IntentFilter();
        for (String action:NotifyHeartEvents.ACTIONS) filter.addAction(action);
        try {
            if (Build.VERSION.SDK_INT>=33) context.registerReceiver(receiver,filter,Context.RECEIVER_EXPORTED);
            else context.registerReceiver(receiver,filter);
            registered=true; status="Listening; delivery and HR support not yet established";
        } catch (RuntimeException e) { status="Listener registration failed"; }
        listener.changed();
    }
    void probe() {
        String target=NotifyBridge.availablePackage(context);
        if (!registered || target.isEmpty()) { probe="Notify listener or service unavailable"; listener.changed(); return; }
        try {
            // Official Xiaomi API: battery read only. No settings or pairing changes.
            context.sendBroadcast(new Intent("com.mc.xiaomi.tasker.batteryRead").setPackage(target));
            probe="Battery-read request sent; no reply inferred. Password-protected commands may be ignored.";
        } catch (RuntimeException e) { probe="Battery-read request could not be sent"; }
        listener.changed();
    }
    String report() { return "Notify listener: "+status+"\nNotify probe: "+probe+"\n"+events.report(); }
    void close() { if (registered) { registered=false; try { context.unregisterReceiver(receiver); } catch (RuntimeException ignored) {} } }
}
