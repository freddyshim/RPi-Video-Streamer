package com.anookday.rpistream.stream

import android.media.MediaCodec
import com.pedro.rtplibrary.view.OpenGlView
import net.ossrs.rtmp.ConnectCheckerRtmp
import net.ossrs.rtmp.SrsFlvMuxer
import java.nio.ByteBuffer

class RtmpStreamManager(openGlView: OpenGlView, connectChecker: ConnectCheckerRtmp) :
    StreamManager(openGlView) {
    private val srsFlvMuxer = SrsFlvMuxer(connectChecker)

    override fun onSpsPpsVpsRtp(sps: ByteBuffer, pps: ByteBuffer, vps: ByteBuffer?) {
        srsFlvMuxer.setSpsPPs(sps, pps)
    }

    override fun prepareAudioRtp(isStereo: Boolean, sampleRate: Int) {
        srsFlvMuxer.setIsStereo(isStereo)
        srsFlvMuxer.setSampleRate(sampleRate)
    }

    override fun getH264DataRtp(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        srsFlvMuxer.sendVideo(h264Buffer, info)
    }

    override fun getAacDataRtp(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        srsFlvMuxer.sendAudio(aacBuffer, info)
    }

    override fun startStreamRtp(url: String) {
        if (videoEncoder.rotation == 90 && videoEncoder.rotation == 270) {
            srsFlvMuxer.setVideoResolution(videoEncoder.height, videoEncoder.width)
        } else {
            srsFlvMuxer.setVideoResolution(videoEncoder.width, videoEncoder.height)
        }
        srsFlvMuxer.start(url)
    }

    override fun stopStreamRtp() {
        srsFlvMuxer.stop()
    }

}