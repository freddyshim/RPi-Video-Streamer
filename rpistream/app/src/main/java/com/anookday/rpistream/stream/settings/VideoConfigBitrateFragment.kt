package com.anookday.rpistream.stream.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.anookday.rpistream.R
import com.anookday.rpistream.databinding.FragmentVideoConfigBitrateBinding
import com.anookday.rpistream.repository.database.User
import com.anookday.rpistream.stream.CurrentFragmentName
import com.anookday.rpistream.stream.StreamActivity
import com.anookday.rpistream.stream.StreamViewModel
import com.anookday.rpistream.util.Constants

class VideoConfigBitrateFragment: Fragment() {
    private lateinit var binding: FragmentVideoConfigBitrateBinding
    private val viewModel: StreamViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentVideoConfigBitrateBinding.inflate(inflater, container, false)
        viewModel.apply {
            user.observe(viewLifecycleOwner, ::onUserChange)
        }

        return binding.root
    }

    override fun onResume() {
        viewModel.setCurrentFragment(CurrentFragmentName.VIDEO_CONFIG_BITRATE)
        (activity as StreamActivity).apply {
            supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_baseline_arrow_back_24)
                editNavigationDrawer(getString(R.string.video_config_bitrate_title), false)
        }
        super.onResume()
    }

    private fun onUserChange(user: User?) {
        user?.settings?.videoConfig?.let { config ->
            binding.apply {
                when (config.bitrate / Constants.KILOBYTE_SIZE) {
                    2000 -> videoBitrateRadioGroup.check(R.id.video_bitrate_2000)
                    4000 -> videoBitrateRadioGroup.check(R.id.video_bitrate_4000)
                    6000 -> videoBitrateRadioGroup.check(R.id.video_bitrate_6000)
                    8000 -> videoBitrateRadioGroup.check(R.id.video_bitrate_8000)
                    10000 -> videoBitrateRadioGroup.check(R.id.video_bitrate_10000)
                }

                videoBitrateRadioGroup.setOnCheckedChangeListener { _, checkedId ->
                    when (checkedId) {
                        R.id.video_bitrate_2000 -> viewModel.updateVideoBitrate(2000 * Constants.KILOBYTE_SIZE)
                        R.id.video_bitrate_4000 -> viewModel.updateVideoBitrate(4000 * Constants.KILOBYTE_SIZE)
                        R.id.video_bitrate_6000 -> viewModel.updateVideoBitrate(6000 * Constants.KILOBYTE_SIZE)
                        R.id.video_bitrate_8000 -> viewModel.updateVideoBitrate(8000 * Constants.KILOBYTE_SIZE)
                        R.id.video_bitrate_10000 -> viewModel.updateVideoBitrate(10000 * Constants.KILOBYTE_SIZE)
                    }
                }
            }
        }
    }
}