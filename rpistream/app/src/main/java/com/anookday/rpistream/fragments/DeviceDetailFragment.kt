package com.anookday.rpistream.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.anookday.rpistream.databinding.DeviceDetailFragmentBinding
import com.anookday.rpistream.viewmodels.DeviceListViewModel
import timber.log.Timber

class DeviceDetailFragment: Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = DeviceDetailFragmentBinding.inflate(inflater)

        val viewModel = ViewModelProvider(this).get(DeviceListViewModel::class.java)

        val arguments = DeviceDetailFragmentArgs.fromBundle(requireArguments())
        Timber.i("Retrieved device: ${arguments.device.id}")

        return binding.root
    }
}