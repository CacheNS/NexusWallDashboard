package com.cachens.nexusdashboard;

import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.util.List;

final class MotionDetector implements Camera.PreviewCallback {
    private static final String LOG_TAG = "NexusDashboard";
    interface Listener {
        void onMotionDetected();
    }

    private static final int MIN_PREVIEW_FPS = 5;
    private static final long FRAME_INTERVAL_MS = 500L;
    private static final long MOTION_COOLDOWN_MS = FRAME_INTERVAL_MS;
    private static final int PIXEL_STEP = 16;
    private static final int PIXEL_DIFFERENCE_THRESHOLD = 22;
    private static final float CHANGED_PIXEL_RATIO = 0.08f;

    private final Listener listener;
    private Camera camera;
    private SurfaceTexture previewTexture;
    private byte[] previousSamples;
    private long lastProcessedAt;
    private long lastMotionAt;

    MotionDetector(Listener listener) {
        this.listener = listener;
    }

    synchronized boolean start() {
        if (camera != null) {
            return true;
        }
        int cameraId = findFrontCamera();
        if (cameraId < 0) {
            return false;
        }
        try {
            camera = Camera.open(cameraId);
            Camera.Parameters parameters = camera.getParameters();
            Camera.Size size = choosePreviewSize(parameters.getSupportedPreviewSizes());
            parameters.setPreviewSize(size.width, size.height);
            parameters.setPreviewFormat(ImageFormat.NV21);
                Integer previewFps = choosePreviewFrameRate(
                    parameters.getSupportedPreviewFrameRates());
                if (previewFps != null) {
                parameters.setPreviewFrameRate(previewFps);
                Log.i(LOG_TAG, "Motion preview " + size.width + "x" + size.height
                    + " at " + previewFps + " fps");
                } else {
                int[] fpsRange = choosePreviewFpsRange(
                    parameters.getSupportedPreviewFpsRange());
                if (fpsRange != null) {
                    parameters.setPreviewFpsRange(fpsRange[0], fpsRange[1]);
                    Log.i(LOG_TAG, "Motion preview " + size.width + "x" + size.height
                        + " at " + fpsRange[0] / 1000f + "-"
                        + fpsRange[1] / 1000f + " fps");
                }
            }
            camera.setParameters(parameters);
            previewTexture = new SurfaceTexture(10);
            camera.setPreviewTexture(previewTexture);
            int bufferSize = size.width * size.height
                    * ImageFormat.getBitsPerPixel(ImageFormat.NV21) / 8;
            camera.addCallbackBuffer(new byte[bufferSize]);
            camera.addCallbackBuffer(new byte[bufferSize]);
            camera.setPreviewCallbackWithBuffer(this);
            camera.startPreview();
            return true;
        } catch (RuntimeException error) {
            stop();
            return false;
        } catch (IOException error) {
            stop();
            return false;
        }
    }

    synchronized void stop() {
        if (camera != null) {
            camera.setPreviewCallbackWithBuffer(null);
            camera.stopPreview();
            camera.release();
            camera = null;
        }
        if (previewTexture != null) {
            previewTexture.release();
            previewTexture = null;
        }
        previousSamples = null;
    }

    @Override
    public synchronized void onPreviewFrame(byte[] data, Camera source) {
        if (camera == null || data == null) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastProcessedAt >= FRAME_INTERVAL_MS) {
            lastProcessedAt = now;
            if (hasMotion(data)
                    && now - lastMotionAt >= MOTION_COOLDOWN_MS) {
                lastMotionAt = now;
                listener.onMotionDetected();
            }
        }
        source.addCallbackBuffer(data);
    }

    private boolean hasMotion(byte[] current) {
        int luminanceLength = current.length * 2 / 3;
        int sampleCount = (luminanceLength + PIXEL_STEP - 1) / PIXEL_STEP;
        if (previousSamples == null || previousSamples.length != sampleCount) {
            previousSamples = new byte[sampleCount];
            copySamples(current, luminanceLength);
            return false;
        }
        int changed = 0;
        int sampleIndex = 0;
        for (int pixelIndex = 0; pixelIndex < luminanceLength;
             pixelIndex += PIXEL_STEP) {
            byte currentValue = current[pixelIndex];
            int difference = Math.abs((currentValue & 0xff)
                    - (previousSamples[sampleIndex] & 0xff));
            if (difference >= PIXEL_DIFFERENCE_THRESHOLD) {
                changed++;
            }
            previousSamples[sampleIndex] = currentValue;
            sampleIndex++;
        }
        return sampleCount > 0 && (float) changed / sampleCount >= CHANGED_PIXEL_RATIO;
    }

    private void copySamples(byte[] current, int luminanceLength) {
        int sampleIndex = 0;
        for (int pixelIndex = 0; pixelIndex < luminanceLength;
             pixelIndex += PIXEL_STEP) {
            previousSamples[sampleIndex] = current[pixelIndex];
            sampleIndex++;
        }
    }

    private static int findFrontCamera() {
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                return i;
            }
        }
        return -1;
    }

    private static Camera.Size choosePreviewSize(List<Camera.Size> sizes) {
        Camera.Size smallest = sizes.get(0);
        for (Camera.Size size : sizes) {
            if (size.width * size.height < smallest.width * smallest.height) {
                smallest = size;
            }
        }
        return smallest;
    }

    private static int[] choosePreviewFpsRange(List<int[]> ranges) {
        int[] selected = null;
        if (ranges == null) {
            return null;
        }
        for (int[] range : ranges) {
            if (range == null || range.length < 2
                    || range[1] < MIN_PREVIEW_FPS * 1000) {
                continue;
            }
            if (selected == null || range[1] < selected[1]
                    || (range[1] == selected[1] && range[0] < selected[0])) {
                selected = range;
            }
        }
        return selected;
    }

    private static Integer choosePreviewFrameRate(List<Integer> frameRates) {
        Integer selected = null;
        if (frameRates == null) {
            return null;
        }
        for (Integer frameRate : frameRates) {
            if (frameRate != null && frameRate >= MIN_PREVIEW_FPS
                    && (selected == null || frameRate < selected)) {
                selected = frameRate;
            }
        }
        return selected;
    }
}
