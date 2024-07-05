package com.example.ausbctest

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.BLUETOOTH_SCAN
import android.Manifest.permission.CAMERA
import android.Manifest.permission.RECORD_AUDIO
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.PermissionChecker
import androidx.fragment.app.Fragment
import com.example.ausbctest.databinding.ActivityMainBinding
import java.util.UUID


class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        replaceDemoFragment(cameraFragment())
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        supportActionBar?.hide()
        findViewById<Button>(R.id.btn)
            .setOnClickListener {
                Log.d("ble  log", "inside listener: button pressed")
                connect_ble()
            }
    }
    val my_hander: Handler = Handler();

    fun connect_ble(){
        scanForDevices(getApplicationContext(), service_UUID, my_hander);
        Log.d("ble log", "button pressed");
    }

    @SuppressLint("MissingPermission")
    fun scanForDevices(context: Context, serviceUUID: UUID, handler:Handler){
        val adapter= BluetoothAdapter.getDefaultAdapter()
        if(!adapter.isEnabled){
            Log.d("ble log", "scanForDevices error")
            return;
        }

        val uuid= ParcelUuid(serviceUUID);
        val filter= ScanFilter.Builder().setDeviceName("MyESP32").build()
        val filters=listOf(filter)

        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        val permissions = arrayOf(ACCESS_COARSE_LOCATION, BLUETOOTH_SCAN, BLUETOOTH_CONNECT, ACCESS_FINE_LOCATION);

        if (!checkPermissions(permissions)){
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, ACCESS_COARSE_LOCATION)) {
                val x = "here we art";
//                ToastUtils.show(R.string.permission_tip)
            }
            ActivityCompat.requestPermissions(
                this,
                permissions,
                3
            )
            if(!checkPermissions(arrayOf(ACCESS_COARSE_LOCATION))){
                Log.d("ble log", "ACCESS_COURSE_LOCATION not allowed")
            }
            if(!checkPermissions(arrayOf(BLUETOOTH_SCAN, BLUETOOTH_CONNECT))){
                Log.d("ble log", "other bluetooth not allowed")
            }
            Log.d("ble log", "permission not granted");
            return
        }
        if (!adapter.isEnabled()) {
            Log.d("ble log", "ADAPTER NOT ENABLED")
        }
        adapter.bluetoothLeScanner.stopScan(scannerCallBack);
        adapter.bluetoothLeScanner.startScan(filters, settings, scannerCallBack);
        Handler(Looper.getMainLooper()).postDelayed(Runnable(){
            @Override
            fun run(){
                Log.d("ble log", "scan timed out");
                adapter.bluetoothLeScanner.stopScan(scannerCallBack);
            }
        }, 2000);
        Log.d("ble log", "scan started");
    }
//
    private val scannerCallBack = object : ScanCallback(){
        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                results?.forEach{result->
                    deviceFound(result.device)
            }
        }
//
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let{deviceFound(result.device)}
        }

        override fun onScanFailed(errorCode: Int) {
            Log.d("ble error", "Scan Returned Failure" + errorCode)
        }
    }
//    @SuppressLint("MissingPermission")
    @SuppressLint("MissingPermission")
    private fun deviceFound(device: BluetoothDevice){
        Log.d("ble log", "device found");
        val adapter= BluetoothAdapter.getDefaultAdapter()
        adapter.bluetoothLeScanner.stopScan(scannerCallBack);

        device.connectGatt(getApplicationContext(), true, gattCallback)
    }
    private val gattCallback = object : BluetoothGattCallback(){
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int){
            Log.d("ble log", "ble connection changed: " + newState)
            if(newState == BluetoothGatt.STATE_CONNECTED){
                gatt?.discoverServices();
                Log.d("ble log", "# services: " + gatt?.services?.size)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            Log.d("ble log", "ble services discovered")
            val characteristic = gatt?.getService(service_UUID)
                ?.getCharacteristic(service_UUID);
//            characteristic?.setValue("Hello")
//            gatt?.writeCharacteristic(characteristic)
            global_gatt = gatt;
        }


    }
    private fun checkPermissions(permissions: Array<String>): Boolean{
        for(permission in permissions){
            val hasPermission = PermissionChecker.checkSelfPermission(this, permission)
            if(hasPermission!=PermissionChecker.PERMISSION_GRANTED){
                return false
            }
        }
        return true;
    }

    private fun replaceDemoFragment(fragment: Fragment) {
        val hasCameraPermission = PermissionChecker.checkSelfPermission(this, CAMERA)
//        val hasStoragePermission =
//            PermissionChecker.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE)
        if (hasCameraPermission != PermissionChecker.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, CAMERA)) {
//                ToastUtils.show(R.string.permission_tip)
            }
            ActivityCompat.requestPermissions(
                this,
                arrayOf(CAMERA,
                    WRITE_EXTERNAL_STORAGE, RECORD_AUDIO
                ),
                REQUEST_CAMERA
            )
            return
        }
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container, fragment)
        transaction.commitAllowingStateLoss()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CAMERA -> {
                val hasCameraPermission = PermissionChecker.checkSelfPermission(this, CAMERA)
                if (hasCameraPermission == PermissionChecker.PERMISSION_DENIED) {
//                    ToastUtils.show(R.string.permission_tip)
                    return
                }
                replaceDemoFragment(cameraFragment())
            }
            else -> {
            }
        }

    }
    companion object {
        private const val REQUEST_CAMERA = 0
        public var global_gatt: BluetoothGatt? = null;
        public val service_UUID: UUID = UUID.fromString("1f335a7a-6835-45a0-ab4d-54854ef61706");
    }
}
