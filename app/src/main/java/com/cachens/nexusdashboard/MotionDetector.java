package com.cachens.nexusdashboard;

import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;

import java.io.IOException;
import java.util.List;

final class MotionDetector implements Camera.PreviewCallback {
    interface Listener {
        void onMotionDetected();
    }

    private static final long FRAME_INTERVAL_MS = 500L;
    private static final long MOTION_COOLDOWN_MS = 2000L;
    private static final int PIXEL_STEP = 16;
    private static final int PIXEL_DIFFERENCE_THRESHOLD = 22;
    private static final float CHANGED_PIXEL_RATIO = 0.08f;

    private final Listener listener;
    private Camera camera;
    private SurfaceTexture previewTexture;
    private byte[] previousFrame;
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
        previousFrame = null;
    }

    @Override
    public synchronized void onPreviewFrame(byte[] data, Camera source) {
        if (camera == null || data == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastProcessedAt >= FRAME_INTERVAL_MS) {
            lastProcessedAt = now;
            if (previousFrame != null && hasMotion(previousFrame, data)
                    && now - lastMotionAt >= MOTION_COOLDOWN_MS) {
                lastMotionAt = now;
                listener.onMotionDetected();
            }
            if (previousFrame == null || previousFrame.length != data.length) {
                previousFrame = new byte[data.length];
            }
            System.arraycopy(data, 0, previousFrame, 0, data.length);
        }
        source.addCallbackBuffer(data);
    }

    private static boolean hasMotion(byte[] previous, byte[] current) {
        int sampleCount = 0;
        int changed = 0;
        int luminanceLength = current.length * 2 / 3;
        for (int i = 0; i < luminanceLength; i += PIXEL_STEP) {
            int difference = Math.abs((current[i] & 0xff) - (previous[i] & 0xff));
            if (difference >= PIXEL_DIFFERENCE_THRESHOLD) {
                changed++;
            }
            sampleCount++;
        }
        return sampleCount > 0 && (float) changed / sampleCount >= CHANGED_PIXEL_RATIO;
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
}
