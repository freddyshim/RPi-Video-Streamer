package com.anookday.rpistream

import android.animation.ObjectAnimator
import android.app.PendingIntent
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.text.Editable
import android.text.TextWatcher
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.view.MenuItem
import android.view.SurfaceHolder
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.databinding.DataBindingUtil
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.anookday.rpistream.databinding.ActivityMainBinding
import com.anookday.rpistream.oauth.TwitchManager
import com.google.android.material.navigation.NavigationView
import com.serenegiant.usb.CameraDialog
import com.serenegiant.usb.USBMonitor
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fab_toggle_on.*
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import timber.log.Timber

/**
 * Stream (main) activity class.
 */
class MainActivity : AppCompatActivity(), CameraDialog.CameraDialogParent {
    private lateinit var mFabConstraintOn : ConstraintSet
    private lateinit var mFabConstraintOff : ConstraintSet
    private lateinit var mTransition : ChangeBounds
    private val twitchManager = TwitchManager(this)
    private var isBackPressedOnce = false
    private var isMenuPressed = true

    private val viewModel: MainViewModel by lazy {
        ViewModelProvider(this).get(MainViewModel::class.java)
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
                    twitchManager.authorize()
                    app_container.closeDrawers()
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
        video_fab.setOnClickListener(uvcCameraOnClickListener)
        stream_fab.setOnClickListener(streamOnClickListener)
        menu_fab.setOnClickListener(menuFabOnClickListener)

        // set event callbacks
        binding.cameraPreview.holder.addCallback(surfaceViewCallback)
        binding.streamUriTextbox.addTextChangedListener(onUriChangeListener)

        // set observers
        setObservers()

        // initialize ViewModel objects
        viewModel.init(this, binding.cameraPreview)
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
        showMessage("Press back button again to exit")
        Handler().postDelayed({
            isBackPressedOnce = false
        }, 2000)
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
    private var onUriChangeListener = object : TextWatcher {
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
    }
}