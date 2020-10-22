package com.anookday.rpistream.stream

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.ImageFormat
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaRecorder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.anookday.rpistream.R
import com.anookday.rpistream.config.AudioConfig
import com.anookday.rpistream.config.VideoConfig
import com.pedro.encoder.Frame
import com.pedro.encoder.audio.AudioEncoder
import com.pedro.encoder.audio.GetAacData
import com.pedro.encoder.input.audio.MicrophoneManager
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

const val STREAM_SERVICE_NOTIFICATION_ID = 123456
const val STREAM_SERVICE_NAME = "RPi Streamer"

/**
 * Service that handles streaming video output from UVCCamera and audio output from an audio input
 * source to a designated web server.
 */
class StreamService() : Service() {
    companion object {
        private var context: Context? = null
        private var camera: UVCCamera? = null
        private var glInterface: GlInterface? = null
        private var srsFlvMuxer: SrsFlvMuxer? = null
        private var notificationManager: NotificationManager? = null
        private var streamTimer: StreamTimer? = null
        private var videoFormat: MediaFormat? = null
        private var audioFormat: MediaFormat? = null
        private var previewWidth = 720
        private var previewHeight = 480
        var videoEnabled = false
        var audioEnabled = false
        var isStreaming = false
        var isPreview = false

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

        fun init(openGlView: OpenGlView, connectChecker: ConnectCheckerRtmp) {
            camera = UVCCamera()
            context = openGlView.context
            glInterface = openGlView
            srsFlvMuxer = SrsFlvMuxer(connectChecker)
            openGlView.init()
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
            glInterface?.let { intf ->
                intf.setEncoderSize(previewWidth, previewHeight)
                intf.setRotation(0)
                intf.start()
                camera?.let { cam ->
                    cam.setPreviewTexture(intf.surfaceTexture)
                    cam.startPreview()
                }
                isPreview = true
                Timber.v("RPISTREAM preview enabled")
            }
        }

        /**
         * Stop camera preview if preview is currently enabled.
         */
        fun stopPreview() {
            Timber.v("RPISTREAM preview disabled")
            glInterface?.stop()
            camera?.stopPreview()
            isPreview = false
        }

        /**
         * Enable video input for streaming.
         */
        fun enableCamera(ctrlBlock: USBMonitor.UsbControlBlock?, config: VideoConfig?): String? {
            camera = UVCCamera()
            camera?.let {
                it.open(ctrlBlock)
                if (config != null) {
                    it.setPreviewSize(config.width, config.height, UVCCamera.FRAME_FORMAT_MJPEG)
                    startPreview(config.width, config.height)
                    videoEnabled = true
                }
            }

            return camera?.deviceName
        }

        /**
         * Stop the current stream and preview. Destroy camera instance if initialized.
         */
        fun disableCamera() {
            if (isStreaming) stopStream()
            if (isPreview) stopPreview()
            camera?.destroy()
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
                camera?.let {
                    videoCheck = prepareVideo(videoConfig)
                }
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

        private fun startEncoders() {
            if (videoEnabled) {
                videoEncoder.start()
                glInterface?.let { intf ->
                    intf.stop()
                    intf.setEncoderSize(videoEncoder.width, videoEncoder.height)
                    intf.setRotation(0)
                    intf.start()
                    camera?.let { cam ->
                        cam.setPreviewTexture(intf.surfaceTexture)
                        cam.startPreview()
                        if (videoEncoder.inputSurface != null) {
                            intf.addMediaCodecSurface(videoEncoder.inputSurface)
                        }
                        isPreview = true
                    }
                }
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
        private fun prepareVideo(config: VideoConfig?): Boolean {
            if (config == null) return false

            if (isPreview) {
                stopPreview()
                isPreview = true
            }

            val result = videoEncoder.prepareVideoEncoder(
                config.width,
                config.height,
                config.fps,
                config.bitrate,
                config.rotation,
                config.hardwareRotation,
                config.iFrameInterval,
                FormatVideoEncoder.YUV420SEMIPLANAR
            )

            camera?.setFrameCallback(
                { frame ->
                    frame.rewind()
                    val byteArray = ByteArray(frame.remaining())
                    frame.get(byteArray)
                    videoEncoder.inputYUVData(Frame(byteArray, 0, false, ImageFormat.YUV_420_888))
                },
                UVCCamera.PIXEL_FORMAT_YUV420SP
            )

            return result
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
        fun setGlInterface(newGlInterface: GlInterface) {
            if (isPreview || isStreaming) {
                glInterface?.let {
                    it.removeMediaCodecSurface()
                    it.stop()
                }
                glInterface = newGlInterface
                prepareGlView()
            } else {
                glInterface = newGlInterface
                newGlInterface.init()
            }
        }

        private fun resetVideoEncoder() {
            glInterface?.let {
                it.removeMediaCodecSurface()
                videoEncoder.reset()
                it.addMediaCodecSurface(videoEncoder.inputSurface)
            }
        }

        private fun prepareGlView() {
            glInterface?.let {
                if (videoEncoder.rotation == 90 || videoEncoder.rotation == 270) {
                    it.setEncoderSize(videoEncoder.height, videoEncoder.width)
                } else {
                    it.setEncoderSize(videoEncoder.width, videoEncoder.height)
                }
                it.start()
                it.addMediaCodecSurface(videoEncoder.inputSurface)
            }
        }

        fun stopStream() {
            if (isStreaming) {
                isStreaming = false
                srsFlvMuxer?.stop()
            }
            glInterface?.let {
                it.removeMediaCodecSurface()
                videoEncoder.stop()
                audioEncoder.stop()
            }
            streamTimer?.cancel()
        }
    }

    override fun onCreate() {
        super.onCreate()
        Timber.v("Stream service created")

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