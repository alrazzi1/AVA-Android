package com.example.CoulterGlassesDebug;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.Manifest.permission.BLUETOOTH_SCAN;
import static android.app.Activity.RESULT_OK;

import static androidx.core.app.ActivityCompat.requestPermissions;
import static androidx.core.app.ActivityCompat.startActivityForResult;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class BleManager {
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int PERMISSION_REQUEST_CODE = 2;
    private final String LOG_TAG = "BLE MANAGER";
    private AppCompatActivity activity;
    private Handler handler;
    private BluetoothAdapter adapter;
    private static BleManager instance;
    private UUID uuid= UUID.fromString("1f335a7a-6835-45a0-ab4d-54854ef61706");
    private final String DEVICE_NAME = "MyESP32";

    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic characteristic;
    private Status status;

    public enum Status {
        MANAGER_INIT,
        BT_ENABLED(R.color.green), REQUESTING_BT(R.color.yellow), BT_DISABLED(R.color.red),
        PERMS_GRANTED(R.color.green), REQUESTING_PERMS(R.color.yellow), PERMS_DENIED(R.color.red),
        SCANNING(R.color.yellow), SCAN_SUCCESS(R.color.green), SCAN_TIMEOUT(R.color.red),
        SERVICE_FOUND(R.color.green), FINDING_SERVICE(R.color.yellow), SERVICE_NOT_FOUND(R.color.red),
        CONNECTING(R.color.yellow), CONNECTED(R.color.green), CONNECTION_FAILED(R.color.red);
        private int textColor;
        private Status(int textColor){
            this.textColor=textColor;
        }
        private Status(){
            this(R.color.black);
        }
        public int getTextColor() {
            return textColor;
        }
    }

    public static BleManager getInstance(AppCompatActivity activity){
        if(instance!=null){
            instance.destroy();
        }
        instance = new BleManager(activity);
        return instance;
    }

    private void destroy() {
    }

    private BleManager(AppCompatActivity currentActivity){
        handler=new Handler(currentActivity.getMainLooper());
        setStatus(Status.MANAGER_INIT);
        this.activity=currentActivity;
        this.adapter=BluetoothAdapter.getDefaultAdapter();

        enableBluetooth();
        if(status==status.BT_ENABLED && checkPermissions()){
            scan();
        }
    }
    public void enableBluetooth(){
        setStatus(Status.REQUESTING_BT);
        if (adapter == null) {
            Log.d(LOG_TAG, "Device does not support bluetooth");
            setStatus(Status.BT_DISABLED);
        }
        if (!adapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(activity, enableBtIntent, REQUEST_ENABLE_BT, null);
        }else{
            setStatus(Status.BT_ENABLED);
            if(checkPermissions()){
                scan();
            }
        }
    }
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode==REQUEST_ENABLE_BT){
            setStatus(resultCode==RESULT_OK?Status.BT_ENABLED :Status.BT_DISABLED);
            if(resultCode==RESULT_OK && checkPermissions()) scan();
        }
    }
    public boolean checkPermissions(){
        setStatus(Status.REQUESTING_PERMS);
        ArrayList<String> permissions = new ArrayList<String>(){{
            add(ACCESS_COARSE_LOCATION);
            add(BLUETOOTH_SCAN);
            add(BLUETOOTH_CONNECT);
            add(ACCESS_FINE_LOCATION);
        }};
        Context context = activity.getApplicationContext();
        int permissionGranted = PackageManager.PERMISSION_GRANTED;
        Iterator<String> iterator = permissions.iterator();
        while (iterator.hasNext()) {
            String permission = iterator.next();
            if (ActivityCompat.checkSelfPermission(context, permission) == permissionGranted) {
                iterator.remove();
            }
        }
        if (permissions.size()>0) {
            requestPermissions(activity, permissions.toArray(new String[0]), PERMISSION_REQUEST_CODE);
            return false;
        }
        setStatus(Status.PERMS_GRANTED);
        return true;
    }
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        if(requestCode==PERMISSION_REQUEST_CODE){
            boolean permissionsGranted = true;
            for(int grantResult : grantResults){
                if(grantResult!= PackageManager.PERMISSION_GRANTED){
                    permissionsGranted=false;
                    break;
                }
            }
            setStatus(permissionsGranted?Status.PERMS_GRANTED :Status.PERMS_DENIED);
            if(permissionsGranted)scan();
        }
    }

    @SuppressLint("MissingPermission")
    public void scan(){
        setStatus(Status.SCANNING);

        ScanFilter filter = new ScanFilter.Builder().setDeviceName(DEVICE_NAME).build();
        List<ScanFilter> filters=List.of(filter);

        ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        
        adapter.getBluetoothLeScanner().stopScan(scannerCallBack);
        adapter.getBluetoothLeScanner().startScan(filters, settings, scannerCallBack);
        handler.postDelayed(new Runnable(){
            @SuppressLint("MissingPermission")
            @Override
            public void run(){
                if(status==Status.SCANNING){
                    setStatus(Status.SCAN_TIMEOUT);
                }else{
                    setStatus("SCANNING TIMEOUT WITH SUCCESS");
                }
                adapter.getBluetoothLeScanner().stopScan(scannerCallBack);
            }
        }, 2000);
    }
    private ScanCallback scannerCallBack = new ScanCallback(){
        public void onBatchScanResults(List<ScanResult> results) {
            for(ScanResult result: results){
                onScanResult(-1,result);
            }
        }
//
        public void onScanResult(int callbackType, ScanResult result) {
            deviceFound(result.getDevice());
        }

        public void onScanFailed(int errorCode) {
            setStatus(Status.SCAN_TIMEOUT);
            Log.d(LOG_TAG, "Scan Returned Failure" + errorCode);
        }
    };
    @SuppressLint("MissingPermission")
    private void deviceFound(BluetoothDevice device){
        setStatus(Status.SCAN_SUCCESS);
        adapter.getBluetoothLeScanner().stopScan(scannerCallBack);

        device.connectGatt(activity.getApplicationContext(), true, gattCallback);
        setStatus(Status.CONNECTING);
    }

    private BluetoothGattCallback gattCallback = new BluetoothGattCallback(){
        @SuppressLint("MissingPermission")
        public void onConnectionStateChange(BluetoothGatt currentGatt, int status, int newState){
            Log.d(LOG_TAG, "ble connection changed: " + newState);
            if(newState == BluetoothGatt.STATE_CONNECTED){
                setStatus(Status.CONNECTED);
                setStatus(Status.FINDING_SERVICE);
                Log.d(LOG_TAG, "before");
                currentGatt.discoverServices();
                Log.d(LOG_TAG, "after");
                Log.d(LOG_TAG, "# services: " + gatt.getServices().size());
            }
            else if(newState == BluetoothGatt.STATE_DISCONNECTED){
                setStatus(Status.CONNECTION_FAILED);
                if(gatt!=null){
                    gatt.close();
                }
                gatt=null;
                characteristic=null;
            }else{
                if(gatt!=null){
                    gatt.close();
                }
                gatt=null;
                characteristic=null;
            }
        }

        @SuppressLint("MissingPermission")
        public void onServicesDiscovered(BluetoothGatt currentGatt, int status) {
            setStatus(Status.SERVICE_FOUND);
            BluetoothGattCharacteristic currentCharacteristic = currentGatt.getService(uuid).getCharacteristic(uuid);
//            characteristic?.setValue("Hello")
//            gatt?.writeCharacteristic(characteristic)
            gatt = currentGatt;
            characteristic=currentCharacteristic;
        }
    };
    public Status getStatus(){
        return status;
    }
    public void setStatus(Status status){
        this.status=status;
        handler.post(new Runnable() {
            @Override
            public void run() {
                TextView bluetoothStatusTv = (TextView) activity.findViewById(R.id.ble_status);
                bluetoothStatusTv.setText(status.toString());
                bluetoothStatusTv.setBackgroundColor(activity.getResources().getColor(status.getTextColor()));
            }
        });
        Log.d(LOG_TAG, "status changed: "+status.toString());
    }
    public void setStatus(String msg){
        Log.d(LOG_TAG, msg);
    }

    public BluetoothGatt getGatt(){
        return gatt;
    }
    public BluetoothGattCharacteristic getCharacteristic(){
        return characteristic;
    }
}
