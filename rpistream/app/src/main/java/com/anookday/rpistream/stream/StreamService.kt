package com.anookday.rpistream.stream

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.usb.UsbManager
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaRecorder
import android.opengl.GLSurfaceView
import android.os.IBinder
import android.view.SurfaceHolder
import androidx.core.app.NotificationCompat
import com.anookday.rpistream.R
import com.anookday.rpistream.pi.CommandType
import com.anookday.rpistream.pi.PiRouter
import com.anookday.rpistream.repository.database.AudioConfig
import com.anookday.rpistream.repository.database.VideoConfig
import com.pedro.encoder.Frame
import com.pedro.encoder.audio.AudioEncoder
import com.pedro.encoder.audio.GetAacData
import com.pedro.encoder.input.audio.MicrophoneManager
import com.pedro.encoder.utils.yuv.YUVUtil
import com.pedro.encoder.video.FormatVideoEncoder
import com.pedro.encoder.video.GetVideoData
import com.pedro.encoder.video.VideoEncoder
import com.pedro.rtplibrary.view.GlInterface
import com.pedro.rtplibrary.view.OpenGlView
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import net.ossrs.rtmp.ConnectCheckerRtmp
import net.ossrs.rtmp.SrsFlvMuxer
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.IntBuffer
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

const val STREAM_SERVICE_NOTIFICATION_ID = 123456
const val STREAM_SERVICE_NAME = "RPi Streamer | Stream Service"

/**
 * Service that handles streaming video output from UVCCamera and audio output from an audio input
 * source to a designated web server.
 */
class StreamService() : Service() {
    companion object {
        private var usbManager: UsbManager? = null
        private var camera: UVCCamera? = null
        private var glInterface: GlInterface? = null
        private var surfaceHolder: SurfaceHolder? = null
        private var mainSurfaceView: StreamGLSurfaceView? = null
        private var srsFlvMuxer: SrsFlvMuxer? = null
        private var notificationManager: NotificationManager? = null
        private var streamTimer: StreamTimer? = null
        private var videoFormat: MediaFormat? = null
        private var audioFormat: MediaFormat? = null
        private var piRouter: PiRouter? = null
        private var previewWidth = 720
        private var previewHeight = 480
        var videoEnabled = false
        var audioEnabled = false
        var isStreaming = false
        var isPreview = false
        var isAeEnabled = false

        var videoBitrate: Int
            get() = videoEncoder.bitRate
            set(bitrate) = videoEncoder.setVideoBitrateOnFly(bitrate)

        /**
         * Video encoder class
         */
        private val videoEncoder: VideoEncoder = VideoEncoder(object : GetVideoData {
            override fun onVideoFormat(mediaFormat: MediaFormat) {
                videoFormat = mediaFormat
            }

            override fun onSpsPpsVps(sps: ByteBuffer, pps: ByteBuffer, vps: ByteBuffer) {
                if (isStreaming) {
                    srsFlvMuxer?.setSpsPPs(sps, pps)
                }
            }

            override fun onSpsPps(sps: ByteBuffer, pps: ByteBuffer) {
                if (isStreaming) {
                    srsFlvMuxer?.setSpsPPs(sps, pps)
                }
            }

            override fun getVideoData(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
                if (isStreaming) {
                    srsFlvMuxer?.sendVideo(h264Buffer, info)
                }
            }
        })

        /**
         * Audio encoder class
         */
        private val audioEncoder: AudioEncoder = AudioEncoder(object : GetAacData {
            override fun onAudioFormat(mediaFormat: MediaFormat?) {
                audioFormat = mediaFormat
            }

            override fun getAacData(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
                if (isStreaming) {
                    srsFlvMuxer?.sendAudio(aacBuffer, info)
                }
            }
        })

        /**
         * Microphone manager class
         */
        private val microphoneManager: MicrophoneManager =
            MicrophoneManager { frame -> audioEncoder.inputPCMData(frame) }

        fun init(piCameraView: OpenGlView, glSurfaceView: StreamGLSurfaceView, connectChecker: ConnectCheckerRtmp) {
            val newCamera = UVCCamera()
            camera = newCamera
            glInterface = piCameraView
            surfaceHolder = piCameraView.holder
            mainSurfaceView = glSurfaceView
            srsFlvMuxer = SrsFlvMuxer(connectChecker)
            piRouter = PiRouter(piCameraView.context)
            piCameraView.init()
        }

        fun stream(buffer: IntBuffer, width: Int, height: Int) {
            val yuvBuf = YUVUtil.ARGBtoYUV420SemiPlanar(buffer.array(), width, height)
            videoEncoder.inputYUVData(Frame(yuvBuf, 0, false, ImageFormat.YUV_420_888))
        }

        /**
         * Start camera preview if preview is currently disabled.
         *
         * @param width         Width of preview frame in px.
         * @param height        Height of preview frame in px.
         */
        fun startPreview(width: Int?, height: Int?) {
            if (width != null) previewWidth = width
            if (height != null) previewHeight = height
            camera?.let {
                mainSurfaceView?.renderer?.startPiCameraPreview(it, previewWidth, previewHeight)
            }
            isPreview = true
            Timber.v("RPISTREAM preview enabled")
        }

        /**
         * Stop camera preview if preview is currently enabled.
         */
        fun stopPreview() {
            mainSurfaceView?.renderer?.stopPiCameraPreview()
            isPreview = false
            Timber.v("RPISTREAM preview disabled")
        }

        /**
         * Enable video input for streaming.
         */
        fun enableCamera(ctrlBlock: USBMonitor.UsbControlBlock?, config: VideoConfig?): String? {
            val numSkipFrames = 2
            var currentFrame = 0
            var currentExposure = 500

            camera = UVCCamera()

            camera?.let {
                it.open(ctrlBlock)
                if (config != null) {
                    it.setPreviewSize(config.width, config.height, UVCCamera.FRAME_FORMAT_MJPEG)
                    startPreview(config.width, config.height)
                    videoEnabled = true
                }
                it.setFrameCallback(
                    { frame ->
                        frame.rewind()
                        val byteArray = ByteArray(frame.remaining())
                        frame.get(byteArray)
                        // process image
                        if (isAeEnabled) {
                            if (currentFrame >= numSkipFrames) {
                                config?.let {
                                    currentExposure = processImage(byteArray, it, currentExposure)
                                }
                                currentFrame = 0
                            }
                            currentFrame++
                        }
                        // stream video
                        if (isStreaming) {
                            //videoEncoder.inputYUVData(Frame(byteArray, 0, false, ImageFormat.YUV_420_888))
                        }
                    },
                    UVCCamera.PIXEL_FORMAT_YUV420SP
                )
            }

            return camera?.deviceName
        }

        @OptIn(ExperimentalTime::class)
        fun processImage(byteArray: ByteArray, config: VideoConfig, currentExposure: Int): Int {
            val lumLimit = 255
            val maxLum = 194
            val minLum = 61
            val targetLum = minLum + (maxLum - minLum) / 2
            val exposureAbsoluteMinimum = 15
            val exposureAbsoluteLimit = 1000
            val alpha = 0.25f
            val scale = exposureAbsoluteLimit / lumLimit
            var exposure = currentExposure

            val (lum, duration) = measureTimedValue {
                getAverageLuminanceOfCenterBox(byteArray, config.width, config.height)
                //getAvgLumWithNd4j(byteArray, config.width, config.height)
                //getAvgLumWithMultik(byteArray, config.width, config.height)
            }
            Timber.d("Average Luminance: $lum")
            Timber.d("Average luminance calculation time (ms): ${duration.inMilliseconds}")

            if (lum < minLum || lum > maxLum) {
                exposure = (currentExposure + alpha * (scale * (targetLum - lum))).toInt()
                if (exposure > exposureAbsoluteLimit) exposure = exposureAbsoluteLimit
                else if (exposure < exposureAbsoluteMinimum) exposure = exposureAbsoluteMinimum
                Timber.d("Exposure Absolute Time: $exposure")
                piRouter?.routeCommand(CommandType.EXPOSURE_TIME, exposure.toString())
            }

            return exposure
        }

        //@OptIn(ExperimentalUnsignedTypes::class)
        //private fun getAvgLumWithNd4j(
        //    image: ByteArray,
        //    imageWidth: Int,
        //    imageHeight: Int,
        //    boxWidth: Int = 640,
        //    boxHeight: Int = 360
        //): Int {
        //    val size = boxWidth * boxHeight
        //    val xStart = (imageWidth - boxWidth) / 2
        //    val xEnd = xStart + boxWidth
        //    val yStart = (imageHeight - boxHeight) / 2
        //    val yEnd = yStart + boxHeight
        //    var list: IntArray = intArrayOf()
        //    for (i in 0..size) {
        //        list += image[i].toUByte().toInt()
        //    }
        //    val arr: INDArray = Nd4j.createFromArray(list.toTypedArray())
        //    val shape = intArrayOf(imageWidth, imageHeight)
        //    arr.reshape(shape)
        //    val box = arr.get(interval(xStart, xEnd), interval(yStart, yEnd))
        //    return box.sumNumber().toByte().toUByte().toInt()
        //}

        //@OptIn(ExperimentalUnsignedTypes::class)
        //private fun getAvgLumWithMultik(
        //    image: ByteArray,
        //    imageWidth: Int,
        //    imageHeight: Int,
        //    boxWidth: Int = 640,
        //    boxHeight: Int = 360
        //): Int {
        //    val size = boxWidth * boxHeight
        //    val xStart = (imageWidth - boxWidth) / 2
        //    val xEnd = xStart + boxWidth
        //    val yStart = (imageHeight - boxHeight) / 2
        //    val yEnd = yStart + boxHeight
        //    val img = mk.ndarray(image.toList().subList(0, size), imageWidth, imageHeight)
        //    val box = img[xStart..xEnd, yStart..yEnd]
        //    val sum: Byte = box.sum()
        //    return sum.toUByte().toInt()
        //}

        /**
         * Returns the mean luminance value (range of 0-255) of a horizontally and vertically
         * centered box within an image luminance matrix.
         * @param image byte buffer of an image in YUV format
         * @param imageWidth pixel length of image
         * @param imageHeight pixel height of image
         * @param boxWidth pixel length of center box
         * @param boxHeight pixel height of center box
         */
        @OptIn(ExperimentalUnsignedTypes::class)
        private fun getAverageLuminanceOfCenterBox(
            image: ByteArray,
            imageWidth: Int,
            imageHeight: Int,
            boxWidth: Int = 640,
            boxHeight: Int = 360
        ): Int {
            val size = boxWidth * boxHeight
            val xStart = (imageWidth - boxWidth) / 2
            val xEnd = xStart + boxWidth
            val yStart = (imageHeight - boxHeight) / 2
            val yEnd = yStart + boxHeight
            var sum = 0

            for (x in xStart until xEnd) {
                for (y in yStart until yEnd) {
                    val index = y * imageWidth + x
                    sum += image[index].toUByte().toInt()
                }
            }

            return sum / size
        }

        /**
         * Stop the current stream and preview. Destroy camera instance if initialized.
         */
        fun disableCamera() {
            if (isStreaming) stopStream()
            if (isPreview) stopPreview()
            camera?.destroy()
            camera = null
            videoEnabled = false
        }

        /**
         * Enable audio input for streaming.
         */
        fun enableAudio() {
            audioEnabled = true
        }

        /**
         * Disable audio input for streaming.
         */
        fun disableAudio() {
            audioEnabled = false
        }

        /**
         * Prepare video and audio input for streaming. Return true if at least one of them are
         * prepared for streaming.
         */
        fun prepareStream(videoConfig: VideoConfig?, audioConfig: AudioConfig?): Boolean {
            if (isStreaming) return false
            var videoCheck = false
            var audioCheck = false
            if (videoEnabled) {
                videoCheck = prepareVideo(videoConfig)
            }
            if (audioEnabled) {
                audioCheck = prepareAudio(audioConfig)
            }
            return videoCheck || audioCheck
        }

        /**
         * Start streaming to given url.
         *
         * @param uvcCamera UVC Camera module.
         * @param url URL of the stream's designated location (eg. rtmp://live.twitch.tv/app/{stream_key})
         */
        fun startStream(url: String) {
            isStreaming = true
            startEncoders()
            srsFlvMuxer?.let {
                if (videoEncoder.rotation == 90 && videoEncoder.rotation == 270) {
                    it.setVideoResolution(videoEncoder.height, videoEncoder.width)
                } else {
                    it.setVideoResolution(videoEncoder.width, videoEncoder.height)
                }
                it.start(url)
            }
        }

        /**
         * Prepare encoders before streaming.
         */
        private fun startEncoders() {
            if (videoEnabled) {
                videoEncoder.start()
                //glInterface?.let { intf ->
                //    intf.stop()
                //    intf.setEncoderSize(videoEncoder.width, videoEncoder.height)
                //    intf.setRotation(0)
                //    intf.start()
                //    camera?.let { cam ->
                //        cam.setPreviewTexture(intf.surfaceTexture)
                //        cam.startPreview()
                //        if (videoEncoder.inputSurface != null) {
                //            intf.addMediaCodecSurface(videoEncoder.inputSurface)
                //        }
                //        isPreview = true
                //    }
                //}
            }
            if (audioEnabled) {
                audioEncoder.start()
                microphoneManager.start()
            }
        }

        /**
         * Prepare to stream video. Return true iff system is able and ready to stream video output.
         *
         * @param config Video configuration object
         *
         * @return true if success, otherwise false.
         */
        @OptIn(ExperimentalTime::class)
        private fun prepareVideo(config: VideoConfig?): Boolean {
            if (config == null) return false

            return videoEncoder.prepareVideoEncoder(
                //config.width,
                //config.height,
                1440,
                810,
                config.fps,
                config.bitrate,
                config.rotation,
                config.hardwareRotation,
                config.iFrameInterval,
                FormatVideoEncoder.YUV420SEMIPLANAR
            )
        }

        /**
         * Prepare to stream audio. Return true iff system is able and ready to stream audio output.
         *
         * @param config Audio configuration object
         *
         * @return true if success, otherwise false.
         */
        private fun prepareAudio(config: AudioConfig?): Boolean {
            if (config == null) return false

            microphoneManager.createMicrophone(
                MediaRecorder.AudioSource.MIC,
                config.sampleRate,
                config.stereo,
                config.echoCanceler,
                config.noiseSuppressor
            )

            srsFlvMuxer?.let {
                it.setIsStereo(config.stereo)
                it.setSampleRate(config.sampleRate)
            }

            return audioEncoder.prepareAudioEncoder(
                config.bitrate,
                config.sampleRate,
                config.stereo,
                microphoneManager.maxInputSize
            )
        }

        /**
         * Set GlInterface. If one exists, replace the old one with the new GlInterface.
         */
        //fun setGlInterface(newGlInterface: GlInterface) {
        //    if (isPreview || isStreaming) {
        //        glInterface?.let {
        //            it.removeMediaCodecSurface()
        //            it.stop()
        //        }
        //        glInterface = newGlInterface
        //        prepareGlView()
        //    } else {
        //        glInterface = newGlInterface
        //        newGlInterface.init()
        //    }
        //}

        /**
         * Prepare GlInterface.
         */
        //private fun prepareGlView() {
        //    glInterface?.let {
        //        if (videoEncoder.rotation == 90 || videoEncoder.rotation == 270) {
        //            it.setEncoderSize(videoEncoder.height, videoEncoder.width)
        //        } else {
        //            it.setEncoderSize(videoEncoder.width, videoEncoder.height)
        //        }
        //        it.start()
        //        it.addMediaCodecSurface(videoEncoder.inputSurface)
        //    }
        //}

        /**
         * Stops the stream.
         */
        fun stopStream() {
            if (isStreaming) {
                videoEncoder.stop()
                audioEncoder.stop()
                srsFlvMuxer?.stop()
                isStreaming = false
            }
            //glInterface?.let {
            //    it.removeMediaCodecSurface()
            //    videoEncoder.stop()
            //    audioEncoder.stop()
            //}
            streamTimer?.reset()
        }
    }

    override fun onCreate() {
        super.onCreate()
        Timber.v("Stream service created")

        usbManager = getSystemService(USB_SERVICE) as UsbManager

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            STREAM_SERVICE_NAME,
            STREAM_SERVICE_NAME,
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager?.createNotificationChannel(channel)

        val notificationBuilder = NotificationCompat.Builder(this, STREAM_SERVICE_NAME)
            .setOngoing(true)
            .setContentTitle(STREAM_SERVICE_NAME)
            .setSmallIcon(R.drawable.raspi_pgb001)
            .setContentText("Streaming: 00:00:00")

        streamTimer = object : StreamTimer() {
            override fun updateNotification() {
                notificationBuilder.setContentText("Streaming: ${this.getTimeElapsedString()}")
                notificationManager?.notify(
                    STREAM_SERVICE_NOTIFICATION_ID,
                    notificationBuilder.build()
                )
            }
        }

        startForeground(STREAM_SERVICE_NOTIFICATION_ID, notificationBuilder.build())
        streamTimer?.start()
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.v("Stream service started")
        val endpoint = intent?.extras?.getString("endpoint")
        if (endpoint != null) {
            startStream(endpoint)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStream()
    }

}