package com.anookday.rpistream.config

/**
 * Video configuration data class.
 *
 * @property width              Width resolution in px.
 * @property height             Height resolution in px.
 * @property fps                Frames per second of the stream.
 * @property bitrate            H264 in kb.
 * @property hardwareRotation   If true then rotate using the encoder, else rotate with OpenGL.
 * @property iFrameInterval     Key frame (i-frame) interval.
 * @property rotation           Degree of rotation (eg. 0, 90, 180, or 270).
 */
data class VideoConfig(
    val width: Int = 1920,
    val height: Int = 1080,
    val fps: Int = 30,
    val bitrate: Int = 6000 * 1024,
    val hardwareRotation: Boolean = false,
    val iFrameInterval: Int = 0,
    val rotation: Int = 0
)
