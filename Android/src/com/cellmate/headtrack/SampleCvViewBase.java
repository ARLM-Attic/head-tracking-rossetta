package com.cellmate.headtrack;

import java.util.List;

import org.opencv.core.Size;
import org.opencv.highgui.VideoCapture;
import org.opencv.highgui.Highgui;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

public abstract class SampleCvViewBase extends GLSurfaceView implements SurfaceHolder.Callback, Runnable {
    private static final String TAG = "Sample::SurfaceView";

    private SurfaceHolder       mHolder;
    private VideoCapture        mCamera;
    private FpsMeter            mFps;
    
	private final float TOUCH_SCALE_FACTOR = 180.0f / 320;
    private final float TRACKBALL_SCALE_FACTOR = 36.0f;
    private OpenGLRenderer mMyRenderer;
    private float mPreviousX;
    private float mPreviousY;
	public Context mContext;
	
    public SampleCvViewBase(Context context) {
        super(context);
        mHolder = getHolder();
        mHolder.addCallback(this);

        mFps = new FpsMeter();
        
        mContext = context;
        mMyRenderer = new OpenGLRenderer(mContext);

        setRenderer(mMyRenderer);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        Log.i(TAG, "Instantiated new " + this.getClass());
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
        else if (keyCode == KeyEvent.KEYCODE_SPACE)
        {
            //zeros the head position and computes the camera tilt
            double angle = Math.acos(.5 / mMyRenderer.headDist) - Math.PI / 2;//angle of head to screen
            if (!mMyRenderer.cameraIsAboveScreen)
                angle = -angle;
            mMyRenderer.cameraVerticaleAngle = (float)((angle - mMyRenderer.relativeVerticalAngle));//absolute camera angle 
            
            return true;
        }
        else if (keyCode == KeyEvent.KEYCODE_C)
        {
        	mMyRenderer.cameraIsAboveScreen = !mMyRenderer.cameraIsAboveScreen;
        	return true;
        }
        else if (keyCode == KeyEvent.KEYCODE_B)
        {
        	mMyRenderer.showBackground = !mMyRenderer.showBackground;
        	return true;
        }
        else if (keyCode == KeyEvent.KEYCODE_W)
        {
        	mMyRenderer.showWebcamPreview = !mMyRenderer.showWebcamPreview;
        	return true;
        }
        else if (keyCode == KeyEvent.KEYCODE_G)
        {
        	mMyRenderer.showGrid = !mMyRenderer.showGrid;
        	return true;
        }
        else if (keyCode == KeyEvent.KEYCODE_R)
        {
        	mMyRenderer.InitTargets();
        	return true;
        }
        else if (keyCode == KeyEvent.KEYCODE_H)
        {
        	mMyRenderer.showHelp = !mMyRenderer.showHelp;
        	return true;
        }
        else if (keyCode == KeyEvent.KEYCODE_T)
        {
        	mMyRenderer.showTargets = !mMyRenderer.showTargets;
        	return true;
        }
        else if (keyCode == KeyEvent.KEYCODE_L)
        {
        	mMyRenderer.showLines = !mMyRenderer.showLines;
        	return true;
        }

        return super.onKeyDown(keyCode, event);
    }
    
    public void surfaceChanged(SurfaceHolder _holder, int format, int width, int height) {
        Log.i(TAG, "surfaceCreated");
        synchronized (this) {
            if (mCamera != null && mCamera.isOpened()) {
                Log.i(TAG, "before mCamera.getSupportedPreviewSizes()");
                List<Size> sizes = mCamera.getSupportedPreviewSizes();
                Log.i(TAG, "after mCamera.getSupportedPreviewSizes()");
                int mFrameWidth = width;
                int mFrameHeight = height;

                // selecting optimal camera preview size
                {
                    double minDiff = Double.MAX_VALUE;
                    for (Size size : sizes) {
                        if (Math.abs(size.height - height) < minDiff) {
                            mFrameWidth = (int) size.width;
                            mFrameHeight = (int) size.height;
                            minDiff = Math.abs(size.height - height);
                        }
                    }
                }

                mCamera.set(Highgui.CV_CAP_PROP_FRAME_WIDTH, mFrameWidth);
                mCamera.set(Highgui.CV_CAP_PROP_FRAME_HEIGHT, mFrameHeight);
            }
        }
    }

    public void surfaceCreated(SurfaceHolder holder) {
        Log.i(TAG, "surfaceCreated");
        mCamera = new VideoCapture(Highgui.CV_CAP_ANDROID);
        if (mCamera.isOpened()) {
            (new Thread(this)).start();
        } else {
            mCamera.release();
            mCamera = null;
            Log.e(TAG, "Failed to open native camera");
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.i(TAG, "surfaceDestroyed");
        if (mCamera != null) {
            synchronized (this) {
                mCamera.release();
                mCamera = null;
            }
        }
    }

    protected abstract Bitmap processFrame(VideoCapture capture);

    public void run() {
        Log.i(TAG, "Starting processing thread");
        while (true) {
            Bitmap bmp = null;

            synchronized (this) {
                if (mCamera == null)
                    break;

                if (!mCamera.grab()) {
                    Log.e(TAG, "mCamera.grab() failed");
                    break;
                }

                // returns a processed bitmap (with face tracking)                
                bmp = processFrame(mCamera);
                
                mFps.measure();
            }

            // Draws the bitmap unto the canvas
            if (bmp != null) 
            {
            	// Transfer bitmap to GL renderer
            	OpenGLRenderer.ReloadWebcamTexture(bmp);
            	/*
            	 * mFps.Draw( ... );
                Canvas canvas = mHolder.lockCanvas();
                if (canvas != null) {
                    canvas.drawBitmap(bmp, (canvas.getWidth() - bmp.getWidth()) / 2, (canvas.getHeight() - bmp.getHeight()) / 2, null);
                    mHolder.unlockCanvasAndPost(canvas);
                }
                */
                bmp.recycle();
            }
        }

        Log.i(TAG, "Finishing processing thread");
    }
}