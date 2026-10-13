package com.wmahmood.pickleball;

public final class NotifyHeartEventsTest {
    static int checks;
    static void check(boolean value,String message) { checks++; if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        NotifyHeartEvents events=new NotifyHeartEvents();
        check(events.receive("com.mc.xiaomi.heartRateGot",120,false)==120,"Xiaomi integer HR event");
        check(events.receive("com.mc.miband.heartRateGot",85,false)==85,"Legacy compatibility HR event");
        check(events.hrEvents==2 && events.validHrEvents==2,"Valid HR counters");
        check(events.receive("com.mc.xiaomi.batteryStatGot",90,false)==-1,"Battery must never become heart rate");
        check(events.otherEvents==1 && events.validHrEvents==2,"Other events counted separately");
        check(events.receive("com.mc.xiaomi.connected",null,false)==-1,"Connection must never become heart rate");
        check(events.receive("com.mc.xiaomi.tasker.trigger.bandDisconnected",null,false)==-1,"Disconnect trigger is diagnostic only");
        check(events.receive("com.mc.xiaomi.heartRateGot",0,false)==-1,"Zero rejected");
        check(events.receive("com.mc.xiaomi.heartRateGot",-5,false)==-1,"Negative rejected");
        check(events.receive("com.mc.xiaomi.heartRateGot",256,false)==-1,"Out of range rejected");
        check(events.receive("com.mc.xiaomi.heartRateGot","120",false)==-1,"Wrong payload type rejected");
        check(events.receive("com.mc.xiaomi.heartRateGot",null,false)==-1,"Missing payload rejected");
        check(events.rejected==5 && events.validHrEvents==2,"Rejected values never count as saved HR");
        long total=events.events;
        check(events.receive("unknown.heartRateGot",120,false)==-1 && events.events==total,"Unknown action ignored");
        check(events.receive("com.mc.xiaomi.heartRateGot",120,true)==-1 && events.events==total,"Sticky replay ignored");
        check(!events.report().contains("120"),"Diagnostics need not include raw measurement values");
        System.out.println(checks+" Notify event classification checks passed");
    }
}
