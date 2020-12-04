package com.anookday.rpistream.stream

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
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
import androidx.navigation.findNavController
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
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_stream)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = navHostFragment.navController

        setSupportActionBar(binding.appBar as Toolbar)
        supportActionBar?.apply {
            setHomeAsUpIndicator(R.drawable.ic_baseline_menu_24)
            setDisplayHomeAsUpEnabled(true)
        }

        binding.accDrawer.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.nav_account -> {
                    true
                }
                R.id.nav_settings -> {
                    true
                }
                R.id.nav_logout -> {
                    lifecycleScope.launch {
                        viewModel.logout()
                    }
                    true
                }
                else -> false
            }
        }

        viewModel.user.observe(this, Observer { user ->
            if (user != null) {
                user_id.text = user.profile.displayName
                user_description.text = user.profile.description
                Glide.with(this).load(user.profile.profileImage).into(user_icon)
                navController.navigate(R.id.streamFragment)
            } else {
                val loginIntent = Intent(this, LandingActivity::class.java)
                loginIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(loginIntent)
                finish()
            }
        })

    }

    override fun onDestroy() {
        if (StreamService.isStreaming) {
            application.stopService(Intent(applicationContext, StreamService::class.java))
        }
        viewModel.unregisterUsbMonitor()
        viewModel.disableCamera()
        viewModel.destroyUsbMonitor()
        viewModel.disconnectFromChat()
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> binding.appContainer.openDrawer(GravityCompat.START)
        }
        return true
    }

    override fun onBackPressed() {
        when (viewModel.currentFragment.value) {
            CurrentFragmentName.LOGIN -> exitApp()
            CurrentFragmentName.STREAM -> {
                val streamWarning =
                    if (viewModel.connectStatus.value == RtmpConnectStatus.SUCCESS) " Your current stream will end." else ""
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
        moveTaskToBack(true)
        finish()
    }

    fun enableHeaderAndDrawer() {
        supportActionBar?.show()
        binding.appContainer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
    }

    fun disableHeaderAndDrawer() {
        supportActionBar?.hide()
        binding.appContainer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
    }
}