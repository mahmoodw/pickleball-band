package com.wmahmood.pickleball;

import android.content.SharedPreferences;
import com.xiaomi.xms.wearable.message.MessageApi;
import com.xiaomi.xms.wearable.tasks.*;
import org.json.JSONObject;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executor;
import sun.misc.Unsafe;

/** Exercises the real service's reply/cleanup paths with SDK and preference test doubles.
 * Android's compile-time stubs cannot construct a Service, so only these test
 * doubles skip constructors. No Android runtime, Bluetooth or TTS is emulated.
 */
public final class AndroidTransportTest {
    static int checks;
    static final Map<String,Object> prefs = new HashMap<>();
    static final List<String> destinations = new ArrayList<>(), removed = new ArrayList<>();
    static final List<JSONObject> packets = new ArrayList<>();
    static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }

    static final class Preferences implements InvocationHandler {
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if (name.equals("edit")) return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{SharedPreferences.Editor.class}, this);
            if (name.startsWith("put")) { prefs.put((String)args[0], args[1]); return proxy; }
            if (name.equals("apply")) return null;
            if (name.equals("contains")) return prefs.containsKey(args[0]);
            if (name.startsWith("get") && args != null && args.length == 2) return prefs.getOrDefault(args[0],args[1]);
            throw new AssertionError("Unexpected preference operation: " + name);
        }
    }
    public static class FakeService extends AnnouncerService {
        @Override public SharedPreferences getSharedPreferences(String name, int mode) {
            return (SharedPreferences)Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{SharedPreferences.class}, new Preferences());
        }
    }
    public static class FakeMessages extends MessageApi {
        private FakeMessages() { super(null); }
        @Override public Task<Void> sendMessage(String node, byte[] bytes) {
            destinations.add(node);
            try { packets.add(new JSONObject(new String(bytes, StandardCharsets.UTF_8))); }
            catch (Exception error) { throw new AssertionError(error); }
            return new PendingTask();
        }
        @Override public Task<Void> removeListener(String node) { removed.add(node); return new PendingTask(); }
    }
    static final class PendingTask extends Task<Void> {
        public boolean isComplete() { return false; }
        public boolean isSuccessful() { return false; }
        public boolean isCanceled() { return false; }
        public Void getResult() { return null; }
        public <X extends Throwable> Void getResult(Class<X> type) { return null; }
        public Exception getException() { return null; }
        public Task<Void> addOnSuccessListener(OnSuccessListener<? super Void> listener) { return this; }
        public Task<Void> addOnSuccessListener(Executor executor, OnSuccessListener<? super Void> listener) { return this; }
        public Task<Void> addOnFailureListener(OnFailureListener listener) { return this; }
        public Task<Void> addOnFailureListener(Executor executor, OnFailureListener listener) { return this; }
    }
    static void set(AnnouncerService service, String name, Object value) throws Exception {
        Field field=AnnouncerService.class.getDeclaredField(name); field.setAccessible(true); field.set(service,value);
    }
    static Object call(AnnouncerService service, String name, Class<?>[] types, Object... args) throws Exception {
        Method method=AnnouncerService.class.getDeclaredMethod(name,types); method.setAccessible(true);
        return method.invoke(service,args);
    }
    public static void main(String[] args) throws Exception {
        Field field=Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        Unsafe allocator=(Unsafe)field.get(null);
        FakeService service=(FakeService)allocator.allocateInstance(FakeService.class);
        FakeMessages messages=(FakeMessages)allocator.allocateInstance(FakeMessages.class);
        set(service,"messages",messages); set(service,"challenge","test-challenge");

        // Notify's empty ID must be passed through unchanged. This was the
        // reported combination: hello received, but send was "Not attempted".
        set(service,"node","");
        call(service,"hello",new Class<?>[0]);
        check(destinations.size()==1,"Handshake reply was silently skipped for Notify's empty node ID");
        check(destinations.get(0).equals(""),"Provider ID was changed");
        check(packets.get(0).getString("type").equals("hello"),"Missing hello reply");
        check(packets.get(0).getString("challenge").equals("test-challenge"),"Wrong handshake challenge");
        check(prefs.get("messageSend").equals("Waiting for Notify's send result"),"Send attempt was not recorded");

        Score score=new Score(ScoreTest.packet("sync",2,0,0,0,0,2));
        call(service,"ack",new Class<?>[]{Score.class,String.class},score,"synced");
        check(destinations.size()==2 && destinations.get(1).equals(""),"Sync acknowledgment did not use the empty route");
        check(packets.get(1).getString("status").equals("synced"),"Wrong sync acknowledgment");
        call(service,"ack",new Class<?>[]{Score.class,String.class},score,"spoken");
        check(packets.get(2).getString("status").equals("spoken"),"Speech acknowledgment was skipped");

        call(service,"detach",new Class<?>[0]);
        check(removed.equals(Collections.singletonList("")),"Empty-ID listener was not removed on detach");
        int sent=destinations.size();
        call(service,"hello",new Class<?>[0]);
        check(destinations.size()==sent,"Detached service sent a reply");
        call(service,"detach",new Class<?>[0]);
        check(removed.size()==1,"Detached listener was removed twice");

        set(service,"node","normal-id");
        call(service,"hello",new Class<?>[0]);
        check(destinations.get(sent).equals("normal-id"),"Nonempty node routing regressed");
        call(service,"detach",new Class<?>[0]);
        check(removed.get(1).equals("normal-id"),"Nonempty listener cleanup regressed");

        set(service,"node",null);
        call(service,"receive",new Class<?>[]{String.class,byte[].class},"",new byte[]{123,125});
        check(!prefs.containsKey("bandReceived"),"Detached service accepted an empty-ID message");

        // Exercise the actual receive branch as well as the standalone command
        // parser. Missing AudioManager in this fixture produces an error reply.
        MediaRemote media=new MediaRemote(); media.sync("music-session");
        set(service,"media",media); set(service,"node",""); set(service,"challenge","test-challenge");
        JSONObject control=new JSONObject().put("type","media").put("version",1).put("session","music-session")
            .put("sequence",1).put("action","toggle").put("challenge","test-challenge");
        call(service,"receive",new Class<?>[]{String.class,byte[].class},"",control.toString().getBytes(StandardCharsets.UTF_8));
        JSONObject mediaReply=packets.get(packets.size()-1);
        check(mediaReply.getString("type").equals("mediaAck"),"Media was not routed to its own handler");
        check(mediaReply.getString("status").equals("error"),"Missing audio service was reported as success");
        check(destinations.get(destinations.size()-1).equals(""),"Media reply lost Notify's empty route");
        check(!prefs.containsKey("score") && !prefs.containsKey("speech"),"Media altered score or speech");
        int before=packets.size();
        call(service,"receive",new Class<?>[]{String.class,byte[].class},"other-node",control.toString().getBytes(StandardCharsets.UTF_8));
        check(packets.size()==before,"Media accepted from a different band");
        control.put("challenge","obsolete");
        call(service,"receive",new Class<?>[]{String.class,byte[].class},"",control.toString().getBytes(StandardCharsets.UTF_8));
        check(packets.get(packets.size()-1).getString("type").equals("hello"),"Old challenge dispatched media instead of re-handshaking");
        System.out.println(checks+" Android reply/cleanup checks passed");
    }
}
