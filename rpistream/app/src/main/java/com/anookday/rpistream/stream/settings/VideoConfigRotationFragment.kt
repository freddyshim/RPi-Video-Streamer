package com.anookday.rpistream.stream.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.anookday.rpistream.R
import com.anookday.rpistream.databinding.FragmentVideoConfigRotationBinding
import com.anookday.rpistream.repository.database.User
import com.anookday.rpistream.stream.CurrentFragmentName
import com.anookday.rpistream.stream.StreamActivity
import com.anookday.rpistream.stream.StreamViewModel

class VideoConfigRotationFragment : Fragment() {

    private lateinit var binding: FragmentVideoConfigRotationBinding
    private val viewModel: StreamViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentVideoConfigRotationBinding.inflate(inflater, container, false)
        viewModel.apply {
            user.observe(viewLifecycleOwner, ::onUserChange)
        }

        return binding.root
    }

    override fun onResume() {
        viewModel.setCurrentFragment(CurrentFragmentName.VIDEO_CONFIG_ROTATION)
        (activity as StreamActivity).editNavigationDrawer(
            R.string.video_config_rotation_title,
            R.drawable.ic_baseline_arrow_back_24,
            false
        )
        super.onResume()
    }

    private fun onUserChange(user: User?) {
        user?.settings?.videoConfig?.let { config ->
            binding.apply {
                when (config.rotation) {
                    0 -> rotationRadioGroup.check(R.id.rotation_0)
                    90 -> rotationRadioGroup.check(R.id.rotation_90)
                    180 -> rotationRadioGroup.check(R.id.rotation_180)
                    270 -> rotationRadioGroup.check(R.id.rotation_270)
                }

                rotationRadioGroup.setOnCheckedChangeListener { _, checkedId ->
                    when (checkedId) {
                        R.id.rotation_0 -> viewModel.updateVideoRotation(0)
                        R.id.rotation_90 -> viewModel.updateVideoRotation(90)
                        R.id.rotation_180 -> viewModel.updateVideoRotation(180)
                        R.id.rotation_270 -> viewModel.updateVideoRotation(270)
                    }
                }
            }
        }
    }
}