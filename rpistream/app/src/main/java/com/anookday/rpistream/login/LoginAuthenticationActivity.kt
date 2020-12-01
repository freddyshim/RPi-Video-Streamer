package com.anookday.rpistream.login

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.anookday.rpistream.R
import com.anookday.rpistream.stream.StreamActivity

class LoginAuthenticationActivity : AppCompatActivity() {
    private val viewModel by viewModels<LoginViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_authentication)

        viewModel.user.observe(this, Observer { user ->
            if (user != null) {
                if (System.currentTimeMillis() > user.tokenExpiryDate) {
                    viewModel.logout()
                } else {
                    startActivity(Intent(this, StreamActivity::class.java))
                }
            }
        })

        intent.data?.let { uri ->
            val accessToken: String? = uri.getQueryParameter("accessToken")
            val refreshToken: String? = uri.getQueryParameter("refreshToken")
            val expiresIn: Int? = uri.getQueryParameter("expiresIn")?.toInt()
            if (accessToken != null && refreshToken != null && expiresIn != null) {
                viewModel.completeLogin(accessToken, refreshToken, expiresIn)
            } else {
                val loginIntent = Intent(this, LoginActivity::class.java)
                loginIntent.putExtra("error", getString(R.string.login_failed))
                startActivity(loginIntent)
            }
        }
    }
}