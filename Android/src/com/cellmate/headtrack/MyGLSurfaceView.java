package com.cellmate.headtrack;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.KeyEvent;
import android.view.MotionEvent;

class MyGLSurfaceView extends GLSurfaceView {
	private final float TOUCH_SCALE_FACTOR = 180.0f / 320;
    private final float TRACKBALL_SCALE_FACTOR = 36.0f;
    private OpenGLRenderer mMyRenderer;
    private float mPreviousX;
    private float mPreviousY;
    private Context _context;
    
    public MyGLSurfaceView(Context context) {
		super(context);
		
		_context = context;
		
		// TODO Auto-generated constructor stub
		start();     
	}


    public void start() {
        mMyRenderer = new OpenGLRenderer(_context);
        setRenderer(mMyRenderer);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    @Override public boolean onTrackballEvent(MotionEvent e) {
    	//mMyRenderer.mAngleX += e.getX() * TRACKBALL_SCALE_FACTOR;
    	//mMyRenderer.mAngleY += e.getY() * TRACKBALL_SCALE_FACTOR;
        requestRender();
        return true;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX();
        float y = e.getY();
        switch (e.getAction()) {
        case MotionEvent.ACTION_MOVE:
            float dx = x - mPreviousX;
            float dy = y - mPreviousY;
           // mMyRenderer.mAngleX += dx * TOUCH_SCALE_FACTOR;
           // mMyRenderer.mAngleY += dy * TOUCH_SCALE_FACTOR;
            requestRender();
        }
        mPreviousX = x;
        mPreviousY = y;
        return true;
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            queueEvent(new Runnable() {
                // This method will be called on the rendering
                // thread:
                public void run() {
                    mMyRenderer.handlePressLeft();
                }});
            return true;
        }
        else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            queueEvent(new Runnable() {
                // This method will be called on the rendering
                // thread:
                public void run() {
                    mMyRenderer.handlePressRight();
                }});
            return true;
        }
        else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            queueEvent(new Runnable() {
                // This method will be called on the rendering
                // thread:
                public void run() {
                    mMyRenderer.handlePressUp();
                }});
            return true;
        }
        else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            queueEvent(new Runnable() {
                // This method will be called on the rendering
                // thread:
                public void run() {
                    mMyRenderer.handlePressDown();
                }});
            return true;
        }
        else if (keyCode == KeyEvent.KEYCODE_A) {
            queueEvent(new Runnable() {
                // This method will be called on the rendering
                // thread:
                public void run() {
                    mMyRenderer.handlePressA();
                }});
            return true;
        }
        else if (keyCode == KeyEvent.KEYCODE_Z) {
            queueEvent(new Runnable() {
                // This method will be called on the rendering
                // thread:
                public void run() {
                    mMyRenderer.handlePressZ();
                }});
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
} 

