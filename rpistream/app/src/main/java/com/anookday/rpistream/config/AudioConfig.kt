package com.anookday.rpistream.config

/**
 * Audio configuration data class.
 *
 * @property bitrate            AAC in kb.
 * @property sampleRate         Sample rate of audio in hz (eg. 8000, 16000, 22500, 32000, 44100).
 * @property stereo           If true then enable stereo audio. Else, use mono aduio.
 * @property echoCanceler       If true then enable echo canceler.
 * @property noiseSuppressor    If true then enable noise suppressor.
 */
data class AudioConfig(
    val bitrate: Int = 64 * 1024,
    val sampleRate: Int = 32000,
    val stereo: Boolean = true,
    val echoCanceler: Boolean = false,
    val noiseSuppressor: Boolean = false
)