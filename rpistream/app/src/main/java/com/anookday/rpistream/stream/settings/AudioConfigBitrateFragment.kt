package com.anookday.rpistream.stream.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.anookday.rpistream.R
import com.anookday.rpistream.databinding.FragmentAudioConfigBitrateBinding
import com.anookday.rpistream.databinding.FragmentVideoConfigBitrateBinding
import com.anookday.rpistream.repository.database.User
import com.anookday.rpistream.stream.CurrentFragmentName
import com.anookday.rpistream.stream.StreamActivity
import com.anookday.rpistream.stream.StreamViewModel
import com.anookday.rpistream.util.Constants

class AudioConfigBitrateFragment : Fragment() {
    private lateinit var binding: FragmentAudioConfigBitrateBinding
    private val viewModel: StreamViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentAudioConfigBitrateBinding.inflate(inflater, container, false)
        viewModel.apply {
            user.observe(viewLifecycleOwner, ::onUserChange)
        }

        return binding.root
    }

    override fun onResume() {
        viewModel.setCurrentFragment(CurrentFragmentName.AUDIO_CONFIG_BITRATE)
        (activity as StreamActivity).editNavigationDrawer(
            R.string.audio_config_bitrate_title,
            R.drawable.ic_baseline_arrow_back_24,
            false
        )
        super.onResume()
    }

    private fun onUserChange(user: User?) {
        user?.settings?.audioConfig?.let { config ->
            binding.apply {
                when (config.bitrate / Constants.KILOBYTE_SIZE) {
                    64 -> audioBitrateRadioGroup.check(R.id.audio_bitrate_64)
                    128 -> audioBitrateRadioGroup.check(R.id.audio_bitrate_128)
                    256 -> audioBitrateRadioGroup.check(R.id.audio_bitrate_256)
                    512 -> audioBitrateRadioGroup.check(R.id.audio_bitrate_512)
                }

                audioBitrateRadioGroup.setOnCheckedChangeListener { _, checkedId ->
                    when (checkedId) {
                        R.id.audio_bitrate_64 -> viewModel.updateAudioBitrate(64 * Constants.KILOBYTE_SIZE)
                        R.id.audio_bitrate_128 -> viewModel.updateAudioBitrate(128 * Constants.KILOBYTE_SIZE)
                        R.id.audio_bitrate_256 -> viewModel.updateAudioBitrate(256 * Constants.KILOBYTE_SIZE)
                        R.id.audio_bitrate_512 -> viewModel.updateAudioBitrate(512 * Constants.KILOBYTE_SIZE)
                    }
                }
            }
        }
    }
}