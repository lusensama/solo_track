package org.droidplanner.android.fragments.widget.video

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import com.o3dr.android.client.apis.ControlApi
import com.o3dr.android.client.apis.GimbalApi
import com.o3dr.android.client.apis.solo.SoloCameraApi
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent
import com.o3dr.services.android.lib.drone.attribute.AttributeType
import com.o3dr.services.android.lib.drone.companion.solo.SoloAttributes
import com.o3dr.services.android.lib.drone.companion.solo.SoloEvents
import com.o3dr.services.android.lib.drone.companion.solo.tlv.SoloGoproConstants
import com.o3dr.services.android.lib.drone.companion.solo.tlv.SoloGoproState
import com.o3dr.services.android.lib.drone.property.Attitude
import com.o3dr.services.android.lib.model.AbstractCommandListener
import org.droidplanner.android.R
import org.droidplanner.android.dialogs.LoadingDialog
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.HOGDescriptor
import timber.log.Timber
import java.lang.System.loadLibrary

/**
 * Created by Fredia Huya-Kouadio on 7/19/15.
 */
public class FullWidgetSoloLinkVideo : BaseVideoWidget() {



    private var counter = 0
    private var ALTITUDE = 1.8

    companion object {
        @JvmStatic public var Moving = false
        private val filter = initFilter()

        @JvmStatic protected val TAG = FullWidgetSoloLinkVideo::class.java.simpleName

        private fun initFilter(): IntentFilter {
            val temp = IntentFilter()
            temp.addAction(AttributeEvent.STATE_CONNECTED)
            temp.addAction(SoloEvents.SOLO_GOPRO_STATE_UPDATED)
            return temp
        }
    }

    private val handler = Handler()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                AttributeEvent.STATE_CONNECTED -> {
                    tryStreamingVideo()
                    onGoproStateUpdate()
                }

                SoloEvents.SOLO_GOPRO_STATE_UPDATED -> {
                    onGoproStateUpdate()
                }
            }
        }

    }

    private val resetGimbalControl = object: Runnable {

        override fun run() {
            if (drone != null) {
                GimbalApi.getApi(drone).stopGimbalControl(orientationListener)
            }
            handler.removeCallbacks(this)
        }
    }

    private var fpvLoader: LoadingDialog? = null

    private var surfaceRef: Surface? = null

    private var toggle: Boolean? = true

    private val textureView by lazy(LazyThreadSafetyMode.NONE) {
        view?.findViewById(R.id.sololink_video_view) as TextureView?
    }
//    private val imageView by lazy(LazyThreadSafetyMode.NONE) {
//        view?.findViewById(R.id.sololink_video_image) as ImageView?
//    }

    private val videoStatus by lazy(LazyThreadSafetyMode.NONE) {
        view?.findViewById(R.id.sololink_video_status) as TextView?
    }

    private val widgetButtonBar by lazy(LazyThreadSafetyMode.NONE) {
        view?.findViewById(R.id.widget_button_bar)
    }

    private val takePhotoButton by lazy(LazyThreadSafetyMode.NONE) {
        view?.findViewById(R.id.sololink_take_picture_button)
    }

    private val recordVideo by lazy(LazyThreadSafetyMode.NONE) {
        view?.findViewById(R.id.sololink_record_video_button)
    }

    private val fpvVideo by lazy(LazyThreadSafetyMode.NONE) {
        view?.findViewById(R.id.sololink_vr_video_button)
    }

    private val touchCircleImage by lazy(LazyThreadSafetyMode.NONE) {
        view?.findViewById(R.id.sololink_gimbal_joystick)
    }

    private val orientationListener = object : GimbalApi.GimbalOrientationListener {
        override fun onGimbalOrientationUpdate(orientation: GimbalApi.GimbalOrientation) {
        }

        override fun onGimbalOrientationCommandError(code: Int) {
            Timber.e("command failed with error code: %d", code)
        }
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        loadLibrary("opencv_java3");
        return inflater?.inflate(R.layout.fragment_widget_sololink_video, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        textureView?.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
                adjustAspectRatio(textureView as TextureView);
                surfaceRef = Surface(surface)
                tryStreamingVideo()
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
                surfaceRef = null
                tryStoppingVideoStream()
                return true
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
            }
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {

                if (toggle == true && counter==5) {
                    var bmp: Bitmap? = textureView?.getBitmap()

                    Timber.d("Height is :" + bmp?.height.toString())
                    Timber.d("Width is :" + bmp?.width.toString())

                    val cvimg = Mat()
                    Utils.bitmapToMat(bmp, cvimg)
                    val coords = get_person(cvimg)
                    var center: Point
//                    center = Point(coords[0], arr[0].y + arr[0].height * 0.5)
//                    Imgproc.ellipse(img1ch, center, Size(arr[0].width * 0.5, arr[0].height * 0.5), 0.0, 0.0, 360.0, Scalar(255.0, 0.0, 255.0), 4, 8, 0)
                    Imgproc.circle(cvimg, Point(coords[0].toDouble(),coords[1].toDouble()), 20, Scalar(255.0, 0.0, 255.0), 4, 8, 0)
                    moveLogic(coords[0], coords[1], bmp?.width, bmp?.height)
                    drawCircle(view, coords[0], coords[1])  // x y swapped
//                    val displacementArray: FloatArray? = detectAndDisplay(cvimg);
//                    val displacementX: Float? = displacementArray?.get(0)
//                    val displacementY: Float? = displacementArray?.get(1)
                    counter = 0
                }
                else{
                    counter++
                }
            }
        }

        takePhotoButton?.setOnClickListener {
            Timber.d("Taking photo.. cheeze!")
            val drone = drone
            if (drone != null) {
                //TODO: fix when camera control support is stable on sololink
                SoloCameraApi.getApi(drone).takePhoto(null)
            }
        }

        recordVideo?.setOnClickListener {
            Timber.d("Recording video!")
            val drone = drone
            if (drone != null) {
                //TODO: fix when camera control support is stable on sololink
                SoloCameraApi.getApi(drone).toggleVideoRecording(null)
            }
        }

        fpvVideo?.setOnClickListener {
            launchFpvApp()
        }
    }

    private fun launchFpvApp() {
        val appId = "meavydev.DronePro"

        //Check if the dronepro app is installed.
        val activity = activity ?: return
        val pm = activity.getPackageManager()
        var launchIntent: Intent? = pm.getLaunchIntentForPackage(appId)
        if (launchIntent == null) {

            //Search for the dronepro app in the play store
            launchIntent = Intent(Intent.ACTION_VIEW).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).setData(Uri.parse("market://details?id=" + appId))

            if (pm.resolveActivity(launchIntent, PackageManager.MATCH_DEFAULT_ONLY) == null) {
                launchIntent = Intent(Intent.ACTION_VIEW).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).setData(Uri.parse("https://play.google.com/store/apps/details?id=" + appId))
            }

            startActivity(launchIntent)

        } else {
            if(fpvLoader == null) {
                launchIntent.putExtra("meavydev.DronePro.launchFPV", "Tower")

                fpvLoader = LoadingDialog.newInstance("Starting FPV...", object : LoadingDialog.Listener {
                    override fun onStarted() {
                        handler.postDelayed( {startActivity(launchIntent) }, 500L)
                    }

                    override fun onCancel() {
                        fpvLoader = null
                    }

                    override fun onDismiss() {
                        fpvLoader = null
                    }

                });
                fpvLoader?.show(childFragmentManager, "FPV launch dialog")
            }
        }
    }

    override fun onApiConnected() {
        tryStreamingVideo()
        onGoproStateUpdate()
        broadcastManager.registerReceiver(receiver, filter)
    }

    override fun onResume() {
        super.onResume()
        tryStreamingVideo()
    }

    override fun onPause() {
        super.onPause()
        tryStoppingVideoStream()
    }

    override fun onStop(){
        super.onStop()
        fpvLoader?.dismiss()
        fpvLoader = null
    }

    override fun onApiDisconnected() {
        tryStoppingVideoStream()
        onGoproStateUpdate()
        broadcastManager.unregisterReceiver(receiver)
    }

    private fun tryStreamingVideo() {
        if (surfaceRef == null)
            return

        val drone = drone
        videoStatus?.visibility = View.GONE

        startVideoStream(surfaceRef!!, TAG, object : AbstractCommandListener() {
            override fun onError(error: Int) {
                Timber.d("Unable to start video stream: %d", error)
                GimbalApi.getApi(drone).stopGimbalControl(orientationListener)
                textureView?.setOnTouchListener(null)
                videoStatus?.visibility = View.VISIBLE
            }

            override fun onSuccess() {
                videoStatus?.visibility = View.GONE
                Timber.d("Video stream started successfully")

                val gimbalTracker = object : View.OnTouchListener {
                    var startX: Float = 0f
                    var startY: Float = 0f

                    override fun onTouch(view: View, event: MotionEvent): Boolean {
                        return moveCopter(view, event)
                    }

                    private fun yawRotateTo(view: View, event: MotionEvent): Double {
                        val drone = drone ?: return -1.0

                        val attitude = drone.getAttribute<Attitude>(AttributeType.ATTITUDE)
                        var currYaw = attitude.getYaw()

                        //yaw value is between -180 and 180. Convert so the value is between 0 to 360
                        if (currYaw < 0) {
                            currYaw += 360.0
                        }

                        val degreeIntervals = (360f / view.width).toDouble()
                        val rotateDeg = (degreeIntervals * (event.x - startX)).toFloat()
                        var rotateTo = currYaw.toFloat() + rotateDeg

                        //Ensure value stays in range between 0 and 360
                        rotateTo = (rotateTo + 360) % 360
                        return rotateTo.toDouble()
                    }

                    private fun moveCopter(view: View, event: MotionEvent): Boolean {
                        val xTouch = event.x
                        val yTouch = event.y

                        val touchWidth = touchCircleImage?.width ?: 0
                        val touchHeight = touchCircleImage?.height ?: 0
                        val centerTouchX = (touchWidth / 2f).toFloat()
                        val centerTouchY = (touchHeight / 2f).toFloat()

                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                handler.removeCallbacks(resetGimbalControl)
                                GimbalApi.getApi(drone).startGimbalControl(orientationListener)

                                touchCircleImage?.setVisibility(View.VISIBLE)
                                touchCircleImage?.setX(xTouch - centerTouchX)
                                touchCircleImage?.setY(yTouch - centerTouchY)
                                startX = event.x
                                startY = event.y
                                return true
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val yawRotateTo = yawRotateTo(view, event).toFloat()
                                sendYawAndPitch(view, event, yawRotateTo)
                                touchCircleImage?.setVisibility(View.VISIBLE)
                                touchCircleImage?.setX(xTouch - centerTouchX)
                                touchCircleImage?.setY(yTouch - centerTouchY)
                                return true
                            }
                            MotionEvent.ACTION_UP -> {
                                touchCircleImage?.setVisibility(View.GONE)
                                handler.postDelayed(resetGimbalControl, 3500L)
                            }
                        }
                        return false
                    }

                    private fun sendYawAndPitch(view: View, event: MotionEvent, rotateTo: Float) {
                        val orientation = GimbalApi.getApi(drone).getGimbalOrientation()

                        val degreeIntervals = 90f / view.height
                        val pitchDegree = (degreeIntervals * (startY - event.y)).toFloat()
                        val pitchTo = orientation.getPitch() + pitchDegree

                        Timber.d("Pitch %f roll %f yaw %f", orientation.getPitch(), orientation.getRoll(), rotateTo)
                        Timber.d("degreeIntervals: %f pitchDegree: %f, pitchTo: %f", degreeIntervals, pitchDegree, pitchTo)

                        GimbalApi.getApi(drone).updateGimbalOrientation(pitchTo, orientation.getRoll(), rotateTo, orientationListener)
                    }
                }

                textureView?.setOnTouchListener(gimbalTracker)
            }

            override fun onTimeout() {
                Timber.d("Timed out while trying to start the video stream")
                GimbalApi.getApi(drone).stopGimbalControl(orientationListener)
                textureView?.setOnTouchListener(null)
                videoStatus?.visibility = View.VISIBLE
            }

        })
    }

    private fun tryStoppingVideoStream() {
        val drone = drone

        stopVideoStream(TAG, object : AbstractCommandListener() {
            override fun onError(error: Int) {
                Timber.d("Unable to stop video stream: %d", error)
            }

            override fun onSuccess() {
                Timber.d("Video streaming stopped successfully.")
                GimbalApi.getApi(drone).stopGimbalControl(orientationListener)
            }

            override fun onTimeout() {
                Timber.d("Timed out while stopping video stream.")
            }

        })
    }

    private fun onGoproStateUpdate() {
        val goproState: SoloGoproState? = drone?.getAttribute(SoloAttributes.SOLO_GOPRO_STATE)
        if (goproState == null) {
            widgetButtonBar?.visibility = View.GONE
        } else {
            widgetButtonBar?.visibility = View.VISIBLE

            //Update the video recording button
            recordVideo?.isActivated = goproState.captureMode == SoloGoproConstants.CAPTURE_MODE_VIDEO
                    && goproState.recording == SoloGoproConstants.RECORDING_ON
        }
    }

    private fun adjustAspectRatio(textureView: TextureView) {
        val viewWidth = textureView.width
        val viewHeight = textureView.height
        val aspectRatio: Float = 9f / 16f

        val newWidth: Int
        val newHeight: Int
        if (viewHeight > (viewWidth * aspectRatio)) {
            //limited by narrow width; restrict height
            newWidth = viewWidth
            newHeight = (viewWidth * aspectRatio).toInt()
        } else {
            //limited by short height; restrict width
            newWidth = (viewHeight / aspectRatio).toInt();
            newHeight = viewHeight
        }

        val xoff = (viewWidth - newWidth) / 2f
        val yoff = (viewHeight - newHeight) / 2f

        val txform = Matrix();
        textureView.getTransform(txform);
        txform.setScale((newWidth.toFloat() / viewWidth), newHeight.toFloat() / viewHeight);

        txform.postTranslate(xoff, yoff);
        textureView.setTransform(txform);
    }

    // Move logic
    private fun drawCircle(view:View, x:Int, y:Int) {
        val touchWidth = touchCircleImage?.width ?: 0
        val touchHeight = touchCircleImage?.height ?: 0
        val centerTouchX = (touchWidth / 2f).toFloat()
        val centerTouchY = (touchHeight / 2f).toFloat()
        touchCircleImage?.setVisibility(View.VISIBLE)
        touchCircleImage?.setX(x - centerTouchX)
        touchCircleImage?.setY(y - centerTouchY)
        if(x==0 && y ==0) touchCircleImage?.setVisibility(View.GONE)
    }
    private fun stop() {
        Moving = false
        ControlApi.getApi(drone).manualControl(0f, 0.toFloat(), 0f, null)
    }

    private fun runLeft() {
        Moving = true
        ControlApi.getApi(drone).manualControl(0f, (-0.4).toFloat(), 0f, null)
    }

    private fun runRight() {
        Moving = true
        ControlApi.getApi(drone).manualControl(0f, 0.4.toFloat(), 0f, null)
    }

    private fun walkLeft() {
        Moving = true
        ControlApi.getApi(drone).manualControl(0f, (-0.2).toFloat(), 0f, null)
    }

    private fun walkRight() {
        Moving = true
        ControlApi.getApi(drone).manualControl(0f, 0.2.toFloat(), 0f, null)
    }

    private fun climbUp() {
        ALTITUDE += 0.1
        ControlApi.getApi(drone).climbTo(ALTITUDE)
    }

    private fun moveDown() {
        ALTITUDE -= 0.1
        ControlApi.getApi(drone).climbTo(ALTITUDE)
    }
    fun moveLogic(coordX: Int, coordY: Int, frameX: Int?, frameY: Int?) {
        Timber.d("move logic is called with x= %d, y=%d\n",coordX, coordY)
        val partition = (frameX!! / 9)?.toDouble()
        val stopRegion1 = 3 * partition
        val stopRegion2 = 6 * partition
        val runRegion1 = 2 * partition
        val runRegion2 = 7 * partition

        val partHeight = (frameY!! / 5).toDouble()
        val cameraTooLow = 4 * partHeight

        // horizontal movement
        if(Moving==true){
            if (coordX <= runRegion1) {
                Timber.d("RUuuuuuuuuuuuuuuuuuun left\n")
                runLeft()
            } else if (coordX >= runRegion2) {
                Timber.d("RUuuuuuuuuuuuuuuuuuun right\n")
                runRight()
            } else if (coordX <= stopRegion2 && coordX >= stopRegion1) {
                if (!Moving) {
                    Timber.d("Stoooooooooooop\n")
                    stop()
                }
            } else if (coordX > runRegion1) {
                Timber.d("Waaaaaaaaaaaaaaaaalk left\n")
                walkLeft()
            } else if (coordX < runRegion2) {
                Timber.d("Waaaaaaaaaaaaaaaaalk right\n")
                walkRight()
            }

            //vertical movements, remove if drone behaves abnormally
            if (coordY < partHeight) {
                Timber.d("UUUUUUUUUUUUUUUUUUUUUp\n")
                moveDown()
            } else if (coordY < cameraTooLow) {
                Timber.d("Doooooooooooooown\n")
                climbUp()
            }
        }

    }
    fun setMovingStatus(move:Boolean){
        Moving = move
    }
    fun get_person(img: Mat): IntArray
    {
        val hog =  HOGDescriptor()
        val img1ch = Mat(img.height(),img.width(), CvType.CV_8U)
        img.convertTo(img1ch, CvType.CV_8U)
        hog.setSVMDetector(HOGDescriptor.getDefaultPeopleDetector())
        val found = MatOfRect()
        val matOfDouble = MatOfDouble()
        val found_filtered: MatOfRect
        Imgproc.cvtColor(img1ch, img1ch, Imgproc.COLOR_BGR2GRAY)
        // equalize the frame histogram to improve the result
        Imgproc.equalizeHist(img1ch, img1ch)
//        Timber.d("Image channel is " + img1ch.channels())
//        Timber.d("Image type is " + img1ch.type())

        hog.detectMultiScale(img1ch, found, matOfDouble, 0.0,
                Size(8.0, 8.0), Size(16.0, 16.0), 1.04, 2.0, false)
        val arr = found.toArray()

        if(arr.size > 0) {
            Timber.d("number of people detected is "+ arr.size.toString())
            var guess_position = 1
            if (arr.size == 1){
                guess_position = 0
            }
            else if  (arr.size > 1){
                guess_position = (arr.size/2).toInt()
                Timber.d("Position is "+ guess_position.toString())
            }
            val coord_x = arr[guess_position].x
            val coord_y = arr[guess_position].y
            val center_x = (arr[guess_position].width + coord_x) / 2
            val center_y = (arr[guess_position].height + coord_y) / 2
            Timber.d("xxxxcenter x is " + center_x)
            Timber.d("yyyycenter y is " + center_y)

            val x_y = intArrayOf(center_x, center_y)




            return x_y // return a pointer to r.x and r.y
        }
        val nothing = intArrayOf(img.height() / 2, img.width() / 2)
        return nothing
    }
}