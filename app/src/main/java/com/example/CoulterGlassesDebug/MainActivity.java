package com.example.CoulterGlassesDebug;

import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.PermissionChecker;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.CoulterGlassesDebug.databinding.ActivityMainBinding;

import edu.wpi.first.math.geometry.Transform3d;


public class MainActivity extends AppCompatActivity {

    public MainActivity(){
        super();
    }
    private final static int REQUEST_CAMERA = 0;
    public static MainActivity instance = null;
    private SoundManager soundManager;
    private ActivityMainBinding viewBinding;
    private BleManager bleManager;
    private final String LOG_TAG = "MAIN_LOG";
    private int targetId = 0;
    private static double TURN_THRESHOLD = 5;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(viewBinding.getRoot());
        getSupportActionBar().hide();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        replaceDemoFragment(new cameraFragment());

        instance = this;
        bleManager = BleManager.getInstance(this);
        soundManager = SoundManager.getInstance(this);

        findViewById(R.id.scan_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bleManager.getStatus() == BleManager.Status.SCAN_TIMEOUT ||
                        bleManager.getStatus() == BleManager.Status.CONNECTION_FAILED) {
                    bleManager.scan();
                }
            }
        });

        EditText targetIdEt = (EditText)findViewById(R.id.atTargetID);
        targetIdEt.setText(targetId + "");
        targetIdEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                char[] newId = {'0'};
                s.getChars(0, s.length(), newId, 0);
                targetId = Character.getNumericValue(newId[0]);
            }
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });
    }
    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    @SuppressLint("MissingPermission")
    public void sendToESP(Result result){
        if(result.getNumDetections()>0){
            Transform3d camToTarget = cameraFragment.getCamToTarget(result);

            var dist = camToTarget.getTranslation().getNorm();
            //var pitch = Rotation2d(camToTarget!!.rotation.y).degrees;

            var relativeAngle = (result.getCenter_pixels()[0] - cameraFragment.getFrameDimensions().getWidth()/2.0) * (97.0/cameraFragment.getFrameDimensions().getWidth());
            var bestAtTranslation = apriltag.getApriltagMap().get(result.getId()).getTranslation();
            var bestAtAngle = Math.toDegrees(Math.atan2(bestAtTranslation.getY(), bestAtTranslation.getX()));
            var angle = bestAtAngle + relativeAngle;

            dist = Helper.roundToDecimalPlaces(dist, 2);
            angle = Helper.roundToDecimalPlaces(angle, 0);
            relativeAngle = Helper.roundToDecimalPlaces(relativeAngle, 0);
            bestAtAngle = Helper.roundToDecimalPlaces(bestAtAngle, 0);

            var targetAtTranslation = apriltag.getApriltagMap().get(targetId).getTranslation();
            var targetAtAngle = Math.toDegrees(Math.atan2(targetAtTranslation.getY(), targetAtTranslation.getX()));

            var turn = calcTurn(angle, bestAtAngle);

            String msg = getESPMsg(turn);
            Log.d(LOG_TAG, msg);

            TextView myTextView = findViewById(R.id.polar_output);
            myTextView.setText("ID: " + result.getId() + "; ANGLE: " + angle + "; DEST ANGLE: " + targetAtAngle);

            BluetoothGattCharacteristic characteristic = bleManager.getCharacteristic();
            if(characteristic != null){
                characteristic.setValue(msg);
                bleManager.getGatt().writeCharacteristic(characteristic);
            }

            if(soundManager.timeSinceCompleted() > 1000){
                if(turn.direction == Direction.STOP){
                    soundManager.play(R.raw.stop);
                }else if(turn.direction == Direction.RIGHT){
                    soundManager.play(R.raw.turn_right);
                }else{
                    soundManager.play(R.raw.turn_left);
                }
            }
        }else{
            TextView myTextView = findViewById(R.id.polar_output);
            myTextView.setText("No Apriltags Detected");
        }
    }
    public static enum Direction {
        LEFT, RIGHT, STOP;
    }

    public static class Turn{
        public Direction direction;
        public double mag;
        public Turn(Direction direction, double mag){
            this.direction = direction;
            this.mag = mag;
        }
    }
    public static Turn calcTurn(double currentAngle, double targetAngle) {
        currentAngle = normalizeAngle(currentAngle);
        targetAngle = normalizeAngle(targetAngle);

        if (Math.abs(currentAngle - targetAngle) < TURN_THRESHOLD) {
            return new Turn(Direction.STOP, Math.abs(currentAngle-targetAngle));
        }

        double[] targets = {targetAngle, targetAngle+360, targetAngle-360};
        double closestTarget= targets[0];
        double closestDistance = Double.MAX_VALUE;
        for (double target : targets) {
            if(Math.abs(target-currentAngle) < closestDistance){
                closestTarget= target;
                closestDistance = Math.abs(target-currentAngle);
            }
        }

        if (closestTarget > currentAngle) {
            return new Turn(Direction.LEFT, closestDistance);
        } else {
            return new Turn(Direction.RIGHT, closestDistance);
        }
    }

    public static double determineTurnMag(double currentAngle, double targetAngle) {
        currentAngle = normalizeAngle(currentAngle);
        targetAngle = normalizeAngle(targetAngle);

        if (Math.abs(currentAngle - targetAngle) < 5) {
            return 0;
        }

        double[] targets = {targetAngle, targetAngle+360, targetAngle-360};
        double closestTarget= targets[0];
        double closestDistance = Double.MAX_VALUE;
        for (double target : targets) {
            if(Math.abs(target-currentAngle) < closestDistance){
                closestTarget= target;
                closestDistance = Math.abs(target-currentAngle);
            }
        }
        return closestDistance;
    }

    public static String getESPMsg(Turn turn){
        var vibration_period = Helper.reverseLinearMap(turn.mag, 10, 1000, 0, 50);
        vibration_period = Helper.roundToDecimalPlaces(vibration_period, 2);
        switch(turn.direction){
            case STOP:
                return "0; 0";
            case LEFT:
                return vibration_period + "; 0";
            case RIGHT:
                return "0; " + vibration_period;
            default:
                return "error";
        }
    }

    private static double normalizeAngle(double angle) {
        // Convert angle from radians to degrees if necessary
        // angle = angle * 180 / Math.PI;

        // Normalize angle to be within [0, 360)
        while (angle >= 360.0) {
            angle -= 360.0;
        }
        while (angle < 0.0) {
            angle += 360.0;
        }
        return angle;
    }
    public void replaceDemoFragment(Fragment fragment) {
        int hasCameraPermission = PermissionChecker.checkSelfPermission(this, CAMERA);
        if (hasCameraPermission != PermissionChecker.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, CAMERA)) {
            }
            ActivityCompat.requestPermissions(
                this,
                    new String[]{CAMERA, WRITE_EXTERNAL_STORAGE, RECORD_AUDIO},
                REQUEST_CAMERA
            );
            return;
        }
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, fragment);
        transaction.commitAllowingStateLoss();
    }
    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        bleManager.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case REQUEST_CAMERA:
                int hasCameraPermission = PermissionChecker.checkSelfPermission(this, CAMERA);
                if (hasCameraPermission == PermissionChecker.PERMISSION_DENIED) {
                    return;
                }
                replaceDemoFragment(new cameraFragment());
                break;
            // Handle other request codes if needed
        }
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        bleManager.onActivityResult(requestCode, resultCode, data);
    }
}
