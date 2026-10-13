package com.wmahmood.pickleball;

import android.bluetooth.*;
import android.content.Context;
import android.os.*;
import java.util.UUID;

/** Reads only the standard HR service. Never pairs, starts a workout, or writes
 * Xiaomi commands. GATT notifications run independently of Notify Interconnect. */
final class HeartRateClient {
    static final UUID SERVICE=UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");
    static final UUID MEASUREMENT=UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD=UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    interface Listener { void status(String value); void sample(int bpm); }
    private final Context context;
    private final Listener listener;
    private final Handler main=new Handler(Looper.getMainLooper());
    private BluetoothGatt gatt;
    private boolean closed;
    private final Runnable timeout=() -> fail("Heart-rate connection timed out. Retry or check broadcasting.");
    HeartRateClient(Context context,Listener listener) { this.context=context; this.listener=listener; }
    void connect(String address) {
        try {
            BluetoothManager manager=context.getSystemService(BluetoothManager.class);
            BluetoothAdapter adapter=manager==null ? null : manager.getAdapter();
            if (adapter==null || !adapter.isEnabled()) { fail("Bluetooth is off or unavailable."); return; }
            listener.status("Connecting to selected heart-rate source...");
            gatt=adapter.getRemoteDevice(address).connectGatt(context,false,callback,BluetoothDevice.TRANSPORT_LE);
            if (gatt==null) { fail("Could not open heart-rate connection."); return; }
            main.postDelayed(timeout,20000);
        } catch (RuntimeException e) { fail("Heart-rate connection unavailable. Check Nearby devices permission and Bluetooth."); }
    }
    private final BluetoothGattCallback callback=new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt source,int status,int state) { main.post(() -> {
            if (!current(source)) return;
            if (status!=BluetoothGatt.GATT_SUCCESS || state==BluetoothProfile.STATE_DISCONNECTED) {
                fail("Heart-rate link disconnected (GATT "+status+"). Session continues; tap Retry heart rate."); return;
            }
            if (state==BluetoothProfile.STATE_CONNECTED) {
                listener.status("Checking standard heart-rate service...");
                try { if (!source.discoverServices()) fail("Heart-rate service discovery could not start."); }
                catch (RuntimeException e) { fail("Heart-rate service discovery denied."); }
            }
        }); }
        @Override public void onServicesDiscovered(BluetoothGatt source,int status) { main.post(() -> {
            if (!current(source)) return;
            try {
                BluetoothGattService service=source.getService(SERVICE);
                BluetoothGattCharacteristic measurement=service==null ? null : service.getCharacteristic(MEASUREMENT);
                if (status!=0 || measurement==null) { fail("No standard heart-rate service. Enable heart-rate broadcasting; this test does not start a native workout."); return; }
                BluetoothGattDescriptor descriptor=measurement.getDescriptor(CCCD);
                if (descriptor==null || (measurement.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY)==0 || !source.setCharacteristicNotification(measurement,true)) {
                    fail("Heart-rate notifications are unavailable."); return;
                }
                listener.status("Subscribing to heart-rate readings...");
                boolean ok;
                if (Build.VERSION.SDK_INT>=33) ok=source.writeDescriptor(descriptor,BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)==BluetoothStatusCodes.SUCCESS;
                else { descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE); ok=source.writeDescriptor(descriptor); }
                if (!ok) fail("Heart-rate subscription could not start.");
            } catch (RuntimeException e) { fail("Heart-rate subscription denied. Check Bluetooth permission."); }
        }); }
        @Override public void onDescriptorWrite(BluetoothGatt source,BluetoothGattDescriptor descriptor,int status) { main.post(() -> {
            if (!current(source) || !CCCD.equals(descriptor.getUuid())) return;
            if (status!=0) { fail("Heart-rate subscription failed (GATT "+status+")."); return; }
            main.removeCallbacks(timeout);
            listener.status("Subscribed. Waiting for a fresh heart-rate reading...");
        }); }
        @Override public void onCharacteristicChanged(BluetoothGatt source,BluetoothGattCharacteristic characteristic,byte[] value) {
            if (MEASUREMENT.equals(characteristic.getUuid())) received(source,value.clone());
        }
        @Override public void onCharacteristicChanged(BluetoothGatt source,BluetoothGattCharacteristic characteristic) {
            if (Build.VERSION.SDK_INT<33 && MEASUREMENT.equals(characteristic.getUuid())) {
                byte[] value=characteristic.getValue(); received(source,value==null ? null : value.clone());
            }
        }
    };
    private void received(BluetoothGatt source,byte[] data) { main.post(() -> {
        if (!current(source)) return;
        int bpm=HeartRateMeasurement.parse(data);
        if (bpm<0) { listener.status("Reading rejected: no skin contact or invalid heart-rate packet."); return; }
        listener.status("Receiving heart-rate readings"); listener.sample(bpm);
    }); }
    private boolean current(BluetoothGatt source) { return !closed && gatt==source; }
    private void fail(String status) { close(); listener.status(status); }
    void close() {
        closed=true; main.removeCallbacksAndMessages(null);
        BluetoothGatt previous=gatt; gatt=null;
        if (previous!=null) { try { previous.disconnect(); } catch (RuntimeException ignored) {} try { previous.close(); } catch (RuntimeException ignored) {} }
    }
}
