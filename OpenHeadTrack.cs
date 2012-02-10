
using System;
using System.Drawing;
using System.Windows.Forms;

using System.Threading;
using System.IO;
using System.Diagnostics;//for reading config file

using OpenTK;
using OpenTK.Graphics;
using OpenTK.Graphics.OpenGL;
using OpenTK.Input;
using OpenHeadTrack.Text;
using System.ComponentModel;
using System.Reflection;
using System.Drawing.Imaging;
using Emgu.CV;
using Emgu.CV.Structure;

namespace OpenHeadTrack
{
    public class OpenHeadTrack : GameWindow
    {

        Vector3[] targetVertices =
		{
			new Vector3(-1.0f, 1.0f,.0f ),
			new Vector3( 1.0f, 1.0f,.0f ),
			new Vector3(-1.0f,-1.0f,.0f ),
			new Vector3( 1.0f,-1.0f,.0f ),
        };

        Vector2[] targetVerticesTexCoord =
		{
			new Vector2( 0.0f,0.0f ),
			new Vector2( 1.0f,0.0f ),
			new Vector2( 0.0f,1.0f ),
			new Vector2( 1.0f,1.0f ),
        };


        #region "Static Variables"
            //        float dotDistanceInMM = 5.75f*25.4f;
            static float dotDistanceInMM = 8.5f * 25.4f;//width of the wii sensor bar
            static float screenHeightinMM = 20 * 25.4f;
            static float radiansPerPixel = (float)(Math.PI / 4) / 1024.0f; //45 degree field of view with a 1024x768 camera
            static float movementScaling = 1.0f;

            static public bool isReady = false;
            static int m_dwWidth = 1024;
            static int m_dwHeight = 768;

            //headposition
            static float headX = 0;
            static float headY = 0;
            static float headDist = 2;

            static float cameraVerticaleAngle = 0; //begins assuming the camera is point straight forward
            static float relativeVerticalAngle = 0; //current head position view angle
            static bool cameraIsAboveScreen = false;//has no affect until zeroing and then is set automatically.
        #endregion

        #region "Numerical Settings"
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
        #endregion

        #region "Configuration Variables"
            int lastKey = 0;
            bool isLoaded = false;
            bool badConfigFile = false;
            bool showGrid = true;
            bool showHelp = true; // Press H to hide
            bool showMouseCursor = false;
            bool showTargets = true;
            bool showLines = true;
            bool doFullscreen = true;
            bool mouseDown = false;
            bool showWebcamPreview = true;
        #endregion

        #region "Texture Related"
            System.Drawing.Font font = new System.Drawing.Font(FontFamily.GenericSerif, 14.0f);
            int texture = 0; // holds the texture id
            int webcamBuffer, list, linesBuffer, gridBuffer, backgroundBuffer;
            String textureFilename = "target.png";

            bool showBackground = false;
            int backgroundtexture = 0;
            String backgroundFilename = "stad_2.png";
            int backgroundStepCount = 10;

           
            static int webcamTexture = 0;     

        #endregion

        #region "Private Members"
            // Our global variables for this project
            private Assembly _assembly;
            private HeadTracker headTracker;
            private ITextPrinter printer = new TextPrinter();

            Random random = new Random();

            Vector3[] targetPositions;
            Vector3[] targetSizes;

            static public bool isLoadingWebcamTexture = false;
            static public bool isRendering = false;
            public bool isTrackingStarted = false;

            Stopwatch sw = new Stopwatch(); // available to all event handlers
            double accumulator = 0;
            int idleCounter = 0;

            int lastFrameTick = 0;
            int frameCount;
            float frameRate = 0;

            Matrix4 cameraMatrix;
            Matrix4 matProjection;

            //cube rotation
            float rotX;
            float rotY;
            float rotZ;
        #endregion

        #region "Text Rendering, Target Init, LoadTexture and Geometry Creations"

            /*
            public Vector4 Mult(Matrix4 m, Vector4 v)
            {
                
                Vector4 result = new Vector4();

                result.X = m.M11 * v.X + m.M12 * v.Y + m.M13 * v.Z + m.M14 * v.W;
                result.Y = m.M21 * v.X + m.M22 * v.Y + m.M23 * v.Z + m.M24 * v.W;
                result.Z = m.M31 * v.X + m.M32 * v.Y + m.M33 * v.Z + m.M34 * v.W;
                result.W = m.M41 * v.X + m.M42 * v.Y + m.M43 * v.Z + m.M44 * v.W;

                return result;
            }
            */

            void RenderText()
            {
                printer.Begin();

                // Output statistics
                DrawTextLine("Stats---------------");

                frameCount++;
                if (frameCount == 100)
                {
                    frameRate = 100 * 1000.0f / (Environment.TickCount - lastFrameTick);
                    lastFrameTick = Environment.TickCount;
                    frameCount = 0;
                }

                DrawTextLine("Avg Framerate: " + frameRate);

                if (headTracker != null)
                {
                    try
                    {
                        if (headTracker.faceDetected.Length == 1)
                        {
                            // Features computed on a different coordinate system are shifted to their original location
                            for (int i = 0; i < headTracker.ActualFeature[0].Length; i++)
                            {
                                DrawTextLine("Face features detected: Feature [" + i + "] X = " + headTracker.ActualFeature[0][i].X + ", Y = " + headTracker.ActualFeature[0][i].Y);
                            }
                        }
                    }
                    catch (Exception Ex)
                    {

                    }
                }

                //DrawTextLine("Last Key Pressed: " + lastKey);
                //  DrawTextLine("Mouse X-Y: " + firstP.X + ", " + firstP.Y);
                DrawTextLine("Est Head X-Y (mm): " + headX * screenHeightinMM + ", " + headY * screenHeightinMM);
                DrawTextLine("Est Head Dist (mm): " + headDist * screenHeightinMM);
                DrawTextLine("Camera Vert Angle (rad): " + cameraVerticaleAngle);
                if (cameraIsAboveScreen)
                    DrawTextLine("Camera Position: Above Screen");
                else
                    DrawTextLine("Camera Position: Below Screen");

                if (showWebcamPreview)
                    DrawTextLine("Webcam preview: ON");
                else
                    DrawTextLine("Webcam preview: OFF");

                
                DrawTextLine("Screen Height (mm) : " + screenHeightinMM);
                DrawTextLine("IR Dot Width (mm) : " + dotDistanceInMM);
                DrawTextLine("");

                DrawTextLine("Controls -----");
                DrawTextLine("Space - calibrate camera angle/center view");
                DrawTextLine("R - Reposition the targets");
                DrawTextLine("C - Toggle camera position above/below screen");
                DrawTextLine("esc - Quit");
                DrawTextLine("");

                DrawTextLine("Show--------");
                DrawTextLine("T - Targets");
                DrawTextLine("L - Lines");
                DrawTextLine("B - Background");
                DrawTextLine("M - Mouse Cursor");
                DrawTextLine("G - Grid");
                DrawTextLine("W - Webcam Preview");
                DrawTextLine("H - Help Text");
                DrawTextLine("");

                printer.End();
            }

            private void DrawTextLine(String text)
            {
                printer.Print(text, font, Color.White);
                GL.Translate(0, font.Height, 0);
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
                for (int i = 0; i < numTargets; i++)
                {
                    targetPositions[i] = new Vector3(.7f * screenAspect * (random.Next(1000) / 1000.0f - .5f),
                                                        .7f * (random.Next(1000) / 1000.0f - .5f),
                                                        startDepth - i * depthStep);
                    if (i < numInFront)//pull in the ones out in front of the display closer the center so they stay in frame
                    {
                        targetPositions[i].X *= .5f;
                        targetPositions[i].Y *= .5f;
                    }
                    targetSizes[i] = new Vector3(targetScale, targetScale, targetScale);
                }
            }

            static public void ReloadWebcamTexture(Image<Bgr, Byte> frame)
            {
                if (isRendering)
                    return;

                isLoadingWebcamTexture = true;

                int wTexture = 0;

                // We generate the texture once and then re-bind to it on each texture change
                if (webcamTexture == 0)
                {
                    //generate one texture and put its ID number into the "Texture" variable
                    GL.GenTextures(1, out wTexture);

                    //tell OpenGL that this is a 2D texture
                    GL.BindTexture(TextureTarget.Texture2D, wTexture);
                }
                else
                {


                    GL.BindTexture(TextureTarget.Texture2D, webcamTexture);
                }

                // This line is super important!!! Without it, the texture won't be drawn
                GL.TexParameter(TextureTarget.Texture2D, TextureParameterName.GenerateMipmap, (int)All.True);
                GL.TexParameter(TextureTarget.Texture2D, TextureParameterName.TextureMinFilter, (int)TextureMinFilter.LinearMipmapLinear);
                //the following code sets certian parameters for the texture
                GL.TexParameter(TextureTarget.Texture2DArray, TextureParameterName.TextureMagFilter, (int)TextureMagFilter.Linear);

                Bitmap TextureBitmap = frame.Bitmap; // maybe can do without the new Bitmap and go frame.Bitmap instead ?
               // Bitmap TextureBitmap = new Bitmap("stad_2.png");
                //get the data out of the bitmap
                System.Drawing.Imaging.BitmapData TextureData =
                TextureBitmap.LockBits(
                        new System.Drawing.Rectangle(0, 0, TextureBitmap.Width, TextureBitmap.Height),
                        System.Drawing.Imaging.ImageLockMode.ReadOnly,
                        System.Drawing.Imaging.PixelFormat.Format32bppArgb
                    );

                // GL.TexImage2D discards the old data and (possibly) allocates memory anew - you can use it to change the texture size, pixel format etc. 
                // GL.TexSubImage2D updates a region of an existing texture (that was previously allocated with GL.TexImage2D).
                if (webcamTexture == 0)
                    GL.TexImage2D(TextureTarget.Texture2D, 0, PixelInternalFormat.Rgba, TextureData.Width, TextureData.Height, 0, OpenTK.Graphics.OpenGL.PixelFormat.Bgra, PixelType.UnsignedByte, TextureData.Scan0);
                else
                    GL.TexSubImage2D(TextureTarget.Texture2D, 0, 0, 0, TextureData.Width, TextureData.Height, OpenTK.Graphics.OpenGL.PixelFormat.Bgra, PixelType.UnsignedByte, TextureData.Scan0);
                
                GL.Finish();
                //free the bitmap data (we dont need it anymore because it has been passed to the OpenGL driver
                TextureBitmap.UnlockBits(TextureData);

                if (webcamTexture == 0)
                    webcamTexture = wTexture;

                isLoadingWebcamTexture = false;
            }

            private int LoadTexture(String fileName)
            {

                //Code to get the data to the OpenGL Driver
                int lTexture;

                //generate one texture and put its ID number into the "Texture" variable
                GL.GenTextures(1, out lTexture);
                //tell OpenGL that this is a 2D texture
                GL.BindTexture(TextureTarget.Texture2D, lTexture);

                GL.TexParameter(TextureTarget.Texture2D, TextureParameterName.GenerateMipmap, (int)All.True);
                GL.TexParameter(TextureTarget.Texture2D, TextureParameterName.TextureMinFilter, (int)TextureMinFilter.LinearMipmapLinear);
                //the following code sets certian parameters for the texture
                GL.TexParameter(TextureTarget.Texture2DArray, TextureParameterName.TextureMagFilter, (int)TextureMagFilter.Linear);
                //  GL.TexParameter(TextureTarget.Texture2DArray, TextureParameterName.TextureMinFilter, (int)TextureMinFilter.Linear);

                //make a bitmap out of the file on the disk
                Bitmap TextureBitmap = new Bitmap(fileName);
                //get the data out of the bitmap
                System.Drawing.Imaging.BitmapData TextureData =
                TextureBitmap.LockBits(
                        new System.Drawing.Rectangle(0, 0, TextureBitmap.Width, TextureBitmap.Height),
                        System.Drawing.Imaging.ImageLockMode.ReadOnly,
                        System.Drawing.Imaging.PixelFormat.Format32bppArgb
                    );

                //load the data by telling OpenGL to build mipmaps out of the bitmap data
                GL.TexImage2D(TextureTarget.Texture2D, 0, PixelInternalFormat.Rgba, TextureData.Width, TextureData.Height, 0, OpenTK.Graphics.OpenGL.PixelFormat.Bgra, PixelType.UnsignedByte, TextureData.Scan0);
                GL.Finish();
                //free the bitmap data (we dont need it anymore because it has been passed to the OpenGL driver
                TextureBitmap.UnlockBits(TextureData);

                return lTexture;
            }

            public void CreateBackgroundGeometry()
            {
                backgroundBuffer = GL.GenLists(1);

                GL.NewList(backgroundBuffer, ListMode.Compile);
                {
                    GL.Begin(BeginMode.TriangleStrip);

                    float angleStep = (float)(Math.PI / backgroundStepCount);
                    for (int i = 0; i <= backgroundStepCount; i++)
                    {
                        // On even steps (0, 2, 4)
                        if (i % 2 == 0)
                        {
                            GL.TexCoord2(new Vector2(i / (float)backgroundStepCount, 1));
                            GL.Vertex3(new Vector3((float)(Math.Cos(angleStep * i)), -1, -(float)(Math.Sin(angleStep * i))));
                        }
                        else
                        {
                            // On odd steps (1,3,5)
                            GL.TexCoord2(new Vector2(i / (float)backgroundStepCount, 0));
                            GL.Vertex3(new Vector3((float)(Math.Cos(angleStep * i)), 1, -(float)(Math.Sin(angleStep * i))));
                        }
                    }

                    GL.End();
                }
                GL.EndList();
            }

            public void CreateWebcamGeometry()
            {
                webcamBuffer = GL.GenLists(1);
                CreateSquareTexturedGeometry(webcamBuffer);
            }

            private void CreateSquareTexturedGeometry(int buffer)
            {
                GL.NewList(buffer, ListMode.Compile);
                {
                    GL.Begin(BeginMode.TriangleStrip);

                    GL.TexCoord2(targetVerticesTexCoord[0]);
                    GL.Vertex3(targetVertices[0]);
                    GL.TexCoord2(targetVerticesTexCoord[1]);
                    GL.Vertex3(targetVertices[1]);
                    GL.TexCoord2(targetVerticesTexCoord[2]);
                    GL.Vertex3(targetVertices[2]);
                    GL.TexCoord2(targetVerticesTexCoord[3]);
                    GL.Vertex3(targetVertices[3]);

                    GL.End();
                }
                GL.EndList();
            }

            public void CreateTargetGeometry()
            {
                list = GL.GenLists(1);

                CreateSquareTexturedGeometry(list);

                linesBuffer = GL.GenLists(1);

                GL.NewList(linesBuffer, ListMode.Compile);
                {
                    GL.Begin(BeginMode.Lines);

                    GL.Color4(new Color4((byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF));
                    GL.Vertex3(new Vector3(0.0f, 0.0f, 0.0f));
                    GL.Vertex3(new Vector3(0.0f, 0.0f, lineDepth));

                    GL.End();
                }
                GL.EndList();
            }

            // Builds a grid wall which we can later scale, rotate and transform when rendering
            // (I could have used display lists as in the 
            void CreateGridGeometry()
            {
                gridBuffer = GL.GenLists(1);

                GL.NewList(gridBuffer, ListMode.Compile);
                {
                    GL.Begin(BeginMode.Lines);

                    int step = m_dwWidth / numGridlines;

                    GL.Color4(new Color4((byte)0xCC, (byte)0xCC, (byte)0xCC, (byte)0xCC));

                    for (int i = 0; i <= numGridlines * 2; i += 2)
                    {
                        GL.Vertex3(new Vector3((i * step / 2.0f) / m_dwWidth, 0.0f, 0.0f));
                        GL.Vertex3(new Vector3((i * step / 2.0f) / m_dwWidth, 1.0f, 0.0f));
                    }

                    for (int i = 0; i <= numGridlines * 2; i += 2)
                    {
                        GL.Vertex3(new Vector3(0.0f, (i * step / 2.0f) / m_dwWidth, 0.0f));
                        GL.Vertex3(new Vector3(1.0f, (i * step / 2.0f) / m_dwWidth, 0.0f));
                    }

                    GL.End();
                }
                GL.EndList();
            }

        #endregion

            /// <summary>Creates a 800x600 window with the specified title.</summary>
        public OpenHeadTrack()
            : base(1024, 768, GraphicsMode.Default, "Crazy 3D")
        {
            VSync = VSyncMode.On;
            _assembly = Assembly.GetExecutingAssembly();
        }

        /// <summary>Load resources here.</summary>
        /// <param name="e">Not used.</param>
        protected override void OnLoad(EventArgs e)
        {
            base.OnLoad(e);

            // Set the initial size of our form
            loadConfigurationData();

            if (screenAspect == 0)//only override if it's emtpy
                screenAspect = m_dwWidth / (float)m_dwHeight;

            // Background color is black
            GL.ClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            GL.ShadeModel(ShadingModel.Smooth);
            GL.PolygonMode(MaterialFace.FrontAndBack, PolygonMode.Fill);

            GL.Enable(EnableCap.ColorMaterial);

            GL.Enable(EnableCap.DepthTest);
            
            GL.ColorMaterial(MaterialFace.FrontAndBack, ColorMaterialParameter.AmbientAndDiffuse);
            GL.Enable(EnableCap.ColorMaterial);

            // creates array for grid used later in drawing
            CreateWebcamGeometry();
            CreateGridGeometry();
            CreateTargetGeometry();
            CreateBackgroundGeometry();

            // Randomizes locations of targets and lengths (depth)
            InitTargets();

            //check if the file exists
            if (System.IO.File.Exists(textureFilename))
            {
                texture = LoadTexture(textureFilename);
            }

            if (System.IO.File.Exists(backgroundFilename))
            {
                backgroundtexture = LoadTexture(backgroundFilename);
            }

            Keyboard.KeyUp += new EventHandler<OpenTK.Input.KeyboardKeyEventArgs>(Keyboard_KeyUp);

            // Here we can start head tracking once we loaded the scene
            isLoaded = true;

            StartHeadTracking();
        }

        public void StartHeadTracking()
        {
            isTrackingStarted = true;
            headTracker = new HeadTracker();
            headTracker.InitializeFaceTracking();

           // headTracker.GetNextFrameForProcessing();
        }

        public void loadConfigurationData()
        {
            // create reader & open file
            try
            {
                TextReader tr = new StreamReader("config.dat");
                char[] seps = { ':' };
                String line;
                String[] values;

                line = tr.ReadLine();
                values = line.Split(seps);
                screenHeightinMM = float.Parse(values[1]);

                line = tr.ReadLine();
                values = line.Split(seps);
                dotDistanceInMM = float.Parse(values[1]);

                line = tr.ReadLine();
                values = line.Split(seps);
                screenAspect = float.Parse(values[1]);

                line = tr.ReadLine();
                values = line.Split(seps);
                cameraIsAboveScreen = bool.Parse(values[1]);

                // close the stream
                tr.Close();
            }
            catch (System.NullReferenceException)
            {

            }
            catch (System.FormatException)
            {
                //bad config, ignore
                throw new Exception("Config file is mal-formatted.");

            }
            catch (System.IO.FileNotFoundException)
            {
                //no prexsting config, create one with the deafult values

                TextWriter tw = new StreamWriter("config.dat");

                // write a line of text to the file
                tw.WriteLine("screenHieght(mm):" + screenHeightinMM);
                tw.WriteLine("sensorBarWidth(mm):" + dotDistanceInMM);
                tw.WriteLine("screenAspect(width/height):" + screenAspect);
                tw.WriteLine("cameraIsAboveScreen(true/false):" + cameraIsAboveScreen);

                // close the stream
                tw.Close();

                return;
            }
        }

        /// <summary>
        /// Called when your window is resized. Set your viewport here. It is also
        /// a good place to set up your projection matrix (which probably changes
        /// along when the aspect ratio of your window).
        /// </summary>
        /// <param name="e">Not used.</param>
        protected override void OnResize(EventArgs e)
        {
            base.OnResize(e);

            m_dwWidth = ClientSize.Width;
            m_dwHeight = ClientSize.Height;

            screenAspect = m_dwWidth / (float)m_dwHeight;

            SetupMatrices();
        }

        public void Keyboard_KeyUp(object sender, OpenTK.Input.KeyboardKeyEventArgs e)
        {
            if ((Key)e.Key == Key.Escape)
            {
                isReady = false;
                Cursor.Show();//set the flag to stop the rendering call driven by incoming wiimote reports
                Exit();
                return;
            }
            else if ((Key)e.Key == Key.Space)
            {
                //zeros the head position and computes the camera tilt
                double angle = Math.Acos(.5 / headDist) - Math.PI / 2;//angle of head to screen
                if (!cameraIsAboveScreen)
                    angle = -angle;
                cameraVerticaleAngle = (float)((angle - relativeVerticalAngle));//absolute camera angle 
            }
            else if ((Key)e.Key == Key.C)
                cameraIsAboveScreen = !cameraIsAboveScreen;
            else if ((Key)e.Key == Key.B)
                showBackground = !showBackground;
            else if ((Key)e.Key == Key.W)
                showWebcamPreview = !showWebcamPreview;
            else if ((Key)e.Key == Key.G)
                showGrid = !showGrid;
            else if ((Key)e.Key == Key.R)
                InitTargets();
            else if ((Key)e.Key == Key.H)
                showHelp = !showHelp;
            else if ((Key)e.Key == Key.T)
                showTargets = !showTargets;
            else if ((Key)e.Key == Key.L)
                showLines = !showLines;
            else if ((Key)e.Key == Key.M)
                showMouseCursor = !showMouseCursor;
        }

        /// <summary>
        /// Called when it is time to setup the next frame. Add you game logic here.
        /// </summary>
        /// <param name="e">Contains timing information for framerate independent logic.</param>
        protected override void OnUpdateFrame(FrameEventArgs e)
        {
            base.OnUpdateFrame(e);

            if (Keyboard[Key.Left])
            {
                headX -= 0.01f;
            }
            else if (Keyboard[Key.Right])
            {
                headX += 0.01f;

            }
            else if (Keyboard[Key.Up])
            {
                headY -= 0.01f;

            }
            else if (Keyboard[Key.Down])
            {
                headY += 0.01f;

            }
            else if (Keyboard[Key.A])
            {
                headDist -= 0.01f;
            }
            else if (Keyboard[Key.Z])
            {
                headDist += 0.01f;
            }
        }

        /// <summary>
        /// Called when it is time to render the next frame. Add your rendering code here.
        /// </summary>
        /// <param name="e">Contains timing information.</param>
        protected override void OnRenderFrame(FrameEventArgs e)
        {
            base.OnRenderFrame(e);
            
            // Get next frame and reload the texture
            headTracker.GetNextFrameForProcessing();

            Render();
        }

        // calculate the distance between (x1,y1) and (x2,y2)
        float calc_distance(float x1, float y1, float x2, float y2)
        {
            float xdiff = x1 - x2;
            float ydiff = y1 - y2;
            float distsqr = (xdiff * xdiff) + (ydiff * ydiff);
            return (float)Math.Sqrt(distsqr);
        }

        // This is supposed to give me where the head is moving
        // But we're already doing that with HeadTracker
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

        private void SetupMatrices()
        {

            GL.Viewport(0, 0, ClientRectangle.Width, ClientRectangle.Height);

            GL.MatrixMode(MatrixMode.Modelview);

            // device.Transform.World = Matrix.Identity;
            GL.LoadIdentity();

            // Set up our view matrix. A view matrix can be defined given an eye point,
            // a point to lookat, and a direction for which way is up. Here, we set the
            // eye five units back along the z-axis and up three units, look at the
            // origin, and define "up" to be in the y-direction.
            //            device.Transform.View = Matrix.LookAtLH(new Vector3(mouseCursor.X, mouseCursor.Y, -5.0f), new Vector3(0.0f, 0.0f, 0.0f), new Vector3(0.0f, 1.0f, 0.0f));
            cameraMatrix = Matrix4.LookAt(new Vector3(headX, headY, headDist), new Vector3(headX, headY, 0), new Vector3(0.0f, 1.0f, 0.0f));

            // device.Transform.View = (because we're in ModelView mode)
            GL.LoadMatrix(ref cameraMatrix);

            // For the projection matrix, we set up a perspective transform (which
            // transforms geometry from 3D view space to 2D viewport space, with
            // a perspective divide making objects smaller in the distance). To build
            // a perpsective transform, we need the field of view (1/4 pi is common),
            // the aspect ratio, and the near and far clipping planes (which define at
            // what distances geometry should be no longer be rendered).

            //compute the near plane so that the camera stays fixed to -.5f*screenAspect, .5f*screenAspect, -.5f,.5f
            //compting a closer plane rather than simply specifying xmin,xmax,ymin,ymax allows things to float in front of the display
            GL.MatrixMode(MatrixMode.Projection);
            GL.LoadIdentity();

            float nearPlane = 0.05f;
            float farPlane = 500f; // 100f ?

            matProjection = Matrix4.CreatePerspectiveOffCenter(nearPlane * (-.5f * screenAspect - headX) / headDist,
                                                               nearPlane * (.5f * screenAspect - headX) / headDist,
                                                               nearPlane * (-.5f - headY) / headDist,
                                                               nearPlane * (.5f - headY) / headDist,
                                                               nearPlane, farPlane);
            // device.Transform.Projection = 
            GL.LoadMatrix(ref matProjection);
        }

        private void Render()
        {
            if (isRendering)
                return;

            isRendering = true;

            GL.Clear(ClearBufferMask.ColorBufferBit | ClearBufferMask.DepthBufferBit);

            // Setup the world, view, and projection matrices
            SetupMatrices();

            GL.MatrixMode(MatrixMode.Modelview);

            EnableFog();

            if (showGrid)
            {
                GL.Disable(EnableCap.Texture2D);
                GL.Disable(EnableCap.Blend);
                GL.Disable(EnableCap.AlphaTest);

                /*
                 * If you want to perform your transformations 
                 * in this order : translation -> rotation -> scale (which makes perfectly sense, it's what's wanted usually), 
                 * you have to multiply your matrices in the reverse order. (in DirectX it's left handed system and regular order multiplication)
                 * */
                //  device.Transform.World = Matrix.Translation
                // translations to the World view in DirectX are done on the GL
                // zoom out by 1/2 boxdepth, move 0.5 right and 0.5 above the floor
                GL.PushMatrix(); // copies the current matrix and add the copy to the top of the stack
                GL.Scale(new Vector3(screenAspect, 1, 1));
                GL.Translate(new Vector3(-.5f, -.5f, -1 * boxdepth / 2));
                GL.CallList(gridBuffer);
                GL.PopMatrix();
    
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
                GL.PushMatrix();               
                GL.Translate(new Vector3(.5f * screenAspect, -.5f, 0));  
                GL.Rotate(90f, new Vector3(0, 1, 0));
                GL.Scale(new Vector3(1 * boxdepth / 2, 1, 1));
                GL.CallList(gridBuffer);
                GL.PopMatrix();

                // Left
                GL.PushMatrix();
                GL.Translate(new Vector3(-.5f * screenAspect, -.5f, 0));
                GL.Rotate(90f, new Vector3(0, 1, 0));
                GL.Scale(new Vector3(1 * boxdepth / 2, 1, 1));
                GL.CallList(gridBuffer);
                GL.PopMatrix();

                // ceiling
                GL.PushMatrix();
                GL.Translate(new Vector3(-.5f * screenAspect, .5f, -1f * boxdepth / 2));  
                GL.Rotate(90f, new Vector3(1, 0, 0));
                GL.Scale(new Vector3(screenAspect, 1 * boxdepth / 2, 1));
                GL.CallList(gridBuffer);
                GL.PopMatrix();

                // floor
                GL.PushMatrix();
                GL.Translate(new Vector3(-.5f * screenAspect, -.5f, -1f * boxdepth / 2));
                GL.Rotate(90f, new Vector3(1, 0, 0));
                GL.Scale(new Vector3(screenAspect, 1 * boxdepth / 2, 1));
                GL.CallList(gridBuffer);
                GL.PopMatrix();            
            }

            if (showWebcamPreview && webcamTexture != 0)
            {
                GL.PushMatrix();
                GL.Translate(new Vector3(-.5f * screenAspect, -.5f, -.5f));
                GL.Scale(new Vector3(0.2f, 0.2f, 0.2f));

                GL.Enable(EnableCap.Texture2D);
                GL.BindTexture(TextureTarget.Texture2D, webcamTexture);

              //  GL.Disable(EnableCap.Fog);

                //Render States
                GL.Disable(EnableCap.Blend);
                GL.Disable(EnableCap.AlphaTest);

                GL.TexEnv(TextureEnvTarget.TextureEnv, TextureEnvParameter.TextureEnvMode, (float)TextureEnvMode.Modulate);
                // GL.TexEnv(TextureEnvTarget.TextureEnv, TextureEnvParameter.TextureEnvColor, new Color4(1, 1, 1, 0));

                GL.CallList(webcamBuffer);

                GL.PopMatrix();

                GL.Disable(EnableCap.Texture2D);
            }

            if (showLines)
            {
                GL.Disable(EnableCap.Texture2D);
                GL.Disable(EnableCap.Blend);
                GL.Disable(EnableCap.AlphaTest);

                for (int i = 0; i < numTargets; i++)
                {
                    GL.PushMatrix(); // copies the current matrix and add the copy to the top of the stack
                    GL.Translate(targetPositions[i]);
                    GL.Scale(targetSizes[i]);

                    // Calls on our "mini program" which draws TriangleStrips in this case
                    GL.CallList(linesBuffer);

                    GL.PopMatrix();
                }
            }



            if (showTargets)//draw targets
            {
                GL.Enable(EnableCap.Texture2D);
                GL.BindTexture(TextureTarget.Texture2D, texture);
                GL.Enable(EnableCap.ColorMaterial);

                //Render States   

                ///////////////////////////////////////////////////////////////////////////////
                // ENABLE_TRANSPARENCY (mask + clip)
                ///////////////////////////////////////////////////////////////////////////////                
                GL.Enable(EnableCap.Blend);
                GL.BlendFunc(BlendingFactorSrc.SrcAlpha, BlendingFactorDest.OneMinusSrcAlpha);

                GL.Enable(EnableCap.AlphaTest);
                GL.AlphaFunc(AlphaFunction.Greater, 0.5f);                 
                ///////////////////////////////////////////////////////////////////////////////

                ///////////////////////////////////////////////////////////////////////////////
                // I think this part is unnecessary as these are the default values
                ///////////////////////////////////////////////////////////////////////////////
                //Color blending ops (these are the default values as it is)
                GL.TexEnv(TextureEnvTarget.TextureEnv, TextureEnvParameter.TextureEnvMode, (float)TextureEnvMode.Modulate);
                GL.TexEnv(TextureEnvTarget.TextureEnv, TextureEnvParameter.TextureEnvColor, new Color4(1, 1, 1, 0));

                //set the first alpha stage to texture alpha
                //GL.TexEnv(TextureEnvTarget.TextureEnv, TextureEnvParameter.Operand0Alpha, (float)TextureEnvParameter.Src1Alpha);
                ///////////////////////////////////////////////////////////////////////////////

                for (int i = 0; i < numTargets; i++)
                {
                    GL.PushMatrix(); // copies the current matrix and add the copy to the top of the stack
                    GL.Translate(targetPositions[i]);
                    GL.Scale(targetSizes[i]);

                    // Calls on our "mini program" which draws TriangleStrips in this case
                    GL.CallList(list);
                    
                    GL.PopMatrix();                    
                }

                GL.Disable(EnableCap.ColorMaterial);
                GL.Disable(EnableCap.Texture2D);
            }



            if (showBackground)
            {
                GL.PushMatrix();
                GL.Scale(new Vector3(3, 2, 3));

                GL.Enable(EnableCap.Texture2D);
                GL.BindTexture(TextureTarget.Texture2D, backgroundtexture);

                GL.Disable(EnableCap.Fog);

                //Render States
                GL.Disable(EnableCap.Blend);
                GL.Disable(EnableCap.AlphaTest);

                GL.TexEnv(TextureEnvTarget.TextureEnv, TextureEnvParameter.TextureEnvMode, (float)TextureEnvMode.Modulate);
               // GL.TexEnv(TextureEnvTarget.TextureEnv, TextureEnvParameter.TextureEnvColor, new Color4(1, 1, 1, 0));

                GL.CallList(backgroundBuffer);

                GL.PopMatrix();

                GL.Disable(EnableCap.Texture2D);
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

            if (showHelp)
                RenderText();

            SwapBuffers();

            isReady = true;//rendering triggered by wiimote is waiting for this.

            isRendering = false;
        }

        void EnableFog()
        {
            GL.Enable(EnableCap.Fog);
            float[] color = { 0.0f, 0.0f, 0.0f, 1.0f };
            GL.Fog(FogParameter.FogMode, (float)FogMode.Linear);
            GL.Fog(FogParameter.FogColor, color);
            GL.Fog(FogParameter.FogDensity, 0.35f);
            GL.Hint(HintTarget.FogHint, HintMode.Nicest);
            GL.Fog(FogParameter.FogStart, headDist);
            GL.Fog(FogParameter.FogEnd, headDist + fogdepth);
        }

        /// <summary>
        /// The main entry point for the application.
        /// </summary>
        static void Main()
        {
            // The 'using' idiom guarantees proper resource cleanup.
            // We request 30 UpdateFrame events per second, and unlimited
            // RenderFrame events (as fast as the computer can handle).
            using (OpenHeadTrack game = new OpenHeadTrack())
            {
                game.Run(30.0);
            }

            /*
            using (OpenHeadTrack frm = new OpenHeadTrack())
            {
                if (!frm.InitializeGraphics()) // Initialize Direct3D
                {
                    MessageBox.Show("Could not initialize Direct3D.  This tutorial will exit.");
                    return;
                }
                frm.Show();

                // While the form is still valid, render and process messages
                while (frm.Created)
                {
                    Application.DoEvents();
                    if (!frm.doWiimote)
                        frm.Render();
                }
                Cursor.Show();

            }
             * */
        }
    }
}
