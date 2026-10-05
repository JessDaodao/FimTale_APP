package com.fimtale;

import android.app.Instrumentation;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import static org.junit.Assert.assertTrue;

/** Public input injection API, including emulator versions newer than Espresso's input shim. */
final class DeviceGestures {
    static void swipe(Instrumentation instrumentation, float x1, float y1, float x2, float y2) {
        long start = SystemClock.uptimeMillis();
        for (int step = 0; step <= 20; step++) {
            int action = step == 0 ? MotionEvent.ACTION_DOWN : step == 20 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE;
            float fraction = step / 20f;
            MotionEvent event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action,
                    x1 + (x2 - x1) * fraction, y1 + (y2 - y1) * fraction, 0);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            try { assertTrue(instrumentation.getUiAutomation().injectInputEvent(event, true)); }
            finally { event.recycle(); }
            SystemClock.sleep(20);
        }
        instrumentation.waitForIdleSync();
    }
}
