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
import com.serenegiant.usb.CameraDialog
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

class MainActivity : AppCompatActivity(), CameraDialog.CameraDialogParent {
    private lateinit var mUVCCamera: UVCCamera
    private lateinit var mPreviewSurface: Surface
    private lateinit var mUSBMonitor: USBMonitor
    private lateinit var mUVCCameraView: SurfaceView
    private var mutex = Mutex()
    private var isActive = false
    private var isPreview = false
    private var isBackPressedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.v("RPISTREAM lifecycle: onCreate called")

        val binding : ActivityMainBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        mUVCCameraView = binding.cameraPreview

        binding.cameraConnectButton.setOnClickListener(mOnClickListener)
        binding.cameraPreview.holder.addCallback(mSurfaceViewCallback)

        mUSBMonitor = USBMonitor(this, mOnDeviceConnectListener)
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
            isActive = false
            if (this::mUVCCamera.isInitialized) {
                mUVCCamera.destroy()
            }
            if (this::mUSBMonitor.isInitialized) {
                mUSBMonitor.destroy()
            }
        }
    }

    private var mOnClickListener = View.OnClickListener { view ->
        if (!isActive && !isPreview) {
            CameraDialog.showDialog(this)
        } else {
            runWithLock {
                if (this::mUVCCamera.isInitialized) {
                    mUVCCamera.destroy()
                }
                isActive = false
                isPreview = false
            }
        }
    }

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
                    mUVCCamera.setPreviewSize(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, UVCCamera.FRAME_FORMAT_MJPEG)
                    mPreviewSurface = mUVCCameraView.holder.surface
                    isActive = true
                    mUVCCamera.setPreviewDisplay(mPreviewSurface)
                    mUVCCamera.startPreview()
                    isPreview = true
                } catch (e: IllegalArgumentException) {
                    Timber.i("RPISTREAM onDeviceConnectListener: Incorrect preview configuration passed")
                    mUVCCamera.destroy()
                    isActive = false
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
                /* if (this@MainActivity::mPreviewSurface.isInitialized) {
                    mPreviewSurface.release()
                } */
                isActive = false
                isPreview = false
            }
        }

        override fun onDettach(device: UsbDevice?) {
            Timber.v("RPISTREAM onDeviceConnectListener: Device detached")
            Toast.makeText(this@MainActivity, "USB device detached", Toast.LENGTH_SHORT).show()
        }

    }

    private var mSurfaceViewCallback = object: SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            Timber.v("RPISTREAM surfaceViewCallback: Surface created")
        }

        override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            if (width == 0 || height == 0) return
            Timber.v("RPISTREAM surfaceViewCallback: Surface changed")
            mPreviewSurface = holder!!.surface
            runWithLock {
                if (isActive && !isPreview && this@MainActivity::mUVCCamera.isInitialized) {
                    mUVCCamera.setPreviewDisplay(mPreviewSurface)
                    mUVCCamera.startPreview()
                    isPreview = true
                }
            }
        }

        override fun surfaceDestroyed(holder: SurfaceHolder?) {
            Timber.v("RPISTREAM surfaceViewCallback: Surface destroyed")
            runWithLock {
                mUVCCamera.stopPreview()
                isPreview = false
            }
        }
    }

    override fun getUSBMonitor(): USBMonitor = mUSBMonitor

    override fun onDialogResult(canceled: Boolean) {
        Timber.v("RPISTREAM dialog result: ${canceled.toString()}")
    }
}