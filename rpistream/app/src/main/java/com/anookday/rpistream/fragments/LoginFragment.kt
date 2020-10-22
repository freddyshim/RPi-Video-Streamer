package com.anookday.rpistream.fragments

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.anookday.rpistream.*
import com.anookday.rpistream.databinding.FragmentLoginBinding

class LoginFragment: Fragment() {
    private val viewModel: MainViewModel by activityViewModels()
    private val activityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            viewModel.handleAuthorizationResponse(result.data!!)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding: FragmentLoginBinding = DataBindingUtil.inflate(inflater, R.layout.fragment_login, container, false)

        binding.twitchLoginButton.setOnClickListener {
            viewModel.getAuthorizationIntent()?.let { intent ->
                activityResultLauncher.launch(intent)
            }
        }

        return binding.root
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        viewModel.setCurrentFragment(CurrentFragmentName.LOGIN)
    }

    override fun onResume() {
        super.onResume()
        (activity as MainActivity).disableHeaderAndDrawer()
    }
}
