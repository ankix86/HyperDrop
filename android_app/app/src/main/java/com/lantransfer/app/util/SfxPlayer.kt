package com.lantransfer.app.util

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.lantransfer.app.R

class SfxPlayer(context: Context) {
    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val connectOrPairSoundId = soundPool.load(context, R.raw.connect_or_paired_success, 1)
    private val fileSendSoundId = soundPool.load(context, R.raw.file_send_success, 1)
    private val fileReceivedSoundId = soundPool.load(context, R.raw.file_recevied_success, 1)

    private fun play(soundId: Int) {
        soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
    }

    fun playForEvent(text: String) {
        val lower = text.lowercase()
        when {
            "incoming transfer complete" in lower -> play(fileReceivedSoundId)
            "transfer complete" in lower || "transfer finished" in lower -> play(fileSendSoundId)
            "connected target" in lower -> play(connectOrPairSoundId)
            "pair" in lower && ("confirm" in lower || "success" in lower || "trusted" in lower) -> play(connectOrPairSoundId)
        }
    }

    fun release() {
        soundPool.release()
    }
}
