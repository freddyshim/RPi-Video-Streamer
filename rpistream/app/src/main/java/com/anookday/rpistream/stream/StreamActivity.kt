package com.anookday.rpistream.stream

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.databinding.DataBindingUtil
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.anookday.rpistream.R
import com.anookday.rpistream.databinding.ActivityStreamBinding
import com.anookday.rpistream.landing.LandingActivity
import com.bumptech.glide.Glide
import kotlinx.android.synthetic.main.nav_header.*
import kotlinx.coroutines.launch

class StreamActivity : AppCompatActivity() {
    private lateinit var binding: ActivityStreamBinding
    private lateinit var navController: NavController
    private val viewModel: StreamViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_stream)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = navHostFragment.navController

        setSupportActionBar(binding.appBar as Toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.apply {
            lifecycleOwner = this@StreamActivity
            accDrawer.setNavigationItemSelectedListener {
                when (it.itemId) {
                    R.id.nav_account -> {
                        appContainer.closeDrawers()
                        viewModel.prepareNavigation()
                        navController.navigate(R.id.action_streamFragment_to_accountFragment)
                        true
                    }
                    R.id.nav_settings -> {
                        appContainer.closeDrawers()
                        viewModel.prepareNavigation()
                        navController.navigate(R.id.action_streamFragment_to_settingsFragment)
                        true
                    }
                    R.id.nav_logout -> {
                        viewModel.prepareNavigation()
                        lifecycleScope.launch {
                            viewModel.logout()
                        }
                        true
                    }
                    else -> false
                }
            }
        }

        viewModel.apply {
            user.observe(this@StreamActivity, Observer { user ->
                if (user != null) {
                    user_id.text = user.profile.displayName
                    user_description.text = user.profile.description
                    Glide.with(this@StreamActivity).load(user.profile.profileImage).into(user_icon)
                } else {
                    val loginIntent = Intent(this@StreamActivity, LandingActivity::class.java)
                    loginIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(loginIntent)
                    finish()
                }
            })
        }

    }

    override fun onDestroy() {
        viewModel.prepareNavigation()
        viewModel.unregisterUsbMonitor()
        viewModel.disableCamera()
        viewModel.destroyUsbMonitor()
        viewModel.disconnectFromChat()
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                when (viewModel.currentFragment.value) {
                    CurrentFragmentName.STREAM -> binding.appContainer.openDrawer(GravityCompat.START)
                    else -> onBackPressed()
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.navHostFragment)
        when (viewModel.currentFragment.value) {
            CurrentFragmentName.STREAM -> {
                val streamWarning =
                    if (StreamService.isStreaming) " Your current stream will end." else ""
                AlertDialog.Builder(this, R.style.AlertDialogStyle)
                    .setMessage("Are you sure you want to exit?$streamWarning")
                    .setCancelable(false)
                    .setPositiveButton("Yes") { _, _ -> exitApp() }
                    .setNegativeButton("No", null)
                    .show()
            }
            else -> super.onBackPressed()
        }
    }

    private fun exitApp() {
        viewModel.prepareNavigation()
        finish()
    }

    fun editNavigationDrawer(barTitle: Int, button: Int?, enableDrawer: Boolean) {
        supportActionBar?.apply {
            title = getString(barTitle)
            setDisplayHomeAsUpEnabled(button != null)
            if (button != null) setHomeAsUpIndicator(button)
        }

        binding.appContainer.setDrawerLockMode(
            if (enableDrawer) {
                DrawerLayout.LOCK_MODE_UNLOCKED
            } else {
                DrawerLayout.LOCK_MODE_LOCKED_CLOSED
            }
        )
    }
}