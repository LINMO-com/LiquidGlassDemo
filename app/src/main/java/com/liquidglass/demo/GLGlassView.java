package com.liquidglass.demo;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;
import android.view.VelocityTracker;

public class GLGlassView extends GLSurfaceView {

    private final LiquidGlassRenderer renderer;

    private VelocityTracker velocityTracker;
    private float lastTouchX, lastTouchY;
    private float offsetX, offsetY;
    private float velocityX, velocityY;
    private boolean isDragging;
    private long lastFrameTime;

    private static final float SPRING_STIFFNESS = 280f;
    private static final float DAMPING = 12f;
    private static final float MAX_OFFSET = 180f;
    private static final float INERTIA_FACTOR = 0.94f;

    public GLGlassView(Context context) {
        super(context);

        setEGLContextClientVersion(2);
        setPreserveEGLContextOnPause(true);

        renderer = new LiquidGlassRenderer();
        setRenderer(renderer);

        setRenderMode(RENDERMODE_WHEN_DIRTY);
        lastFrameTime = System.nanoTime();

        post(this::startAnimationLoop);
    }

    private void startAnimationLoop() {
        lastFrameTime = System.nanoTime();
        Runnable loop = new Runnable() {
            @Override
            public void run() {
                updatePhysics();
                renderer.updateTime();
                requestRender();
                postDelayed(this, 16);
            }
        };
        post(loop);
    }

    private void updatePhysics() {
        long now = System.nanoTime();
        float dt = (now - lastFrameTime) / 1_000_000_000f;
        if (dt > 0.1f) dt = 0.016f;
        lastFrameTime = now;

        if (!isDragging) {
            float springForceX = -SPRING_STIFFNESS * offsetX;
            float springForceY = -SPRING_STIFFNESS * offsetY;
            float dampForceX = -DAMPING * velocityX;
            float dampForceY = -DAMPING * velocityY;

            velocityX += (springForceX + dampForceX) * dt;
            velocityY += (springForceY + dampForceY) * dt;

            offsetX += velocityX * dt;
            offsetY += velocityY * dt;

            if (Math.abs(offsetX) < 0.3f && Math.abs(velocityX) < 1f) {
                offsetX = 0;
                velocityX = 0;
            }
            if (Math.abs(offsetY) < 0.3f && Math.abs(velocityY) < 1f) {
                offsetY = 0;
                velocityY = 0;
            }
        }

        offsetX = Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, offsetX));
        offsetY = Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, offsetY));

        renderer.setOffset(offsetX, offsetY);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        velocityTracker.addMovement(event);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                isDragging = true;
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                velocityX = 0;
                velocityY = 0;
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - lastTouchX;
                float dy = event.getY() - lastTouchY;
                offsetX += dx;
                offsetY += dy;
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                velocityTracker.computeCurrentVelocity(1000);
                velocityX = velocityTracker.getXVelocity() * 0.25f;
                velocityY = velocityTracker.getYVelocity() * 0.25f;
                velocityTracker.recycle();
                velocityTracker = null;
                return true;
        }

        return super.onTouchEvent(event);
    }

    public void onResume() {
        super.onResume();
        setRenderMode(RENDERMODE_WHEN_DIRTY);
    }

    public void onPause() {
        super.onPause();
    }
}
