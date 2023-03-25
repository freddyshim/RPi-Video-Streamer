package com.anookday.rpistream.stream

import android.app.Application
import android.app.PendingIntent
import android.content.*
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.opengl.GLSurfaceView
import android.view.SurfaceView
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.anookday.rpistream.R
import com.anookday.rpistream.UserViewModel
import com.anookday.rpistream.chat.*
import com.anookday.rpistream.extensions.addNewItem
import com.anookday.rpistream.oauth.TwitchManager
import com.anookday.rpistream.pi.CommandType
import com.anookday.rpistream.pi.PiRouter
import com.anookday.rpistream.repository.database.Message
import com.pedro.rtplibrary.util.BitrateAdapter
import com.pedro.rtplibrary.view.OpenGlView
import com.serenegiant.usb.USBMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.ossrs.rtmp.ConnectCheckerRtmp
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import timber.log.Timber
import java.net.InetAddress

enum class UsbConnectStatus { ATTACHED, DETACHED }
enum class RtmpConnectStatus { SUCCESS, FAIL, DISCONNECT }
enum class RtmpAuthStatus { SUCCESS, FAIL }
enum class ChatStatus { CONNECTED, DISCONNECTED }
enum class CurrentFragmentName {
    STREAM,
    ACCOUNT,
    SETTINGS,
    VIDEO_CONFIG,
    VIDEO_CONFIG_RESOLUTION,
    VIDEO_CONFIG_FPS,
    VIDEO_CONFIG_BITRATE,
    VIDEO_CONFIG_IFRAME,
    VIDEO_CONFIG_ROTATION,
    AUDIO_CONFIG,
    AUDIO_CONFIG_BITRATE,
    AUDIO_CONFIG_SAMPLERATE,
    DARK_MODE
}

/**
 * ViewModel for [StreamActivity].
 */
class StreamViewModel(app: Application) : UserViewModel(app) {
    // name of currently visible fragment
    private val _currentFragment = MutableLiveData<CurrentFragmentName>()
    val currentFragment: LiveData<CurrentFragmentName>
        get() = _currentFragment

    // twitch oauth manager
    private val twitchManager = TwitchManager(app.applicationContext, database)

    // usb manager
    var usbManager = app.getSystemService(Context.USB_SERVICE) as UsbManager

    // USB monitor object used to control connected USB devices
    private var usbMonitor: USBMonitor? = null

    // USB device status
    private val _usbStatus = MutableLiveData<UsbConnectStatus>()
    val usbStatus: LiveData<UsbConnectStatus>
        get() = _usbStatus

    // stream connection status
    private val _connectStatus = MutableLiveData<RtmpConnectStatus>()
    val connectStatus: LiveData<RtmpConnectStatus>
        get() = _connectStatus

    // stream authentication status
    private val _authStatus = MutableLiveData<RtmpAuthStatus>()
    val authStatus: LiveData<RtmpAuthStatus>
        get() = _authStatus

    // selfie cam toggle status
    private val _selfieToggleStatus = MutableLiveData<Boolean>(false)
    val selfieToggleStatus: LiveData<Boolean>
        get() = _selfieToggleStatus

    // auto exposure toggle status
    private val _aeToggleStatus = MutableLiveData<Boolean>(false)
    val aeToggleStatus: LiveData<Boolean>
        get() = _aeToggleStatus

    // video connection status
    private val _videoStatus = MutableLiveData<String?>()
    val videoStatus: LiveData<String?>
        get() = _videoStatus

    // audio connection status
    private val _audioStatus = MutableLiveData<String?>()
    val audioStatus: LiveData<String?>
        get() = _audioStatus

    // chat messages
    val chatMessages: LiveData<List<Message>> = database.messageDao.getChat()

    private val _videoBitrate = MutableLiveData<Long?>()
    val videoBitrate: LiveData<Long?>
        get() = _videoBitrate

    /**
     * Initialize required LiveData variables.
     *
     * @param context Activity context
     * @param openGlView OpenGL surface view that displays the camera
     */
    fun init(context: Context, openGlView: SurfaceView) {
        usbMonitor = USBMonitor(context, onDeviceConnectListener)
        StreamService.init(context, connectCheckerRtmp)
        registerUsbMonitor()
        connectToChat()
    }

    /**
     * Update live data with current fragment's name.
     */
    fun setCurrentFragment(fragmentName: CurrentFragmentName) {
        _currentFragment.value = fragmentName
    }

    /**
     * Register USB monitor.
     */
    fun registerUsbMonitor() {
        viewModelScope.launch {
            usbMonitor?.register()
        }
    }

    /**
     * Unregister USB monitor.
     */
    fun unregisterUsbMonitor() {
        viewModelScope.launch {
            usbMonitor?.unregister()
        }
    }

    /**
     * Stop the current stream and preview. Destroy camera instance if initialized.
     */
    fun disableCamera() {
        viewModelScope.launch {
            StreamService.disableCamera()
            _videoStatus.value = null
        }
    }

    /**
     * Send a command to the connected video device to toggle auto exposure.
     */
    fun toggleSelfieCam() {
        _selfieToggleStatus.value?.let {
            val newToggleStatus = !it
            _selfieToggleStatus.value = newToggleStatus

            if (newToggleStatus) {
                StreamService.startFrontPreview(app.applicationContext)
            } else {
                StreamService.stopFrontPreview()
            }
        }
    }

    /**
     * Send a command to the connected video device to toggle auto exposure.
     */
    fun toggleAutoExposure() {
        _aeToggleStatus.value?.let {
            val newToggleStatus = !it
            _aeToggleStatus.value = newToggleStatus
            StreamService.isAeEnabled = !StreamService.isAeEnabled
        }
    }

    /**
     * Connect to UVCCamera if video is disabled. Otherwise, disable video.
     */
    fun toggleVideo() {
        if (videoStatus.value == null) {
            val deviceList = usbManager.deviceList
            if (deviceList.isNotEmpty()) {
                val device: UsbDevice = deviceList.values.elementAt(0)
                usbMonitor?.requestPermission(device)
            }
        } else {
            disableCamera()
        }
    }

    /**
     * Enable or disable audio recording.
     */
    fun toggleAudio() {
        if (_audioStatus.value == null) {
            _audioStatus.value = app.getString(R.string.audio_on_text)
            StreamService.enableAudio()
        } else {
            _audioStatus.value = null
            StreamService.disableAudio()
        }
    }

    fun destroyUsbMonitor() {
        usbMonitor?.destroy()
    }

    /**
     * Start streaming to registered URI address if not currently streaming.
     * Otherwise, stop the current stream.
     */
    fun toggleStream() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // only logged in users can toggle stream
                user.value?.let {
                    if (StreamService.isStreaming) {
                        stopStream()
                    } else if (StreamService.prepareStream(it.settings.videoConfig, it.settings.audioConfig)) {
                        val streamEndpoint = twitchManager.getIngestEndpoint(it.id, it.auth.accessToken)
                        if (streamEndpoint != null) {
                            startStream(streamEndpoint)
                        }
                    } else {
                        _connectStatus.postValue(RtmpConnectStatus.FAIL)
                    }
                }
            }
        }
    }

    private fun isInternetAvailable(): Boolean {
        return try {
            val ipAddr: InetAddress = InetAddress.getByName("google.com")
            //You can replace it with your name
            !ipAddr.equals("")
        } catch (e: Exception) {
            false
        }
    }

    fun startStream(endpoint: String) {
        if (!isInternetAvailable()) return
        val intent = Intent(app.applicationContext, StreamService::class.java)
        intent.putExtra("endpoint", endpoint)
        app.startService(intent)
        _connectStatus.postValue(RtmpConnectStatus.SUCCESS)
    }

    fun stopStream() {
        val intent = Intent(app.applicationContext, StreamService::class.java)
        app.stopService(intent)
        _connectStatus.postValue(RtmpConnectStatus.DISCONNECT)
    }

    /**
     * Disable streaming and video preview. Called before navigating to another activity/fragment.
     */
    fun prepareNavigation() {
        if (StreamService.isPreview) {
            toggleVideo()
        }
        if (StreamService.isStreaming) {
            toggleStream()
        }
        _audioStatus.value = null
        StreamService.disableAudio()
    }

    /**
     * Connect to user's chat web socket.
     */
    fun connectToChat() {
        Timber.d("User ${user.value}")
        user.value?.let {
            val intent = Intent(app.applicationContext, ChatService::class.java)
            intent.putExtra("accessToken", it.auth.accessToken)
            intent.putExtra("displayName", it.profile.displayName)
            app.startService(intent)
        }
    }

    /**
     * Send a message to the chat web socket.
     */
    fun sendMessage(text: String) {
        user.value?.let {
            val intent = Intent(app.applicationContext, ChatService::class.java)
            intent.action = "sendMessage"
            intent.putExtra("displayName", it.profile.displayName)
            intent.putExtra("text", text)
            app.startService(intent)
        }
    }

    /**
     * Safely disconnect from user's chat web socket.
     */
    fun disconnectFromChat() {
        Timber.d("Disconnecting from chat")
        val intent = Intent(app.applicationContext, ChatService::class.java)
        app.stopService(intent)
    }

    fun deleteChatHistory() {
        database.messageDao.deleteChat()
    }

    /**
     * Update video resolution in user settings.
     * @param width resolution width in pixels
     * @param height resolution height in pixels
     */
    fun updateVideoResolution(width: Int, height: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                user.value?.id?.let { id ->
                    database.userDao.updateVideoResolution(id, width, height)
                }
            }
        }
    }

    /**
     * Update max frames-per-second in user settings.
     * @param fps frames-per-second threshold
     */
    fun updateVideoFps(fps: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                user.value?.id?.let { id ->
                    database.userDao.updateVideoFps(id, fps)
                }
            }
        }
    }

    /**
     * Update maximum video bitrate in user settings.
     * @param bitrate maximum bitrate in bytes
     */
    fun updateVideoBitrate(bitrate: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                user.value?.id?.let { id ->
                    database.userDao.updateVideoBitrate(id, bitrate)
                }
            }
        }
    }

    /**
     * Update i-frame limit in user settings.
     * @param iframe keyframe (i-frame) count
     */
    fun updateVideoIFrame(iframe: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                user.value?.id?.let { id ->
                    database.userDao.updateVideoIFrame(id, iframe)
                }
            }
        }
    }

    /**
     * Update video rotation angle in user settings.
     * @param rotation rotation angle in degrees
     */
    fun updateVideoRotation(rotation: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                user.value?.id?.let { id ->
                    database.userDao.updateVideoRotation(id, rotation)
                }
            }
        }
    }

    /**
     * Update maximum audio bitrate in user settings.
     * @param bitrate audio bitrate in bytes
     */
    fun updateAudioBitrate(bitrate: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                user.value?.id?.let { id ->
                    database.userDao.updateAudioBitrate(id, bitrate)
                }
            }
        }
    }

    /**
     * Update audio sample rate in user settings.
     * @param sampleRate sample rate in hertz
     */
    fun updateAudioSampleRate(sampleRate: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                user.value?.id?.let { id ->
                    database.userDao.updateAudioSampleRate(id, sampleRate)
                }
            }
        }
    }

    /**
     * Update stereo toggle in user settings.
     * @param stereo if true then enable stereo
     */
    fun updateAudioStereo(stereo: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                user.value?.id?.let { id ->
                    database.userDao.updateAudioStereo(id, stereo)
                }
            }
        }
    }

    /**
     * Update echo canceler toggle in user settings.
     * @param echoCanceler if true then enable echo canceler
     */
    fun updateAudioEchoCanceler(echoCanceler: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                user.value?.id?.let { id ->
                    database.userDao.updateAudioEchoCanceler(id, echoCanceler)
                }
            }
        }
    }

    /**
     * Update noise suppressor toggle in user settings.
     * @param noiseSuppressor if true then enable noise suppressor
     */
    fun updateAudioNoiseSuppressor(noiseSuppressor: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                user.value?.id?.let { id ->
                    database.userDao.updateAudioNoiseSuppressor(id, noiseSuppressor)
                }
            }
        }
    }

    /**
     * Update dark mode toggle in user settings.
     * @param darkMode if true then enable dark mode
     */
    fun updateDarkMode(darkMode: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                user.value?.id?.let { id ->
                    database.userDao.updateDarkMode(id, darkMode)
                }
            }
        }
    }

    /**
     * Update developer mode toggle in user settings.
     * @param developerMode if true then enable developer mode
     */
    fun updateDeveloperMode(developerMode: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                user.value?.id?.let { id ->
                    database.userDao.updateDeveloperMode(id, developerMode)
                }
            }
        }
    }


    /**
     * deviceConnectListener object
     */
    private var onDeviceConnectListener = object : USBMonitor.OnDeviceConnectListener {
        private val ACTION_USB_PERMISSION = "com.anookday.rpistream.USB_PERMISSION"

        private val usbReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (ACTION_USB_PERMISSION == intent.action) {
                    synchronized(this) {
                        val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)

                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            device?.apply {
                                //call method to set up device communication
                            }
                        } else {
                            Timber.d("permission denied for device $device")
                        }
                    }
                }
            }
        }

        override fun onConnect(
            device: UsbDevice?,
            ctrlBlock: USBMonitor.UsbControlBlock?,
            createNew: Boolean
        ) {
            Timber.v("onDeviceConnectListener: Device connected")
            user.value?.let {
                viewModelScope.launch {
                    withContext(Dispatchers.IO) {
                        try {
                            _videoStatus.postValue(
                                StreamService.enableCamera(
                                    ctrlBlock,
                                    it.settings.videoConfig
                                )
                            )
                        } catch (e: IllegalArgumentException) {
                            disableCamera()
                        }
                    }
                }
            }
        }

        override fun onCancel(device: UsbDevice?) {
            //
        }

        override fun onAttach(device: UsbDevice?) {
            Timber.v("onDeviceConnectListener: Device attached")
            _usbStatus.postValue(UsbConnectStatus.ATTACHED)
            val permissionIntent = PendingIntent.getBroadcast(
                app.applicationContext, 0, Intent(
                    ACTION_USB_PERMISSION
                ), 0
            )
            val intentFilter = IntentFilter(ACTION_USB_PERMISSION)
            app.registerReceiver(usbReceiver, intentFilter)
            usbManager.requestPermission(device, permissionIntent)
        }

        override fun onDisconnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
            Timber.v("onDeviceConnectListener: Device disconnected")
            viewModelScope.launch {
                StreamService.disableCamera()
                _videoStatus.postValue(null)
            }
        }

        override fun onDettach(device: UsbDevice?) {
            Timber.v("onDeviceConnectListener: Device detached")
            _usbStatus.postValue(UsbConnectStatus.DETACHED)
        }

    }

    /**
     * RTMP connection notification object
     */
    private var connectCheckerRtmp = object : ConnectCheckerRtmp {
        private var bitrateAdapter: BitrateAdapter? = null

        override fun onConnectionSuccessRtmp() {
            bitrateAdapter = BitrateAdapter(BitrateAdapter.Listener { bitrate ->
                StreamService.videoBitrate = bitrate
            })
            bitrateAdapter?.setMaxBitrate(StreamService.videoBitrate)
            _connectStatus.postValue(RtmpConnectStatus.SUCCESS)
        }

        override fun onConnectionFailedRtmp(reason: String) {
            StreamService.stopStream()
            _connectStatus.postValue(RtmpConnectStatus.FAIL)
        }

        override fun onAuthSuccessRtmp() {
            _authStatus.postValue(RtmpAuthStatus.SUCCESS)
        }

        override fun onNewBitrateRtmp(bitrate: Long) {
            user.value?.settings?.let { settings ->
                if (settings.developerMode) {
                    _videoBitrate.postValue(bitrate)
                }
            }
            bitrateAdapter?.adaptBitrate(bitrate)
        }

        override fun onAuthErrorRtmp() {
            _authStatus.postValue(RtmpAuthStatus.FAIL)
        }

        override fun onDisconnectRtmp() {
            _videoBitrate.postValue(null)
            _connectStatus.postValue(RtmpConnectStatus.DISCONNECT)
        }
    }
}