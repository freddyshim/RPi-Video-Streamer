package com.anookday.rpistream.stream

import android.content.Context
import android.graphics.Point
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import com.serenegiant.usb.UVCCamera
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10


class StreamGLRenderer(view: StreamGLSurfaceView) : GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {
    private val vss_default = """
            attribute vec2 vPosition;
            attribute vec2 vTexCoord;
            varying vec2 texCoord;
            void main() {
              texCoord = vTexCoord;
              gl_Position = vec4 ( vPosition.x, vPosition.y, 0.0, 1.0 );
            }"""

    private val fss_default = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES sTexture;
            varying vec2 texCoord;
            void main() {
              gl_FragColor = texture2D(sTexture,texCoord);
            }"""

    private lateinit var hTex: IntArray
    private lateinit var pVertex: FloatBuffer
    private lateinit var pTexCoord: FloatBuffer
    private var hProgram = 0

    private lateinit var mSTexture: SurfaceTexture

    private var mGLInit = false
    private var mUpdateST = false

    private var mView: StreamGLSurfaceView? = null

    private var mCameraDevice: CameraDevice? = null
    private var mCaptureSession: CameraCaptureSession? = null
    private var mPreviewRequestBuilder: CaptureRequest.Builder? = null
    private var mCameraID: String? = null
    private var mPreviewSize: Size = Size(1920, 1080)

    private var mPiCamera: UVCCamera? = null
    private var mPiSurfaceHolder: SurfaceHolder? = null

    private var mBackgroundThread: HandlerThread? = null
    private var mBackgroundHandler: Handler? = null
    private val mCameraOpenCloseLock: Semaphore = Semaphore(1)

    init {
        mView = view
        val vtmp = floatArrayOf(1.0f, -1.0f, -1.0f, -1.0f, 1.0f, 1.0f, -1.0f, 1.0f)
        //val vtmp = floatArrayOf( -1.0f, -1.0f, -1.0f, 1.0f, 1.0f, -1.0f, 1.0f, 1.0f)
        //val vtmp = floatArrayOf( 1.0f, 1.0f, 1.0f, -1.0f, -1.0f, 1.0f, -1.0f, -1.0f)
        val ttmp = floatArrayOf(1.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f)
        pVertex = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        pVertex.put(vtmp)
        pVertex.position(0)
        pTexCoord = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        pTexCoord.put(ttmp)
        pTexCoord.position(0)
    }

    fun onResume() {
        startBackgroundThread()
    }

    fun onPause() {
        mGLInit = false
        mUpdateST = false
        closeCamera()
        stopBackgroundThread()
    }

    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        initTex()
        mSTexture = SurfaceTexture(hTex[0])
        mSTexture.setOnFrameAvailableListener(this)

        GLES30.glClearColor(1.0f, 1.0f, 1.0f, 1.0f)

        hProgram = loadShader(vss_default, fss_default)

        val ss = Point()
        mView!!.display.getRealSize(ss)

        cacPreviewSize(ss.x, ss.y)
        //openCamera()

        mGLInit = true
    }

    /**
     * Start camera preview if preview is currently disabled.
     *
     * @param width         Width of preview frame in px.
     * @param height        Height of preview frame in px.
     */
    fun startPiCameraPreview(camera: UVCCamera, width: Int?, height: Int?) {
        mPiCamera = camera
        val surface = Surface(mSTexture)
        camera.setPreviewDisplay(surface)
        camera.startPreview()
    }

    fun stopPiCameraPreview() {
        mPiCamera?.stopPreview()
    }

    private fun cacPreviewSize(width: Int, height: Int) {
        val manager = mView!!.context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            for (cameraID in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(cameraID)
                if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) continue
                mCameraID = cameraID
                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                for (psize in map!!.getOutputSizes(SurfaceTexture::class.java)) {
                    Timber.d("${psize.width}x${psize.height}")
                    if (width == psize.width && height == psize.height) {
                        mPreviewSize = psize
                        break
                    }
                }
                break
            }
        } catch (e: CameraAccessException) {
            Timber.e("cacPreviewSize - Camera Access Exception")
        } catch (e: java.lang.IllegalArgumentException) {
            Timber.e( "cacPreviewSize - Illegal Argument Exception")
        } catch (e: SecurityException) {
            Timber.e("cacPreviewSize - Security Exception")
        }
    }

    fun openCamera() {
        val manager = mView!!.context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val characteristics = manager.getCameraCharacteristics(mCameraID!!)
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            manager.openCamera(mCameraID!!, mStateCallback, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            Timber.e("OpenCamera - Camera Access Exception")
        } catch (e: IllegalArgumentException) {
            Timber.e( "OpenCamera - Illegal Argument Exception")
        } catch (e: SecurityException) {
            Timber.e( "OpenCamera - Security Exception")
        } catch (e: InterruptedException) {
            Timber.e( "OpenCamera - Interrupted Exception")
        }
    }

    fun closeCamera() {
        try {
            mCameraOpenCloseLock.acquire()
            mCaptureSession?.apply {
                close()
            }
            mCaptureSession = null
            mCameraDevice?.apply {
                close()
            }
            mCameraDevice = null
        } catch (e: InterruptedException) {
            throw java.lang.RuntimeException("Interrupted while trying to lock camera closing.", e)
        } finally {
            mCameraOpenCloseLock.release()
        }
    }

    private val mStateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(cameraDevice: CameraDevice) {
            mCameraOpenCloseLock.release()
            mCameraDevice = cameraDevice
            createCameraPreviewSession()
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            mCameraOpenCloseLock.release()
            cameraDevice.close()
            mCameraDevice = null
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            mCameraOpenCloseLock.release()
            cameraDevice.close()
            mCameraDevice = null
        }
    }

    private fun createCameraPreviewSession() {
        try {
            Timber.d("preview size: ${mPreviewSize.width}x${mPreviewSize.height}")
            mSTexture.setDefaultBufferSize(mPreviewSize.width, mPreviewSize.height)
            val surface = Surface(mSTexture)
            mCameraDevice?.let { device ->
                mPreviewRequestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    addTarget(surface)
                    mCameraDevice!!.createCaptureSession(
                        listOf(surface),
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                                if (null == mCameraDevice) return
                                mCaptureSession = cameraCaptureSession
                                try {
                                    set(
                                        CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                                    )
                                    set(
                                        CaptureRequest.CONTROL_AE_MODE,
                                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                                    )
                                    mCaptureSession!!.setRepeatingRequest(
                                        build(),
                                        null,
                                        mBackgroundHandler
                                    )
                                } catch (e: CameraAccessException) {
                                    Timber.e("createCaptureSession")
                                }
                            }

                            override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                                Timber.e(cameraCaptureSession.toString())
                            }
                        }, null
                    )
                }
            }

        } catch (e: CameraAccessException) {
            Timber.e("createCameraPreviewSession")
        }
    }

    private fun startBackgroundThread() {
        mBackgroundThread = HandlerThread("CameraBackground").apply {
            start()
            Timber.d("creating mBackgroundHandler")
            mBackgroundHandler = Handler(looper)
        }
    }

    private fun stopBackgroundThread() {
        mBackgroundThread!!.quitSafely()
        try {
            mBackgroundThread!!.join()
            mBackgroundThread = null
            mBackgroundHandler = null
        } catch (e: InterruptedException) {
            Timber.e("stopBackgroundThread")
        }
    }

    override fun onDrawFrame(unused: GL10) {
        if (!mGLInit) return
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        synchronized(this) {
            if (mUpdateST) {
                mSTexture.updateTexImage()
                mUpdateST = false
            }
        }

        GLES30.glUseProgram(hProgram)

        val ph = GLES30.glGetAttribLocation(hProgram, "vPosition")
        val tch = GLES30.glGetAttribLocation(hProgram, "vTexCoord")

        GLES30.glVertexAttribPointer(ph, 2, GLES30.GL_FLOAT, false, 4 * 2, pVertex)
        GLES30.glVertexAttribPointer(tch, 2, GLES30.GL_FLOAT, false, 4 * 2, pTexCoord)
        GLES30.glEnableVertexAttribArray(ph)
        GLES30.glEnableVertexAttribArray(tch)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, hTex[0])
        GLES30.glUniform1i(GLES30.glGetUniformLocation(hProgram, "sTexture"), 0)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glFlush()
    }

    override fun onSurfaceChanged(unused: GL10, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
    }

    private fun initTex() {
        hTex = IntArray(1)
        GLES30.glGenTextures(1, hTex, 0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, hTex[0])
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_WRAP_S,
            GLES30.GL_CLAMP_TO_EDGE
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_WRAP_T,
            GLES30.GL_CLAMP_TO_EDGE
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_MIN_FILTER,
            GLES30.GL_NEAREST
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_MAG_FILTER,
            GLES30.GL_NEAREST
        )
    }

    private fun loadShader(vss: String, fss: String): Int {
        var vshader = GLES30.glCreateShader(GLES30.GL_VERTEX_SHADER)
        GLES30.glShaderSource(vshader, vss)
        GLES30.glCompileShader(vshader)
        val compiled = IntArray(1)
        GLES30.glGetShaderiv(vshader, GLES30.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            Timber.e("Could not compile vshader")
            Timber.v("Could not compile vshader:%s", GLES30.glGetShaderInfoLog(vshader))
            GLES30.glDeleteShader(vshader)
            vshader = 0
        }
        var fshader = GLES30.glCreateShader(GLES30.GL_FRAGMENT_SHADER)
        GLES30.glShaderSource(fshader, fss)
        GLES30.glCompileShader(fshader)
        GLES30.glGetShaderiv(fshader, GLES30.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            Timber.e("Could not compile fshader")
            Timber.v("Could not compile fshader:%s", GLES30.glGetShaderInfoLog(fshader))
            GLES30.glDeleteShader(fshader)
            fshader = 0
        }
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vshader)
        GLES30.glAttachShader(program, fshader)
        GLES30.glLinkProgram(program)
        return program
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        mUpdateST = true
        mView?.requestRender()
    }
}