package com.anookday.rpistream.stream.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.anookday.rpistream.R
import com.anookday.rpistream.databinding.FragmentDarkModeBinding
import com.anookday.rpistream.repository.database.User
import com.anookday.rpistream.stream.CurrentFragmentName
import com.anookday.rpistream.stream.StreamActivity
import com.anookday.rpistream.stream.StreamViewModel

enum class DarkMode(val value: String) {
    ON("on"),
    OFF("off"),
    SYSTEM("system")
}

class DarkModeFragment : Fragment() {
    private lateinit var binding: FragmentDarkModeBinding
    private val viewModel: StreamViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentDarkModeBinding.inflate(inflater, container, false)
        viewModel.apply {
            user.observe(viewLifecycleOwner, ::onUserChange)
        }

        return binding.root
    }

    override fun onResume() {
        viewModel.setCurrentFragment(CurrentFragmentName.DARK_MODE)
        (activity as StreamActivity).editNavigationDrawer(
            R.string.dark_mode_title,
            R.drawable.ic_baseline_arrow_back_24,
            false
        )
        super.onResume()
    }

    private fun onUserChange(user: User?) {
        user?.settings?.darkMode?.let { mode ->
            binding.apply {
                when (mode) {
                    DarkMode.ON.value -> darkModeRadioGroup.check(R.id.dark_mode_on)
                    DarkMode.OFF.value -> darkModeRadioGroup.check(R.id.dark_mode_off)
                    DarkMode.SYSTEM.value -> darkModeRadioGroup.check(R.id.dark_mode_system)
                }

                darkModeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
                    when (checkedId) {
                        R.id.dark_mode_on -> viewModel.updateDarkMode(DarkMode.ON.value)
                        R.id.dark_mode_off -> viewModel.updateDarkMode(DarkMode.OFF.value)
                        R.id.dark_mode_system -> viewModel.updateDarkMode(DarkMode.SYSTEM.value)
                    }
                }
            }
        }
    }
}