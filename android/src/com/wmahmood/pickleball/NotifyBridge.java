package com.wmahmood.pickleball;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

/** Notify implements the Xiaomi-named service; Mi Fitness is not a provider. */
final class NotifyBridge {
    static final String[] PACKAGES = {"com.mc.xiaomi1", "com.mc.xiaomi1.huawei"};
    static final String ACTION = "com.xiaomi.wearable.XMS_WEARABLE_SERVICE";

    static String availablePackage(Context context) {
        PackageManager pm = context.getPackageManager();
        for (String name : PACKAGES) {
            ResolveInfo info = pm.resolveService(new Intent(ACTION).setPackage(name), 0);
            if (info != null && info.serviceInfo != null && info.serviceInfo.enabled && info.serviceInfo.exported) return name;
        }
        return "";
    }

    static String missingMessage(Context context) {
        for (String name : PACKAGES) {
            try {
                context.getPackageManager().getPackageInfo(name, 0);
                return "Notify is installed but its Interconnect service is unavailable. Update Notify for Xiaomi and open it, then retry.";
            } catch (PackageManager.NameNotFoundException ignored) {}
        }
        return "Notify for Xiaomi is not installed. Install and connect it to your band first.";
    }

    static Intent launchIntent(Context context) {
        String available = availablePackage(context);
        if (!available.isEmpty()) return context.getPackageManager().getLaunchIntentForPackage(available);
        for (String name : PACKAGES) {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(name);
            if (intent != null) return intent;
        }
        return null;
    }
}
