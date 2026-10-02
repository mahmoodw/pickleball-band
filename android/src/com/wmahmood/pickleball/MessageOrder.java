package com.wmahmood.pickleball;

/** One ordered snapshot stream per connection challenge. */
final class MessageOrder {
    private String session = "", game = "";
    private int sequence, revision;
    void reset() { session = ""; game = ""; sequence = 0; revision = 0; }
    boolean accept(Score score) {
        if (!score.session.equals(session)) {
            // Establish each new sender with a silent snapshot before announcements.
            if (!score.action.equals("sync")) return false;
            session = score.session; game = ""; sequence = 0; revision = 0;
        }
        if (score.sequence <= sequence) return false;
        if (score.gameId.equals(game) && score.revision < revision) return false;
        sequence = score.sequence; game = score.gameId; revision = score.revision;
        return true;
    }
}
