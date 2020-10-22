package com.anookday.rpistream.fragments

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import android.transition.ChangeBounds
import android.view.SurfaceHolder
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import com.anookday.rpistream.*
import com.anookday.rpistream.databinding.FragmentStreamBinding
import com.anookday.rpistream.stream.StreamService
import kotlinx.android.synthetic.main.fab_toggle_off.*
import kotlinx.android.synthetic.main.fragment_stream.*
import timber.log.Timber

class StreamFragment : Fragment() {
    private lateinit var binding: FragmentStreamBinding
    private val viewModel: MainViewModel by activityViewModels()
    private var isMenuPressed = false

    /**
     * Record audio request permission launcher
     */
    private val audioRequestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                viewModel.toggleAudio()
            } else {
                showMessage("audioRequestPermissionLauncher: Record audio permission denied")
            }
        }

    /**
     * SurfaceView callback object
     */
    private var surfaceViewCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            Timber.v("surfaceViewCallback: Surface created")
        }

        override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            Timber.v("surfaceViewCallback: Surface changed")
            if (width == 0 || height == 0) return
            if (StreamService.isPreview) {
                viewModel.startPreview(width, height)
            }
        }

        override fun surfaceDestroyed(holder: SurfaceHolder?) {
            Timber.v("surfaceViewCallback: Surface destroyed")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // initialize data binding
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_stream, container, false)
        // set event callbacks
        binding.cameraPreview.holder.addCallback(surfaceViewCallback)
        // initialize ViewModel objects
        viewModel.init(requireContext(), binding.cameraPreview)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // set onClick listeners
        setOnClickListeners()
        // set LiveData observers
        setObservers()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        viewModel.setCurrentFragment(CurrentFragmentName.STREAM)
    }

    override fun onResume() {
        super.onResume()
        (activity as MainActivity).enableHeaderAndDrawer()
    }

    /**
     * Displays given message in a toast.
     *
     * @param msg Text to display
     */
    private fun showMessage(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    /**
     * Initialize view onClickListeners.
     */
    private fun setOnClickListeners() {
        val fabConstraintOn = ConstraintSet()
        fabConstraintOn.clone(context, R.layout.fab_toggle_on)
        val fabConstraintOff = ConstraintSet()
        fabConstraintOff.clone(context, R.layout.fab_toggle_off)
        val fabTransition = ChangeBounds()
        fabTransition.interpolator = OvershootInterpolator(1.0F)

        menu_fab.setOnClickListener {
            isMenuPressed = !isMenuPressed
            var alpha = 0F
            TransitionManager.beginDelayedTransition(fab_container, fabTransition)
            if (isMenuPressed) {
                alpha = 0.75F
                fabConstraintOn.applyTo(fab_container)
            } else {
                fabConstraintOff.applyTo(fab_container)
            }
            ObjectAnimator.ofFloat(main_dimmed_bg, "alpha", alpha).apply{
                duration = 300
                start()
            }
        }

        video_fab.setOnClickListener {
            viewModel.toggleVideo()
        }

        audio_fab.setOnClickListener {
            when (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)) {
                PackageManager.PERMISSION_GRANTED -> {
                    viewModel.toggleAudio()
                }
                else -> {
                    audioRequestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }

        stream_fab.setOnClickListener {
            viewModel.toggleStream()
        }
    }

    /**
     * Initialize view model LiveData value observers.
     */
    private fun setObservers() {
        viewModel.usbStatus.observe(viewLifecycleOwner, Observer { status ->
            status?.let {
                when (it) {
                    UsbConnectStatus.ATTACHED -> showMessage("USB device attached")
                    UsbConnectStatus.DETACHED -> showMessage("USB device detached")
                }
            }
        })

        viewModel.connectStatus.observe(viewLifecycleOwner, Observer { status ->
            status?.let {
                when (it) {
                    RtmpConnectStatus.SUCCESS -> {
                        stream_fab_text.text = getText(R.string.stream_on_text)
                        stream_fab.backgroundTintList = ColorStateList.valueOf(
                            ContextCompat.getColor(requireContext(), R.color.colorAccent)
                        )
                        showMessage("Connection success")
                    }
                    RtmpConnectStatus.FAIL -> showMessage("Connection failed")
                    RtmpConnectStatus.DISCONNECT -> {
                        stream_fab_text.text = getText(R.string.stream_off_text)
                        stream_fab.backgroundTintList = ColorStateList.valueOf(
                            ContextCompat.getColor(requireContext(), R.color.colorPrimary)
                        )
                        showMessage("Disconnected")
                    }
                }
            }
        })

        viewModel.authStatus.observe(viewLifecycleOwner, Observer { status ->
            status?.let {
                when (it) {
                    RtmpAuthStatus.SUCCESS -> showMessage("Auth success")
                    RtmpAuthStatus.FAIL -> showMessage("Auth error")
                }
            }
        })

        viewModel.videoStatus.observe(viewLifecycleOwner, Observer { status ->
            if (status != null) {
                video_fab.setImageResource(R.drawable.ic_baseline_videocam_24)
                video_fab.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.colorAccent)
                )
                video_fab_text.text = status
            } else {
                video_fab.setImageResource(R.drawable.ic_baseline_videocam_off_24)
                video_fab.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.colorPrimary)
                )
                video_fab_text.text = getText(R.string.video_off_text)
            }
        })

        viewModel.audioStatus.observe(viewLifecycleOwner, Observer { status ->
            if (status != null) {
                audio_fab.setImageResource(R.drawable.ic_baseline_mic_24)
                audio_fab.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.colorAccent)
                )
                audio_fab_text.text = status
            } else {
                audio_fab.setImageResource(R.drawable.ic_baseline_mic_off_24)
                audio_fab.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.colorPrimary)
                )
                audio_fab_text.text = getString(R.string.audio_off_text)
            }
        })
    }
}