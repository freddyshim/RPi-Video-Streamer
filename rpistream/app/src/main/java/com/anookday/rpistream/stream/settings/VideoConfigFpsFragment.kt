package com.anookday.rpistream.stream.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.anookday.rpistream.R
import com.anookday.rpistream.databinding.FragmentVideoConfigFpsBinding
import com.anookday.rpistream.repository.database.User
import com.anookday.rpistream.stream.CurrentFragmentName
import com.anookday.rpistream.stream.StreamActivity
import com.anookday.rpistream.stream.StreamViewModel

class VideoConfigFpsFragment : Fragment() {
    private lateinit var binding: FragmentVideoConfigFpsBinding
    private val viewModel: StreamViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentVideoConfigFpsBinding.inflate(inflater, container, false)
        viewModel.apply {
            user.observe(viewLifecycleOwner, ::onUserChange)
        }

        return binding.root
    }

    override fun onResume() {
        viewModel.setCurrentFragment(CurrentFragmentName.VIDEO_CONFIG_FPS)
        (activity as StreamActivity).editNavigationDrawer(
            R.string.video_config_fps_title,
            R.drawable.ic_baseline_arrow_back_24,
            false
        )
        super.onResume()
    }

    private fun onUserChange(user: User?) {
        user?.settings?.videoConfig?.let { config ->
            binding.apply {
                when (config.fps) {
                    24 -> fpsRadioGroup.check(R.id.fps_24)
                    30 -> fpsRadioGroup.check(R.id.fps_30)
                    60 -> fpsRadioGroup.check(R.id.fps_60)
                }
                fpsRadioGroup.setOnCheckedChangeListener { _, checkedId ->
                    when (checkedId) {
                        R.id.fps_24 -> viewModel.updateVideoFps(24)
                        R.id.fps_30 -> viewModel.updateVideoFps(30)
                        R.id.fps_60 -> viewModel.updateVideoFps(60)
                    }
                }
            }
        }
    }
}