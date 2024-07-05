package com.example.ausbctest
import android.R
import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.ausbctest.databinding.CameraFragmentBinding
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.base.CameraFragment
import com.jiangdg.ausbc.callback.ICameraStateCallBack
import com.jiangdg.ausbc.callback.IPreviewDataCallBack
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.jiangdg.ausbc.render.env.RotateType
import com.jiangdg.ausbc.widget.IAspectRatio
import edu.wpi.first.math.MatBuilder
import edu.wpi.first.math.geometry.Transform3d
import edu.wpi.first.math.geometry.Translation2d
import java.math.BigDecimal
import java.math.RoundingMode
import edu.wpi.first.math.Matrix
import edu.wpi.first.math.Nat
import edu.wpi.first.math.geometry.Rotation2d
import edu.wpi.first.math.geometry.Rotation3d
import edu.wpi.first.math.geometry.Translation3d
import edu.wpi.first.math.numbers.N2
import edu.wpi.first.math.numbers.N3
import kotlin.math.pow
import kotlin.math.roundToInt

class cameraFragment() : CameraFragment() {
    private var mViewBinding: CameraFragmentBinding? = null

    data class FrameDimensions(val width: Int, val height: Int)
    var frameDimensions: FrameDimensions? = FrameDimensions(640,480);
    val analysisCallBack = object : IPreviewDataCallBack {
        @SuppressLint("MissingPermission")
        override fun onPreviewData(
            data: ByteArray?,
            width: Int,
            height: Int,
            format: IPreviewDataCallBack.DataFormat
        ) {
            // This function will be called when preview data is available
            // You can process the preview data here
            // The 'data' parameter contains the raw preview data
            // The 'format' parameter specifies the format of the data (e.g., NV21 or RGBA)
            // The 'width' and 'height' parameters specify the dimensions of the preview frame
//            updateResolution(320, 240);
            Log.d("Preview Data Received", "Width: $width, Height: $height")
            // INFO: RGBA, width=1116, height=837
            // Example: Log the width and height of the preview frame
//                if (width > 800) {
//                    return;
//                }

            // Example: Process the preview data (replace this with your own logic)
            if (data != null) {
                processPreviewData(data, width, height, format)
            }
        }
    }

    override fun getRootView(inflater: LayoutInflater, container: ViewGroup?): View? {
        if (mViewBinding == null) {
            mViewBinding = CameraFragmentBinding.inflate(inflater, container, false)
        }
//            updateResolution(320, 240);
        getCameraView()?.setAspectRatio(frameDimensions?.width!!, frameDimensions?.height!!);//320,240
        // Define a function that implements the IPreviewDataCallBack interface
        // Implementing the IPreviewDataCallBack interface

        addPreviewDataCallBack(analysisCallBack);
        return mViewBinding?.root
    }
    fun roundToDecimalPlaces(number: Double, decimalPlaces: Int): Double {
        require(decimalPlaces >= 0) { "Decimal places must be non-negative" }
        val bd = BigDecimal(number).setScale(decimalPlaces, RoundingMode.HALF_UP)
        return bd.toDouble()
    }
    private fun processPreviewData(
        data: ByteArray,
        width: Int,
        height: Int,
        format: IPreviewDataCallBack.DataFormat
    ) {
//        Log.d("child count", getCameraViewContainer()?.childCount.toString());
//        Log.d("Format of image", format.toString());
        var result = apriltagAnalysis(data, width, height);
        print_transform_output(result);
        sendToESP(result);
    }

    private fun print_transform_output(result: Result) {
        val transform_output_tv: TextView = requireActivity().findViewById<TextView>(
            com.example.ausbctest.R.id.transform_output);
        if(result.numDetections>0){
            val transformPacket = result.camToTargetPacket;
            val camToTarget = getCamToTarget(result);
            var x = camToTarget!!.translation.x
            var y = camToTarget!!.translation.y;
            var z = camToTarget!!.translation.z;
            var yaw = Rotation2d(camToTarget!!.rotation.z).degrees;
            var pitch = Rotation2d(camToTarget!!.rotation.y).degrees;
            var roll = Rotation2d(camToTarget!!.rotation.x).degrees;
            x = roundToDecimalPlaces(x, 2);
            y= roundToDecimalPlaces(y, 2);
            z = roundToDecimalPlaces(z, 2);
            yaw = roundToDecimalPlaces(yaw, 0);
            pitch = roundToDecimalPlaces(pitch, 0);
            roll = roundToDecimalPlaces(roll, 0);
            val cx = result.center_pixels?.get(0);
            val cy = result.center_pixels?.get(1);

            transform_output_tv.text = "x: $x, y: $y, z: $z,\n yaw: $yaw, pitch: $pitch, roll: $roll, cx: $cx, cy: $cy";
        }else{
            transform_output_tv.text = "No Apriltags"
        }
    }
    fun roundToDecimalPlaces(values: List<Double>, decimalPlaces: Int): List<Double> {
        val factor = 10.0.pow(decimalPlaces)
        return values.map { (it * factor).roundToInt() / factor }
    }

    private fun getCamToTarget(result: Result): Transform3d?{
        if(result.numDetections>0){
            val transformPacket = result.camToTargetPacket;

            val translation = Translation3d(transformPacket!!.x,transformPacket!!.y,transformPacket!!.z);

            val r_matrix: Matrix<N3, N3> = MatBuilder(Nat.N3(), Nat.N3()).fill(
                transformPacket.r1, transformPacket.r2, transformPacket.r3,
                transformPacket.r4, transformPacket.r5, transformPacket.r6,
                transformPacket.r7, transformPacket.r8, transformPacket.r9
            );
            val rotation = Rotation3d(r_matrix);
            val camToTarget = Transform3d(translation, rotation);
            return camToTarget;
        }
        return null;
    }
    @SuppressLint("MissingPermission")
    private fun sendToESP(result:Result){
        if(result.numDetections>0){
            var camToTarget = getCamToTarget(result);
            var dist = camToTarget!!.translation.norm;
//            var pitch = Rotation2d(camToTarget!!.rotation.y).degrees;
            var angle = (result.center_pixels!!.get(0) - frameDimensions!!.width/2.0) * (97.0/640);
            dist = roundToDecimalPlaces(dist, 2);
            angle = roundToDecimalPlaces(angle, 0);
            Log.d("APRILTAG", "Dist: $dist; Angle: $angle")
            var gatt = MainActivity.global_gatt;
            val myTextView: TextView = requireActivity().findViewById<TextView>(
                com.example.ausbctest.R.id.polar_output);
            // Change the text of the TextView
            myTextView.text = "Dist: $dist; Angle: $angle"
            if(gatt != null){
                val characteristic = gatt?.getService(MainActivity.service_UUID)
                ?.getCharacteristic(MainActivity.service_UUID);
                characteristic?.setValue("Dist: $dist; Angle: $angle")
                gatt?.writeCharacteristic(characteristic)
            }
        }else{
            val myTextView: TextView = requireActivity().findViewById<TextView>(
                com.example.ausbctest.R.id.polar_output);
            // Change the text of the TextView
            myTextView.text = "No Apriltags"
        }
    }
    private fun apriltagAnalysis(
        data: ByteArray,
        width: Int,
        height: Int,
    ): Result {
        val frameSize = frameDimensions?.width!! * frameDimensions?.height!!;
        val yArray = ByteArray(frameSize)//320*240

        System.arraycopy(data, 0, yArray, 0, frameSize)//320*240
        val x = yArray.size
        val ids = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        var result = apriltag.externalCameraAnalysis(yArray, width, height, ids);
        Log.d("april #", result.numDetections.toString());
        return result;
    }
    private fun find_range_nv21(
        data: ByteArray,
        width: Int,
        height: Int,
    ) {
        // Initialize min and max values for red, green, blue, and alpha
        var min = 1000;
        var max = -1000;
        data.size
        // Iterate over the byte array in steps of 4 (RGBA format)
        for (i in 0 until data.size/5) {
            val currentVal = data[i].toInt()

            // Update min and max values for red, green, blue, and alpha
            min = minOf(min, currentVal)
            max = maxOf(max, currentVal)
        }

        // Print or use the calculated ranges as needed
    }
    // if you want offscreen render
    // please return null
    override fun getCameraView(): IAspectRatio? {
        return mViewBinding?.tvCameraRender
    }

    // if you want offscreen render
    // please return null
    override fun getCameraViewContainer(): LinearLayout? {
        return mViewBinding?.cameraViewContainer
    }

    override fun onCameraState(
        self: MultiCameraClient.ICamera,
        code: ICameraStateCallBack.State,
        msg: String?
    ) {
        addPreviewDataCallBack(analysisCallBack);

    }

    override fun getCameraRequest(): CameraRequest {
        return CameraRequest.Builder()
            .setPreviewWidth(frameDimensions?.width!!)//320
            .setPreviewHeight(frameDimensions?.height!!)//240
            .setRenderMode(CameraRequest.RenderMode.OPENGL)
            .setDefaultRotateType(RotateType.ANGLE_0)
            .setAudioSource(CameraRequest.AudioSource.SOURCE_SYS_MIC)
            .setPreviewFormat(CameraRequest.PreviewFormat.FORMAT_MJPEG)
            .setAspectRatioShow(true)
            .setCaptureRawImage(false)
            .setRawPreviewData(true)
            .create()
    }
}