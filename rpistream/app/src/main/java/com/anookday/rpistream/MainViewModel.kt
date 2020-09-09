package com.anookday.rpistream

import android.content.Context
import android.hardware.usb.UsbDevice
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.anookday.rpistream.config.AudioConfig
import com.anookday.rpistream.config.VideoConfig
import com.anookday.rpistream.stream.RtmpStreamManager
import com.anookday.rpistream.stream.StreamManager
import com.pedro.rtplibrary.view.OpenGlView
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.ossrs.rtmp.ConnectCheckerRtmp
import timber.log.Timber

enum class UsbConnectStatus { ATTACHED, DETACHED }
enum class RtmpConnectStatus { SUCCESS, FAIL, DISCONNECT }
enum class RtmpAuthStatus { SUCCESS, FAIL }

/**
 * ViewModel for [MainActivity].
 */
class MainViewModel : ViewModel() {
    private val viewModelJob = Job()
    private val coroutineScope = CoroutineScope(viewModelJob + Dispatchers.Main)

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

    // URI address to which the stream output is sent
    private val _streamUri = MutableLiveData<String>()
    val streamUri: LiveData<String>
        get() = _streamUri

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

    fun registerUsbMonitor() {
        coroutineScope.launch {
            _usbMonitor.value?.register()
        }
    }

    fun unregisterUsbMonitor() {
        coroutineScope.launch {
            _usbMonitor.value?.unregister()
        }
    }

    fun setStreamUri(uri: String) {
        _streamUri.value = uri
    }

    /**
     * Start camera preview if there is no preview.
     */
    fun startPreview(width: Int, height: Int) {
        coroutineScope.launch {
            _uvcCamera.value?.let { camera ->
                _streamManager.value?.let { stream ->
                    if (!stream.isPreview) stream.startPreview(camera, width, height)
                }
            }
        }
    }

    /**
     * Stop the current stream and preview. Destroy camera instance if initialized.
     */
    fun destroyCamera() {
        coroutineScope.launch {
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
        _uvcCamera.value?.let { camera ->
            _streamManager.value?.let { stream ->
                if (!stream.isStreaming) {
                    if (stream.prepareVideo(camera, _videoConfig.value) && stream.prepareAudio(
                            _audioConfig.value
                        )
                    ) {
                        if (_streamUri.value != null) {
                            stream.startStream(camera, _streamUri.value!!)
                        } else {
                            stream.startStream(camera, "")
                        }
                    }
                } else {
                    stream.stopStream(camera)
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
            coroutineScope.launch {
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
            coroutineScope.launch {
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

}