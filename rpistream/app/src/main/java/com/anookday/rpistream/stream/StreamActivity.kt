package com.anookday.rpistream.stream

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.provider.Settings
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
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.anookday.rpistream.R
import com.anookday.rpistream.chat.ChatService
import com.anookday.rpistream.chat.ChatStatus
import com.anookday.rpistream.databinding.ActivityStreamBinding
import com.anookday.rpistream.landing.LandingActivity
import com.bumptech.glide.Glide
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import kotlinx.android.synthetic.main.nav_header.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Activity that encompasses all stream related fragments and view models.
 */
class StreamActivity : AppCompatActivity() {
    private lateinit var binding: ActivityStreamBinding
    private lateinit var navController: NavController
    private lateinit var audioManager: AudioManager
    private val viewModel: StreamViewModel by viewModels()
    // broadcast receiver for bluetooth audio connections
    private val bluetoothScoReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = when (intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)) {
                0 -> "disconnected"
                1 -> "connected"
                2 -> "connecting"
                else -> "?"
            }
            Timber.v("Audio SCO state: $status")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //val myIntent = Intent()
        //myIntent.action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
        //startActivity(myIntent)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_stream)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = navHostFragment.navController

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

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
                    R.id.nav_licenses -> {
                        appContainer.closeDrawers()
                        viewModel.prepareNavigation()
                        OssLicensesMenuActivity.setActivityTitle(getString(R.string.licenses))
                        startActivity(Intent(this@StreamActivity, OssLicensesMenuActivity::class.java))
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
                    if (ChatService.status == ChatStatus.DISCONNECTED) {
                        connectToChat()
                    }
                } else {
                    val loginIntent = Intent(this@StreamActivity, LandingActivity::class.java)
                    loginIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(loginIntent)
                    finish()
                }
            })
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    deleteChatHistory()
                }
            }
        }

        registerBluetooth()
    }

    override fun onDestroy() {
        viewModel.prepareNavigation()
        viewModel.unregisterUsbMonitor()
        viewModel.disableCamera()
        viewModel.destroyUsbMonitor()
        viewModel.disconnectFromChat()
        unregisterBluetooth()
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

    /**
     * Finish all running processes, free allocated memory and exit the app.
     */
    private fun exitApp() {
        viewModel.prepareNavigation()
        finish()
    }

    /**
     * Register bluetooth audio.
     */
    private fun registerBluetooth() {
        registerReceiver(bluetoothScoReceiver, IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED))
        audioManager.startBluetoothSco()
    }

    /**
     * Unregister bluetooth audio.
     */
    private fun unregisterBluetooth() {
        unregisterReceiver(bluetoothScoReceiver)
        audioManager.stopBluetoothSco()
    }

    /**
     * Edit action bar appearance and toggle navigation drawer lock.
     * @param barTitle resource id of string to display as action bar title
     * @param button resource id of icon to display as action bar home button
     * @param enableDrawer if true then enable use of navigation drawer
     */
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