package com.wmahmood.pickleball;

import org.json.JSONObject;
import java.util.*;

public final class MediaRemoteTest {
    private static int checks;
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    static JSONObject packet(String session, Object sequence, String action) throws Exception {
        return new JSONObject().put("type","media").put("version",1).put("session",session).put("sequence",sequence).put("action",action);
    }
    public static void main(String[] args) throws Exception {
        MediaRemote remote = new MediaRemote();
        List<String> actions = new ArrayList<>();
        MediaRemote.Output output = action -> { actions.add(action); return "Control sent"; };
        check(remote.receive(packet("a",1,"toggle"),output)==null,"Unsynced session dispatched media");
        remote.sync("a");
        JSONObject reply=remote.receive(packet("a",1,"toggle"),output);
        check(actions.equals(Arrays.asList("toggle")),"Play/pause not dispatched");
        check(reply.getString("type").equals("mediaAck") && reply.getString("session").equals("a") && reply.getInt("sequence")==1,"Uncorrelated reply");
        check(reply.getString("status").equals("sent"),"Wrong success status");
        check(remote.receive(packet("a",1,"toggle"),output)==reply && actions.size()==1,"Duplicate toggled playback twice");
        remote.sync("a");
        remote.receive(packet("a",1,"toggle"),output);
        check(actions.size()==1,"Repeated score sync reset media deduplication");
        remote.receive(packet("a",3,"next"),output);
        check(remote.receive(packet("a",2,"previous"),output)==null && actions.size()==2,"Out-of-order control dispatched");
        check(remote.receive(packet("b",4,"toggle"),output)==null,"Wrong session dispatched");
        for(Object bad : new Object[]{0,-1,1.5,"4",2147483648L})
            check(remote.receive(packet("a",bad,"toggle"),output)==null,"Invalid sequence accepted: "+bad);
        check(remote.receive(packet("a",4,"unknown"),output)==null,"Unknown control dispatched");
        check(remote.receive(packet("a",4,"toggle").put("type","score"),output)==null,"Score treated as media");
        check(remote.receive(packet("a",4,"toggle").put("version",2),output)==null,"Unknown protocol accepted");
        int seq=4;
        for(String action : new String[]{"previous","volumeUp","volumeDown"}) {
            remote.receive(packet("a",seq++,action),output);
            check(actions.get(actions.size()-1).equals(action),"Wrong control forwarded");
        }
        final int[] attempts={0};
        MediaRemote.Output failure=action -> { attempts[0]++; throw new SecurityException("denied"); };
        reply=remote.receive(packet("a",seq,"toggle"),failure);
        check(reply.getString("status").equals("error"),"Dispatch failure reported as success");
        remote.receive(packet("a",seq,"toggle"),failure);
        check(attempts[0]==1,"Failed toggle retried automatically");
        remote.reset();
        check(remote.receive(packet("a",100,"toggle"),output)==null,"Old handshake accepted after reconnect");
        remote.sync("b");
        check(remote.receive(packet("b",1,"toggle"),output)!=null,"New session cannot control music");
        check(remote.receive(packet("a",101,"toggle"),output)==null,"Old sender replaced new session");
        System.out.println(checks+" music protocol checks passed");
    }
}
