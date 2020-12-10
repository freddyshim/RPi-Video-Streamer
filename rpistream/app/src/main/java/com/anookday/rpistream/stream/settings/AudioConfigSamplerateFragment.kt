package com.anookday.rpistream.stream.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.anookday.rpistream.R
import com.anookday.rpistream.databinding.FragmentAudioConfigSamplerateBinding
import com.anookday.rpistream.repository.database.User
import com.anookday.rpistream.stream.CurrentFragmentName
import com.anookday.rpistream.stream.StreamActivity
import com.anookday.rpistream.stream.StreamViewModel

class AudioConfigSamplerateFragment: Fragment() {
    private lateinit var binding: FragmentAudioConfigSamplerateBinding
    private val viewModel: StreamViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentAudioConfigSamplerateBinding.inflate(inflater, container, false)
        viewModel.apply {
            user.observe(viewLifecycleOwner, ::onUserChange)
        }

        return binding.root
    }

    override fun onResume() {
        viewModel.setCurrentFragment(CurrentFragmentName.AUDIO_CONFIG_SAMPLERATE)
        (activity as StreamActivity).apply {
            supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_baseline_arrow_back_24)
            editNavigationDrawer(getString(R.string.audio_config_samplerate_title), false)
        }
        super.onResume()
    }

    private fun onUserChange(user: User?) {
        user?.settings?.audioConfig?.let { config ->
            binding.apply {
                when (config.sampleRate) {
                    32000 -> audioSamplerateRadioGroup.check(R.id.audio_samplerate_32000)
                    44100 -> audioSamplerateRadioGroup.check(R.id.audio_samplerate_44100)
                    48000 -> audioSamplerateRadioGroup.check(R.id.audio_samplerate_48000)
                }

                audioSamplerateRadioGroup.setOnCheckedChangeListener { _, checkedId ->
                    when (checkedId) {
                        R.id.audio_samplerate_32000 -> viewModel.updateAudioSamplerate(32000)
                        R.id.audio_samplerate_44100 -> viewModel.updateAudioSamplerate(44100)
                        R.id.audio_samplerate_48000 -> viewModel.updateAudioSamplerate(48000)
                    }
                }
            }
        }
    }
}