package com.cellmate.headtrack;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGL11;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.opengles.GL11;

import java.util.Random;
import java.lang.Math;

import android.app.Activity;
import android.content.Context;
import android.hardware.Camera;
import android.opengl.GLU;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * This class uses OpenGL ES to render the camera's viewfinder image on the screen.
 * Unfortunately I don't know much about OpenGL (ES). The code is mostly copied from
 * some examples. The only interesting stuff happens in the main loop (the run method)
 * and the onPreviewFrame method.
 */
public class HeadGLLayer extends SurfaceView implements SurfaceHolder.Callback, Runnable, Camera.PreviewCallback {
	final static float targetVertices[] = new float[] {
		-1.0f, 1.0f,.0f,
		1.0f, 1.0f,.0f,
		-1.0f,-1.0f,.0f,
		1.0f,-1.0f,.0f	 
	};
	final static float targetVerticesTexCoord[] = new float[] {
		0.0f,0.0f,
		1.0f,0.0f,
		0.0f,1.0f,
		1.0f,1.0f			 
	};
    
	/////////////////////////////////////////////////////
    // Static variables
    // float dotDistanceInMM = 5.75f*25.4f;
	/////////////////////////////////////////////////////
    static float dotDistanceInMM = 2.5f * 25.4f;//width of the wii sensor bar
    static float screenHeightinMM = 6 * 25.4f; // screen height is 6 inches
    static float radiansPerPixel = (float)(Math.PI / 4) / 480.0f; //45 degree field of view with a 640x960 camera
    static float movementScaling = 1.0f;

    static public boolean isReady = false;
    static int m_dwWidth = 480;
    static int m_dwHeight = 800;

    //headposition
    static float headX = 0;
    static float headY = 0;
    static float headDist = 2;

    static float cameraVerticaleAngle = 0; //begins assuming the camera is point straight forward
    static float relativeVerticalAngle = 0; //current head position view angle
    static boolean cameraIsAboveScreen = false;//has no affect until zeroing and then is set automatically.
	/////////////////////////////////////////////////////
    // Numerical settings 
    /////////////////////////////////////////////////////
    int numGridlines = 10;
    float boxdepth = 8;
    float fogdepth = 5;
    int numTargets = 10;
    int numInFront = 3;
    float targetScale = .065f;
    float screenAspect = 0;

    int gridColor = 0xCCCCCC;
    int lineColor = 0xFFFFFF;
    int lineDepth = -200;
    
    int backgroundStepCount = 10;
    /////////////////////////////////////////////////////
    int lastKey = 0;
    boolean isLoaded = false;
    boolean badConfigFile = false;
    boolean showGrid = true;
    boolean showHelp = true; // Press H to hide
    boolean showMouseCursor = false;
    boolean showTargets = true;
    boolean showLines = true;
    boolean doFullscreen = true;
    boolean mouseDown = false;
    boolean showWebcamPreview = true;
    boolean showBackground = false;
    /////////////////////////////////////////////////////
    Vector3[] targetPositions;
    Vector3[] targetSizes;

    FloatBuffer webcamBuffer, webcamTexBuffer, targetsBuffer, targetsTexBuffer, linesBuffer, gridBuffer, backgroundBuffer, backgroundTexBuffer;
    int vertexBufferIndex, textureBufferIndex, linesBufferIndex, gridBufferIndex;
    int texture;
    
    int compiledList;
    IntBuffer lists;

    static public boolean isLoadingWebcamTexture = false;
    static public boolean isRendering = false;
    public boolean isTrackingStarted = false;

    //Stopwatch sw = new Stopwatch(); // available to all event handlers
    double accumulator = 0;
    int idleCounter = 0;

    int lastFrameTick = 0;
    int frameCount;
    float frameRate = 0;

    //Matrix4 cameraMatrix;
   // Matrix4 matProjection;

    //cube rotation
    float rotX;
    float rotY;
    float rotZ;
    /////////////////////////////////////////////////////
    
	protected EGLContext eglContext;
	protected SurfaceHolder sHolder;
	protected Thread mainLoop;
	protected boolean running;
	int width;
	int height;
	
	
	EGLSurface surface;
	EGLDisplay dpy;
	EGL10 egl;
	GL10 gl;
	
	byte[] glCameraFrame;
	int[] cameraTexture;

	BooleanLock newFrameLock=new BooleanLock(false);	

	public void handlePressLeft()
	{
		headX -= 0.01f;
	}
	
	public void handlePressRight()
	{
		 headX += 0.01f;
	}
	
	public void handlePressUp()
	{
		 headY -= 0.01f;
	}
	
	public void handlePressDown()
	{
		 headY += 0.01f;
	}
		
	public void handlePressA()
	{
		headDist -= 0.01f;
	}
	
	public void handlePressZ()
	{
		headDist += 0.01f;
	}
	
	public HeadGLLayer(Context c) {
		super(c);
		
		sHolder = getHolder();
		sHolder.addCallback(this);
		sHolder.setType(SurfaceHolder.SURFACE_TYPE_GPU);
	}
	
	FloatBuffer makeFloatBuffer(float[] arr, int size) {
		ByteBuffer bb = ByteBuffer.allocateDirect(arr.length*size);
		bb.order(ByteOrder.nativeOrder());
		FloatBuffer fb = bb.asFloatBuffer();
		fb.put(arr);
		fb.position(0);
		return fb;
	}
	
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		synchronized (this) {
			this.width = width;
			this.height = height;
		}
	}

	public void surfaceCreated(SurfaceHolder holder) {
		// Init GL stuff
		init();
		
		mainLoop = new Thread(this);
		mainLoop.start();
	}

	public void surfaceDestroyed(SurfaceHolder arg0) {
		if (running) {
			running = false;
			this.newFrameLock.setValue(true);
			try {
				mainLoop.join();
			}
			catch (Exception ex) {}
			mainLoop = null;
			
	        egl.eglMakeCurrent(dpy, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
	        egl.eglDestroySurface(dpy, surface);
	        egl.eglDestroyContext(dpy, eglContext);
	        egl.eglTerminate(dpy);
		}
	}	
	
	/**
	 * Some initialization of the OpenGL stuff (that I don't
	 * understand...)
	 */
	

	protected void init() {	
	    if (screenAspect == 0)//only override if it's emtpy
	        screenAspect = m_dwWidth / (float)m_dwHeight;
	    
	    
		// Much of this code is from GLSurfaceView in the Google API Demos.
		// I encourage those interested to look there for documentation.
		egl = (EGL10)EGLContext.getEGL();
		dpy = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
		
		int[] version = new int[2];
        egl.eglInitialize(dpy, version);
        
        int[] configSpec = {
                EGL10.EGL_RED_SIZE,      5,
                EGL10.EGL_GREEN_SIZE,    6,
                EGL10.EGL_BLUE_SIZE,     5,
                EGL10.EGL_DEPTH_SIZE,    8,
                EGL10.EGL_NONE
        };
        
        EGLConfig[] configs = new EGLConfig[1];
        int[] num_config = new int[1];
        egl.eglChooseConfig(dpy, configSpec, configs, 1, num_config);
        EGLConfig config = configs[0];
		
		eglContext = egl.eglCreateContext(dpy, config, EGL10.EGL_NO_CONTEXT, null);
		
		surface = egl.eglCreateWindowSurface(dpy, config, sHolder, null);
		egl.eglMakeCurrent(dpy, surface, surface, eglContext);
			
		gl = (GL10)eglContext.getGL();
		
		//Load the buffer and stuff		
		// background is black
		gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
		gl.glClearDepthf(1.0f);

		gl.glShadeModel(GL10.GL_SMOOTH);
		
		// GL.PolygonMode(MaterialFace.FrontAndBack, PolygonMode.Fill);
		gl.glCullFace(GL10.GL_FRONT_AND_BACK);
		
		gl.glEnable(GL10.GL_COLOR_MATERIAL);
		gl.glMaterialf(GL10.GL_FRONT_AND_BACK,GL10.GL_COLOR_MATERIAL,(float)GL10.GL_AMBIENT_AND_DIFFUSE);		
		
		gl.glEnable(GL10.GL_DEPTH_TEST );
		
	    // creates array for grid used later in drawing
	    CreateWebcamGeometry();
	    CreateGridGeometry();
	    CreateTargetGeometry();
	    CreateBackgroundGeometry();

	    // Randomizes locations of targets and lengths (depth)
	    InitTargets();
	    
	    // Load Textures
	    
	    SetupMatrices();
		
	    /*
		//gl11.glVertexPointer(3, GL10.GL_FLOAT, 0, cubeBuff);
		gl11.glEnableClientState(GL10.GL_VERTEX_ARRAY);
		//gl11.glTexCoordPointer(2, GL10.GL_FLOAT, 0, texBuff);
		gl11.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
		gl11.glEnable(GL10.GL_TEXTURE_2D);
		*/
	}
	
    private void SetupMatrices()
    {
        // For the projection matrix, we set up a perspective transform (which
        // transforms geometry from 3D view space to 2D viewport space, with
        // a perspective divide making objects smaller in the distance). To build
        // a perpsective transform, we need the field of view (1/4 pi is common),
        // the aspect ratio, and the near and far clipping planes (which define at
        // what distances geometry should be no longer be rendered).

        //compute the near plane so that the camera stays fixed to -.5f*screenAspect, .5f*screenAspect, -.5f,.5f
        //compting a closer plane rather than simply specifying xmin,xmax,ymin,ymax allows things to float in front of the display
        float nearPlane = 0.05f;
        float farPlane = 500f; // 100f ?
        
		gl.glMatrixMode(GL10.GL_PROJECTION);
		gl.glLoadIdentity();
		gl.glViewport(0,0,width,height);
		//GLU.gluPerspective(gl, 45.0f, ((float)width)/height, 1f, 100f);
		
		gl.glFrustumf(nearPlane * ( -.5f * screenAspect - headX) / headDist,
					                nearPlane * (.5f * screenAspect - headX) / headDist,
					                nearPlane * (-.5f - headY) / headDist,
					                nearPlane * (.5f - headY) / headDist,
					                nearPlane, farPlane);
		

        // Set up our view matrix. A view matrix can be defined given an eye point,
        // a point to lookat, and a direction for which way is up. Here, we set the
        // eye five units back along the z-axis and up three units, look at the
        // origin, and define "up" to be in the y-direction.
		// device.Transform.View = Matrix.LookAtLH(new Vector3(mouseCursor.X, mouseCursor.Y, -5.0f), new Vector3(0.0f, 0.0f, 0.0f), new Vector3(0.0f, 1.0f, 0.0f));
		gl.glMatrixMode(GL10.GL_MODELVIEW);
		gl.glLoadIdentity();
		GLU.gluLookAt(gl, headX, headY, headDist, headX, headY, 0, 0.0f, 1.0f, 0.0f);
		//gl11.glNormal3f(0,0,1);
    }
    
    // This is supposed to give me where the head is moving
    // But we're already doing that with HeadTracker
    /*
    static public void ParseHeadTrackingData(PointF firstPoint, PointF secondPoint)
    {
        if (!isReady || float.IsNaN(firstPoint.X) || float.IsNaN(secondPoint.X))
            return;

        float dx = firstPoint.X - secondPoint.X;
        float dy = firstPoint.Y - secondPoint.Y;
        float pointDist = (float)Math.Sqrt(dx * dx + dy * dy);

        float angle = radiansPerPixel * pointDist / 2;
        //in units of screen hieght since the box is a unit cube and box hieght is 1
        headDist = movementScaling * (float)((dotDistanceInMM / 2) / Math.Tan(angle)) / screenHeightinMM;


        float avgX = (firstPoint.X + secondPoint.X) / 2.0f;
        float avgY = (firstPoint.Y + secondPoint.Y) / 2.0f;


        //should  calaculate based on distance
        headX = (float)(movementScaling * Math.Sin(radiansPerPixel * (avgX - (m_dwWidth/2))) * headDist);

        relativeVerticalAngle = (avgY - (m_dwHeight/2)) * radiansPerPixel;//relative angle to camera axis

        if (cameraIsAboveScreen)
            headY = .5f + (float)(movementScaling * Math.Sin(relativeVerticalAngle + cameraVerticaleAngle) * headDist);
        else
            headY = -.5f + (float)(movementScaling * Math.Sin(relativeVerticalAngle + cameraVerticaleAngle) * headDist);
    }
    */
    
	// OnResize ??
	/*
	 * 
 *          m_dwWidth = ClientSize.Width;
            m_dwHeight = ClientSize.Height;

            screenAspect = m_dwWidth / (float)m_dwHeight;

            SetupMatrices();
	 */
	 public void CreateBackgroundGeometry()
     {		 
		 /*
 		float backgroundVertices[] = new float[backgroundStepCount*3+1]; 		
 		float backgroundTexVertices[] = new float[backgroundStepCount*2+1];
 		
        float angleStep = (float)(Math.PI / backgroundStepCount);
        
        int bgIndex = 0, bgTexIndex = 0;
        
        for (int i = 0; i <= backgroundStepCount; i++)
        {
            // On even steps (0, 2, 4)
            if (i % 2 == 0)
            {
            	backgroundVertices[bgIndex] = (float)(java.lang.Math.cos(angleStep * i));
            	backgroundVertices[bgIndex+1] = -1f;
            	backgroundVertices[bgIndex+2] = -(float)(java.lang.Math.sin(angleStep * i));
            	
            	bgIndex += 3;
            	
            	backgroundTexVertices[bgTexIndex] = i / (float)backgroundStepCount;
            	backgroundTexVertices[bgTexIndex+1] = 1f;
            	
            	bgIndex += 2;

            }
            else
            {
                // On odd steps (1,3,5)
            	backgroundVertices[bgIndex] = (float)(java.lang.Math.cos(angleStep * i));
            	backgroundVertices[bgIndex+1] = 1f;
            	backgroundVertices[bgIndex+2] = -(float)(java.lang.Math.sin(angleStep * i));
            	
            	bgIndex += 3;
            	
            	backgroundTexVertices[bgTexIndex] = i / (float)backgroundStepCount;
            	backgroundTexVertices[bgTexIndex+1] = 0;
            	
            	bgIndex += 2;
            }
            */
       // }
        
		 backgroundBuffer = makeFloatBuffer(targetVertices,4);
		 backgroundTexBuffer = makeFloatBuffer(targetVerticesTexCoord,4);	

     }

     public void CreateWebcamGeometry()
     {
 		webcamBuffer = makeFloatBuffer(targetVertices,4);
 		webcamTexBuffer = makeFloatBuffer(targetVerticesTexCoord,4);	
     }


     public void CreateTargetGeometry()
     {
    	 targetsBuffer = makeFloatBuffer(targetVertices,4);
    	 targetsTexBuffer = makeFloatBuffer(targetVerticesTexCoord,4);
    	      
    	 GL11 gl11 = (GL11)gl;
    	 
         int[] buffer = new int[1];
         gl11.glGenBuffers(1, buffer, 0);
         vertexBufferIndex = buffer[0];
         gl11.glBindBuffer(GL11.GL_ARRAY_BUFFER, vertexBufferIndex);
         gl11.glBufferData(GL11.GL_ARRAY_BUFFER, 12 * 4, targetsBuffer, GL11.GL_STATIC_DRAW);
        
         gl11.glGenBuffers(1, buffer, 0);
         textureBufferIndex = buffer[0];
         gl11.glBindBuffer(GL11.GL_ARRAY_BUFFER, textureBufferIndex);
         gl11.glBufferData(GL11.GL_ARRAY_BUFFER, 8 * 4, targetsTexBuffer, GL11.GL_STATIC_DRAW);
        
        // gl11.glBindBuffer(GL11.GL_ELEMENT_ARRAY_BUFFER, 0);
        // gl11.glBindBuffer(GL11.GL_ARRAY_BUFFER, 0);
         
         
 		// lines
 		float linesVertices[] = new float[] {
 				0.0f, 0.0f, 0.0f,
 				0.0f, 0.0f, lineDepth	 
 		};
 		
         linesBuffer = makeFloatBuffer(linesVertices,4);	

         gl11.glGenBuffers(1, buffer, 0);
         linesBufferIndex = buffer[0];
         gl11.glBindBuffer(GL11.GL_ARRAY_BUFFER, linesBufferIndex);
         gl11.glBufferData(GL11.GL_ARRAY_BUFFER, 6 * 2, linesBuffer, GL11.GL_STATIC_DRAW);
         
         // GL.Color4(new Color4((byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF));

     }

     // Builds a grid wall which we can later scale, rotate and transform when rendering
     // (I could have used display lists as in the 
     void CreateGridGeometry()
     {
    	 /*
    	 gridBuffer = gl11.glGenLists(1);
    	 gl11.glNewList(gridBuffer,GL10.GL_COMPILE);
    	// gl11.glBindTexture(GL10.GL_TEXTURE_2D, texture);
		 gl11.glBegin(GL10.GL_QUADS);
		 gl11.glTexCoord2f(0, 0); gl11.glVertex2f(0, 0);
		 gl11.glTexCoord2f(0, 1); gl11.glVertex2f(0, height);
		 gl11.glTexCoord2f(1, 1); gl11.glVertex2f( width,  height);
		 gl11.glTexCoord2f(1, 0); gl11.glVertex2f(width, 0);
		 gl11.glEnd();
		 gl11.glEndList();		
		 */
     }
     

     
     // Generates random target areas
     public void InitTargets()
     {    			 
         if (targetPositions == null)
             targetPositions = new Vector3[numTargets];
         if (targetSizes == null)
             targetSizes = new Vector3[numTargets];
         float depthStep = (boxdepth / 2.0f) / numTargets;
         float startDepth = numInFront * depthStep;
         
         final Random myRandom = new Random();
         
         for (int i = 0; i < numTargets; i++)
         {
             targetPositions[i] = new Vector3(.7f * screenAspect * (myRandom.nextInt(1000) / 1000.0f - .5f),
                                                 .7f * (myRandom.nextInt(1000) / 1000.0f - .5f),
                                                 startDepth - i * depthStep);
             if (i < numInFront)//pull in the ones out in front of the display closer the center so they stay in frame
             {
                 targetPositions[i].x *= .5f;
                 targetPositions[i].y *= .5f;
             }
             targetSizes[i] = new Vector3(targetScale, targetScale, targetScale);
         }
     }
     
	/**
	 * Generates a texture from the black and white array filled by the onPreviewFrame
	 * method.
	 */
	void bindCameraTexture(GL10 gl) {
		synchronized(this) {
			if (cameraTexture==null)
				cameraTexture=new int[1];
			else
				gl.glDeleteTextures(1, cameraTexture, 0);
			
			gl.glGenTextures(1, cameraTexture, 0);
			int tex = cameraTexture[0];
			gl.glBindTexture(GL10.GL_TEXTURE_2D, tex);
			gl.glTexImage2D(GL10.GL_TEXTURE_2D, 0, GL10.GL_LUMINANCE, 256, 256, 0, GL10.GL_LUMINANCE, GL10.GL_UNSIGNED_BYTE, ByteBuffer.wrap(glCameraFrame));
			gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
		}
	}	
	
	/**
	 * After some initialization the method loops and renders the camera image
	 * whenever a new frame arrived. The black and white byte array is binded to a
	 * texture by the bindCameraTexture method. Afterwards an object can be
	 * rendered with this texture.
	 */
	@SuppressWarnings("static-access")
	public void run() {
		running = true;
		
		while (running) {
			if (!running) return;

			 isRendering = true;
			 
				gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);
				
	            // Setup the world, view, and projection matrices
	            SetupMatrices();

	    		gl.glMatrixMode(GL10.GL_MODELVIEW);

	            EnableFog();
	            
	            GL11 gl11 = (GL11)gl;
	            
	            if (!showGrid)
	            {            	 
	            	gl11.glColor4f((byte)0xCC, (byte)0xCC, (byte)0xCC, (byte)0xCC);
	            	
	            	gl11.glDisable(GL10.GL_TEXTURE_2D);
	            	gl11.glDisable(GL10.GL_BLEND);
	            	gl11.glDisable(GL10.GL_ALPHA_TEST );
	            
	            	gl11.glEnableClientState(GL10.GL_VERTEX_ARRAY);
	            	
	            	gl11.glVertexPointer(3, GL10.GL_FLOAT, 0, gridBuffer);
	        		
	                /*
	                 * If you want to perform your transformations 
	                 * in this order : translation -> rotation -> scale (which makes perfectly sense, it's what's wanted usually), 
	                 * you have to multiply your matrices in the reverse order. (in DirectX it's left handed system and regular order multiplication)
	                 * */
	                //  device.Transform.World = Matrix.Translation
	                // translations to the World view in DirectX are done on the GL
	                // zoom out by 1/2 boxdepth, move 0.5 right and 0.5 above the floor
	            	gl11.glPushMatrix(); // copies the current matrix and add the copy to the top of the stack
	            	gl11.glScalef(screenAspect, 1, 1);
	                gl11.glTranslatef(-.5f, -.5f, -1 * boxdepth / 2);
	            
	                gl11.glDrawArrays(GL10.GL_LINES, 0, numGridlines * 6); // ?? *12
	                
	                gl11.glPopMatrix();
	    
	                // FULLLLL REVERSER ORDER ON THIS FROM DIRECTX to OPENGL. Also, whenever there's Transform.World = it means we need to use PushMatrix and PopMatrix 
	                // because the = sign means we're starting fresh each time so we don't want to be using the same matrix throughout the operation (in OpenGL)
	                /*
	                 * Original Code
	                 * 
	                 *  device.Transform.World = Matrix.Translation(new Vector3(-.5f, -.5f, 0));
	                    device.Transform.World *= Matrix.Scaling(new Vector3(1 * boxdepth / 2, 1, 1));
	                    device.Transform.World *= Matrix.RotationY((float)(Math.PI / 2));
	                    device.Transform.World *= Matrix.Translation(new Vector3(0.5f * screenAspect, 0, -.5f*boxdepth/2));
	                 * */
	                // Right first (because it's in reverse order from DirectX)
	                // Unfortuntely, I have to draw each part from scratch. Maybe I can use VBOs here or GL.DrawElements ?
	                gl11.glPushMatrix();              
	                gl11.glTranslatef(.5f * screenAspect, -.5f, 0);                  
	                gl11.glRotatef(90f,0, 1, 0);
	                
	                IntBuffer lists;

	               // gl11.glCallLists(lists);

	                gl11.glScalef(1 * boxdepth / 2, 1, 1);
	                gl11.glDrawArrays(GL10.GL_LINES, 0, numGridlines * 6);
	                gl11.glPopMatrix();

	                // Left
	                gl11.glPushMatrix();
	                gl11.glTranslatef(-.5f * screenAspect, -.5f, 0);
	                gl11.glRotatef(90f,0, 1, 0);
	                gl11.glScalef(1 * boxdepth / 2, 1, 1);
	                gl11.glDrawArrays(GL10.GL_LINES, 0, numGridlines * 6);
	                gl11.glPopMatrix();

	                // ceiling
	                gl11.glPushMatrix();
	                gl11.glTranslatef(-.5f * screenAspect, .5f, -1f * boxdepth / 2);  
	                gl11.glRotatef(90f,1, 0, 0);
	                gl11.glScalef(screenAspect, 1 * boxdepth / 2, 1);
	                gl11.glDrawArrays(GL10.GL_LINES, 0, numGridlines * 6);
	                gl11.glPopMatrix();

	                // floor
	                gl11.glPushMatrix();
	                gl11.glTranslatef(-.5f * screenAspect, -.5f, -1f * boxdepth / 2);
	                gl11.glRotatef(90f,1, 0, 0);
	                gl11.glScalef(screenAspect, 1 * boxdepth / 2, 1);
	                gl11.glDrawArrays(GL10.GL_LINES, 0, numGridlines * 6);
	                gl11.glPopMatrix();        
	                
	             // Disable the vertices buffer.
	                gl11.glDisableClientState(GL10.GL_VERTEX_ARRAY);
	            }

	            /*
	            if (showWebcamPreview && webcamTexture != 0)
	            {
	                gl11.glPushMatrix();
	                gl11.glTranslatef(-.5f * screenAspect, -.5f, -.5f));
	                gl11.glScalef(0.2f, 0.2f, 0.2f));

	                gl11.glEnable(GL10.GL_TEXTURE_2D);
	                GL.BindTexture(TextureTarget.Texture2D, webcamTexture);

	              //  GL.Disable(EnableCap.Fog);

	                //Render States
	                gl11.glDisable(GL10.GL_BLEND);
	            	gl11.glDisable(GL10.GL_ALPHA_TEST );

	                GL.TexEnv(TextureEnvTarget.TextureEnv, TextureEnvParameter.TextureEnvMode, (float)TextureEnvMode.Modulate);

	                GL.CallList(webcamBuffer);

	                gl11.glPopMatrix();

	                gl11.glDisable(GL10.GL_TEXTURE_2D);
	            }
	            */
	            
	            if (showLines)
	            {
	            	gl11.glDisable(GL10.GL_TEXTURE_2D);
	            	gl11.glDisable(GL10.GL_BLEND);
	            	gl11.glDisable(GL10.GL_ALPHA_TEST );

	            	gl11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
	            	
                	gl11.glBindBuffer(GL11.GL_ARRAY_BUFFER, vertexBufferIndex);
                	gl11.glVertexPointer(3, GL11.GL_FLOAT, 0, 0);	
                	
	                for (int i = 0; i < numTargets; i++)
	                {
	                    gl11.glPushMatrix(); // copies the current matrix and add the copy to the top of the stack
	                    gl11.glTranslatef(targetPositions[i].x, targetPositions[i].y, targetPositions[i].z);
	                    gl11.glScalef(targetSizes[i].x, targetSizes[i].y, targetSizes[i].z );

	                    gl11.glDrawElements(GL11.GL_LINES, 1, GL10.GL_UNSIGNED_SHORT, 0);
        	            		                    
	                    gl11.glPopMatrix();                    
	                }

	            	gl11.glDisableClientState(GL10.GL_VERTEX_ARRAY);		            
	            }

	            if (!showTargets)//draw targets
	            {
	            	gl11.glEnable(GL11.GL_TEXTURE_2D);
	            	
	            	////////////////// VBO ///////////////////////////////
	            	gl11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
		            gl11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
		            
                	gl11.glBindBuffer(GL11.GL_ARRAY_BUFFER, vertexBufferIndex);
                	gl11.glVertexPointer(3, GL11.GL_FLOAT, 0, 0);	     	                            
                            
                    gl11.glBindBuffer(GL11.GL_ARRAY_BUFFER, textureBufferIndex);
    	            gl11.glTexCoordPointer(2, GL11.GL_FLOAT, 0, 0);
    	            /////////////////////////////////////////////////////
    	            
		            gl11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
	                gl11.glEnable(GL11.GL_COLOR_MATERIAL);

	                //Render States   

	                ///////////////////////////////////////////////////////////////////////////////
	                // ENABLE_TRANSPARENCY (mask + clip)
	                ///////////////////////////////////////////////////////////////////////////////                
	                gl11.glEnable(GL11.GL_BLEND);
	                gl11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

	                gl11.glEnable(GL11.GL_ALPHA_TEST);
	                gl11.glAlphaFunc(GL11.GL_GREATER, 0.5f);                 
	                ///////////////////////////////////////////////////////////////////////////////

	                ///////////////////////////////////////////////////////////////////////////////
	                // I think this part is unnecessary as these are the default values
	                ///////////////////////////////////////////////////////////////////////////////
	                //Color blending ops (these are the default values as it is)
	                float[] color4 = {1f, 1f, 1f, 0};
	                
	                gl11.glTexEnvf(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, (float)GL11.GL_MODULATE);
	                gl11.glTexEnvfv(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_COLOR, color4,0);

	                //set the first alpha stage to texture alpha
	                //gl11.glTexEnvf(GL11.GL_TEXTURE_ENV, TextureEnvParameter.Operand0Alpha, (float)TextureEnvParameter.Src1Alpha);
	                ///////////////////////////////////////////////////////////////////////////////
	                for (int i = 0; i < numTargets; i++)
	                {
	                	gl11.glPushMatrix(); // copies the current matrix and add the copy to the top of the stack
	                	gl11.glTranslatef(targetPositions[i].x, targetPositions[i].y, targetPositions[i].z);
	                	gl11.glScalef(targetSizes[i].x, targetSizes[i].y, targetSizes[i].z );

        	            gl11.glDrawElements(GL11.GL_TRIANGLE_STRIP, 1, GL10.GL_UNSIGNED_SHORT, 0);
        	            
        	           // gl11.glBindBuffer(GL11.GL_ELEMENT_ARRAY_BUFFER, 0);
        	           // gl11.glBindBuffer(GL11.GL_ELEMENT_ARRAY_BUFFER, 0);
	                    
	                    gl11.glPopMatrix();                    
	                }

	                gl11.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
	            	gl11.glDisableClientState(GL10.GL_VERTEX_ARRAY);		            
		            
	                gl11.glDisable(GL10.GL_TEXTURE_2D);
	                gl11.glDisable(GL10.GL_COLOR_MATERIAL);
	            }



	            if (showBackground)
	            {
	                gl11.glPushMatrix();
	                gl11.glScalef(3, 2, 3);

	                gl11.glEnable(GL10.GL_TEXTURE_2D);
	               // GL.BindTexture(TextureTarget.Texture2D, backgroundtexture);

	                gl11.glDisable(GL10.GL_FOG);

	                //Render States
	                gl11.glDisable(GL10.GL_BLEND);
	            	gl11.glDisable(GL10.GL_ALPHA_TEST );

	              //  GL.TexEnv(TextureEnvTarget.TextureEnv, TextureEnvParameter.TextureEnvMode, (float)TextureEnvMode.Modulate);
	               // GL.TexEnv(TextureEnvTarget.TextureEnv, TextureEnvParameter.TextureEnvColor, new Color4(1, 1, 1, 0));

	              //  GL.CallList(backgroundBuffer);

	                gl11.glPopMatrix();

	                gl11.glDisable(GL10.GL_TEXTURE_2D);
	            }



	            /*
	           if (showMouseCursor)
	           {
	               device.TextureState[0].ColorOperation = TextureOperation.Disable;
	               device.RenderState.AlphaBlendEnable = false;
	               device.RenderState.AlphaTestEnable = false;

	               device.Transform.World = Matrix.Identity;
	               mouseCursor.Render(device);
	           }           
	           */

	           // if (showHelp)
	           //     RenderText();          
	            /*
				newFrameLock.waitUntilTrue(1000000);
				newFrameLock.setValue(false);
				bindCameraTexture(gl);
				gl11.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 0, 4);
				
				gl11.glRotatef(1,0,0,1); //Rotate the camera image
	         	*/

			egl.eglSwapBuffers(dpy, surface);
			
            if (egl.eglGetError() == EGL11.EGL_CONTEXT_LOST) {
                Context c = getContext();
                if (c instanceof Activity) {
                    ((Activity)c).finish();
                }
            }
		}
	}

    void EnableFog()
    {
    	gl.glEnable(GL10.GL_FOG);
        float[] color = { 0.0f, 0.0f, 0.0f, 1.0f };
        
        gl.glFogf(GL10.GL_FOG_MODE, GL10.GL_LINEAR);
        gl.glFogfv(GL10.GL_FOG_COLOR,color,0);
        gl.glFogf(GL10.GL_FOG_DENSITY, 0.35f);        
        gl.glHint(GL10.GL_FOG_HINT, GL10.GL_NICEST);
        gl.glFogf(GL10.GL_FOG_START, headDist);
        gl.glFogf(GL10.GL_FOG_END, headDist + fogdepth);
    }
    
	/**
	 * This method is called if a new image from the camera arrived. The camera
	 * delivers images in a yuv color format. It is converted to a black and white
	 * image with a size of 256x256 pixels (only a fraction of the resulting image
	 * is used). Afterwards Rendering the frame (in the main loop thread) is started by
	 * setting the newFrameLock to true. 
	 */
	public void onPreviewFrame(byte[] yuvs, Camera camera) {
		if (!running) return;
		
		if (glCameraFrame==null)
			glCameraFrame=new byte[256*256]; //size of a texture must be a power of 2
		
   		int bwCounter=0;
   		int yuvsCounter=0;
   		for (int y=0;y<160;y++) {
   			System.arraycopy(yuvs, yuvsCounter, glCameraFrame, bwCounter, 240);
   			yuvsCounter=yuvsCounter+240;
   			bwCounter=bwCounter+256;
   		}
   		
		newFrameLock.setValue(true);	
	}
	
	// Used for static images sent over the web browser (for testing)
	public void onPreviewFrame(byte[] yuvs) {
		if (!running) return;
		
		if (glCameraFrame==null)
			glCameraFrame=new byte[256*256]; //size of a texture must be a power of 2
		
   		int bwCounter=0;
   		int yuvsCounter=0;
   		for (int y=0;y<160;y++) {
   			System.arraycopy(yuvs, yuvsCounter, glCameraFrame, bwCounter, 240);
   			yuvsCounter=yuvsCounter+240;
   			bwCounter=bwCounter+256;
   		}
   		
		newFrameLock.setValue(true);	
	}
}

