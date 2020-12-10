package com.anookday.rpistream.stream.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.anookday.rpistream.R
import com.anookday.rpistream.databinding.FragmentAudioConfigBinding
import com.anookday.rpistream.repository.database.User
import com.anookday.rpistream.stream.CurrentFragmentName
import com.anookday.rpistream.stream.StreamActivity
import com.anookday.rpistream.stream.StreamViewModel

class AudioConfigFragment: Fragment() {
    private lateinit var binding: FragmentAudioConfigBinding
    private val viewModel: StreamViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentAudioConfigBinding.inflate(inflater, container, false).apply {
            model = viewModel
            bitrateItem.onClick = { _ ->
                findNavController().navigate(R.id.action_audioConfigFragment_to_audioConfigBitrateFragment)
            }
            samplerateItem.onClick = { _ ->
                findNavController().navigate(R.id.action_audioConfigFragment_to_audioConfigSamplerateFragment)
            }
        }
        viewModel.apply {
            user.observe(viewLifecycleOwner, ::onUserChange)
        }

        return binding.root
    }

    override fun onResume() {
        viewModel.setCurrentFragment(CurrentFragmentName.AUDIO_CONFIG)
        (activity as StreamActivity).apply {
            supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_baseline_arrow_back_24)
            editNavigationDrawer(getString(R.string.audio_config_title), false)
        }
        super.onResume()
    }

    private fun onUserChange(user: User?) {
        user?.settings?.audioConfig?.let { config ->
            binding.apply {
                bitrateItem.subtitle = getString(R.string.audio_config_bitrate_subtitle, config.bitrate / 1024)
                samplerateItem.subtitle = getString(R.string.audio_config_samplerate_subtitle, config.sampleRate)
                stereoItem.checked = config.stereo
                echoCancelerItem.checked = config.echoCanceler
                noiseSuppressorItem.checked = config.noiseSuppressor
            }
        }
    }
}