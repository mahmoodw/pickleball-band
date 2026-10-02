package com.wmahmood.pickleball;

import org.json.JSONObject;
import org.json.JSONArray;

public final class ScoreTest {
    static int checks;
    static void check(boolean ok) { checks++; if (!ok) throw new AssertionError("Check " + checks); }
    static JSONObject packet(String action, int seq, int rev, int us, int them, int serving, int server) throws Exception {
        return new JSONObject().put("protocol",1).put("type","score").put("action",action).put("session","session-one")
            .put("gameId","game-one").put("sequence",seq).put("revision",rev)
            .put("state",new JSONObject().put("mode","doubles").put("scores",new JSONArray(new int[]{us,them}))
                .put("serving",serving).put("server",server).put("target",11));
    }
    static void invalid(JSONObject p) throws Exception {
        boolean failed = false;
        try { new Score(p); } catch (Exception expected) { failed = true; }
        check(failed);
    }
    public static void main(String[] args) throws Exception {
        check(new Score(packet("start",1,0,0,0,0,2)).speech().equals("zero. zero. two."));
        check(new Score(packet("rally",2,1,4,2,0,1)).speech().equals("four. two. one."));
        check(new Score(packet("correct",3,2,4,2,1,1)).speech().equals("Correction. two. four. one."));
        check(new Score(packet("undo",4,3,4,2,0,2)).speech().equals("Correction. four. two. two."));
        JSONObject singles=packet("rally",1,0,4,2,1,1);
        singles.getJSONObject("state").put("mode","singles");
        check(new Score(singles).speech().equals("two. four."));
        check(new Score(packet("rally",1,0,11,9,0,1)).speech().equals("Game. eleven to nine. We win."));
        check(new Score(packet("correct",1,0,12,14,1,2)).speech().equals("Correction. Game. fourteen to twelve. They win."));
        check(new Score(packet("rally",1,0,11,10,0,1)).winner() == -1);
        check(Score.word(21).equals("twenty one")); check(Score.word(99).equals("ninety nine"));
        invalid(packet("rally",1,0,-1,0,0,1));
        invalid(packet("rally",1,0,0,0,0,3));
        JSONObject fractional=packet("rally",1,0,0,0,0,1); fractional.getJSONObject("state").getJSONArray("scores").put(0,1.5); invalid(fractional);
        JSONObject string=packet("rally",1,0,0,0,0,1); string.put("sequence","2"); invalid(string);
        singles.getJSONObject("state").put("server",2); invalid(singles);
        invalid(packet("unexpected",1,0,0,0,0,1));
        MessageOrder order=new MessageOrder();
        check(!order.accept(new Score(packet("rally",1,0,0,0,0,2))));
        check(order.accept(new Score(packet("sync",2,0,0,0,0,2))));
        check(order.accept(new Score(packet("rally",3,1,1,0,0,2))));
        check(!order.accept(new Score(packet("rally",3,1,1,0,0,2))));
        check(!order.accept(new Score(packet("rally",2,0,0,0,0,2))));
        check(!order.accept(new Score(packet("rally",4,0,0,0,0,2))));
        check(order.accept(new Score(packet("undo",5,2,0,0,0,2))));
        order.reset();
        check(!order.accept(new Score(packet("rally",8,6,5,0,0,2))));
        check(order.accept(new Score(packet("sync",9,6,5,0,0,2))));
        System.out.println(checks+" Android score/protocol checks passed");
    }
}
