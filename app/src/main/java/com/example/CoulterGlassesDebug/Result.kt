package com.example.CoulterGlassesDebug;

public class Result {
    var center_pixels: DoubleArray? = null;
    var id: Int = 0;
    var yaw: Double = 0.0;
    var dist: Double = 0.0;
    var numDetections: Int = 0;
    var camToTargetPacket: TransformPacket? = null;
    var isTagDetected: Boolean = false; // Default value is false

    // Existing constructor
    constructor(id: Int, numDetections: Int, camToTargetPacket: TransformPacket, center_pixels: DoubleArray) {
        this.id = id;
        this.yaw = yaw;
        this.dist = dist;
        this.numDetections = numDetections;
        this.camToTargetPacket = camToTargetPacket;
        this.isTagDetected = true;
        this.center_pixels=center_pixels;
    }

    // Additional constructor to set isTagDetected
    constructor(isTagDetected: Boolean) {
        this.isTagDetected = isTagDetected;
    }
}