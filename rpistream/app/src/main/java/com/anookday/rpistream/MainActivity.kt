package com.anookday.rpistream

import android.hardware.usb.UsbDevice
import android.os.Bundle
import android.os.Handler
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import com.anookday.rpistream.databinding.ActivityMainBinding
import com.anookday.rpistream.stream.RtmpStreamManager
import com.pedro.rtplibrary.view.OpenGlView
import com.serenegiant.usb.CameraDialog
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.ossrs.rtmp.ConnectCheckerRtmp
import timber.log.Timber

class MainActivity : AppCompatActivity(), CameraDialog.CameraDialogParent, ConnectCheckerRtmp {
    // data binding object
    private lateinit var binding: ActivityMainBinding
    // LibUVCCamera objects
    private lateinit var uvcCamera: UVCCamera
    private lateinit var previewSurface: Surface
    private lateinit var usbMonitor: USBMonitor
    private lateinit var uvcCameraView: OpenGlView
    // Stream manager wrapper object
    private lateinit var streamManager: RtmpStreamManager
    // Video configuration
    private val width = 1920
    private val height = 1080
    private val fps = 30
    private val videoBitrate = 4000 * 1024
    private val hardwareRotation = false
    private val iFrameInterval = 0
    private val rotation = 0
    // Audio configuration
    private val audioBitrate = 64 * 1024
    private val sampleRate = 32000
    private val isStereo = true
    private val echoCanceler = false
    private val noiseSuppressor = false
    // Lock for synchronization
    private var mutex = Mutex()
    // Booleans
    private var isBackPressedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.v("RPISTREAM lifecycle: onCreate called")

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        uvcCameraView = binding.cameraPreview

        binding.cameraConnectButton.setOnClickListener(uvcCameraOnClickListener)
        binding.streamToggleButton.setOnClickListener(mStreamOnClickListener)
        binding.cameraPreview.holder.addCallback(mSurfaceViewCallback)

        usbMonitor = USBMonitor(this, mOnDeviceConnectListener)

        streamManager = RtmpStreamManager(uvcCameraView, this)
  }

    override fun onStart() {
        super.onStart()
        Timber.v("RPISTREAM lifecycle: onStart called")
        runWithLock {
            if (this::usbMonitor.isInitialized) {
                usbMonitor.register()
            }
        }
    }


    override fun onStop() {
        Timber.v("RPISTREAM lifecycle: onStop called")
        runWithLock {
            if (this::usbMonitor.isInitialized) {
                usbMonitor.unregister()
            }
        }
        super.onStop()
    }

    override fun onDestroy() {
        Timber.v("RPISTREAM lifecycle: onDestroy called")
        destroyCamera()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (isBackPressedOnce) {
            destroyCamera()
            super.onBackPressed()
            return
        }

        isBackPressedOnce = true
        Toast.makeText(this,"Press back button again to exit", Toast.LENGTH_SHORT).show()
        Handler().postDelayed({
            isBackPressedOnce = false
        }, 2000)
    }

    /**
     * Run a given function in a locked thread.
     */
    private fun runWithLock(call: () -> Unit) {
        lifecycleScope.launch {
            withContext(Dispatchers.Default) {
                mutex.withLock { call() }
            }
        }
    }

    /**
     * Destroy all camera resources. Called before attempting to destroy the application process.
     */
    private fun destroyCamera() {
        runWithLock {
            if (streamManager.isStreaming) streamManager.stopStream(uvcCamera)
            if (streamManager.isPreview) streamManager.stopPreview(uvcCamera)
            if (this::uvcCamera.isInitialized) {
                uvcCamera.destroy()
            }
            if (this::usbMonitor.isInitialized) {
                usbMonitor.destroy()
            }
        }
    }

    /**
     * UVCCamera onClickListener object
     */
    private var uvcCameraOnClickListener = View.OnClickListener { view ->
        if (!streamManager.isStreaming && !streamManager.isPreview) {
            CameraDialog.showDialog(this)
        } else {
            runWithLock {
                if (streamManager.isStreaming) streamManager.stopStream(uvcCamera)
                if (streamManager.isPreview) streamManager.stopPreview(uvcCamera)
                if (this::uvcCamera.isInitialized) {
                    uvcCamera.destroy()
                }
            }
        }
    }

    private var mStreamOnClickListener = View.OnClickListener { view ->
        if (!streamManager.isStreaming) {
            if (streamManager.prepareVideo(width, height, fps, videoBitrate, hardwareRotation, iFrameInterval, rotation, uvcCamera)
                    && streamManager.prepareAudio(audioBitrate, sampleRate, isStereo, echoCanceler, noiseSuppressor)) {
                streamManager.startStream(uvcCamera, binding.streamUriTextbox.text.toString())
            }
            binding.streamToggleButton.setImageResource(R.drawable.btn_shutter_video_recording)
        } else {
            binding.streamToggleButton.setImageResource(R.drawable.btn_repeat_shutter_recording)
            streamManager.stopStream(uvcCamera)
        }
    }

    /**
     * deviceConnectListener object
     */
    private var mOnDeviceConnectListener = object: USBMonitor.OnDeviceConnectListener {
        override fun onConnect(
            device: UsbDevice?,
            ctrlBlock: USBMonitor.UsbControlBlock?,
            createNew: Boolean
        ) {
            Timber.v("RPISTREAM onDeviceConnectListener: Device connected")
            runWithLock {
                uvcCamera = UVCCamera()
                uvcCamera.open(ctrlBlock)
                Timber.i("RPISTREAM onDeviceConnectListener: Supported size: ${uvcCamera.supportedSize}")
                try {
                    uvcCamera.setPreviewSize(width, height, UVCCamera.FRAME_FORMAT_MJPEG)
                    streamManager.startPreview(uvcCamera, width, height)
                } catch (e: IllegalArgumentException) {
                    Timber.i("RPISTREAM onDeviceConnectListener: Incorrect preview configuration passed")
                    uvcCamera.destroy()
                }
            }
        }

        override fun onCancel(device: UsbDevice?) {
            //
        }

        override fun onAttach(device: UsbDevice?) {
            Timber.v("RPISTREAM onDeviceConnectListener: Device attached")
            Toast.makeText(this@MainActivity, "USB device attached", Toast.LENGTH_SHORT).show()
        }

        override fun onDisconnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
            Timber.v("RPISTREAM onDeviceConnectListener: Device disconnected")
            runWithLock {
                if (this@MainActivity::uvcCamera.isInitialized) {
                    uvcCamera.close()
                }
            }
        }

        override fun onDettach(device: UsbDevice?) {
            Timber.v("RPISTREAM onDeviceConnectListener: Device detached")
            Toast.makeText(this@MainActivity, "USB device detached", Toast.LENGTH_SHORT).show()
        }

    }

    /**
     * SurfaceView callback object
     */
    private var mSurfaceViewCallback = object: SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            Timber.v("RPISTREAM surfaceViewCallback: Surface created")
        }

        override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            if (width == 0 || height == 0) return
            Timber.v("RPISTREAM surfaceViewCallback: Surface changed")
            previewSurface = holder!!.surface
            runWithLock {
                if (!streamManager.isPreview && this@MainActivity::uvcCamera.isInitialized) {
                    streamManager.startPreview(uvcCamera, width, height)
                }
            }
        }

        override fun surfaceDestroyed(holder: SurfaceHolder?) {
            Timber.v("RPISTREAM surfaceViewCallback: Surface destroyed")
        }
    }

    /**
     * CameraDialogParent abstract methods
     */
    override fun getUSBMonitor(): USBMonitor = usbMonitor

    override fun onDialogResult(canceled: Boolean) {
        Timber.v("RPISTREAM dialog result: ${canceled.toString()}")
    }

    /**
     * ConnectCheckerRtmp abstract methods
     */
    override fun onConnectionSuccessRtmp() {
        runOnUiThread { Toast.makeText(this, "Connection success", Toast.LENGTH_SHORT).show() }
    }

    override fun onConnectionFailedRtmp(reason: String) {
        runOnUiThread {
            Toast.makeText(this, "Connection failed. " + reason, Toast.LENGTH_SHORT).show()
            streamManager.stopStream(uvcCamera)
        }
    }

    override fun onAuthSuccessRtmp() {
        runOnUiThread { Toast.makeText(this, "Auth success", Toast.LENGTH_SHORT).show() }
    }

    override fun onNewBitrateRtmp(bitrate: Long) {

    }

    override fun onAuthErrorRtmp() {
        runOnUiThread { Toast.makeText(this, "Auth error", Toast.LENGTH_SHORT).show() }
    }

    override fun onDisconnectRtmp() {
        runOnUiThread { Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show() }
    }
}