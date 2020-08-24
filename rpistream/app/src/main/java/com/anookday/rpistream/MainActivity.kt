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
import com.anookday.rpistream.streamlib.RtmpUSB
import com.pedro.rtplibrary.rtmp.RtmpCamera1
import com.pedro.rtplibrary.rtmp.RtmpCamera2
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
    private lateinit var mUVCCamera: UVCCamera
    private lateinit var mPreviewSurface: Surface
    private lateinit var mUSBMonitor: USBMonitor
    private lateinit var mUVCCameraView: OpenGlView
    // RTMP objects
    private lateinit var rtmpUSB: RtmpUSB
    private val width = 1920
    private val height = 1080
    // Lock for synchronization
    private var mutex = Mutex()
    // Booleans
    private var isStreaming = false
    private var isPreview = false
    private var isBackPressedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.v("RPISTREAM lifecycle: onCreate called")

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        mUVCCameraView = binding.cameraPreview

        binding.cameraConnectButton.setOnClickListener(mUVCCameraOnClickListener)
        binding.streamToggleButton.setOnClickListener(mStreamOnClickListener)
        binding.cameraPreview.holder.addCallback(mSurfaceViewCallback)

        mUSBMonitor = USBMonitor(this, mOnDeviceConnectListener)

        rtmpUSB = RtmpUSB(mUVCCameraView, this)
  }

    override fun onStart() {
        super.onStart()
        Timber.v("RPISTREAM lifecycle: onStart called")
        runWithLock {
            if (this::mUSBMonitor.isInitialized) {
                mUSBMonitor.register()
            }
        }
    }


    override fun onStop() {
        Timber.v("RPISTREAM lifecycle: onStop called")
        runWithLock {
            if (this::mUSBMonitor.isInitialized) {
                mUSBMonitor.unregister()
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
            isPreview = false
            isStreaming = false
            if (rtmpUSB.isStreaming) rtmpUSB.stopStream(mUVCCamera)
            if (rtmpUSB.isOnPreview) rtmpUSB.stopPreview(mUVCCamera)
            if (this::mUVCCamera.isInitialized) {
                mUVCCamera.destroy()
            }
            if (this::mUSBMonitor.isInitialized) {
                mUSBMonitor.destroy()
            }
        }
    }

    /**
     * UVCCamera onClickListener object
     */
    private var mUVCCameraOnClickListener = View.OnClickListener { view ->
        if (!isStreaming && !isPreview) {
            CameraDialog.showDialog(this)
        } else {
            runWithLock {
                if (rtmpUSB.isStreaming) rtmpUSB.stopStream(mUVCCamera)
                if (rtmpUSB.isOnPreview) rtmpUSB.stopPreview(mUVCCamera)
                if (this::mUVCCamera.isInitialized) {
                    mUVCCamera.destroy()
                }
                isStreaming = false
                isPreview = false
            }
        }
    }

    private var mStreamOnClickListener = View.OnClickListener { view ->
        if (!rtmpUSB.isStreaming) {
            if (rtmpUSB.prepareVideo(width, height, 30, 4000 * 1024, false, 0,
                    mUVCCamera) && rtmpUSB.prepareAudio()) {
                rtmpUSB.startStream(mUVCCamera, binding.streamUriTextbox.text.toString())
            }
            binding.streamToggleButton.setImageResource(R.drawable.btn_shutter_video_recording)
        } else {
            binding.streamToggleButton.setImageResource(R.drawable.btn_repeat_shutter_recording)
            rtmpUSB.stopStream(mUVCCamera)
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
                mUVCCamera = UVCCamera()
                mUVCCamera.open(ctrlBlock)
                Timber.i("RPISTREAM onDeviceConnectListener: Supported size: ${mUVCCamera.supportedSize}")
                try {
                    mUVCCamera.setPreviewSize(width, height, UVCCamera.FRAME_FORMAT_MJPEG)
                    rtmpUSB.startPreview(mUVCCamera, width, height)
                    isPreview = true
                } catch (e: IllegalArgumentException) {
                    Timber.i("RPISTREAM onDeviceConnectListener: Incorrect preview configuration passed")
                    mUVCCamera.destroy()
                    isPreview = false
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
                if (this@MainActivity::mUVCCamera.isInitialized) {
                    mUVCCamera.close()
                }
                isStreaming = false
                isPreview = false
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
            mPreviewSurface = holder!!.surface
            runWithLock {
                if (!isPreview && this@MainActivity::mUVCCamera.isInitialized) {
                    rtmpUSB.startPreview(mUVCCamera, width, height)
                    isPreview = true
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
    override fun getUSBMonitor(): USBMonitor = mUSBMonitor

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
            rtmpUSB.stopStream(mUVCCamera)
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