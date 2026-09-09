package com.cachens.nexusdashboard;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.test.InstrumentationTestCase;
import android.test.InstrumentationTestRunner;
import android.view.View;
import android.widget.ImageButton;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class RadioDeviceTest extends InstrumentationTestCase {
    public void testCancelRestartAndRelease() throws Throwable {
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                RadioController radio = new RadioController(getInstrumentation().getTargetContext(),
                        new RadioController.Listener() {
                            @Override
                            public void onStateChanged(RadioController.State state) { }

                            @Override
                            public void onError() {
                                fail("Immediate cancellation must not report a playback error");
                            }
                        });
                try {
                    assertEquals(RadioController.State.STOPPED, radio.getState());
                    radio.toggle();
                    assertEquals(RadioController.State.BUFFERING, radio.getState());
                    radio.toggle();
                    assertEquals(RadioController.State.STOPPED, radio.getState());
                    radio.toggle();
                    assertEquals(RadioController.State.BUFFERING, radio.getState());
                    radio.stop();
                    radio.stop();
                    assertEquals(RadioController.State.STOPPED, radio.getState());
                    radio.release();
                    radio.toggle();
                    assertEquals(RadioController.State.STOPPED, radio.getState());
                } finally {
                    radio.release();
                }
            }
        });
        getInstrumentation().waitForIdleSync();
    }

    public void testFailedStreamReturnsToStoppedAndCanRetry() throws Throwable {
        final CountDownLatch failed = new CountDownLatch(1);
        final RadioController[] controller = new RadioController[1];
        try {
            runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                    controller[0] = new RadioController(getInstrumentation().getTargetContext(),
                            new RadioController.Listener() {
                                @Override
                                public void onStateChanged(RadioController.State state) { }

                                @Override
                                public void onError() {
                                    failed.countDown();
                                }
                            }, "https://127.0.0.1:1/unavailable.m3u8");
                    controller[0].toggle();
                }
            });
            assertTrue("Failed stream must time out and report an error", failed.await(40, TimeUnit.SECONDS));
            runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                    assertEquals(RadioController.State.STOPPED, controller[0].getState());
                    controller[0].toggle();
                    assertEquals(RadioController.State.BUFFERING, controller[0].getState());
                    controller[0].toggle();
                    assertEquals(RadioController.State.STOPPED, controller[0].getState());
                }
            });
        } finally {
            release(controller);
        }
    }

    public void testControlStatesAndTouchTarget() throws Throwable {
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                Context context = getInstrumentation().getTargetContext();
                final int[] clicks = {0};
                RadioControl control = new RadioControl(context, new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        clicks[0]++;
                    }
                });
                int size = Math.round(48 * context.getResources().getDisplayMetrics().density);
                int spec = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY);
                control.measure(spec, spec);
                control.layout(0, 0, size, size);
                ImageButton button = (ImageButton) control.getChildAt(0);
                assertEquals(size, button.getWidth());
                assertEquals(size, button.getHeight());
                for (RadioController.State state : RadioController.State.values()) {
                    control.setState(state);
                    String key = state == RadioController.State.STOPPED ? "radio_play"
                            : state == RadioController.State.BUFFERING ? "radio_cancel" : "radio_stop";
                    assertEquals(AppText.get(context, key), button.getContentDescription());
                    assertEquals(state == RadioController.State.BUFFERING ? View.VISIBLE : View.GONE,
                            control.getChildAt(1).getVisibility());
                    Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                    try {
                        control.draw(new Canvas(bitmap));
                        assertTrue(bitmap.getPixel(size / 2, size / 2) != 0);
                    } finally {
                        bitmap.recycle();
                    }
                    assertTrue(button.performClick());
                }
                assertEquals(3, clicks[0]);
            }
        });
    }

    public void testLiveStreamWhenRequested() throws Throwable {
        if (!"true".equals(((InstrumentationTestRunner) getInstrumentation())
                .getArguments().getString("radioLive"))) return;
        final CountDownLatch ready = new CountDownLatch(1);
        final boolean[] failed = {false};
        final RadioController[] controller = new RadioController[1];
        try {
            runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                    controller[0] = new RadioController(getInstrumentation().getTargetContext(),
                            new RadioController.Listener() {
                                @Override
                                public void onStateChanged(RadioController.State state) {
                                    if (state == RadioController.State.PLAYING) ready.countDown();
                                }

                                @Override
                                public void onError() {
                                    failed[0] = true;
                                    ready.countDown();
                                }
                            });
                    controller[0].toggle();
                }
            });
            assertTrue("Live stream did not become ready", ready.await(40, TimeUnit.SECONDS));
            assertFalse("Live stream failed on this device", failed[0]);
        } finally {
            release(controller);
        }
    }

    private void release(final RadioController[] controller) throws Throwable {
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (controller[0] != null) controller[0].release();
            }
        });
    }
}