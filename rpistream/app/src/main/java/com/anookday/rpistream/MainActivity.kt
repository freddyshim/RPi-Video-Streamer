package com.anookday.rpistream

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.view.MenuItem
import android.view.SurfaceHolder
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.anookday.rpistream.databinding.ActivityMainBinding
import com.anookday.rpistream.stream.StreamService
import com.bumptech.glide.Glide
import com.serenegiant.usb.CameraDialog
import com.serenegiant.usb.USBMonitor
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fab_toggle_on.*
import kotlinx.android.synthetic.main.nav_header.*
import net.openid.appauth.AuthState
import timber.log.Timber

const val RC_AUTH = 100

/**
 * Stream (main) activity class.
 */
class MainActivity : AppCompatActivity(), CameraDialog.CameraDialogParent {
    private lateinit var mFabConstraintOn: ConstraintSet
    private lateinit var mFabConstraintOff: ConstraintSet
    private lateinit var mTransition: ChangeBounds
    private var isBackPressedOnce = false
    private var isMenuPressed = true
    private var isLoggedIn = false

    private val viewModel: MainViewModel by lazy {
        ViewModelProvider(this, MainViewModel.Factory(application)).get(MainViewModel::class.java)
    }

    /**
     * Lifecycle methods
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.v("RPISTREAM lifecycle: onCreate called")

        // initialize data binding
        val binding: ActivityMainBinding =
            DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel

        // initialize toolbar and navigation drawer
        setSupportActionBar(app_bar as Toolbar)
        supportActionBar?.let {
            it.setHomeAsUpIndicator(R.drawable.ic_baseline_account_circle_24)
            it.setDisplayHomeAsUpEnabled(true)
        }
        acc_drawer.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.action_login -> {
                    viewModel.getAuthorizationIntent()?.let { intent ->
                        startActivityForResult(intent, RC_AUTH)
                    }
                    true
                }
                R.id.action_logout -> {
                    viewModel.logout()
                    true
                }
                else -> false
            }
        }

        // initialize activity (layout) variables
        mFabConstraintOn = ConstraintSet()
        mFabConstraintOn.clone(this, R.layout.fab_toggle_on)
        mFabConstraintOff = ConstraintSet()
        mFabConstraintOff.clone(this, R.layout.fab_toggle_off)
        mTransition = ChangeBounds()
        mTransition.interpolator = OvershootInterpolator(1.0f)

        // set click listeners
        video_fab.setOnClickListener(videoOnClickListener)
        audio_fab.setOnClickListener(audioOnClickListener)
        stream_fab.setOnClickListener(streamOnClickListener)
        menu_fab.setOnClickListener(menuFabOnClickListener)

        // set event callbacks
        binding.cameraPreview.holder.addCallback(surfaceViewCallback)

        // set observers
        setObservers()

        // initialize ViewModel objects
        viewModel.init(this, binding.cameraPreview)
        viewModel.registerUsbMonitor()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> app_container.openDrawer(GravityCompat.START)
        }
        return true
    }

    override fun onStart() {
        super.onStart()
        Timber.v("RPISTREAM lifecycle: onStart called")
    }


    override fun onStop() {
        Timber.v("RPISTREAM lifecycle: onStop called")
        super.onStop()
    }

    override fun onDestroy() {
        Timber.v("RPISTREAM lifecycle: onDestroy called")
        if (StreamService.isStreaming) {
            application.stopService(Intent(applicationContext, StreamService::class.java))
        }
        viewModel.unregisterUsbMonitor()
        viewModel.disableCamera()
        viewModel.destroyUsbMonitor()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (isBackPressedOnce) {
            if (StreamService.isStreaming) {
                application.stopService(Intent(applicationContext, StreamService::class.java))
            }
            viewModel.disableCamera()
            viewModel.destroyUsbMonitor()
            super.onBackPressed()
            return
        }

        isBackPressedOnce = true
        showMessage("Press back button again to exit")
        Handler().postDelayed({
            isBackPressedOnce = false
        }, 2000)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        data?.let {
            if (requestCode == RC_AUTH) {
                viewModel.handleAuthorizationResponse(data)
            }
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    /**
     * Displays given message in a toast.
     *
     * @param msg Text to display
     */
    private fun showMessage(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    /**
     * Record audio request permission launcher
     */
    private val audioRequestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                viewModel.toggleAudio()
            } else {
                showMessage("Record audio permission denied")
            }
        }

    /**
     * Video onClickListener object
     */
    private var videoOnClickListener = View.OnClickListener {
        viewModel.toggleVideo()
    }

    /**
     * Audio onClickListener object
     */
    private var audioOnClickListener = View.OnClickListener {
        when (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO)) {
            PackageManager.PERMISSION_GRANTED -> {
                viewModel.toggleAudio()
            }
            else -> {
                audioRequestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    /**
     * Stream onClickListener object
     */
    private var streamOnClickListener = View.OnClickListener {
        if (isLoggedIn) {
            viewModel.toggleStream()
        } else {
            showMessage("Please log in to start streaming.")
        }
    }

    /**
     * menu fab onClickListener object
     */
    private var menuFabOnClickListener = View.OnClickListener {
        var alpha = 0F
        TransitionManager.beginDelayedTransition(fab_container, mTransition)
        if (isMenuPressed) {
            alpha = 0.75F
            mFabConstraintOn.applyTo(fab_container)
        } else {
            mFabConstraintOff.applyTo(fab_container)
        }
        ObjectAnimator.ofFloat(main_dimmed_bg, "alpha", alpha).apply {
            duration = 300
            start()
        }
        isMenuPressed = !isMenuPressed
    }

    /**
     * SurfaceView callback object
     */
    private var surfaceViewCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            Timber.v("RPISTREAM surfaceViewCallback: Surface created")
        }

        override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            Timber.v("RPISTREAM surfaceViewCallback: Surface changed")
            if (width == 0 || height == 0) return
            if (StreamService.isPreview) {
                viewModel.startPreview(width, height)
            }
        }

        override fun surfaceDestroyed(holder: SurfaceHolder?) {
            Timber.v("RPISTREAM surfaceViewCallback: Surface destroyed")
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
        viewModel.user.observe(this, Observer { user ->
            if (user != null)  {
                if (AuthState.jsonDeserialize(user.authStateJson).needsTokenRefresh) {
                    viewModel.logout()
                } else {
                    isLoggedIn = true
                    user_id.text = user.displayName
                    Glide.with(this).load(user.profileImage).into(user_icon)
                    acc_drawer.menu.findItem(R.id.action_login).isVisible = false
                    acc_drawer.menu.findItem(R.id.action_logout).isVisible = true
                }
            } else {
                isLoggedIn = false
                user_id.text = getString(R.string.no_user_text)
                user_icon.setImageResource(R.drawable.ic_baseline_account_circle_24)
                acc_drawer.menu.findItem(R.id.action_login).isVisible = true
                acc_drawer.menu.findItem(R.id.action_logout).isVisible = false
            }
        })

        viewModel.usbStatus.observe(this, Observer { status ->
            status?.let {
                when (it) {
                    UsbConnectStatus.ATTACHED -> showMessage("USB device attached")
                    UsbConnectStatus.DETACHED -> showMessage("USB device detached")
                }
            }
        })

        viewModel.connectStatus.observe(this, Observer { status ->
            status?.let {
                when (it) {
                    RtmpConnectStatus.SUCCESS -> {
                        stream_fab_text.text = getText(R.string.stream_on_text)
                        stream_fab.backgroundTintList = ColorStateList.valueOf(
                            ContextCompat.getColor(this@MainActivity, R.color.colorAccent)
                        )
                        showMessage("Connection success")
                    }
                    RtmpConnectStatus.FAIL -> showMessage("Connection failed")
                    RtmpConnectStatus.DISCONNECT -> {
                        stream_fab_text.text = getText(R.string.stream_off_text)
                        stream_fab.backgroundTintList = ColorStateList.valueOf(
                            ContextCompat.getColor(this@MainActivity, R.color.colorPrimary)
                        )
                        showMessage("Disconnected")
                    }
                }
            }
        })

        viewModel.authStatus.observe(this, Observer { status ->
            status?.let {
                when (it) {
                    RtmpAuthStatus.SUCCESS -> showMessage("Auth success")
                    RtmpAuthStatus.FAIL -> showMessage("Auth error")
                }
            }
        })

        viewModel.videoStatus.observe(this, Observer { status ->
            if (status != null) {
                video_fab.setImageResource(R.drawable.ic_baseline_videocam_24)
                video_fab.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(this@MainActivity, R.color.colorAccent)
                )
                video_fab_text.text = status
            } else {
                video_fab.setImageResource(R.drawable.ic_baseline_videocam_off_24)
                video_fab.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(this@MainActivity, R.color.colorPrimary)
                )
                video_fab_text.text = getText(R.string.video_off_text)
            }
        })

        viewModel.audioStatus.observe(this, Observer { status ->
            if (status != null) {
                audio_fab.setImageResource(R.drawable.ic_baseline_mic_24)
                audio_fab.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(this@MainActivity, R.color.colorAccent)
                )
                audio_fab_text.text = status
            } else {
                audio_fab.setImageResource(R.drawable.ic_baseline_mic_off_24)
                audio_fab.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(this@MainActivity, R.color.colorPrimary)
                )
                audio_fab_text.text = getString(R.string.audio_off_text)
            }
        })
    }
}