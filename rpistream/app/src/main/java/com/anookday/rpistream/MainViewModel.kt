package com.anookday.rpistream

import android.app.Application
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.widget.Toast
import androidx.lifecycle.*
import com.anookday.rpistream.config.AudioConfig
import com.anookday.rpistream.config.VideoConfig
import com.anookday.rpistream.database.User
import com.anookday.rpistream.database.getDatabase
import com.anookday.rpistream.oauth.TwitchManager
import com.anookday.rpistream.stream.StreamService
import com.pedro.rtplibrary.util.BitrateAdapter
import com.pedro.rtplibrary.view.OpenGlView
import com.serenegiant.usb.CameraDialog
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import kotlinx.coroutines.*
import net.ossrs.rtmp.BitrateManager
import net.ossrs.rtmp.ConnectCheckerRtmp
import timber.log.Timber

enum class UsbConnectStatus { ATTACHED, DETACHED }
enum class RtmpConnectStatus { SUCCESS, FAIL, DISCONNECT }
enum class RtmpAuthStatus { SUCCESS, FAIL }

/**
 * ViewModel for [MainActivity].
 */
class MainViewModel(val app: Application) : AndroidViewModel(app) {
    // user database
    private val database = getDatabase(app)

    // twitch oauth manager
    private val twitchManager = TwitchManager(app.applicationContext, database)

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
    private val _videoStatus = MutableLiveData<String>()
    val videoStatus: LiveData<String>
        get() = _videoStatus

    // audio connection status
    private val _audioStatus = MutableLiveData<String>()
    val audioStatus: LiveData<String>
        get() = _audioStatus

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
            val usbManager = app.getSystemService(Context.USB_SERVICE) as UsbManager
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
                    val streamEndpoint: String? = twitchManager.getIngestEndpoint(it.idToken, it.accessToken)
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
     * deviceConnectListener object
     */
    private var onDeviceConnectListener = object : USBMonitor.OnDeviceConnectListener {
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

    /**
     * Factory for constructing MainViewModel with parameter
     */
    class Factory(val app: Application): ViewModelProvider.Factory {
        override fun <T: ViewModel?> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(app) as T
            }
            throw IllegalArgumentException("Unable to construct view model")
        }
    }
}