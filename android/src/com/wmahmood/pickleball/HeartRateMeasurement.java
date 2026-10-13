package com.wmahmood.pickleball;

/** Bluetooth Heart Rate Measurement (0x2A37), independent of Android. */
final class HeartRateMeasurement {
    static int parse(byte[] data) {
        if (data == null || data.length < 2) return -1;
        int flags = data[0] & 255, index = (flags & 1) == 0 ? 2 : 3;
        if (data.length < index) return -1;
        int bpm = data[1] & 255;
        if (index == 3) bpm |= (data[2] & 255) << 8;
        // If contact detection is supported, do not record off-wrist values.
        if ((flags & 4) != 0 && (flags & 2) == 0) return -1;
        if ((flags & 8) != 0) index += 2; // Energy expended.
        if (data.length < index) return -1;
        if ((flags & 16) != 0 && (data.length == index || (data.length-index)%2 != 0)) return -1;
        return bpm > 0 && bpm <= 255 ? bpm : -1;
    }
    static boolean fresh(long last, long now) { return last >= 0 && now >= last && now-last <= 15000; }
}
