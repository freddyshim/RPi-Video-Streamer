package com.anookday.rpistream

import android.app.Application
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import androidx.lifecycle.*
import com.anookday.rpistream.config.AudioConfig
import com.anookday.rpistream.config.VideoConfig
import com.anookday.rpistream.database.User
import com.anookday.rpistream.database.getDatabase
import com.anookday.rpistream.oauth.KEY_STATE
import com.anookday.rpistream.oauth.STORE_NAME
import com.anookday.rpistream.oauth.TwitchManager
import com.anookday.rpistream.stream.RtmpStreamManager
import com.anookday.rpistream.stream.StreamManager
import com.pedro.rtplibrary.view.OpenGlView
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import kotlinx.coroutines.*
import net.openid.appauth.AuthState
import net.ossrs.rtmp.ConnectCheckerRtmp
import org.json.JSONException
import timber.log.Timber

enum class UsbConnectStatus { ATTACHED, DETACHED }
enum class RtmpConnectStatus { SUCCESS, FAIL, DISCONNECT }
enum class RtmpAuthStatus { SUCCESS, FAIL }

/**
 * ViewModel for [MainActivity].
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    // user database
    private val database = getDatabase(application)

    // twitch oauth manager
    private val twitchManager = TwitchManager(application.applicationContext, database)

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

    // object used to manage stream activity
    private val _streamManager = MutableLiveData<StreamManager>()
    val streamManager: LiveData<StreamManager>
        get() = _streamManager

    // UVC camera object
    private val _uvcCamera = MutableLiveData<UVCCamera>()
    val uvcCamera: LiveData<UVCCamera>
        get() = _uvcCamera

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
        _streamManager.value = RtmpStreamManager(cameraView, connectCheckerRtmp)
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
            _uvcCamera.value?.let { camera ->
                _streamManager.value?.startPreview(camera, width, height)
            }
        }
    }

    /**
     * Stop camera preview if there is a preview.
     */
    fun stopPreview() {
        viewModelScope.launch {
            _uvcCamera.value?.let { camera ->
                _streamManager.value?.stopPreview(camera)
            }
        }
    }

    /**
     * Stop the current stream and preview. Destroy camera instance if initialized.
     */
    fun destroyCamera() {
        viewModelScope.launch {
            _uvcCamera.value?.let { camera ->
                _streamManager.value?.let { stream ->
                    if (stream.isStreaming) stream.stopStream(camera)
                    if (stream.isPreview) stream.stopPreview(camera)
                }
                camera.destroy()
            }
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
                        _uvcCamera.value?.let { camera ->
                            _streamManager.value?.let { stream ->
                                // start stream
                                if (!stream.isStreaming) {
                                    if (stream.prepareVideo(camera, _videoConfig.value) && stream.prepareAudio(_audioConfig.value)) {
                                        stream.startStream(camera, streamEndpoint)
                                    }
                                }
                                // stop stream
                                else {
                                    stream.stopStream(camera)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun initBackgroundProcess() {
        _streamManager.value?.let {
            if (it.isPreview || it.isStreaming) {
                it.isBackground = true
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
                _uvcCamera.value = UVCCamera()
                uvcCamera.value?.let { camera ->
                    camera.open(ctrlBlock)
                    Timber.i("RPISTREAM onDeviceConnectListener: Supported size: ${uvcCamera.value?.supportedSize}")
                    try {
                        videoConfig.value?.let { config ->
                            camera.setPreviewSize(
                                config.width,
                                config.height,
                                UVCCamera.FRAME_FORMAT_MJPEG
                            )
                            streamManager.value?.startPreview(camera, config.width, config.height)
                            _videoStatus.value = camera.deviceName
                        }
                    } catch (e: IllegalArgumentException) {
                        Timber.i("RPISTREAM onDeviceConnectListener: Incorrect preview configuration passed")
                        camera.destroy()
                        _videoStatus.value = null
                    }
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
                uvcCamera.value?.close()
                _videoStatus.value = null
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
        override fun onConnectionSuccessRtmp() {
            _connectStatus.postValue(RtmpConnectStatus.SUCCESS)
        }

        override fun onConnectionFailedRtmp(reason: String) {
            _connectStatus.postValue(RtmpConnectStatus.FAIL)
            uvcCamera.value?.let {
                streamManager.value?.stopStream(it)
            }
        }

        override fun onAuthSuccessRtmp() {
            _authStatus.postValue(RtmpAuthStatus.SUCCESS)
        }

        override fun onNewBitrateRtmp(bitrate: Long) {

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