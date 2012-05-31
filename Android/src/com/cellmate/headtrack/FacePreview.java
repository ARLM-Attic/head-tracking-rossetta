package com.cellmate.headtrack;

import java.util.Timer;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.FaceDetector;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.Window;
import android.view.WindowManager;

public class FacePreview extends Activity {
    private static final String TAG             = "Sample::Activity";
    
    public static float         minFaceSize = 0.5f;
    
    public static final int     VIEW_MODE_RGBA  = 0;
    public static final int     VIEW_MODE_GRAY  = 1;
    public static final int     VIEW_MODE_CANNY = 2;

    public static final float EPSILON = 0.01f;
    
    private MenuItem            mItemFace50;
    private MenuItem            mItemFace40;
    private MenuItem            mItemFace30;
    private MenuItem            mItemFace20;

    float prevRotateLeftRight = 0f;
	float prevRotateUpDown = 0f;
	
    public static int           viewMode        = VIEW_MODE_RGBA;

    private Timer timer = null;
    private TiltCalc mTiltCalc = null;
    
    private ClearGLSurfaceView mGLSurfaceView;
    private Handler mHandler = new Handler();

    public FacePreview() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        
	      final Window win = getWindow(); 
	      win.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        mGLSurfaceView = new ClearGLSurfaceView(this); //new GLSurfaceView(this);
        
        setContentView(mGLSurfaceView);
        mGLSurfaceView.requestFocus();
        mGLSurfaceView.setFocusableInTouchMode(true);
        
        // This will make sure we update the renderer with the values received from the tilt sensors
        mTiltCalc = new TiltCalc(this,mGLSurfaceView.mRenderer);
        
        mHandler.removeCallbacks(mUpdateTimeTask);
        mHandler.postDelayed(mUpdateTimeTask, 100);

        //timer = new Timer();
       // timer.schedule(new UpdateTimeTask(), 100, 1000);
    }

    private Runnable mUpdateTimeTask = new Runnable() {
    	   public void run() {

    			float[] currRotation = new float[4];
    			
    			
    		   mTiltCalc.getTilt(currRotation);
               
               // So vals[0] is rotating the phone around like a compass, vals[1] is tilting the phone up and down, and vals[2] is tilting the phone left and right.
               
               // vals[2] is left/right
               if (prevRotateLeftRight == 0)
               {
               		prevRotateLeftRight = currRotation[2];
               }
               else
               {
	               	float deltaRotateLeftRight = currRotation[2] - prevRotateLeftRight;
	               	
	               	Log.d("HeadTrack", "DELTA LEFT-RIGHT " + deltaRotateLeftRight);
	               	
	               	if (Math.abs(deltaRotateLeftRight) > EPSILON)
	               	{
		               	if (deltaRotateLeftRight < 0)
		               	{
		               		mGLSurfaceView.mRenderer.handlePressRight();
		               	}
		               	else if (deltaRotateLeftRight > 0)
		               	{
		               		mGLSurfaceView.mRenderer.handlePressLeft();
		               	}
		               	
		               	
	               	}
	               	
	               	prevRotateLeftRight = currRotation[2];	

               }
               
               // vals[1] is up/down
               if (prevRotateUpDown == 0)
               {
               		prevRotateUpDown = currRotation[1];
               }
               else
               {
	               	float deltaRotateUpDown = currRotation[1] - prevRotateUpDown;
	               	
	               	Log.d("HeadTrack", "DELTA UP-DOWN " + deltaRotateUpDown);
	               	
	               	if (Math.abs(deltaRotateUpDown) > EPSILON)
	               	{
		               	if (deltaRotateUpDown < 0)
		               	{
		               		mGLSurfaceView.mRenderer.handlePressDown();
		               	}
		               	else if (deltaRotateUpDown > 0)
		               	{
		               		mGLSurfaceView.mRenderer.handlePressUp();
		               	}
		               	
		               	
	               	}
	               	
	               	prevRotateUpDown = currRotation[1];
               }
    	     
    	       mHandler.postDelayed(this, 15);
    	   }
    	};
    	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.i(TAG, "onCreateOptionsMenu");
        mItemFace50 = menu.add("Face size 50%");
        mItemFace40 = menu.add("Face size 40%");
        mItemFace30 = menu.add("Face size 30%");
        mItemFace20 = menu.add("Face size 20%");
        return true;
    }

    @Override 
    public void onDestroy()
    {
    	mHandler.removeCallbacks(mUpdateTimeTask);
    	super.onDestroy();
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG, "Menu Item selected " + item);
        if (item == mItemFace50)
            minFaceSize = 0.5f;
        else if (item == mItemFace40)
            minFaceSize = 0.4f;
        else if (item == mItemFace30)
            minFaceSize = 0.3f;
        else if (item == mItemFace20)
            minFaceSize = 0.2f;
        return true;
    }
    
    class ClearGLSurfaceView extends GLSurfaceView {
    	OpenGLRenderer mRenderer;
    	float prevX = 0f;
    	float prevY = 0f;
    	
    	private ScaleGestureDetector mScaleDetector;
    	private float mScaleFactor = 1.f;
    	private float prevScaleFactor = 1.f;
    	
        public ClearGLSurfaceView(Context context) {
            super(context);
            
            // Create our ScaleGestureDetector
            mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
            
            // set our renderer to be the main renderer with
            // the current activity context
            // This is already done inside the SampleCvViewBase constructor
            mRenderer = new OpenGLRenderer(context);
            
            setRenderer(mRenderer);
            //mGLSurfaceView.setMyRenderer(iGLR);
            setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        }

        public boolean onTouchEvent(final MotionEvent event) {
        	// Let the ScaleGestureDetector inspect all events.
            mScaleDetector.onTouchEvent(event);

            queueEvent(new Runnable(){
                public void run() {
                	if (prevX == 0 && prevY == 0)
                	{
                    	prevX = event.getX();
                    	prevY = event.getY();
                	}
                	else
                	{
                    	float deltaX = event.getX() - prevX;
                    	float deltaY = event.getY() - prevY;
                    	
                    	// Moved right
                    	if (deltaX > 0)
                    	{
                    		mRenderer.handlePressRight();
                    	}
                    	else if (deltaX < 0)
                    	{
                    		mRenderer.handlePressLeft();
                    	}
                    	
                    	// Move down
                    	if (deltaY > 0)
                    	{
                    		mRenderer.handlePressDown();
                    	}
                    	else if (deltaY < 0)
                    	{
                    		mRenderer.handlePressUp();
                    	}
                    	
                    	prevX = event.getX();
                    	prevY = event.getY();
                	}
                }});
                return true;
            }
        
        private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                mScaleFactor *= detector.getScaleFactor();
                
                // Don't let the object get too small or too large.
                mScaleFactor = Math.max(0.1f, Math.min(mScaleFactor, 5.0f));
                
                if (prevScaleFactor < mScaleFactor)
                {
                	// we enlarged
                	mRenderer.handlePressA();
                }
                else if (prevScaleFactor > mScaleFactor)
                {
                	// we made smaller
                	mRenderer.handlePressZ();
                	
                }

                prevScaleFactor = mScaleFactor;

               // invalidate();
                return true;
            }
        }
    }

}
