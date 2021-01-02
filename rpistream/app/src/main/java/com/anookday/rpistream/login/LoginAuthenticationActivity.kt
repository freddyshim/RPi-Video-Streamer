package com.anookday.rpistream.login

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.anookday.rpistream.R

/**
 * Activity that handles redirects from oauth2 callbacks.
 */
class LoginAuthenticationActivity : AppCompatActivity() {
    private val viewModel by viewModels<LoginViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_authentication)

        viewModel.user.observe(this, { user ->
            if (user != null) {
                finish()
            }
        })

        intent.data?.let { uri ->
            val id: String? = uri.getQueryParameter("id")
            val token: String? = uri.getQueryParameter("accessToken")
            if (id != null && token != null) {
                viewModel.completeLogin(id, token)
            } else {
                val loginIntent = Intent(this, LoginActivity::class.java)
                loginIntent.putExtra("ERROR", getString(R.string.login_failed))
                startActivity(loginIntent)
                finish()
            }
        }
    }
}