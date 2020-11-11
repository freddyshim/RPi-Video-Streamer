package com.anookday.rpistream

import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.*
import androidx.lifecycle.*
import com.anookday.rpistream.chat.*
import com.anookday.rpistream.config.AudioConfig
import com.anookday.rpistream.config.VideoConfig
import com.anookday.rpistream.database.User
import com.anookday.rpistream.database.getDatabase
import com.anookday.rpistream.extensions.addNewItem
import com.anookday.rpistream.oauth.TwitchManager
import com.anookday.rpistream.stream.StreamService
import com.pedro.rtplibrary.util.BitrateAdapter
import com.pedro.rtplibrary.view.OpenGlView
import com.serenegiant.usb.USBMonitor
import kotlinx.coroutines.*
import net.ossrs.rtmp.ConnectCheckerRtmp
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import timber.log.Timber
import java.io.IOException
import java.util.*

enum class UsbConnectStatus { ATTACHED, DETACHED }
enum class RtmpConnectStatus { SUCCESS, FAIL, DISCONNECT }
enum class RtmpAuthStatus { SUCCESS, FAIL }
enum class CurrentFragmentName { LANDING, LOGIN, STREAM }

/**
 * ViewModel for [MainActivity].
 */
class MainViewModel(val app: Application) : AndroidViewModel(app) {
    // user database
    private val database = getDatabase(app)

    // name of currently visible fragment
    private val _currentFragment = MutableLiveData<CurrentFragmentName>()
    val currentFragment: LiveData<CurrentFragmentName>
        get() = _currentFragment

    // twitch oauth manager
    private val twitchManager = TwitchManager(app.applicationContext, database)

    // twitch chat variables
    private var chatWebSocket: WebSocket? = null

    // usb manager
    var usbManager = app.getSystemService(Context.USB_SERVICE) as UsbManager

    // user object
    val user: LiveData<User?> = database.userDao.getUser()

    // video configuration object
    private val _videoConfig = MutableLiveData<VideoConfig>()
    val videoConfig: LiveData<VideoConfig>
        get() = _videoConfig

    // audio configuration object
    private val _audioConfig = MutableLiveData<AudioConfig>()
    val audioConfig: LiveData<AudioConfig>
        get() = _audioConfig

    // USB monitor object used to control connected USB devices
    private val _usbMonitor = MutableLiveData<USBMonitor>()
    val usbMonitor: LiveData<USBMonitor>
        get() = _usbMonitor

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

    // video connection status
    private val _videoStatus = MutableLiveData<String?>()
    val videoStatus: LiveData<String?>
        get() = _videoStatus

    // audio connection status
    private val _audioStatus = MutableLiveData<String?>()
    val audioStatus: LiveData<String?>
        get() = _audioStatus

    // chat messages
    private val _chatMessages = MutableLiveData<MutableList<TwitchChatItem>>()
    val chatMessages: LiveData<MutableList<TwitchChatItem>>
        get() = _chatMessages

    /**
     * Initialize required LiveData variables.
     *
     * @param context Activity context
     * @param cameraView OpenGL surface view that displays the camera
     */
    fun init(context: Context, cameraView: OpenGlView) {
        _videoConfig.value = VideoConfig()
        _audioConfig.value = AudioConfig()
        _usbMonitor.value = USBMonitor(context, onDeviceConnectListener)
        StreamService.init(cameraView, connectCheckerRtmp)
        registerUsbMonitor()
    }

    fun setCurrentFragment(fragmentName: CurrentFragmentName) {
        _currentFragment.value = fragmentName
    }

    fun getAuthorizationIntent(): Intent? {
        return twitchManager.getAuthorizationIntent(user.value)
    }

    fun handleAuthorizationResponse(intent: Intent) {
        viewModelScope.launch {
            twitchManager.handleAuthorizationResponse(intent)
        }
    }

    fun logout() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                database.userDao.delete()
            }
        }
    }

    fun registerUsbMonitor() {
        viewModelScope.launch {
            _usbMonitor.value?.register()
        }
    }

    fun unregisterUsbMonitor() {
        viewModelScope.launch {
            _usbMonitor.value?.unregister()
        }
    }

    /**
     * Start camera preview if there is no preview.
     */
    fun startPreview(width: Int?, height: Int?) {
        viewModelScope.launch {
            StreamService.startPreview(width, height)
        }
    }

    /**
     * Stop camera preview if there is a preview.
     */
    fun stopPreview() {
        viewModelScope.launch {
            StreamService.stopPreview()
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
     * Connect to UVCCamera if video is disabled. Otherwise, disable video.
     */
    fun toggleVideo() {
        if (videoStatus.value == null) {
            val deviceList = usbManager.deviceList
            if (deviceList.isNotEmpty()) {
                val device: UsbDevice = deviceList.values.elementAt(0)
                usbMonitor.value?.requestPermission(device)
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
        _usbMonitor.value?.destroy()
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
                    val streamEndpoint: String? =
                        twitchManager.getIngestEndpoint(it.idToken, it.accessToken)
                    if (streamEndpoint != null) {
                        val intent = Intent(app.applicationContext, StreamService::class.java)
                        when {
                            StreamService.isStreaming -> {
                                app.stopService(intent)
                                _connectStatus.postValue(RtmpConnectStatus.DISCONNECT)
                            }
                            StreamService.prepareStream(videoConfig.value, audioConfig.value) -> {
                                intent.putExtra("endpoint", streamEndpoint)
                                app.startService(intent)
                                _connectStatus.postValue(RtmpConnectStatus.SUCCESS)
                            }
                            else -> _connectStatus.postValue(RtmpConnectStatus.FAIL)
                        }
                    }
                }
            }
        }
    }

    /**
     * Connect to user's chat web socket.
     */
    fun connectToChat() {
        user.value?.let {
            val client = OkHttpClient()
            val request = Request.Builder().url("wss://irc-ws.chat.twitch.tv:443").build()
            val twitchChatListener =
                TwitchChatListener(it.accessToken, it.displayName) { message: Message ->
                    _chatMessages.addNewItem(TwitchChatItem(message))

                    viewModelScope.launch {
                        routeMessageToPi(message)
                    }
                }
            chatWebSocket = client.newWebSocket(request, twitchChatListener)
        }
    }

    /**
     * Send a message to the chat web socket.
     */
    fun sendMessage(text: String) {
        user.value?.let {
            chatWebSocket?.apply {
                send("PRIVMSG #${it.displayName} :$text")
                val message = Message.UserMessage(UserMessageType.VALID, it.displayName, text)
                _chatMessages.addNewItem(TwitchChatItem(message))
            }
        }
    }

    /**
     * Safely disconnect from user's chat web socket.
     */
    fun disconnectFromChat() {
        chatWebSocket?.close(NORMAL_CLOSURE_STATUS, null)
        chatWebSocket = null
    }

    /**
     * Send message string over usb connection to pi device.
     *
     * @param message object containing message string
     */
    private suspend fun routeMessageToPi(message: Message) {
        withContext(Dispatchers.IO) {
            if (message is Message.UserMessage) {
                val deviceList = usbManager.deviceList
                if (deviceList.isNotEmpty()) {
                    val device: UsbDevice = deviceList.values.elementAt(0)
                    if (usbManager.hasPermission(device)) {
                        for (i in 0 until device.interfaceCount) {
                            val intf: UsbInterface = device.getInterface(i)
                            for (j in 0 until intf.endpointCount) {
                                val ep = intf.getEndpoint(j)
                                if (ep.direction == UsbConstants.USB_DIR_OUT) {
                                    val buffer = message.message.toByteArray()
                                    usbManager.openDevice(device)?.apply {
                                        claimInterface(intf, true)
                                        bulkTransfer(ep, buffer, buffer.size, 0)
                                    }
                                }
                            }
                        }
                    }
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
            Timber.v("RPISTREAM onDeviceConnectListener: Device connected")
            viewModelScope.launch {
                try {
                    _videoStatus.postValue(StreamService.enableCamera(ctrlBlock, videoConfig.value))
                } catch (e: IllegalArgumentException) {
                    disableCamera()
                }
            }
        }

        override fun onCancel(device: UsbDevice?) {
            //
        }

        override fun onAttach(device: UsbDevice?) {
            Timber.v("RPISTREAM onDeviceConnectListener: Device attached")
            _usbStatus.postValue(UsbConnectStatus.ATTACHED)
            val permissionIntent = PendingIntent.getBroadcast(app.applicationContext, 0, Intent(ACTION_USB_PERMISSION), 0)
            val intentFilter = IntentFilter(ACTION_USB_PERMISSION)
            app.registerReceiver(usbReceiver, intentFilter)
            usbManager.requestPermission(device, permissionIntent)
        }

        override fun onDisconnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
            Timber.v("RPISTREAM onDeviceConnectListener: Device disconnected")
            viewModelScope.launch {
                StreamService.disableCamera()
                _videoStatus.postValue(null)
            }
        }

        override fun onDettach(device: UsbDevice?) {
            Timber.v("RPISTREAM onDeviceConnectListener: Device detached")
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
            _connectStatus.postValue(RtmpConnectStatus.FAIL)
            StreamService.stopStream()
        }

        override fun onAuthSuccessRtmp() {
            _authStatus.postValue(RtmpAuthStatus.SUCCESS)
        }

        override fun onNewBitrateRtmp(bitrate: Long) {
            bitrateAdapter?.adaptBitrate(bitrate)
        }

        override fun onAuthErrorRtmp() {
            _authStatus.postValue(RtmpAuthStatus.FAIL)
        }

        override fun onDisconnectRtmp() {
            _connectStatus.postValue(RtmpConnectStatus.DISCONNECT)
        }
    }
}