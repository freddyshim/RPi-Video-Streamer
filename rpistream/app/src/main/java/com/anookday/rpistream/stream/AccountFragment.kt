package com.anookday.rpistream.stream

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import com.anookday.rpistream.R
import com.anookday.rpistream.databinding.FragmentAccountBinding
import com.anookday.rpistream.repository.database.User

class AccountFragment: Fragment() {
    private lateinit var binding: FragmentAccountBinding
    private val viewModel: StreamViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentAccountBinding.inflate(inflater, container, false)
        viewModel.apply {
            user.observe(viewLifecycleOwner, ::onUserChange)
        }

        return binding.root
    }

    override fun onResume() {
        viewModel.setCurrentFragment(CurrentFragmentName.ACCOUNT)
        (activity as StreamActivity).apply {
            supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_baseline_arrow_back_24)
            editNavigationDrawer(getString(R.string.account_title), false)
        }
        super.onResume()
    }

    private fun onUserChange(user: User?) {
        user?.let {
            binding.user = it
        }
    }
}