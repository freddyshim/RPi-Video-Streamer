package com.anookday.rpistream.stream

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet

class StreamGLSurfaceView(context: Context?, attrs: AttributeSet) : GLSurfaceView(context, attrs) {

    val renderer: StreamGLRenderer

    init {

        // Create an OpenGL ES 3.0 context
        setEGLContextClientVersion(3)

        renderer = StreamGLRenderer(this)

        // Set the Renderer for drawing on the GLSurfaceView
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    override fun onResume() {
        super.onResume()
        renderer.onResume()
    }

    override fun onPause() {
        renderer.onPause()
        super.onPause()
    }

    fun openFrontCamera() {
        renderer.openCamera()
    }

    fun closeFrontCamera() {
        renderer.closeCamera()
    }
}