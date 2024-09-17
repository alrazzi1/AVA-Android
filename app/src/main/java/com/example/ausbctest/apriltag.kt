package com.example.ausbctest

import edu.wpi.first.math.geometry.Pose3d
import edu.wpi.first.math.geometry.Rotation3d
import edu.wpi.first.math.geometry.Translation3d

class apriltag {
    companion object{
        init {
            System.loadLibrary("ApriltagTower")

        }
        open external fun getApriltagResult(imageData: Array<IntArray>, width: Int, height: Int, ids: IntArray): Result
        open external fun externalCameraAnalysis(imageData: ByteArray, width: Int, height: Int, ids: IntArray): Result

        @JvmStatic
        val apriltagMap: HashMap<Int, Pose3d> = hashMapOf(
            *Array(4) { i ->
                val angleIncrement = 360.0/4
                val radius = 1
                val angle = i * angleIncrement
                val x = radius * Math.cos(Math.toRadians(angle))
                val y = radius * Math.sin(Math.toRadians(angle))

                // Tags point inwards, so adjust the rotation to face the center
                val rotation = Rotation3d(0.0, 0.0, angle + Math.PI)

                i to Pose3d(Translation3d(x, y, 0.0), rotation)
            }
        )
    }
}