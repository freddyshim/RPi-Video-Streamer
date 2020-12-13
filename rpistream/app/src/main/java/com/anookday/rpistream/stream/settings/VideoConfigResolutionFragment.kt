package com.anookday.rpistream.stream.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.anookday.rpistream.R
import com.anookday.rpistream.databinding.FragmentVideoConfigResolutionBinding
import com.anookday.rpistream.repository.database.User
import com.anookday.rpistream.stream.CurrentFragmentName
import com.anookday.rpistream.stream.StreamActivity
import com.anookday.rpistream.stream.StreamViewModel

class VideoConfigResolutionFragment : Fragment() {
    private lateinit var binding: FragmentVideoConfigResolutionBinding
    private val viewModel: StreamViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentVideoConfigResolutionBinding.inflate(inflater, container, false)
        viewModel.apply {
            user.observe(viewLifecycleOwner, ::onUserChange)
        }

        return binding.root
    }

    override fun onResume() {
        viewModel.setCurrentFragment(CurrentFragmentName.VIDEO_CONFIG_RESOLUTION)
        (activity as StreamActivity).editNavigationDrawer(
            R.string.video_config_resolution_title,
            R.drawable.ic_baseline_arrow_back_24,
            false
        )
        super.onResume()
    }

    private fun onUserChange(user: User?) {
        user?.settings?.videoConfig?.let { config ->
            binding.apply {
                when (config.height) {
                    360 -> resolutionRadioGroup.check(R.id.resolution_360)
                    480 -> resolutionRadioGroup.check(R.id.resolution_480)
                    720 -> resolutionRadioGroup.check(R.id.resolution_720)
                    1080 -> resolutionRadioGroup.check(R.id.resolution_1080)
                }

                resolutionRadioGroup.setOnCheckedChangeListener { _, checkedId ->
                    when (checkedId) {
                        R.id.resolution_360 -> viewModel.updateVideoResolution(480, 360)
                        R.id.resolution_480 -> viewModel.updateVideoResolution(640, 480)
                        R.id.resolution_720 -> viewModel.updateVideoResolution(1280, 720)
                        R.id.resolution_1080 -> viewModel.updateVideoResolution(1920, 1080)
                    }
                }
            }
        }
    }
}