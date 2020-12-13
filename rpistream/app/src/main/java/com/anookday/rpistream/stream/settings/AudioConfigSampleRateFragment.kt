package com.anookday.rpistream.stream.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.anookday.rpistream.R
import com.anookday.rpistream.databinding.FragmentAudioConfigSampleRateBinding
import com.anookday.rpistream.repository.database.User
import com.anookday.rpistream.stream.CurrentFragmentName
import com.anookday.rpistream.stream.StreamActivity
import com.anookday.rpistream.stream.StreamViewModel

class AudioConfigSampleRateFragment : Fragment() {
    private lateinit var binding: FragmentAudioConfigSampleRateBinding
    private val viewModel: StreamViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentAudioConfigSampleRateBinding.inflate(inflater, container, false)
        viewModel.apply {
            user.observe(viewLifecycleOwner, ::onUserChange)
        }

        return binding.root
    }

    override fun onResume() {
        viewModel.setCurrentFragment(CurrentFragmentName.AUDIO_CONFIG_SAMPLERATE)
        (activity as StreamActivity).editNavigationDrawer(
            R.string.audio_config_sample_rate_title,
            R.drawable.ic_baseline_arrow_back_24,
            false
        )
        super.onResume()
    }

    private fun onUserChange(user: User?) {
        user?.settings?.audioConfig?.let { config ->
            binding.apply {
                when (config.sampleRate) {
                    32000 -> audioSampleRateRadioGroup.check(R.id.audio_sample_rate_32000)
                    44100 -> audioSampleRateRadioGroup.check(R.id.audio_sample_rate_44100)
                    48000 -> audioSampleRateRadioGroup.check(R.id.audio_sample_rate_48000)
                }

                audioSampleRateRadioGroup.setOnCheckedChangeListener { _, checkedId ->
                    when (checkedId) {
                        R.id.audio_sample_rate_32000 -> viewModel.updateAudioSampleRate(32000)
                        R.id.audio_sample_rate_44100 -> viewModel.updateAudioSampleRate(44100)
                        R.id.audio_sample_rate_48000 -> viewModel.updateAudioSampleRate(48000)
                    }
                }
            }
        }
    }
}