package com.fimtale.utils;

import android.content.Context;
import android.graphics.Outline;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.View;
import android.view.ViewOutlineProvider;

public class GravitySensorHelper implements SensorEventListener {

    private final SensorManager sensorManager;
    private final Sensor gravitySensor;
    private final View targetView;
    private boolean isEnabled = false;

    private static final float MAX_ROTATION_ANGLE = 15f;
    private static final float MAX_TRANSLATION = 50f;
    private static final float SCALE_FACTOR = 0.9f;
    private static final float CORNER_RADIUS = 60f;

    static {
        System.loadLibrary("fimtale");
    }

    private native float[] calculateTransforms(float x, float y);

    public GravitySensorHelper(Context context, View targetView) {
        this.targetView = targetView;
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        } else {
            gravitySensor = null;
        }
    }

    public void start() {
        if (isEnabled || gravitySensor == null) return;
        sensorManager.registerListener(this, gravitySensor, SensorManager.SENSOR_DELAY_UI);
        isEnabled = true;
        
        targetView.setScaleX(SCALE_FACTOR);
        targetView.setScaleY(SCALE_FACTOR);
        
        targetView.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), CORNER_RADIUS);
            }
        });
        targetView.setClipToOutline(true);
    }

    public void stop() {
        if (!isEnabled || gravitySensor == null) return;
        sensorManager.unregisterListener(this);
        isEnabled = false;
        
        targetView.animate()
                .rotationX(0)
                .rotationY(0)
                .translationX(0)
                .translationY(0)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(300)
                .withEndAction(() -> {
                    targetView.setClipToOutline(false);
                    targetView.setOutlineProvider(null);
                })
                .start();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_GRAVITY) {
            float x = event.values[0];
            float y = event.values[1];
            
            float[] transforms = calculateTransforms(x, y);
            if (transforms != null && transforms.length == 4) {
                targetView.setRotationX(transforms[0]);
                targetView.setRotationY(transforms[1]);
                targetView.setTranslationX(transforms[2]);
                targetView.setTranslationY(transforms[3]);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // 不做处理
    }
}
