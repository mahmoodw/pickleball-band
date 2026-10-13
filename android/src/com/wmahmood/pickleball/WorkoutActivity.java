package com.wmahmood.pickleball;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.widget.*;
import java.io.*;
import java.util.*;

public class WorkoutActivity extends Activity {
    private final Handler main=new Handler(Looper.getMainLooper());
    private TextView status, source, feedback;
    private Button start, stop;
    private BluetoothLeScanner scanner;
    private final Map<String,BluetoothDevice> devices=new LinkedHashMap<>();
    private ArrayAdapter<String> choices;
    private AlertDialog chooser;
    private File exportFile;
    private boolean exportTcx;
    private final Runnable scanTimeout=() -> {
        stopScan(); feedback.setText(devices.isEmpty() ? "No heart-rate broadcast found. Try Check already connected devices to inspect the band Notify is using." : "Scan finished. Select your band from the list.");
    };
    private final Runnable refresh=new Runnable() { public void run() { render(); main.postDelayed(this,1000); }};
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state!=null) {
            String name=state.getString("exportFile");
            if (name!=null) exportFile=new File(WorkoutService.directory(this),new File(name).getName());
            exportTcx=state.getBoolean("exportTcx");
        }
        ScrollView scroll=new ScrollView(this);
        LinearLayout body=new LinearLayout(this); body.setOrientation(1); body.setPadding(24,48,24,36); body.setBackgroundColor(Color.rgb(7,21,16));
        scroll.setOnApplyWindowInsetsListener((v,insets) -> { v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom()); return insets.consumeSystemWindowInsets(); });
        scroll.addView(body); setContentView(scroll);
        label(body,"WORKOUT LAB",24);
        label(body,"Enable heart-rate broadcasting on the band, keep Notify connected, and open Pickleball Lab. Do not start a native workout for this test.",16);
        source=label(body,"",16);
        button(body,"Find heart-rate source",this::scan);
        button(body,"Check already connected devices",this::connectedDevices);
        button(body,"Use timer only",() -> { getSharedPreferences(WorkoutService.PREFS,0).edit().remove("source").remove("sourceName").apply(); retry(); render(); });
        start=button(body,"Start test session",this::startSession);
        button(body,"Retry heart rate",this::retry);
        stop=button(body,"Finish session",() -> { if (WorkoutService.active!=null) startService(new Intent(this,WorkoutService.class).setAction("stop")); });
        status=label(body,"",16);
        button(body,"Copy workout diagnostics",this::copyReport);
        button(body,"Export session as TCX",() -> chooseExport(true));
        button(body,"Export diagnostic CSV",() -> chooseExport(false));
        button(body,"Back to announcer",this::finish);
        feedback=label(body,"",15);
        label(body,"The phone records timing and received heart rate, including when its screen is off. Watch the sample count and last-reading age: a Bluetooth connection alone does not prove live data. Missing readings stay missing. TCX can be imported manually into Notify; distance and calories are not measured. Stopping the announcer does not stop this recorder; use Finish session.",15);
    }
    private TextView label(LinearLayout parent,String text,int size) {
        TextView v=new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(Color.WHITE); v.setPadding(0,12,0,14); parent.addView(v); return v;
    }
    private Button button(LinearLayout parent,String text,Runnable action) {
        Button b=new Button(this); b.setText(text); b.setAllCaps(false); b.setMinHeight(Math.round(56*getResources().getDisplayMetrics().density)); b.setOnClickListener(v -> action.run()); parent.addView(b); return b;
    }
    private boolean permissions() {
        String[] needed=Build.VERSION.SDK_INT>=31 ? new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT} : new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
        for (String p:needed) if (checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(needed,20); feedback.setText("Allow Bluetooth access, then tap the button again."); return false;
        }
        return true;
    }
    private BluetoothAdapter adapter() {
        BluetoothManager manager=getSystemService(BluetoothManager.class);
        BluetoothAdapter a=manager==null ? null : manager.getAdapter();
        if (a==null || !a.isEnabled()) { feedback.setText("Turn on Bluetooth, then retry."); return null; } return a;
    }
    private void showChooser() {
        stopScan(); devices.clear(); choices=new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,new ArrayList<String>());
        chooser=new AlertDialog.Builder(this).setTitle("Choose your heart-rate device")
            .setAdapter(choices,(d,index) -> {
                BluetoothDevice device=new ArrayList<>(devices.values()).get(index);
                try {
                    getSharedPreferences(WorkoutService.PREFS,0).edit().putString("source",device.getAddress()).putString("sourceName",name(device)).apply();
                    feedback.setText("Source saved. Start a session, or tap Retry heart rate during a session."); render();
                } catch (SecurityException e) { feedback.setText("Bluetooth permission was removed. Grant access and retry."); }
            }).setNegativeButton("Close",null).create();
        chooser.setOnDismissListener(d -> { stopScan(); chooser=null; }); chooser.show();
    }
    private String name(BluetoothDevice device) { String n=device.getName(); return n==null || n.isEmpty() ? "Unnamed Bluetooth device" : n; }
    private void add(BluetoothDevice device) {
        if (chooser==null || !chooser.isShowing()) return;
        try {
            String address=device.getAddress();
            if (!devices.containsKey(address)) { devices.put(address,device); choices.add(name(device)+" · "+address.substring(Math.max(0,address.length()-5))); }
        } catch (SecurityException ignored) {}
    }
    private final ScanCallback scanCallback=new ScanCallback() {
        @Override public void onScanResult(int type,ScanResult result) { main.post(() -> add(result.getDevice())); }
        @Override public void onScanFailed(int code) { main.post(() -> { stopScan(); feedback.setText("Bluetooth scan failed ("+code+"). Check Bluetooth and permissions."); }); }
    };
    private void scan() {
        if (!permissions()) return;
        try {
            BluetoothAdapter a=adapter(); if (a==null) return;
            showChooser(); scanner=a.getBluetoothLeScanner();
            if (scanner==null) { feedback.setText("Bluetooth scanner unavailable."); return; }
            scanner.startScan(Collections.singletonList(new ScanFilter.Builder().setServiceUuid(new ParcelUuid(HeartRateClient.SERVICE)).build()),
                new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),scanCallback);
            feedback.setText("Scanning for 15 seconds. Only standard heart-rate advertisements are shown.");
            main.postDelayed(scanTimeout,15000);
        } catch (RuntimeException e) { stopScan(); feedback.setText("Cannot scan. Check Bluetooth permissions; Android 8–11 also requires Location to be enabled."); }
    }
    private void connectedDevices() {
        if (!permissions()) return;
        try {
            if (adapter()==null) return;
            List<BluetoothDevice> list=getSystemService(BluetoothManager.class).getConnectedDevices(BluetoothProfile.GATT);
            showChooser(); for (BluetoothDevice device:list) add(device);
            feedback.setText(list.isEmpty() ? "Android reports no connected BLE devices." : "Select your band. These devices are not yet confirmed to expose heart rate.");
        } catch (RuntimeException e) { feedback.setText("Could not inspect connected Bluetooth devices."); }
    }
    private void stopScan() { main.removeCallbacks(scanTimeout); if (scanner!=null) { try { scanner.stopScan(scanCallback); } catch (RuntimeException ignored) {} scanner=null; } }
    private void startSession() {
        if (!permissions() || WorkoutService.active!=null) return;
        if (Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},21);
        try { startForegroundService(new Intent(this,WorkoutService.class).setAction("start")); }
        catch (RuntimeException e) { feedback.setText("Could not start recording. Check Nearby devices permission and try with this screen open."); }
    }
    private void retry() { if (WorkoutService.active!=null && permissions()) startService(new Intent(this,WorkoutService.class).setAction("retry")); }
    private void render() {
        SharedPreferences p=getSharedPreferences(WorkoutService.PREFS,0);
        source.setText("Heart-rate source: "+p.getString("sourceName","Timer only"));
        WorkoutService recorder=WorkoutService.active;
        start.setEnabled(recorder==null); stop.setEnabled(recorder!=null);
        status.setText(recorder==null ? "Recorder stopped.\n"+p.getString("report","No test session yet.")+ (p.getString("state","").equals("Recording") ? "\nPrevious recording was interrupted; export its saved samples." : "") : recorder.report());
    }
    private void copyReport() {
        SharedPreferences p=getSharedPreferences(WorkoutService.PREFS,0), a=getSharedPreferences(AnnouncerService.PREFS,0);
        String report="Pickleball Lab 0.1.11-workout.1 / Android API "+Build.VERSION.SDK_INT+"\nRecorder running: "+(WorkoutService.active!=null)+"\n"+
            (WorkoutService.active==null ? p.getString("report","No session") : WorkoutService.active.report())+
            "\nAnnouncer running: "+AnnouncerService.running+"\nNotify service visible: "+!NotifyBridge.availablePackage(this).isEmpty()+
            "\nScore connection: "+a.getString("connection","Not started")+"\nVoice: "+a.getString("speech","Not started");
        getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Workout diagnostics",report)); feedback.setText("Workout diagnostics copied. Device names and Bluetooth addresses are excluded.");
    }
    private void chooseExport(boolean tcx) {
        File[] files=WorkoutService.directory(this).listFiles((dir,name) -> name.endsWith(".csv"));
        if (files==null || files.length==0) { feedback.setText("Record a test session first."); return; }
        Arrays.sort(files,Comparator.comparing(File::getName).reversed());
        String[] names=new String[files.length];
        for (int i=0;i<files.length;i++) names[i]=files[i].getName();
        new AlertDialog.Builder(this).setTitle("Choose saved session (newest first)").setItems(names,(d,index) -> {
            exportFile=files[index]; exportTcx=tcx;
            if (WorkoutService.active!=null && exportFile.getName().equals(getSharedPreferences(WorkoutService.PREFS,0).getString("latest",""))) {
                feedback.setText("Finish this session before exporting it. Older sessions can be exported while recording."); return;
            }
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType(tcx ? "application/vnd.garmin.tcx+xml" : "text/csv")
                .putExtra(Intent.EXTRA_TITLE,exportFile.getName().replace(".csv",tcx ? ".tcx" : ".csv"));
            try { startActivityForResult(intent,30); } catch (ActivityNotFoundException e) { feedback.setText("No document picker available on this phone."); }
        }).setNegativeButton("Cancel",null).show();
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (request!=30 || result!=RESULT_OK || data==null || data.getData()==null || exportFile==null) return;
        final File file=exportFile; final boolean tcx=exportTcx; final android.net.Uri uri=data.getData();
        new Thread(() -> {
            String message;
            try (OutputStream out=getContentResolver().openOutputStream(uri,"wt")) {
                if (out==null) throw new IOException("No output stream");
                if (tcx) WorkoutSession.exportTcx(file,out);
                else try (InputStream in=new FileInputStream(file)) { byte[] buffer=new byte[8192]; int n; while ((n=in.read(buffer))!=-1) out.write(buffer,0,n); }
                message=tcx ? "TCX exported. Import it manually in Notify. Distance and calories are unmeasured." : "Diagnostic CSV exported.";
            } catch (IOException | RuntimeException e) { message="Export failed. Choose another destination and retry."; }
            final String shown=message; runOnUiThread(() -> { if (!isDestroyed()) feedback.setText(shown); });
        },"workout-export").start();
    }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); if (exportFile!=null) out.putString("exportFile",exportFile.getName()); out.putBoolean("exportTcx",exportTcx); }
    @Override public void onResume() { super.onResume(); main.post(refresh); }
    @Override public void onPause() { main.removeCallbacks(refresh); stopScan(); super.onPause(); }
    @Override public void onDestroy() { main.removeCallbacksAndMessages(null); stopScan(); if (chooser!=null) chooser.dismiss(); super.onDestroy(); }
}
