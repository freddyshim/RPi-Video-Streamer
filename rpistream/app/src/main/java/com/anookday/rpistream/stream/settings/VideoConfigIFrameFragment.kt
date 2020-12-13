package com.anookday.rpistream.stream.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.anookday.rpistream.R
import com.anookday.rpistream.databinding.FragmentVideoConfigIframeBinding
import com.anookday.rpistream.repository.database.User
import com.anookday.rpistream.stream.CurrentFragmentName
import com.anookday.rpistream.stream.StreamActivity
import com.anookday.rpistream.stream.StreamViewModel

class VideoConfigIFrameFragment : Fragment() {

    private lateinit var binding: FragmentVideoConfigIframeBinding
    private val viewModel: StreamViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentVideoConfigIframeBinding.inflate(inflater, container, false)
        viewModel.apply {
            user.observe(viewLifecycleOwner, ::onUserChange)
        }

        return binding.root
    }

    override fun onResume() {
        viewModel.setCurrentFragment(CurrentFragmentName.VIDEO_CONFIG_IFRAME)
        (activity as StreamActivity).editNavigationDrawer(
            R.string.video_config_iframe_title,
            R.drawable.ic_baseline_arrow_back_24,
            false
        )
        super.onResume()
    }

    private fun onUserChange(user: User?) {
        user?.settings?.videoConfig?.let { config ->
            binding.apply {
                when (config.iFrameInterval) {
                    0 -> iframeRadioGroup.check(R.id.iframe_0)
                    1 -> iframeRadioGroup.check(R.id.iframe_1)
                    2 -> iframeRadioGroup.check(R.id.iframe_2)
                    3 -> iframeRadioGroup.check(R.id.iframe_3)
                    4 -> iframeRadioGroup.check(R.id.iframe_4)
                    5 -> iframeRadioGroup.check(R.id.iframe_5)
                }

                iframeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
                    when (checkedId) {
                        R.id.iframe_0 -> viewModel.updateVideoIFrame(0)
                        R.id.iframe_1 -> viewModel.updateVideoIFrame(1)
                        R.id.iframe_2 -> viewModel.updateVideoIFrame(2)
                        R.id.iframe_3 -> viewModel.updateVideoIFrame(3)
                        R.id.iframe_4 -> viewModel.updateVideoIFrame(4)
                        R.id.iframe_5 -> viewModel.updateVideoIFrame(5)
                    }
                }
            }
        }
    }
}