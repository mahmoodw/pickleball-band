package com.wmahmood.pickleball;

import org.json.JSONObject;

/** A separate, non-replayed command stream; media buttons must never alter scores. */
final class MediaRemote {
    interface Output { String perform(String action); }
    private String session = "";
    private int sequence;
    private JSONObject lastReply;

    void reset() { session = ""; sequence = 0; lastReply = null; }
    void sync(String sender) {
        if (!sender.equals(session)) { reset(); session = sender; }
    }
    JSONObject receive(JSONObject packet, Output output) throws Exception {
        if (!"media".equals(packet.optString("type")) || packet.optInt("version") != 1 ||
            session.isEmpty() || !session.equals(packet.optString("session"))) return null;
        Object number = packet.opt("sequence");
        if (!(number instanceof Number)) return null;
        double value = ((Number)number).doubleValue();
        if (value < 1 || value > Integer.MAX_VALUE || value != Math.floor(value)) return null;
        int next = (int)value;
        if (next < sequence) return null;
        if (next == sequence) return lastReply;
        String action = packet.optString("action");
        if (!action.equals("toggle") && !action.equals("previous") && !action.equals("next") &&
            !action.equals("volumeUp") && !action.equals("volumeDown")) return null;
        // Consume before dispatch: even an ambiguous failure must not toggle twice.
        sequence = next;
        String result;
        boolean ok = true;
        try { result = output.perform(action); }
        catch (RuntimeException error) { ok = false; result = "Music control unavailable"; }
        lastReply = new JSONObject().put("type", "mediaAck").put("session", session)
            .put("sequence", sequence).put("status", ok ? "sent" : "error").put("message", result);
        return lastReply;
    }
}
