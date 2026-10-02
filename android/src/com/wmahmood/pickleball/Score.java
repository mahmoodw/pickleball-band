package com.wmahmood.pickleball;

import org.json.JSONObject;

final class Score {
    final String mode, action, session, gameId;
    final int us, them, serving, server, target, sequence, revision;
    Score(JSONObject packet) throws Exception {
        if (packet.getInt("protocol") != 1 || !"score".equals(packet.getString("type"))) throw new Exception("Unsupported message");
        action = packet.getString("action");
        if (!java.util.Arrays.asList("sync", "rally", "correct", "undo", "repeat", "start").contains(action)) throw new Exception("Invalid action");
        session = packet.getString("session"); gameId = packet.getString("gameId");
        if (!session.matches("[a-z0-9-]{5,80}") || !gameId.matches("[a-z0-9-]{5,80}")) throw new Exception("Invalid session");
        sequence = number(packet.get("sequence"), 1, 1000000000);
        revision = number(packet.get("revision"), 0, 1000000000);
        JSONObject s = packet.getJSONObject("state");
        mode = s.getString("mode");
        if (!mode.equals("doubles") && !mode.equals("singles")) throw new Exception("Invalid scoring mode");
        if (s.getJSONArray("scores").length() != 2) throw new Exception("Invalid scores");
        us = number(s.getJSONArray("scores").get(0), 0, 99);
        them = number(s.getJSONArray("scores").get(1), 0, 99);
        serving = number(s.get("serving"), 0, 1); server = number(s.get("server"), 1, 2);
        target = number(s.get("target"), 11, 21);
        if ((target != 11 && target != 15 && target != 21) || (mode.equals("singles") && server != 1)) throw new Exception("Invalid rules");
    }
    static int number(Object value, int min, int max) throws Exception {
        if (!(value instanceof Number)) throw new Exception("Invalid number");
        double n = ((Number)value).doubleValue();
        if (!Double.isFinite(n) || n != Math.floor(n) || n < min || n > max) throw new Exception("Invalid number");
        return (int)n;
    }
    int winner() { return Math.max(us, them) >= target && Math.abs(us - them) >= 2 ? (us > them ? 0 : 1) : -1; }
    String call() { return (serving == 0 ? us + " - " + them : them + " - " + us) + (mode.equals("doubles") ? " - " + server : ""); }
    String speech() {
        String prefix = action.equals("correct") || action.equals("undo") ? "Correction. " : "";
        if (winner() != -1) return prefix + "Game. " + word(Math.max(us,them)) + " to " + word(Math.min(us,them)) + ". " + (winner() == 0 ? "We win." : "They win.");
        return prefix + word(serving == 0 ? us : them) + ". " + word(serving == 0 ? them : us) + "." + (mode.equals("doubles") ? " " + word(server) + "." : "");
    }
    static String word(int n) {
        String[] small = {"zero","one","two","three","four","five","six","seven","eight","nine","ten","eleven","twelve","thirteen","fourteen","fifteen","sixteen","seventeen","eighteen","nineteen"};
        String[] tens = {"","","twenty","thirty","forty","fifty","sixty","seventy","eighty","ninety"};
        return n < 20 ? small[n] : tens[n/10] + (n%10 == 0 ? "" : " " + small[n%10]);
    }
}
