package com.anookday.rpistream.stream

import android.Manifest
import android.animation.ObjectAnimator
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.ImageFormat
import android.os.Bundle
import android.transition.TransitionManager
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import android.transition.ChangeBounds
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.anookday.rpistream.*
import com.anookday.rpistream.chat.TwitchChatAdapter
import com.anookday.rpistream.databinding.FragmentStreamBinding
import com.anookday.rpistream.repository.database.Message
import com.anookday.rpistream.util.Constants
import kotlinx.android.synthetic.main.activity_stream.*
import kotlinx.android.synthetic.main.fab_toggle_off.*
import kotlinx.android.synthetic.main.fragment_stream.*
import timber.log.Timber

/**
 * Main fragment for [StreamActivity].
 */
class StreamFragment : Fragment() {
    private lateinit var binding: FragmentStreamBinding
    private lateinit var chatAdapter: TwitchChatAdapter
    private val viewModel: StreamViewModel by activityViewModels()
    private lateinit var preview: SurfaceView

    // fab animation
    private lateinit var fabConstraintOn: ConstraintSet
    private lateinit var fabConstraintOff: ConstraintSet
    private lateinit var fabTransition: ChangeBounds
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

    private val cameraRequestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            // TODO
        } else {
            showMessage("cameraRequestPermissionLauncher: Camera permission denied")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        chatAdapter = TwitchChatAdapter(context)
        fabConstraintOn = ConstraintSet().apply { clone(context, R.layout.fab_toggle_on) }
        fabConstraintOff = ConstraintSet().apply { clone(context, R.layout.fab_toggle_off) }
        fabTransition = ChangeBounds().apply { interpolator = OvershootInterpolator(1.0F) }
        isMenuPressed = false

        try {
            binding = FragmentStreamBinding.inflate(inflater, container, false).apply {
                chatMessages.apply {
                    setHasFixedSize(true)
                    layoutManager = LinearLayoutManager(context)
                    adapter = chatAdapter
                }

                // init GLSurfaceView
                preview = cameraPreview

                viewModel.apply {
                    init(requireContext(), cameraPreview)
                }

            }
        } catch (e: Exception) {
            Timber.e(e)
        }

        cameraRequestPermissionLauncher.launch(Manifest.permission.CAMERA)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        menu_fab.setOnClickListener(::onMenuFabClick)
        selfie_fab.setOnClickListener(::onSelfieFabClick)
        ae_toggle_fab.setOnClickListener(::onAutoExposureToggleFabClick)
        video_fab.setOnClickListener(::onVideoFabClick)
        audio_fab.setOnClickListener(::onAudioFabClick)
        stream_fab.setOnClickListener(::onStreamFabClick)
        chat_edit_message_send.setOnClickListener(::onMessageSubmit)
        chat_edit_message.setOnEditorActionListener(::onMessageActionDone)

        viewModel.apply {
            usbStatus.observe(viewLifecycleOwner, ::observeUsbStatus)
            connectStatus.observe(viewLifecycleOwner, ::observeConnectStatus)
            authStatus.observe(viewLifecycleOwner, ::observeAuthStatus)
            selfieToggleStatus.observe(viewLifecycleOwner, ::observeSelfieStatus)
            aeToggleStatus.observe(viewLifecycleOwner, ::observeAutoExposureToggleStatus)
            videoStatus.observe(viewLifecycleOwner, ::observeVideoStatus)
            audioStatus.observe(viewLifecycleOwner, ::observeAudioStatus)
            chatMessages.observe(viewLifecycleOwner, ::observeChatMessages)
            videoBitrate.observe(viewLifecycleOwner, ::observeVideoBitrate)
        }
    }

    override fun onResume() {
        viewModel.setCurrentFragment(CurrentFragmentName.STREAM)
        (activity as StreamActivity).editNavigationDrawer(
            R.string.app_name,
            R.drawable.ic_baseline_menu_24,
            true
        )
        super.onResume()
        //preview.onResume()
    }

    override fun onPause() {
        super.onPause()
        //preview.onPause()
    }

    override fun onStop() {
        super.onStop()
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
     * Click listener for menu fab.
     */
    private fun onMenuFabClick(view: View) {
        isMenuPressed = !isMenuPressed
        var alpha = 0F
        TransitionManager.beginDelayedTransition(fab_container, fabTransition)
        if (isMenuPressed) {
            alpha = 0.75F
            fabConstraintOn.applyTo(fab_container)
        } else {
            fabConstraintOff.applyTo(fab_container)
        }
        ObjectAnimator.ofFloat(main_dimmed_bg, "alpha", alpha).apply {
            duration = 300
            start()
        }
    }

    /**
     * Click listener for selfie fab.
     */
    private fun onSelfieFabClick(view: View) {
        viewModel.toggleSelfieCam()
    }

    /**
     * Click listener for auto exposure toggle fab.
     */
    private fun onAutoExposureToggleFabClick(view: View) {
        viewModel.toggleAutoExposure()
    }

    /**
     * Click listener for video fab.
     */
    private fun onVideoFabClick(view: View) {
        viewModel.toggleVideo()
    }

    /**
     * Click listener for audio fab.
     */
    private fun onAudioFabClick(view: View) {
        when (ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO
        )) {
            PackageManager.PERMISSION_GRANTED -> {
                viewModel.toggleAudio()
            }
            else -> {
                audioRequestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    /**
     * Click listener for stream fab.
     */
    private fun onStreamFabClick(view: View) {
        viewModel.toggleStream()
    }

    /**
     * Listener for when user clicks "done" on keyboard while editing message text.
     */
    private fun onMessageActionDone(view: TextView, id: Int, event: KeyEvent?): Boolean {
        if (id == EditorInfo.IME_ACTION_DONE) {
            onMessageSubmit(view)
        }
        return false
    }

    /**
     * Listener for submitting messages.
     */
    private fun onMessageSubmit(view: View) {
        chat_edit_message.apply {
            if (text.isNotEmpty()) {
                viewModel.sendMessage(text.toString())
                text = null
            }

        }
    }

    /**
     * Observer for USB status [LiveData].
     */
    private fun observeUsbStatus(status: UsbConnectStatus?) {
        status?.let {
            when (it) {
                UsbConnectStatus.ATTACHED -> showMessage("USB device attached")
                UsbConnectStatus.DETACHED -> showMessage("USB device detached")
            }
        }
    }

    /**
     * Observer for connection status [LiveData].
     */
    private fun observeConnectStatus(status: RtmpConnectStatus?) {
        status?.let {
            when (it) {
                RtmpConnectStatus.SUCCESS -> {
                    stream_fab_text.text = getText(R.string.stream_on_text)
                    stream_fab.backgroundTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.colorAccent)
                    )
                    (activity as StreamActivity).editNavigationDrawer(
                        R.string.currently_streaming_title,
                        null,
                        false
                    )
                    showMessage("Connection success")
                }
                RtmpConnectStatus.FAIL -> showMessage("Connection failed")
                RtmpConnectStatus.DISCONNECT -> {
                    stream_fab_text.text = getText(R.string.stream_off_text)
                    stream_fab.backgroundTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.colorPrimary)
                    )
                    (activity as StreamActivity).editNavigationDrawer(
                        R.string.app_name,
                        R.drawable.ic_baseline_menu_24,
                        true
                    )
                    showMessage("Disconnected")
                }
            }
        }
    }

    /**
     * Observer for authentication status [LiveData].
     */
    private fun observeAuthStatus(status: RtmpAuthStatus?) {
        status?.let {
            when (it) {
                RtmpAuthStatus.SUCCESS -> showMessage("Auth success")
                RtmpAuthStatus.FAIL -> showMessage("Auth error")
            }
        }
    }

    /**
     * Observer for selfie status [LiveData].
     */
    private fun observeSelfieStatus(status: Boolean) {
        if (status) {
            selfie_fab.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.colorAccent)
            )
            selfie_fab_text.text = getText(R.string.selfie_on_text)
        } else {
            selfie_fab.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.colorPrimary)
            )
            selfie_fab_text.text = getText(R.string.selfie_off_text)
        }
    }

    /**
     * Observer for auto exposure toggle status [LiveData].
     */
    private fun observeAutoExposureToggleStatus(status: Boolean) {
        if (status) {
            ae_toggle_fab.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.colorAccent)
            )
            ae_toggle_fab_text.text = getText(R.string.ae_on_text)
        } else {
            ae_toggle_fab.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.colorPrimary)
            )
            ae_toggle_fab_text.text = getText(R.string.ae_off_text)
        }
    }

    /**
     * Observer for video status [LiveData].
     */
    private fun observeVideoStatus(status: String?) {
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
    }

    /**
     * Observer for audio status [LiveData].
     */
    private fun observeAudioStatus(status: String?) {
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
    }

    /**
     * Observer for chat message list [LiveData].
     */
    private fun observeChatMessages(messages: List<Message>) {
        chatAdapter.submitList(messages)
        chat_messages?.scrollToPosition(chatAdapter.itemCount - 1)
    }

    /**
     * Observer for video bitrate [LiveData].
     */
    private fun observeVideoBitrate(bitrate: Long?) {
        if (bitrate != null) {
            video_bitrate_display.apply {
                text = getString(R.string.video_bitrate_display, bitrate / Constants.KILOBYTE_SIZE)
                visibility = View.VISIBLE
            }
        } else {
            video_bitrate_display.apply {
                text = null
                visibility = View.INVISIBLE
            }
        }
    }
}