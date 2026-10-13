package com.wmahmood.pickleball;

/** Optional compatibility probe. HR actions are community-reported, not a
 * guaranteed API on current Notify for Xiaomi. Never treats battery as HR. */
final class NotifyHeartEvents {
    static final String[] ACTIONS={
        "com.mc.xiaomi.heartRateGot", "com.mc.miband.heartRateGot",
        "com.mc.xiaomi.batteryStatGot", "com.mc.miband.batteryStatGot",
        "com.mc.xiaomi.connected", "com.mc.xiaomi.disconnected",
        "com.mc.xiaomi.tasker.trigger.bandConnected", "com.mc.xiaomi.tasker.trigger.bandDisconnected"
    };
    long events, hrEvents, validHrEvents, otherEvents, rejected;
    String last="None";
    int receive(String action,Object value,boolean sticky) {
        boolean known=false;
        for (String allowed:ACTIONS) if (allowed.equals(action)) known=true;
        if (!known || sticky) return -1;
        events++; last=action;
        if (!action.endsWith(".heartRateGot")) { otherEvents++; return -1; }
        hrEvents++;
        // Documented legacy shape: integer extra 'value'. No guessed fields,
        // parsed status text, retained readings or generated measurements.
        if (!(value instanceof Integer) || (Integer)value<1 || (Integer)value>255) { rejected++; return -1; }
        validHrEvents++; return (Integer)value;
    }
    String report() { return "Notify events: "+events+"\nNotify HR events: "+hrEvents+
        "\nNotify valid HR events: "+validHrEvents+"\nNotify other events: "+otherEvents+
        "\nNotify rejected HR values: "+rejected+"\nLast Notify event: "+last; }
}
