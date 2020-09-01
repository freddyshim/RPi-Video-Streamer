package com.anookday.rpistream

import android.os.Bundle
import android.os.Handler
import android.text.Editable
import android.text.TextWatcher
import android.view.SurfaceHolder
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.anookday.rpistream.databinding.ActivityMainBinding
import com.serenegiant.usb.CameraDialog
import com.serenegiant.usb.USBMonitor
import timber.log.Timber

class MainActivity : AppCompatActivity(), CameraDialog.CameraDialogParent {
    private var isBackPressedOnce = false

    private val viewModel: MainViewModel by lazy {
        ViewModelProvider(this).get(MainViewModel::class.java)
    }

    /**
     * Lifecycle methods
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.v("RPISTREAM lifecycle: onCreate called")

        val binding: ActivityMainBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel

        binding.cameraConnectButton.setOnClickListener(uvcCameraOnClickListener)
        binding.streamToggleButton.setOnClickListener(streamOnClickListener)
        binding.cameraPreview.holder.addCallback(surfaceViewCallback)
        binding.streamUriTextbox.addTextChangedListener(onUriChangeListener)

        setObservers()

        viewModel.init(this, binding.cameraPreview)
  }

    override fun onStart() {
        super.onStart()
        Timber.v("RPISTREAM lifecycle: onStart called")
        viewModel.registerUsbMonitor()
    }


    override fun onStop() {
        Timber.v("RPISTREAM lifecycle: onStop called")
        viewModel.unregisterUsbMonitor()
        super.onStop()
    }

    override fun onDestroy() {
        Timber.v("RPISTREAM lifecycle: onDestroy called")
        viewModel.destroyCamera()
        viewModel.destroyUsbMonitor()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (isBackPressedOnce) {
            viewModel.destroyCamera()
            viewModel.destroyUsbMonitor()
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
     * UVCCamera onClickListener object
     */
    private var uvcCameraOnClickListener = View.OnClickListener {
        viewModel.streamManager.value?.let { stream ->
            if (!stream.isStreaming && !stream.isPreview) {
                CameraDialog.showDialog(this)
            } else {
                viewModel.destroyCamera()
            }
        }
    }

    /**
     * Stream onClickListener object
     */
    private var streamOnClickListener = View.OnClickListener {
        viewModel.toggleStream()
    }

    /**
     * SurfaceView callback object
     */
    private var surfaceViewCallback = object: SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            Timber.v("RPISTREAM surfaceViewCallback: Surface created")
        }

        override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            if (width == 0 || height == 0) return
            Timber.v("RPISTREAM surfaceViewCallback: Surface changed")
            viewModel.startPreview(width, height)
        }

        override fun surfaceDestroyed(holder: SurfaceHolder?) {
            Timber.v("RPISTREAM surfaceViewCallback: Surface destroyed")
        }
    }

    /**
     * StreamUriTextBox onChangeListener object
     */
    private var onUriChangeListener = object: TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            //
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            //
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            viewModel.setStreamUri(s.toString())
        }

    }

    /**
     * Override methods for CameraDialogParent
     */
    override fun getUSBMonitor(): USBMonitor = viewModel.usbMonitor.value!!

    override fun onDialogResult(canceled: Boolean) {
        Timber.v("RPISTREAM dialog result: ${canceled.toString()}")
    }

    /**
     * Initialize view model LiveData value observers.
     */
    private fun setObservers() {
        viewModel.usbStatus.observe(this, Observer { status ->
            status?.let {
                when(it) {
                    UsbConnectStatus.ATTACHED -> Toast.makeText(this, "USB device attached", Toast.LENGTH_SHORT).show()
                    UsbConnectStatus.DETACHED -> Toast.makeText(this, "USB device detached", Toast.LENGTH_SHORT).show()
                }
                viewModel.resetUsbStatus()
            }
        })

        viewModel.connectStatus.observe(this, Observer { status ->
            status?.let {
                when(it) {
                    RtmpConnectStatus.SUCCESS -> Toast.makeText(this, "Connection success", Toast.LENGTH_SHORT).show()
                    RtmpConnectStatus.FAIL -> Toast.makeText(this, "Connection failed", Toast.LENGTH_SHORT).show()
                    RtmpConnectStatus.DISCONNECT -> Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show()
                }
                viewModel.resetConnectStatus()
            }
        })

        viewModel.authStatus.observe(this, Observer { status ->
            status?.let {
                when(it) {
                    RtmpAuthStatus.SUCCESS -> Toast.makeText(this, "Auth success", Toast.LENGTH_SHORT).show()
                    RtmpAuthStatus.FAIL -> Toast.makeText(this, "Auth error", Toast.LENGTH_SHORT).show()
                }
                viewModel.resetAuthStatus()
            }
        })
    }
}