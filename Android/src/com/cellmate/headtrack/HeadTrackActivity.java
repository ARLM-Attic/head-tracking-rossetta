package com.cellmate.headtrack;


import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;
import android.view.ViewGroup.LayoutParams;

public class HeadTrackActivity extends Activity {
	private GLSurfaceView mGLSurfaceView;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    	this.requestWindowFeature(Window.FEATURE_NO_TITLE); 
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
        WindowManager.LayoutParams.FLAG_FULLSCREEN);
        
        mGLSurfaceView = new TouchSurfaceView(this);
        setContentView(mGLSurfaceView);
        mGLSurfaceView.requestFocus();
        mGLSurfaceView.setFocusableInTouchMode(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mGLSurfaceView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mGLSurfaceView.onPause();
    }

}

class TouchSurfaceView extends GLSurfaceView 
{
	private final float TOUCH_SCALE_FACTOR = 180.0f / 320;
    private final float TRACKBALL_SCALE_FACTOR = 36.0f;
    private OpenGLRenderer mMyRenderer;
    private float mPreviousX;
    private float mPreviousY;
	public Context mContext;
	
    public TouchSurfaceView(Context context) 
    {
        super(context);
        mContext = context;
        mMyRenderer = new OpenGLRenderer(mContext);

        setRenderer(mMyRenderer);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
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
    
    public void onSurfaceChanged(GL10 gl, int width, int height) 
    {
    	gl.glViewport(0, 0, width, height);

        float ratio = (float) width / height;
        gl.glMatrixMode(GL10.GL_PROJECTION);
        gl.glLoadIdentity();
        gl.glFrustumf(-ratio, ratio, -1, 1, 1, 10);
    }

        
    public void onSurfaceCreated(GL10 gl, EGLConfig config) 
    {
    
    	gl.glDisable(GL10.GL_DITHER);
    	gl.glHint(GL10.GL_PERSPECTIVE_CORRECTION_HINT, GL10.GL_FASTEST);

    	gl.glClearColor(1,1,1,1);
        gl.glEnable(GL10.GL_CULL_FACE);
        gl.glShadeModel(GL10.GL_SMOOTH);
        gl.glEnable(GL10.GL_DEPTH_TEST);
    }
}



