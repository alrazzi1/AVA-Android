package com.example.CoulterGlassesDebug
import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.example.CoulterGlassesDebug.databinding.CameraFragmentBinding
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.base.CameraFragment
import com.jiangdg.ausbc.callback.ICameraStateCallBack
import com.jiangdg.ausbc.callback.IPreviewDataCallBack
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.jiangdg.ausbc.render.env.RotateType
import com.jiangdg.ausbc.widget.IAspectRatio
import edu.wpi.first.math.MatBuilder
import edu.wpi.first.math.geometry.Transform3d
import edu.wpi.first.math.Matrix
import edu.wpi.first.math.Nat
import edu.wpi.first.math.geometry.Rotation3d
import edu.wpi.first.math.geometry.Translation3d
import edu.wpi.first.math.numbers.N3

class cameraFragment() : CameraFragment() {
    private var mViewBinding: CameraFragmentBinding? = null
    private final val LOG_TAG = "CAMERA_FRAGMENT"

    data class FrameDimensions(val width: Int, val height: Int)
    val analysisCallBack = object : IPreviewDataCallBack {
        @SuppressLint("MissingPermission")
        override fun onPreviewData(
            data: ByteArray?,
            width: Int,
            height: Int,
            format: IPreviewDataCallBack.DataFormat
        ) {
//            Log.d(LOG_TAG, "Width: $width, Height: $height")
            if (data != null) {
                var result = detectApriltags(data, width, height);
                printTransformOutput(result);
                MainActivity.instance?.sendToESP(result);
            }
        }
    }
    private fun detectApriltags(
        data: ByteArray,
        width: Int,
        height: Int,
    ): Result {
        val frameSize = frameDimensions?.width!! * frameDimensions?.height!!;
        val yArray = ByteArray(frameSize)//320*240

        System.arraycopy(data, 0, yArray, 0, frameSize)//320*240
        val ids = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        var result = apriltag.externalCameraAnalysis(yArray, width, height, ids);
        Log.d("april #", result.numDetections.toString());
        return result;
    }

    private fun printTransformOutput(result: Result) {
        val transform_output_tv: TextView = requireActivity().findViewById<TextView>(
            com.example.CoulterGlassesDebug.R.id.transform_output);
        if(result.numDetections>0){
            val transformPacket = result.camToTargetPacket;
            val (x,y,z) = Helper.roundToDecimalPlaces(
                listOf(
                    transformPacket!!.x,
                    transformPacket!!.y,
                    transformPacket!!.z),
                2)
            val (yaw, pitch, roll) =
                Helper.roundToDecimalPlaces(
                    Helper.radiansToDegrees(
                listOf(
                    transformPacket!!.x,
                    transformPacket!!.y,
                    transformPacket!!.z)),

                0);
            val (cx,cy) =
                Helper.roundToDecimalPlaces(
                        listOf(
                            result.center_pixels?.get(0),
                            result.center_pixels?.get(1)
                        ),0);

            transform_output_tv.text = "x: $x, y: $y, z: $z,\n yaw: $yaw, pitch: $pitch, roll: $roll, cx: $cx, cy: $cy";
        }else{
            transform_output_tv.text = "No Apriltags"
        }
    }

companion object{
    @JvmStatic
    public var frameDimensions: FrameDimensions? = FrameDimensions(320,240);//3840,2160
    @JvmStatic
    fun getCamToTarget(result: Result): Transform3d?{
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
    ) {addPreviewDataCallBack(analysisCallBack);}

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
    override fun getRootView(inflater: LayoutInflater, container: ViewGroup?): View? {
        if (mViewBinding == null) {
            mViewBinding = CameraFragmentBinding.inflate(inflater, container, false)
        }
        getCameraView()?.setAspectRatio(frameDimensions?.width!!, frameDimensions?.height!!);//320,240
        Log.d(LOG_TAG,"We are requesting analysis callback")
        addPreviewDataCallBack(analysisCallBack);
        return mViewBinding?.root
    }
}