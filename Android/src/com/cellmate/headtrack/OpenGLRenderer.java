package com.cellmate.headtrack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Random;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.opengles.GL11;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLSurfaceView;

import android.opengl.GLU;
import android.opengl.GLUtils;

public class OpenGLRenderer implements GLSurfaceView.Renderer {
	final static float targetVertices[] = new float[] {
		-1.0f, 1.0f,.0f,  // left up
		1.0f, 1.0f,.0f,  // right up
		-1.0f,-1.0f,.0f, // left bottom
		1.0f,-1.0f,.0f	   // right bottom
	};
	final static float targetVerticesTexCoord[] = new float[] {
		0.0f,0.0f,
		1.0f,0.0f,
		0.0f,1.0f,
		1.0f,1.0f			 
	};
	
	float linesVertices[] = new float[] {
			0.0f, 0.0f, 0.0f,
			0.0f, 0.0f, 0.0f	 
	};
	
	float gridVertices[];
	
	float backgroundVertices[];
	float backgroundTexVertices[];
	
	float webcamVertices[];
	float webcamTexVertices[];
	
	TexFont fnt;
	
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

    int textLineDepth = 10;
    
    int[] textureIDs = new int[3];  // 0 - target texture, 1 - background texture, 2 - webcamtexture
    
    FloatBuffer webcamBuffer, webcamTexBuffer, targetsBuffer, targetsTexBuffer, linesBuffer, gridBuffer, backgroundBuffer, backgroundTexBuffer;
    
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
    
	protected Thread mainLoop;
	protected boolean running;
	int width;
	int height;

	//GL11 gl;
	
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
	
	FloatBuffer makeFloatBuffer(float[] arr) {
		ByteBuffer bb = ByteBuffer.allocateDirect(arr.length*4);
		bb.order(ByteOrder.nativeOrder());
		FloatBuffer fb = bb.asFloatBuffer();
		fb.put(arr);
		fb.position(0);
		return fb;
	}
	
	Context context;   // Application's context
	   
	   // Constructor with global application context
	   public OpenGLRenderer(Context context) {
	      this.context = context;
	   }
	   

	protected void init(GL10 gl) {	
	    if (screenAspect == 0)//only override if it's emtpy
	        screenAspect = m_dwWidth / (float)m_dwHeight;
	    		
	     // Create font
	     fnt = new TexFont(context,gl);
	     
	     // Load font file from Assets
	     try
	     {
	      // Note, this is an 8bit BFF file and will be used as alpha channel only
	      fnt.LoadFont("Arial-alpha.bff",gl); 
	     } 
	     catch (IOException e) 
	     {
	      e.printStackTrace();
	     }

	    
		//Load the buffer and stuff		
		// background is black
		gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
		gl.glClearDepthf(1.0f);

		gl.glShadeModel(GL10.GL_SMOOTH);
		
		// GL.PolygonMode(MaterialFace.FrontAndBack, PolygonMode.Fill);
		//gl.glCullFace(GL10.GL_FRONT_AND_BACK);
		
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
	
	    SetupMatrices(gl);
		
	 // Load Textures
	    loadTexture(gl, context, 0);    // Load image into Texture (NEW)
	    loadTexture(gl, context, 1);
	    loadTexture(gl, context, 2);
	    
	}
	
	   // Load an image into GL texture
	   public void loadTexture(GL10 gl, Context context, int index) {
		   GL11 gl11 = (GL11)gl;
		   
	      gl11.glGenTextures(1, textureIDs, index); // Generate texture-ID array

	      gl11.glBindTexture(GL11.GL_TEXTURE_2D, textureIDs[index]);   // Bind to texture ID
	      // Set up texture filters
	      
	      gl11.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_GENERATE_MIPMAP, GL11.GL_TRUE);
	      gl11.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
	      gl11.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
	  
	      // Construct an input stream to texture image "res\drawable\nehe.png"
	      // Choose different image accordinmg to loaded texture
	      InputStream istream = context.getResources().openRawResource(R.drawable.icon);
	      Bitmap bitmap;
	      try {
	         // Read and decode input as bitmap
	         bitmap = BitmapFactory.decodeStream(istream);
	      } finally {
	         try {
	            istream.close();
	         } catch(IOException e) { }
	      }
	  
	      // Build Texture from loaded bitmap for the currently-bind texture ID
	      GLUtils.texImage2D(GL11.GL_TEXTURE_2D, 0, bitmap, 0);
	      bitmap.recycle();
	   }
	   
    private void SetupMatrices(GL10 gl)
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
		gl.glViewport(0,0,m_dwWidth,m_dwHeight);
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
		//gl.glNormal3f(0,0,1);
    }
    

    @Override
	public void onDrawFrame(GL10 gl) {
			//if (!running) return;

			 isRendering = true;
			 
			gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);
			
            // Setup the world, view, and projection matrices
            SetupMatrices(gl);

    		gl.glMatrixMode(GL10.GL_MODELVIEW);

            EnableFog(gl);
            
            if (showGrid)
            {            	 
            	//gl.glColor4f((byte)0xCC, (byte)0xCC, (byte)0xCC, (byte)0xCC);
            	gl.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
            	
            	gl.glDisable(GL10.GL_TEXTURE_2D);
            	gl.glDisable(GL10.GL_BLEND);
            	gl.glDisable(GL10.GL_ALPHA_TEST );
            
            	gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);            	
            	gl.glVertexPointer(3, GL10.GL_FLOAT, 0, gridBuffer);
        		
                /*
                 * If you want to perform your transformations 
                 * in this order : translation -> rotation -> scale (which makes perfectly sense, it's what's wanted usually), 
                 * you have to multiply your matrices in the reverse order. (in DirectX it's left handed system and regular order multiplication)
                 * */
                //  device.Transform.World = Matrix.Translation
                // translations to the World view in DirectX are done on the GL
                // zoom out by 1/2 boxdepth, move 0.5 right and 0.5 above the floor
            	gl.glPushMatrix(); // copies the current matrix and add the copy to the top of the stack
            	gl.glScalef(screenAspect, 1, 1);
                gl.glTranslatef(-.5f, -.5f, -1 * boxdepth / 2);
            
                gl.glDrawArrays(GL10.GL_LINES, 0, gridVertices.length / 3);
                
                gl.glPopMatrix();
    
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
                gl.glPushMatrix();              
                gl.glTranslatef(.5f * screenAspect, -.5f, 0);                  
                gl.glRotatef(90f,0, 1, 0);               
                gl.glDrawArrays(GL10.GL_LINES, 0, gridVertices.length / 3);
                gl.glScalef(1 * boxdepth / 2, 1, 1);
                gl.glDrawArrays(GL10.GL_LINES, 0, gridVertices.length / 3);
                gl.glPopMatrix();

                // Left
                gl.glPushMatrix();
                gl.glTranslatef(-.5f * screenAspect, -.5f, 0);
                gl.glRotatef(90f,0, 1, 0);
                gl.glScalef(1 * boxdepth / 2, 1, 1);
                gl.glDrawArrays(GL10.GL_LINES, 0, gridVertices.length / 3);
                gl.glPopMatrix();

                // ceiling
                gl.glPushMatrix();
                gl.glTranslatef(-.5f * screenAspect, .5f, -1f * boxdepth / 2);  
                gl.glRotatef(90f,1, 0, 0);
                gl.glScalef(screenAspect, 1 * boxdepth / 2, 1);
                gl.glDrawArrays(GL10.GL_LINES, 0, gridVertices.length / 3);
                gl.glPopMatrix();

                // floor
                gl.glPushMatrix();
                gl.glTranslatef(-.5f * screenAspect, -.5f, -1f * boxdepth / 2);
                gl.glRotatef(90f,1, 0, 0);
                gl.glScalef(screenAspect, 1 * boxdepth / 2, 1);
                gl.glDrawArrays(GL10.GL_LINES, 0, gridVertices.length / 3);
                gl.glPopMatrix();        
                
             // Disable the vertices buffer.
                gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);
            }

            /*
            if (showWebcamPreview && webcamTexture != 0)
            {
                gl.glPushMatrix();
                gl.glTranslatef(-.5f * screenAspect, -.5f, -.5f));
                gl.glScalef(0.2f, 0.2f, 0.2f));

                gl.glEnable(GL10.GL_TEXTURE_2D);
                gl.glBindTexture(GL10.GL_TEXTURE_2D, textureIDs[2]);

            	gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
            	gl.glVertexPointer(3, GL10.GL_FLOAT, 0, webcamBuffer);	
            	
	            gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);	                                  
	            gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, webcamTexBuffer);

                gl.glDisable(GL10.GL_FOG);

                gl.glDisable(GL10.GL_BLEND);
            	gl.glDisable(GL10.GL_ALPHA_TEST );
                
                gl.glTexEnvf(GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_MODE, (float)GL10.GL_MODULATE);

                gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 0, webcamVertices.length / 3);

                gl.glPopMatrix();

                gl.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
            	gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);	
            	
                gl.glDisable(GL10.GL_TEXTURE_2D);
            }
            */
            
            if (showLines)
            {
            	gl.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
            	
            	gl.glDisable(GL10.GL_TEXTURE_2D);
            	gl.glDisable(GL10.GL_BLEND);
            	gl.glDisable(GL10.GL_ALPHA_TEST );

            	gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);          
            	gl.glVertexPointer(3, GL10.GL_FLOAT, 0, linesBuffer);	
            	
                for (int i = 0; i < numTargets; i++)
                {
                    gl.glPushMatrix(); // copies the current matrix and add the copy to the top of the stack
                    gl.glTranslatef(targetPositions[i].x, targetPositions[i].y, targetPositions[i].z);
                    gl.glScalef(targetSizes[i].x, targetSizes[i].y, targetSizes[i].z );

                    gl.glDrawArrays(GL10.GL_LINES, 0, linesVertices.length / 3);

                    gl.glPopMatrix();
                }
                
                gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);	
            }



            if (showTargets)//draw targets
            {
            	gl.glEnable(GL10.GL_TEXTURE_2D);
            	
            	////////////////// VBO ///////////////////////////////
            	gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
            	gl.glVertexPointer(3, GL10.GL_FLOAT, 0, targetsBuffer);	
            	
	            gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);	                                  
	            gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, targetsTexBuffer);
	            
	            
	            /////////////////////////////////////////////////////
	            gl.glBindTexture(GL10.GL_TEXTURE_2D, textureIDs[0]);
                gl.glEnable(GL10.GL_COLOR_MATERIAL);

                //Render States   

                ///////////////////////////////////////////////////////////////////////////////
                // ENABLE_TRANSPARENCY (mask + clip)
                ///////////////////////////////////////////////////////////////////////////////                
                gl.glEnable(GL10.GL_BLEND);
                gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA);

                gl.glEnable(GL10.GL_ALPHA_TEST);
                gl.glAlphaFunc(GL10.GL_GREATER, 0.5f);                 
                ///////////////////////////////////////////////////////////////////////////////

                ///////////////////////////////////////////////////////////////////////////////
                // I think this part is unnecessary as these are the default values
                ///////////////////////////////////////////////////////////////////////////////
                //Color blending ops (these are the default values as it is)
                float[] color4 = {1f, 1f, 1f, 0};
                
                gl.glTexEnvf(GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_MODE, (float)GL10.GL_MODULATE);
                gl.glTexEnvfv(GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_COLOR, color4,0);

                //set the first alpha stage to texture alpha
                //gl.glTexEnvf(GL10.GL_TEXTURE_ENV, TextureEnvParameter.Operand0Alpha, (float)TextureEnvParameter.Src1Alpha);
                ///////////////////////////////////////////////////////////////////////////////
                for (int i = 0; i < numTargets; i++)
                {
                	gl.glPushMatrix(); // copies the current matrix and add the copy to the top of the stack
                	gl.glTranslatef(targetPositions[i].x, targetPositions[i].y, targetPositions[i].z);
                	gl.glScalef(targetSizes[i].x, targetSizes[i].y, targetSizes[i].z );

                	gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 0, targetVertices.length / 3);
                    
                    gl.glPopMatrix();                    
                }

                gl.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
            	gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);		            
	            
                gl.glDisable(GL10.GL_TEXTURE_2D);
                gl.glDisable(GL10.GL_COLOR_MATERIAL);
            }



            if (showBackground)
            {
                gl.glPushMatrix();
                gl.glScalef(3, 2, 3);

                gl.glEnable(GL10.GL_TEXTURE_2D);
                gl.glBindTexture(GL10.GL_TEXTURE_2D, textureIDs[1]);

            	gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
            	gl.glVertexPointer(3, GL10.GL_FLOAT, 0, backgroundBuffer);	
            	
	            gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);	                                  
	            gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, backgroundTexBuffer);
	            
                gl.glDisable(GL10.GL_FOG);

                //Render States
                gl.glDisable(GL10.GL_BLEND);
            	gl.glDisable(GL10.GL_ALPHA_TEST );

                float[] color4 = {1f, 1f, 1f, 0};
                
                gl.glTexEnvf(GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_MODE, (float)GL10.GL_MODULATE);
                gl.glTexEnvfv(GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_COLOR, color4,0);

                gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 0, backgroundVertices.length / 3);

                gl.glPopMatrix();

                gl.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
            	gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);	
            	
                gl.glDisable(GL10.GL_TEXTURE_2D);
            }

            if (showHelp)
              RenderText(gl);  
            
            /*
			newFrameLock.waitUntilTrue(1000000);
			newFrameLock.setValue(false);
			bindCameraTexture(gl);
			gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 0, 4);
			
			gl.glRotatef(1,0,0,1); //Rotate the camera image
         	*/

		//	egl.eglSwapBuffers(dpy, surface);
			
           // if (egl.eglGetError() == EGL10.EGL_CONTEXT_LOST) {
              //  Context c = getContext();
           //     if (c instanceof Activity) {
            //        ((Activity)c).finish();
           //     }
           // }
	}

    void RenderText(GL10 gl)
    {
        gl.glDisable(GL10.GL_DEPTH_TEST);
        gl.glEnable(GL10.GL_TEXTURE_2D);
        
        textLineDepth = 700;
        fnt.SetCursor(2, textLineDepth);
        fnt.SetPolyColor(1.0f, 1.0f, 1.0f);
        
        // Output statistics
        DrawTextLine(gl, "Stats---------------");

        /*
        frameCount++;
        if (frameCount == 100)
        {
            frameRate = 100 * 1000.0f / (Environment.TickCount - lastFrameTick);
            lastFrameTick = Environment.TickCount;
            frameCount = 0;
        }

        DrawTextLine(gl, "Avg Framerate: " + frameRate);

        if (headTracker != null)
        {
            try
            {
                if (headTracker.faceDetected.Length == 1)
                {
                    // Features computed on a different coordinate system are shifted to their original location
                    for (int i = 0; i < headTracker.ActualFeature[0].Length; i++)
                    {
                        DrawTextLine(gl, "Face features detected: Feature [" + i + "] X = " + headTracker.ActualFeature[0][i].X + ", Y = " + headTracker.ActualFeature[0][i].Y);
                    }
                }
            }
            catch (Exception Ex)
            {

            }
        }
         */
        
        //DrawTextLine("Last Key Pressed: " + lastKey);
        //  DrawTextLine("Mouse X-Y: " + firstP.X + ", " + firstP.Y);
        DrawTextLine(gl, "Est Head X-Y (mm): " + headX * screenHeightinMM + ", " + headY * screenHeightinMM);
        DrawTextLine(gl, "Est Head Dist (mm): " + headDist * screenHeightinMM);
        DrawTextLine(gl, "Camera Vert Angle (rad): " + cameraVerticaleAngle);
        if (cameraIsAboveScreen)
            DrawTextLine(gl, "Camera Position: Above Screen");
        else
            DrawTextLine(gl, "Camera Position: Below Screen");

        if (showWebcamPreview)
            DrawTextLine(gl, "Webcam preview: ON");
        else
            DrawTextLine(gl, "Webcam preview: OFF");

        
        DrawTextLine(gl, "Screen Height (mm) : " + screenHeightinMM);
        DrawTextLine(gl, "IR Dot Width (mm) : " + dotDistanceInMM);
        DrawTextLine(gl, "");

        DrawTextLine(gl, "Controls -----");
        DrawTextLine(gl, "Space - calibrate camera angle/center view");
        DrawTextLine(gl, "R - Reposition the targets");
        DrawTextLine(gl, "C - Toggle camera position above/below screen");
        DrawTextLine(gl, "A/Z - Zoom in/out");
        DrawTextLine(gl, "Arrow Keys - Change perspective");
        DrawTextLine(gl, "esc - Quit");
        DrawTextLine(gl, "");

        DrawTextLine(gl, "Show--------");
        DrawTextLine(gl, "T - Targets");
        DrawTextLine(gl, "L - Lines");
        DrawTextLine(gl, "B - Background");
        DrawTextLine(gl, "G - Grid");
        DrawTextLine(gl, "W - Webcam Preview");
        DrawTextLine(gl, "H - Help Text");
        DrawTextLine(gl, "");
    }
    
    private void DrawTextLine(GL10 gl, String text)
    {
        fnt.Print(gl, text);

        textLineDepth -= 16;
        
        fnt.SetCursor(2,textLineDepth);
    }
    
    void EnableFog(GL10 gl)
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
    

	public void CreateBackgroundGeometry()
    {		 
		 backgroundVertices = new float[3*(backgroundStepCount+1)]; 		
		 backgroundTexVertices = new float[2*(backgroundStepCount+1)];
		
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
           	
           	bgTexIndex += 2;

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
           	
           	bgTexIndex += 2;
           }
       }
       
		 backgroundBuffer = makeFloatBuffer(backgroundVertices);
		 backgroundTexBuffer = makeFloatBuffer(backgroundTexVertices);	

    }

    public void CreateWebcamGeometry()
    {
		webcamBuffer = makeFloatBuffer(targetVertices);
		webcamTexBuffer = makeFloatBuffer(targetVerticesTexCoord);	
    }


    public void CreateTargetGeometry()
    {
		targetsBuffer = makeFloatBuffer(targetVertices);
		targetsTexBuffer = makeFloatBuffer(targetVerticesTexCoord);	
		
		linesVertices[5] = lineDepth;		
        linesBuffer = makeFloatBuffer(linesVertices);	

        // GL.Color4(new Color4((byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF));

    }

    // Builds a grid wall which we can later scale, rotate and transform when rendering
    // (I could have used display lists as in the 
    void CreateGridGeometry()
    {
   	 gridVertices = new float[(numGridlines * 13)+2];
   	 
   	 int step = m_dwWidth / numGridlines;
   	 
   	 // GL.Color4(new Color4((byte)0xCC, (byte)0xCC, (byte)0xCC, (byte)0xCC));
   	 
   	 int index = 0;
   	 
        for (int i = 0; i <= numGridlines * 2; i += 2)
        {
       	 gridVertices[index] = (i * step / 2.0f) / m_dwWidth;
       	 gridVertices[index+1] = 0.0f;
       	 gridVertices[index+2] = 0.0f;        	 
       	 gridVertices[index+3] = (i * step / 2.0f) / m_dwWidth;
       	 gridVertices[index+4] = 1.0f;
       	 gridVertices[index+5] = 0.0f;
       	 
       	 index += 6;
        }

        for (int i = 0; i <= numGridlines * 2; i += 2)
        {
       	 gridVertices[index] = 0.0f;
       	 gridVertices[index+1] = (i * step / 2.0f) / m_dwWidth;
       	 gridVertices[index+2] = 0.0f;        	 
       	 gridVertices[index+3] = 1.0f;
       	 gridVertices[index+4] = (i * step / 2.0f) / m_dwWidth;
       	 gridVertices[index+5] = 0.0f;
       	 
       	 index += 6;
        }
        
   	 gridBuffer = makeFloatBuffer(gridVertices);				
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

	


	@Override
	public void onSurfaceChanged(GL10 gl, int width, int height) {
		// TODO Auto-generated method stub
		 m_dwWidth = width;
         m_dwHeight = height;

         screenAspect = m_dwWidth / (float)m_dwHeight;

         SetupMatrices(gl);
	}

	@Override
	public void onSurfaceCreated(GL10 gl, EGLConfig config) {
		// TODO Auto-generated method stub
		init(gl);
	}	
}
