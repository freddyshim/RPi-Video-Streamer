package com.anookday.rpistream.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.anookday.rpistream.CurrentFragmentName
import com.anookday.rpistream.MainActivity
import com.anookday.rpistream.MainViewModel
import com.anookday.rpistream.R
import com.anookday.rpistream.databinding.FragmentLandingBinding

class LandingFragment : Fragment() {
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding: FragmentLandingBinding =
            DataBindingUtil.inflate(inflater, R.layout.fragment_landing, container, false)

        return binding.root
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        viewModel.setCurrentFragment(CurrentFragmentName.LANDING)
    }

    override fun onResume() {
        super.onResume()
        (activity as MainActivity).disableHeaderAndDrawer()
    }
}