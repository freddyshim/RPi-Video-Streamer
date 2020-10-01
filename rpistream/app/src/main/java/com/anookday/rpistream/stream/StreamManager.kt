package com.anookday.rpistream.stream

import android.content.Context
import android.graphics.ImageFormat
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaRecorder
import com.anookday.rpistream.config.AudioConfig
import com.anookday.rpistream.config.VideoConfig
import com.pedro.encoder.Frame
import com.pedro.encoder.audio.AudioEncoder
import com.pedro.encoder.audio.GetAacData
import com.pedro.encoder.input.audio.GetMicrophoneData
import com.pedro.encoder.input.audio.MicrophoneManager
import com.pedro.encoder.video.FormatVideoEncoder
import com.pedro.encoder.video.GetVideoData
import com.pedro.encoder.video.VideoEncoder
import com.pedro.rtplibrary.view.GlInterface
import com.pedro.rtplibrary.view.OffScreenGlThread
import com.pedro.rtplibrary.view.OpenGlView
import com.serenegiant.usb.IFrameCallback
import com.serenegiant.usb.UVCCamera
import timber.log.Timber
import java.nio.ByteBuffer

/**
 * Wrapper class to stream video output from OpenGlView (custom implementation of SurfaceView that
 * incorporates OpenGL) and audio output from an audio input source to a designated web server.
 */
abstract class StreamManager(openGlView: OpenGlView) {
    private val context: Context = openGlView.context
    private var glInterface: GlInterface = openGlView
    var isStreaming = false
    var isPreview = false
    private var videoFormat: MediaFormat? = null
    private var audioFormat: MediaFormat? = null
    private var previewWidth = 720
    private var previewHeight = 480

    protected abstract fun onSpsPpsVpsRtp(sps: ByteBuffer, pps: ByteBuffer, vps: ByteBuffer?)
    protected abstract fun prepareAudioRtp(isStereo: Boolean, sampleRate: Int)
    protected abstract fun getH264DataRtp(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo)
    protected abstract fun getAacDataRtp(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo)
    protected abstract fun startStreamRtp(url: String)
    protected abstract fun stopStreamRtp()

    init {
        glInterface.init()
    }

    /**
     * Video encoder class
     */
    protected val videoEncoder: VideoEncoder = VideoEncoder(object : GetVideoData {
        override fun onVideoFormat(mediaFormat: MediaFormat) {
            videoFormat = mediaFormat
        }

        override fun onSpsPpsVps(sps: ByteBuffer, pps: ByteBuffer, vps: ByteBuffer) {
            if (isStreaming) {
                onSpsPpsVpsRtp(sps, pps, vps)
            }
        }

        override fun onSpsPps(sps: ByteBuffer, pps: ByteBuffer) {
            if (isStreaming) {
                onSpsPpsVpsRtp(sps, pps, null)
            }
        }

        override fun getVideoData(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
            if (isStreaming) {
                getH264DataRtp(h264Buffer, info)
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
                getAacDataRtp(aacBuffer, info)
            }
        }
    })

    /**
     * Microphone manager class
     */
    private val microphoneManager: MicrophoneManager =
        MicrophoneManager { frame -> audioEncoder.inputPCMData(frame) }

    /**
     * Prepare to stream video. Return true iff system is able and ready to stream video output.
     *
     * @param uvcCamera UVC camera module
     * @param config Video configuration object
     *
     * @return true if success, otherwise false.
     */
    fun prepareVideo(uvcCamera: UVCCamera, config: VideoConfig?): Boolean {
        if (config == null) return false

        if (isPreview) {
            stopPreview(uvcCamera)
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

        uvcCamera.setFrameCallback(
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
    fun prepareAudio(config: AudioConfig?): Boolean {
        if (config == null) return false

        microphoneManager.createMicrophone(
            MediaRecorder.AudioSource.MIC,
            config.sampleRate,
            config.stereo,
            config.echoCanceler,
            config.noiseSuppressor
        )

        prepareAudioRtp(config.stereo, config.sampleRate)
        return audioEncoder.prepareAudioEncoder(
            config.bitrate,
            config.sampleRate,
            config.stereo,
            microphoneManager.maxInputSize
        )
    }

    /**
     * Start camera preview if preview is currently disabled.
     *
     * @param uvcCamera     UVC Camera module.
     * @param width         Width of preview frame in px.
     * @param height        Height of preview frame in px.
     */
    fun startPreview(uvcCamera: UVCCamera, width: Int?, height: Int?) {
        if (!isStreaming && !isPreview && glInterface !is OffScreenGlThread) {
            if (width != null) previewWidth = width
            if (height != null) previewHeight = height
            Timber.v("RPISTREAM preview enabled")
            glInterface.setEncoderSize(previewWidth, previewHeight)
            glInterface.setRotation(0)
            glInterface.start()
            uvcCamera.setPreviewTexture(glInterface.surfaceTexture)
            uvcCamera.startPreview()
            isPreview = true
        }
    }

    /**
     * Stop camera preview if preview is currently enabled.
     *
     * @param uvcCamera UVC Camera module.
     */
    fun stopPreview(uvcCamera: UVCCamera) {
        if (!isStreaming && isPreview && glInterface !is OffScreenGlThread) {
            Timber.v("RPISTREAM preview disabled")
            glInterface.stop()
            uvcCamera.stopPreview()
            isPreview = false
        }
    }

    /**
     * Start streaming to given url.
     *
     * @param uvcCamera UVC Camera module.
     * @param url URL of the stream's designated location (eg. rtmp://live.twitch.tv/app/{stream_key})
     */
    fun startStream(uvcCamera: UVCCamera, url: String) {
        isStreaming = true
        startEncoders(uvcCamera)
        startStreamRtp(url)
    }

    private fun startEncoders(uvcCamera: UVCCamera) {
        videoEncoder.start()
        audioEncoder.start()
        microphoneManager.start()
        glInterface.stop()
        glInterface.setEncoderSize(videoEncoder.width, videoEncoder.height)
        glInterface.setRotation(0)
        glInterface.start()
        uvcCamera.setPreviewTexture(glInterface.surfaceTexture)
        uvcCamera.startPreview()
        if (videoEncoder.inputSurface != null) {
            glInterface.addMediaCodecSurface(videoEncoder.inputSurface)
        }
        isPreview = true
    }

    private fun resetVideoEncoder() {
        glInterface.removeMediaCodecSurface()
        videoEncoder.reset()
        glInterface.addMediaCodecSurface(videoEncoder.inputSurface)
    }

    private fun prepareGlView() {
        if (glInterface is OffScreenGlThread) {
            glInterface = OffScreenGlThread(context)
            glInterface.setFps(videoEncoder.fps)
        }
        glInterface.init()
        if (videoEncoder.rotation == 90 || videoEncoder.rotation == 270) {
            glInterface.setEncoderSize(videoEncoder.height, videoEncoder.width)
        } else {
            glInterface.setEncoderSize(videoEncoder.width, videoEncoder.height)
        }
        glInterface.start()
        glInterface.addMediaCodecSurface(videoEncoder.inputSurface)
    }

    fun stopStream(uvcCamera: UVCCamera) {
        if (isStreaming) {
            isStreaming = false
            stopStreamRtp()
        }
        glInterface.removeMediaCodecSurface()
        if (glInterface is OffScreenGlThread) {
            glInterface.stop()
            uvcCamera.stopPreview()
        }
        videoEncoder.stop()
        audioEncoder.stop()

    }
}