package com.anookday.rpistream.stream.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.anookday.rpistream.R
import com.anookday.rpistream.databinding.FragmentVideoConfigBinding
import com.anookday.rpistream.repository.database.User
import com.anookday.rpistream.stream.CurrentFragmentName
import com.anookday.rpistream.stream.StreamActivity
import com.anookday.rpistream.stream.StreamViewModel

/**
 * Video base configuration fragment.
 */
class VideoConfigFragment : Fragment() {
    private lateinit var binding: FragmentVideoConfigBinding
    private val viewModel: StreamViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentVideoConfigBinding.inflate(inflater, container, false).apply {
            model = viewModel
            resolutionItem.onClick = { _ ->
                findNavController().navigate(R.id.action_videoConfigFragment_to_videoConfigResolutionFragment)
            }
            fpsItem.onClick = { _ ->
                findNavController().navigate(R.id.action_videoConfigFragment_to_videoConfigFpsFragment)
            }
            bitrateItem.onClick = { _ ->
                findNavController().navigate(R.id.action_videoConfigFragment_to_videoConfigBitrateFragment)
            }
            iframeIntervalItem.onClick = { _ ->
                findNavController().navigate(R.id.action_videoConfigFragment_to_videoConfigIFrameFragment)
            }
            rotationItem.onClick = { _ ->
                findNavController().navigate(R.id.action_videoConfigFragment_to_videoConfigRotationFragment)
            }
        }
        viewModel.apply {
            user.observe(viewLifecycleOwner, ::onUserChange)
        }

        return binding.root
    }

    override fun onResume() {
        viewModel.setCurrentFragment(CurrentFragmentName.VIDEO_CONFIG)

        (activity as StreamActivity).editNavigationDrawer(
            R.string.video_config_title,
            R.drawable.ic_baseline_arrow_back_24,
            false
        )
        super.onResume()
    }

    private fun onUserChange(user: User?) {
        user?.settings?.videoConfig?.let { config ->
            binding.apply {
                resolutionItem.subtitle = getString(
                    R.string.video_config_resolution_subtitle,
                    config.width,
                    config.height
                )
                fpsItem.subtitle = config.fps.toString()
                bitrateItem.subtitle =
                    getString(R.string.video_config_bitrate_subtitle, config.bitrate / 1024)
                iframeIntervalItem.subtitle =
                    getString(R.string.video_config_iframe_subtitle, config.iFrameInterval)
                rotationItem.subtitle = config.rotation.toString()
            }
        }
    }
}