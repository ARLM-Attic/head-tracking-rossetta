using System;
//using System.Linq;

using System.Windows.Forms;
using Emgu.CV;
using Emgu.CV.Structure;
using System.Drawing;
using System.Timers;

namespace OpenHeadTrack
{
    public class HeadTracker
    {
        //////////////////////////////////////////////////////////////
        // For Head Tracking
        //////////////////////////////////////////////////////////////
        private Capture _capture; // for capturing frames from the webcam
        private HaarCascade _faces;
        private HaarCascade _eyes;

        public MCvAvgComp[] faceDetected;

        public Image<Bgr, Byte> frame { get; set; }
        public Image<Gray, Byte> grayFrame { get; set; }
        public Image<Bgr, Byte> nextFrame { get; set; }
        public Image<Gray, Byte> nextGrayFrame { get; set; }
        public Image<Bgr, Byte> opticalFlowFrame { get; set; }
        public Image<Gray, Byte> opticalFlowGrayFrame { get; set; }
        public Image<Bgr, Byte> faceImage { get; set; }
        public Image<Bgr, Byte> faceNextImage { get; set; }
        public Image<Gray, Byte> faceGrayImage { get; set; }
        public Image<Gray, Byte> faceNextGrayImage { get; set; }
        public Image<Gray, Single> velx { get; set; }
        public Image<Gray, Single> vely { get; set; }
        public PointF[][] vectorField { get; set; }
        public int vectorFieldX { get; set; }
        public int vectorFieldY { get; set; }
        public Image<Gray, Byte> flow { get; set; }

        public PointF[][] ActualFeature;
        public PointF[] NextFeature;
        public Byte[] Status;
        public float[] TrackError;

        public Rectangle trackingArea;
        public PointF[] hull, nextHull;
        public PointF referenceCentroid, nextCentroid;
        public float sumVectorFieldX;
        public float sumVectorFieldY;

        public bool loaded = false;
        public bool startedTracking = false;

        //////////////////////////////////////////////////////////////

        public HeadTracker()
        {
            InitializeVideoInput();
        }

        private void InitializeVideoInput()
        {
            // Cam Index
            // passing 0 gets zeroth webcam
            try
            {
                _capture = new Capture(0);
                _faces = new HaarCascade("haarcascade_frontalface_alt_tree.xml");
                _eyes = new HaarCascade("haarcascade_mcs_eyepair_big.xml");
            }
            catch (Exception ex)
            {
                MessageBox.Show(ex.Message);
                return;
            }

            // Start face tracking algorithm
            InitializeFaceTracking();

            startedTracking = true;

        }

        public void InitializeFaceTracking()
        {
            /*
            frame = _capture.QueryFrame();

            if (frame != null)
            {
                //We convert it to grayscale
                grayFrame = frame.Convert<Gray, Byte>();
                //Equalization step
                grayFrame._EqualizeHist();

                MCvAvgComp[] facesDetected = null;
                int numOfFacesDetected = 0;

                // We assume there's only one face in the video
                try
                {
                    facesDetected = _faces.Detect(grayFrame, 1.1, 1, Emgu.CV.CvEnum.HAAR_DETECTION_TYPE.FIND_BIGGEST_OBJECT, new Size(20, 20));
                    //  MCvAvgComp[][] facesDetected = grayFrame.DetectHaarCascade(_faces, 1.1, 1, Emgu.CV.CvEnum.HAAR_DETECTION_TYPE.FIND_BIGGEST_OBJECT, new Size(20, 20));

                    numOfFacesDetected = facesDetected.Length;
                }
                catch (Exception ex)
                {
                    MessageBox.Show(ex.Message);
                }

                if (numOfFacesDetected == 1)
                {
                    MCvAvgComp face = facesDetected[0];

                    #region Luca Del Tongo Search Roi based on Face Metric Estimation --- based on empirical measuraments on a couple of photos ---  a really trivial heuristic

                    // Our Region of interest where find eyes will start with a sample estimation using face metric
                    Int32 yCoordStartSearchEyes = face.rect.Top + (face.rect.Height * 3 / 11);
                    Point startingPointSearchEyes = new Point(face.rect.X, yCoordStartSearchEyes);
                    Size searchEyesAreaSize = new Size(face.rect.Width, (face.rect.Height * 3 / 11));
                    Rectangle possibleROI_eyes = new Rectangle(startingPointSearchEyes, searchEyesAreaSize);

                    #endregion

                    int widthNav = (frame.Width / 10 * 2);
                    int heightNav = (frame.Height / 10 * 2);
                    Rectangle nav = new Rectangle(new Point(frame.Width / 2 - widthNav / 2, frame.Height / 2 - heightNav / 2), new Size(widthNav, heightNav));
                    frame.Draw(nav, new Bgr(Color.Lavender), 3);
                    Point cursor = new Point(face.rect.X + searchEyesAreaSize.Width / 2, yCoordStartSearchEyes + searchEyesAreaSize.Height / 2);



                    grayFrame.ROI = possibleROI_eyes;
                    MCvAvgComp[] eyesDetected = _eyes.Detect(grayFrame, 1.15, 3, Emgu.CV.CvEnum.HAAR_DETECTION_TYPE.DO_CANNY_PRUNING, new Size(20, 20));
                    //MCvAvgComp[][] eyesDetected = grayFrame.DetectHaarCascade(_eyes, 1.15, 3, Emgu.CV.CvEnum.HAAR_DETECTION_TYPE.DO_CANNY_PRUNING, new Size(20, 20));
                    grayFrame.ROI = Rectangle.Empty;

                    //Our keypoints will be the center of our detected eyes
                    if (eyesDetected.Length != 0)
                    {
                        //draw the face
                        frame.Draw(face.rect, new Bgr(Color.Yellow), 1);

                        foreach (MCvAvgComp eye in eyesDetected)
                        {
                            //We compute and draw the rect of our eyes
                            Rectangle eyeRect = eye.rect;
                            eyeRect.Offset(possibleROI_eyes.X, possibleROI_eyes.Y);

                            try
                            {
                                // Region of interest
                                grayFrame.ROI = eyeRect;

                                frame.Draw(eyeRect, new Bgr(Color.DarkSeaGreen), 2);
                                //We also draw our rectangles eyes estimation
                                frame.Draw(possibleROI_eyes, new Bgr(Color.DeepPink), 2);
                            }
                            catch (Exception ex)
                            {
                                MessageBox.Show(ex.Message);
                            }

                            //We check our eyes cursor to lie between our navigation area
                            if (nav.Left < cursor.X && cursor.X < (nav.Left + nav.Width) && nav.Top < cursor.Y && cursor.Y < nav.Top + nav.Height)
                            {
                                LineSegment2D CursorDraw = new LineSegment2D(cursor, new Point(cursor.X, cursor.Y + 1));
                                frame.Draw(CursorDraw, new Bgr(Color.White), 3);
                                //we compute new cursor coordinate using a simple scale based on frame width and height
                                int xCoord = (frame.Width * (cursor.X - nav.Left)) / nav.Width;
                                int yCoord = (frame.Height * (cursor.Y - nav.Top)) / nav.Height;
                                //We set our new cursor position
                                Cursor.Position = new Point(xCoord, yCoord);
                            }
                        }
                    }

                   // if (!OpenHeadTrack.isLoadingWebcamTexture)
                   // {
                   //     Image<Bgr, Byte> tempFrame = frame.Copy();
                   //     OpenHeadTrack.ReloadWebcamTexture(tempFrame);
                   // }
                        

                   // imageBoxFrame.Image = frame;
                }
             * */

            frame = _capture.QuerySmallFrame();

            if (frame != null)
            {

                //We convert it to grayscale
                grayFrame = frame.Convert<Gray, Byte>();
                // We detect a face using haar cascade classifiers, we'll work only on face area

                try
                {
                    faceDetected = _faces.Detect(grayFrame, 1.1, 1, Emgu.CV.CvEnum.HAAR_DETECTION_TYPE.DO_CANNY_PRUNING, new Size(20, 20));
                }
                catch (Exception ex)
                {
                  //  MessageBox.Show(ex.Message);
                }

               
                if (faceDetected.Length == 1)
                {
                    trackingArea = new Rectangle(faceDetected[0].rect.X, faceDetected[0].rect.Y, faceDetected[0].rect.Width, faceDetected[0].rect.Height);

                    // Here we enlarge or restrict the search features area on a smaller or larger face area
                    float scalingAreaFactor = 0.6f;
                    int trackingAreaWidth = (int)(faceDetected[0].rect.Width * scalingAreaFactor);
                    int trackingAreaHeight = (int)(faceDetected[0].rect.Height * scalingAreaFactor);
                    int leftXTrackingCoord = faceDetected[0].rect.X - (int)(((faceDetected[0].rect.X + trackingAreaWidth) - (faceDetected[0].rect.X + faceDetected[0].rect.Width)) / 2);
                    int leftYTrackingCoord = faceDetected[0].rect.Y - (int)(((faceDetected[0].rect.Y + trackingAreaHeight) - (faceDetected[0].rect.Y + faceDetected[0].rect.Height)) / 2);
                    trackingArea = new Rectangle(leftXTrackingCoord, leftYTrackingCoord, trackingAreaWidth, trackingAreaHeight);

                    // Allocating proper working images
                    faceImage = new Image<Bgr, Byte>(trackingArea.Width, trackingArea.Height);
                    faceGrayImage = new Image<Gray, Byte>(trackingArea.Width, trackingArea.Height);
                    frame.ROI = trackingArea;
                    frame.Copy(faceImage, null);
                    frame.ROI = Rectangle.Empty;
                    faceGrayImage = faceImage.Convert<Gray, Byte>();

                    // Detecting good features that will be tracked in following frames
                    ActualFeature = faceGrayImage.GoodFeaturesToTrack(400, 0.5d, 5d, 5);
                    faceGrayImage.FindCornerSubPix(ActualFeature, new Size(5, 5), new Size(-1, -1), new MCvTermCriteria(25, 1.5d));

                    // Features computed on a different coordinate system are shifted to their original location
                    for (int i = 0; i < ActualFeature[0].Length; i++)
                    {
                        ActualFeature[0][i].X += trackingArea.X;
                        ActualFeature[0][i].Y += trackingArea.Y;
                    }

                    // Computing convex hull                
                    using (MemStorage storage = new MemStorage())
                        hull = PointCollection.ConvexHull(ActualFeature[0], storage, Emgu.CV.CvEnum.ORIENTATION.CV_CLOCKWISE).ToArray();

                    referenceCentroid = FindCentroid(hull);
                }
            }
        }


        public void GetNextFrameForProcessing()
        {
            if (!startedTracking)
                return;

            // no guard needed -- we hooked into the event in Load handler
            

            // Gets next frame from camera
            try
            {
                nextFrame = _capture.QuerySmallFrame();
            }
            catch (Exception Ex)
            {
                MessageBox.Show(Ex.Message);
                return;
            }
            
            ////Uncomment this line if you want to try out dense optical flow
            //faceNextGrayImage = new Image<Gray, byte>(trackingArea.Width, trackingArea.Height);

            if (nextFrame != null && faceDetected.Length == 1)
            {
                nextGrayFrame = nextFrame.Convert<Gray, Byte>();

                ////Uncomment this lines if you want to try out dense optical flow
                //nextGrayFrame.ROI = trackingArea;
                //nextGrayFrame.Copy(faceNextGrayImage, null);
                //nextGrayFrame.ROI = Rectangle.Empty;

                opticalFlowFrame = new Image<Bgr, Byte>(frame.Width, frame.Height);
                opticalFlowGrayFrame = new Image<Gray, Byte>(frame.Width, frame.Height);
                opticalFlowFrame = nextFrame.Copy();

                ////Uncomment this line if you want to try out dense optical flow
                //ComputeDenseOpticalFlow();
                //ComputeMotionFromDenseOpticalFlow();

                //Comment this line if you want to try out dense optical flow
                ComputeSparseOpticalFlow();
                ComputeMotionFromSparseOpticalFlow();

                opticalFlowFrame.Draw(new CircleF(referenceCentroid, 1.0f), new Bgr(Color.Goldenrod), 2);
                opticalFlowFrame.Draw(new CircleF(nextCentroid, 1.0f), new Bgr(Color.Red), 2);

                try
                {
                    OpenHeadTrack.ReloadWebcamTexture(opticalFlowFrame);
                     //_frmCamera.imageBoxOpticalFlow.Image = opticalFlowFrame;
                     // _frmCamera.label1.Text = ActualFeature[0].Length.ToString();
                }
                catch (Exception ex)
                {

                }


                //Updating actual frames and features with the new ones
                frame = nextFrame;
                grayFrame = nextGrayFrame;
                faceGrayImage = faceNextGrayImage;
                faceImage = faceNextImage;
                ActualFeature[0] = NextFeature;
            }

            return;
        }
            

        void ComputeDenseOpticalFlow()
        {
            // Compute dense optical flow using Horn and Schunk algo
            velx = new Image<Gray, float>(faceGrayImage.Size);
            vely = new Image<Gray, float>(faceNextGrayImage.Size);

            OpticalFlow.HS(faceGrayImage, faceNextGrayImage, true, velx, vely, 0.1d, new MCvTermCriteria(100));

            #region Dense Optical Flow Drawing
            Size winSize = new Size(10, 10);
            vectorFieldX = (int)Math.Round((double)faceGrayImage.Width / winSize.Width);
            vectorFieldY = (int)Math.Round((double)faceGrayImage.Height / winSize.Height);
            sumVectorFieldX = 0f;
            sumVectorFieldY = 0f;
            vectorField = new PointF[vectorFieldX][];
            for (int i = 0; i < vectorFieldX; i++)
            {
                vectorField[i] = new PointF[vectorFieldY];
                for (int j = 0; j < vectorFieldY; j++)
                {
                    Gray velx_gray = velx[j * winSize.Width, i * winSize.Width];
                    float velx_float = (float)velx_gray.Intensity;
                    Gray vely_gray = vely[j * winSize.Height, i * winSize.Height];
                    float vely_float = (float)vely_gray.Intensity;
                    sumVectorFieldX += velx_float;
                    sumVectorFieldY += vely_float;
                    vectorField[i][j] = new PointF(velx_float, vely_float);

                    Cross2DF cr = new Cross2DF(
                        new PointF((i * winSize.Width) + trackingArea.X,
                                   (j * winSize.Height) + trackingArea.Y),
                                   1, 1);
                    opticalFlowFrame.Draw(cr, new Bgr(Color.Red), 1);

                    LineSegment2D ci = new LineSegment2D(
                        new Point((i * winSize.Width) + trackingArea.X,
                                  (j * winSize.Height) + trackingArea.Y),
                        new Point((int)((i * winSize.Width) + trackingArea.X + velx_float),
                                  (int)((j * winSize.Height) + trackingArea.Y + vely_float)));
                    opticalFlowFrame.Draw(ci, new Bgr(Color.Yellow), 1);

                }
            }
            #endregion
        }

        private void ComputeMotionFromDenseOpticalFlow()
        {
            // To be implemented
        }

        private void ComputeSparseOpticalFlow()
        {
            // Compute optical flow using pyramidal Lukas Kanade Method                
            OpticalFlow.PyrLK(grayFrame, nextGrayFrame, ActualFeature[0], new System.Drawing.Size(10, 10), 3, new MCvTermCriteria(20, 0.03d), out NextFeature, out Status, out TrackError);

            using (MemStorage storage = new MemStorage())
                nextHull = PointCollection.ConvexHull(ActualFeature[0], storage, Emgu.CV.CvEnum.ORIENTATION.CV_CLOCKWISE).ToArray();
            nextCentroid = FindCentroid(nextHull);

            // for (int i = 0; i < ActualFeature[0].Length; i++)
            //     DrawTrackedFeatures(i);


            //Uncomment this to draw optical flow vectors
            //DrawFlowVectors(i);                
        }

        private void ComputeMotionFromSparseOpticalFlow()
        {
           // OpenHeadTrack.ParseHeadTrackingData(referenceCentroid, nextCentroid);

            /*
            float xCentroidsDifference = referenceCentroid.X - nextCentroid.X;
            float yCentroidsDifference = referenceCentroid.Y - nextCentroid.Y;

            float threshold = trackingArea.Width / 5;
            
            try
            {
                _frmCamera.label4.Text = "center";

                if (Math.Abs(xCentroidsDifference) > Math.Abs(yCentroidsDifference))
                {
                    if (xCentroidsDifference > threshold)
                    {
                        _frmCamera.label4.Text = "right";
                    }

                    if (xCentroidsDifference < -threshold)
                    {
                        _frmCamera.label4.Text = "left";
                    }
                }
                if (Math.Abs(xCentroidsDifference) < Math.Abs(yCentroidsDifference))
                {
                    if (yCentroidsDifference > threshold)
                    {
                        _frmCamera.label4.Text = "up";
                    }
                    if (yCentroidsDifference < -threshold)
                    {
                        _frmCamera.label4.Text = "down";
                    }
                }
            }
            catch (Exception Ex)
            {

            }
             * */
          
        }

        //Code adapted and improved from: http://blog.csharphelper.com/2010/01/04/find-a-polygons-centroid-in-c.aspx
        // refer to wikipedia for math formulas centroid of polygon http://en.wikipedia.org/wiki/Centroid        
        private PointF FindCentroid(PointF[] Hull)
        {
            // Add the first point at the end of the array.
            int num_points = Hull.Length;
            PointF[] pts = new PointF[num_points + 1];
            Hull.CopyTo(pts, 0);
            pts[num_points] = Hull[0];

            // Find the centroid.
            float X = 0;
            float Y = 0;
            float second_factor;
            for (int i = 0; i < num_points; i++)
            {
                second_factor = pts[i].X * pts[i + 1].Y - pts[i + 1].X * pts[i].Y;
                X += (pts[i].X + pts[i + 1].X) * second_factor;
                Y += (pts[i].Y + pts[i + 1].Y) * second_factor;
            }
            // Divide by 6 times the polygon's area.
            float polygon_area = Math.Abs(SignedPolygonArea(Hull));
            X /= (6 * polygon_area);
            Y /= (6 * polygon_area);

            // If the values are negative, the polygon is
            // oriented counterclockwise so reverse the signs.
            if (X < 0)
            {
                X = -X;
                Y = -Y;
            }
            return new PointF(X, Y);
        }

        private float SignedPolygonArea(PointF[] Hull)
        {
            int num_points = Hull.Length;
            // Get the areas.
            float area = 0;
            for (int i = 0; i < num_points; i++)
            {
                area +=
                    (Hull[(i + 1) % num_points].X - Hull[i].X) *
                    (Hull[(i + 1) % num_points].Y + Hull[i].Y) / 2;
            }
            // Return the result.
            return area;
        }

        private void DrawTrackedFeatures(int i)
        {
            opticalFlowFrame.Draw(new CircleF(new PointF(ActualFeature[0][i].X, ActualFeature[0][i].Y), 1f), new Bgr(Color.Blue), 1);
        }

        private void DrawFlowVectors(int i)
        {
            System.Drawing.Point p = new Point();
            System.Drawing.Point q = new Point();

            p.X = (int)ActualFeature[0][i].X;
            p.Y = (int)ActualFeature[0][i].Y;
            q.X = (int)NextFeature[i].X;
            q.Y = (int)NextFeature[i].Y;

            double angle;
            angle = Math.Atan2((double)p.Y - q.Y, (double)p.X - q.X);

            LineSegment2D line = new LineSegment2D(p, q);
            opticalFlowFrame.Draw(line, new Bgr(255, 0, 0), 1);

            p.X = (int)(q.X + 6 * Math.Cos(angle + Math.PI / 4));
            p.Y = (int)(q.Y + 6 * Math.Sin(angle + Math.PI / 4));
            opticalFlowFrame.Draw(new LineSegment2D(p, q), new Bgr(255, 0, 0), 1);
            p.X = (int)(q.X + 6 * Math.Cos(angle - Math.PI / 4));
            p.Y = (int)(q.Y + 6 * Math.Sin(angle - Math.PI / 4));
            opticalFlowFrame.Draw(new LineSegment2D(p, q), new Bgr(255, 0, 0), 1);
        }
    }
}
