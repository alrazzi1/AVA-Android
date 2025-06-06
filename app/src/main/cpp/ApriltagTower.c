/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
#include <string.h>
#include <jni.h>
#include "apriltag.h"
#include "tag36h11.h"
#include "apriltag_pose.h"
#include <math.h>
#include <stdbool.h>
#include <stdlib.h>
#include <stdio.h>

jlong pixel_array_to_uint_8_img(JNIEnv *env, jobject instance, jobjectArray pixelArray, jint width, jint height) {
    // Create an instance of image_u8_t
    image_u8_t *image = image_u8_create_stride((int) width, (int) height, (int) width);

    if (image == NULL) {
        // Handle error, unable to create image structure
        return 0;
    }

    int length = (*env)->GetArrayLength(env, pixelArray);

    for (int i = 0; i < length; i++) {
        jobject pixel = (*env)->GetObjectArrayElement(env, pixelArray, i);

        if (pixel != NULL) {
            jintArray intArray = (jintArray) pixel;
            jint *pixels = (*env)->GetIntArrayElements(env, intArray, 0);

            if (pixels != NULL) {
                jint r = pixels[0];
                jint g = pixels[1];
                jint b = pixels[2];
                jint a = pixels[3];

                // Now you can use the values of r, g, b, and a in your C code
                uint8_t gray = (r+g+g+b)/4;

                // Store the grayscale value in the image structure
                image->buf[i] = gray;
                // Release the jint array
                (*env)->ReleaseIntArrayElements(env, intArray, pixels, 0);
            }
        }
    }

    return (jlong)image;
}

jlong ec_pixel_array_to_uint_8_img(JNIEnv *env, jobject instance, jobjectArray pixelArray, jint width, jint height) {
    image_u8_t *image = image_u8_create_stride((int) width, (int) height, (int) width);

    if (image == NULL) {
        // Handle error, unable to create image structure
        return 0;
    }

    // Get the length of the byte array
    int length = (*env)->GetArrayLength(env, pixelArray);

    // Check if the length matches the expected size
    if (length != width * height) {
        // Handle error, array size does not match image dimensions
        return 0;
    }

    // Get the byte array elements
    jbyte *pixels = (*env)->GetByteArrayElements(env, pixelArray, NULL);

    if (pixels != NULL) {
        // Copy the grayscale values into the image structure
        for (int i = 0; i < length; i++) {
            image->buf[i] = (uint8_t) pixels[i];
        }

        // Release the byte array elements
        (*env)->ReleaseByteArrayElements(env, pixelArray, pixels, 0);
    } else {
        // Handle error, unable to get byte array elements
        return 0;
    }

    return (jlong)image;
}

char* test_img(JNIEnv *env, jobject instance, jobjectArray imageData, jint width, jint height, jlong imagePointer) {
    // Cast the image pointer back to image_u8_t
    image_u8_t *image = (image_u8_t*)imagePointer;

    if (image == NULL) {
        // Handle an error if the image pointer is invalid
        return strdup("Image pointer is invalid");
    }

    // Verify dimensions
    if (image->width != width || image->height != height) {
        // Handle dimension mismatch error
        char error[100];
        snprintf(error, sizeof(error), "Height/width mismatch. Image: Height=%d, Width=%d, Expected: Height=%d, Width=%d", image->height, image->width, height, width);
        return strdup(error);
    }

    // Iterate through the ArrayList to validate pixel data
    for (int y = 0; y < height; y++) {
        jintArray intArray = (jintArray)(*env)->GetObjectArrayElement(env, imageData, y);
        jint* pixelData = (*env)->GetIntArrayElements(env, intArray, 0);

        for (int x = 0; x < width; x++) {
            int index = y * width + x;
            uint8_t expectedPixelValue = (uint8_t)pixelData[x];

            if (image->buf[index] != expectedPixelValue) {
                // Handle pixel value mismatch error
                (*env)->ReleaseIntArrayElements(env, intArray, pixelData, JNI_ABORT);
                char problem[50];
                snprintf(problem, sizeof(problem), "Pixel value mismatch at index %d: Expected=%d, Actual=%d", index, expectedPixelValue, image->buf[index]);
                return strdup(problem);
            }
        }

        // Release the IntArray elements
        (*env)->ReleaseIntArrayElements(env, intArray, pixelData, 0);
    }

    // All checks passed; the image is valid
    return strdup("everything passed!");
}
apriltag_pose_t get_pose(apriltag_detection_t *detection){
    apriltag_detection_info_t info;
    info.det = detection;
    info.tagsize = 0.238; // in meters
    info.fx = 371.8;//657=test_cam
    info.fy = 371.2;//657.26=test_cam
    info.cx = 398.9;//312.18=test_cam
    info.cy = 297.2;//241.739=test_cam

    // Then call estimate_tag_pose.
    apriltag_pose_t pose;
    double err = estimate_tag_pose(&info, &pose);

    return pose;
}

double get_pose_error(apriltag_detection_t detection){
    apriltag_detection_info_t info;
    info.det = &detection;
    info.tagsize = 0.04; // in meters
    info.fx = 657;
    info.fy = 657.26;
    info.cx = 312.18;
    info.cy = 241.739;

    // Then call estimate_tag_pose.
    apriltag_pose_t pose;
    double err = estimate_tag_pose(&info, &pose);

    return err;
}
bool is_int_in_jintArray(JNIEnv *env, jintArray arr, int x) {
    jsize len = (*env)->GetArrayLength(env, arr);
    jint *elements = (*env)->GetIntArrayElements(env, arr, NULL);

    // Iterate through the array elements to check if x is present
    for (jsize i = 0; i < len; i++) {
        int y = elements[i];
        int z = y;
    }
    for (jsize i = 0; i < len; i++) {
        if (elements[i] == (jint)x) {
            // Value x is in the array
            (*env)->ReleaseIntArrayElements(env, arr, elements, JNI_ABORT); // Release without copying back changes
            return true;
        }
    }

    // Value x is not in the array
    (*env)->ReleaseIntArrayElements(env, arr, elements, JNI_ABORT); // Release without copying back changes
    return false;
}
apriltag_detection_t* get_best_detection(zarray_t *detections, JNIEnv *env, jintArray ids){
    apriltag_detection_t* best_detection = NULL;
    double least_error = MAXFLOAT;
    apriltag_pose_t pose;
    for (int i = 0; i < zarray_size(detections); i++) {
        apriltag_detection_t *det;
        zarray_get(detections, i, &det);

        if(!is_int_in_jintArray(env, ids, det->id)){
            continue;
        }

        double err = get_pose_error(*det);
        if(err < least_error){
            least_error=err;
            best_detection=det;
        }
    }
    return best_detection;
}
void cleanup(apriltag_detector_t *td ,apriltag_family_t *tf,image_u8_t *img){
    tag36h11_destroy(tf);
    apriltag_detector_destroy(td);
    if (img) {
        if (img->buf) {
            free(img->buf); // Free the pixel data
        }
        free(img);      // Free the image structure
    }
}


// Function to create a Java Result object
jobject createResultObject(JNIEnv *env, int id, int numDetections, jobject transformPacket, jdoubleArray center_pixels) {
    jclass resultClass = (*env)->FindClass(env, "com/example/CoulterGlassesDebug/Result");
    jmethodID resultConstructor = (*env)->GetMethodID(env, resultClass, "<init>",
                                                      "(IILcom/example/ausbctest/TransformPacket;[D)V");
    return (*env)->NewObject(env, resultClass, resultConstructor, id, numDetections, transformPacket, center_pixels);
}

jobject createEmptyResult(JNIEnv *env) {
    // Get the class reference
    jclass resultClass = (*env)->FindClass(env, "com/example/CoulterGlassesDebug/Result");

    // Get the method ID for the constructor with isTagDetected parameter
    jmethodID constructor = (*env)->GetMethodID(env, resultClass, "<init>", "(Z)V");

    // Call the constructor to create a new Result object with isTagDetected set to false
    jobject resultObject = (*env)->NewObject(env, resultClass, constructor, JNI_FALSE);

    // Return the created Result object
    return resultObject;
}

jobject apriltag_pose_t_to_transformPacket(JNIEnv *env, apriltag_pose_t pose_t){
    jclass transformPacketClass = (*env)->FindClass(env, "com/example/CoulterGlassesDebug/TransformPacket");
    jmethodID transformPacketConstructor = (*env)->GetMethodID(env, transformPacketClass, "<init>", "(DDDDDDDDDDDD)V");

    // Access elements from the apriltag_pose_t struct
    matd_t *rMatrix = pose_t.R;
    matd_t *tMatrix = pose_t.t;

    // Create a TransformPacket object
    jobject transformPacketObj = (*env)->NewObject(env, transformPacketClass, transformPacketConstructor,
                                                   matd_get(tMatrix, 0, 0),
                                                   matd_get(tMatrix, 1, 0),
                                                   matd_get(tMatrix, 2, 0),
                                                   matd_get(rMatrix, 0, 0),
                                                   matd_get(rMatrix, 0, 1),
                                                   matd_get(rMatrix, 0, 2),
                                                   matd_get(rMatrix, 1, 0),
                                                   matd_get(rMatrix, 1, 1),
                                                   matd_get(rMatrix, 1, 2),
                                                   matd_get(rMatrix, 2, 0),
                                                   matd_get(rMatrix, 2, 1),
                                                   matd_get(rMatrix, 2, 2)
    );
    return transformPacketObj;
}
JNIEXPORT jobject
JNICALL
Java_com_example_ausbctest_apriltag_00024Companion_getApriltagResult
( JNIEnv* env, jobject thiz, jbyteArray pixelArray, jint width, jint height, jintArray ids)
{
    image_u8_t *img = (image_u8_t *) pixel_array_to_uint_8_img(env, thiz, pixelArray, width, height);
//    char* result = test_img(env, thiz, pixelArray, width, height, (jlong) img);

    apriltag_detector_t *td = apriltag_detector_create();
    apriltag_family_t *tf = tag36h11_create();
    apriltag_detector_add_family(td, tf);
    zarray_t *detections = apriltag_detector_detect(td, img);

    apriltag_detection_t* best_detection = get_best_detection(detections, env, ids);
    if(best_detection==NULL){
        cleanup(td, tf, img);
        return createEmptyResult(env);
    }
    apriltag_pose_t pose = get_pose(best_detection);

    int num_detections = zarray_size(detections);
    int id = best_detection->id;

    jdoubleArray center_pixels;
    center_pixels = (*env)->NewDoubleArray(env, 2);

    // Set the values of the Java double array
    (*env)->SetDoubleArrayRegion(env, center_pixels, 0, 2, best_detection->c);

    jobject transformPacket = apriltag_pose_t_to_transformPacket(env, pose);
    jobject result = createResultObject(env, id, num_detections, transformPacket, center_pixels);

    cleanup(td, tf, img);
    return result;
}

JNIEXPORT jobject
JNICALL
Java_com_example_ausbctest_apriltag_00024Companion_externalCameraAnalysis
        ( JNIEnv* env, jobject thiz, jbyteArray pixelArray, jint width, jint height, jintArray ids)
{
    image_u8_t *img = (image_u8_t *) ec_pixel_array_to_uint_8_img(env, thiz, pixelArray, width, height);
//    char* result = test_img(env, thiz, pixelArray, width, height, (jlong) img);

    apriltag_detector_t *td = apriltag_detector_create();
    apriltag_family_t *tf = tag36h11_create();
    apriltag_detector_add_family(td, tf);
    zarray_t *detections = apriltag_detector_detect(td, img);

    apriltag_detection_t* best_detection = get_best_detection(detections, env, ids);
    if(best_detection==NULL){
        cleanup(td, tf, img);
        return createEmptyResult(env);
    }
    apriltag_pose_t pose = get_pose(best_detection);

    int num_detections = zarray_size(detections);
    int id = best_detection->id;

    // Create a Java double array
    jdoubleArray center_pixels;
    center_pixels = (*env)->NewDoubleArray(env, 2);

    // Set the values of the Java double array
    (*env)->SetDoubleArrayRegion(env, center_pixels, 0, 2, best_detection->c);

    jobject transformPacket = apriltag_pose_t_to_transformPacket(env, pose);
    jobject result = createResultObject(env, id, num_detections, transformPacket, center_pixels);

    cleanup(td, tf, img);
    return result;
}