package com.anookday.rpistream.stream.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.anookday.rpistream.R
import com.anookday.rpistream.databinding.FragmentSettingsBinding
import com.anookday.rpistream.repository.database.User
import com.anookday.rpistream.stream.CurrentFragmentName
import com.anookday.rpistream.stream.StreamActivity
import com.anookday.rpistream.stream.StreamViewModel

class SettingsFragment: Fragment() {
    private lateinit var binding: FragmentSettingsBinding
    private val viewModel: StreamViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentSettingsBinding.inflate(inflater, container, false).apply {
            model = viewModel
            videoConfigItem.onClick = { _ ->
                findNavController().navigate(R.id.action_settingsFragment_to_videoConfigFragment)
            }
            audioConfigItem.onClick = { _ ->
                findNavController().navigate(R.id.action_settingsFragment_to_audioConfigFragment)
            }
            darkModeItem.onClick = { _ ->
                findNavController().navigate(R.id.action_settingsFragment_to_darkModeFragment)
            }
        }
        viewModel.apply {
            user.observe(viewLifecycleOwner, ::onUserChange)
        }

        return binding.root
    }

    override fun onResume() {
        viewModel.setCurrentFragment(CurrentFragmentName.SETTINGS)
        (activity as StreamActivity).apply {
            supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_baseline_arrow_back_24)
            editNavigationDrawer(getString(R.string.settings_title), false)
        }
        super.onResume()
    }

    private fun onUserChange(user: User?) {
        user?.settings?.let {
            binding.apply {
                settings = it
                when (it.darkMode) {
                    DarkMode.ON.value -> darkModeItem.subtitle = getString(R.string.dark_mode_on)
                    DarkMode.OFF.value -> darkModeItem.subtitle = getString(R.string.dark_mode_off)
                    DarkMode.SYSTEM.value -> darkModeItem.subtitle = getString(R.string.dark_mode_system)
                }
            }
        }
    }
}