package com.anookday.rpistream.login

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import com.anookday.rpistream.R
import com.anookday.rpistream.databinding.ActivityLoginBinding
import com.anookday.rpistream.stream.StreamActivity

class LoginActivity: AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private val viewModel by viewModels<LoginViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_login)
        binding.viewModel = viewModel

        binding.twitchLoginButton.setOnClickListener {
            viewModel.login(this)
        }

        viewModel.user.observe(this, { user ->
            if (user != null) {
                val loginConfirmIntent = Intent(this, StreamActivity::class.java)
                startActivity(loginConfirmIntent)
                finish()
            }
        })
    }
}
