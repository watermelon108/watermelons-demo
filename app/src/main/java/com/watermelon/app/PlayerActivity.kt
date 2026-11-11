package com.watermelon.app

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Normal files, network streams aur M3U8 (HLS) ke liye.
 * Audio languages aur embedded subtitle tracks player ke settings icon se chunein.
 * Optional external subtitle (.srt / .vtt) bhi add ho sakta hai.
 */
class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        val url = intent.getStringExtra(EXTRA_URL)
        if (url.isNullOrEmpty()) {
            finish()
            return
        }
        val sub = intent.getStringExtra(EXTRA_SUB)
        val view = findViewById<PlayerView>(R.id.playerView)

        val builder = MediaItem.Builder().setUri(Uri.parse(url))
        if (!sub.isNullOrEmpty()) {
            val mime = if (sub.endsWith(".vtt", ignoreCase = true)) {
                MimeTypes.TEXT_VTT
            } else {
                MimeTypes.APPLICATION_SUBRIP
            }
            val subConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub))
                .setMimeType(mime)
                .setLanguage("und")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            builder.setSubtitleConfigurations(listOf(subConfig))
        }

        val p = ExoPlayer.Builder(this).build()
        player = p
        view.player = p
        p.setMediaItem(builder.build())
        p.prepare()
        p.playWhenReady = true
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_SUB = "sub"
    }
}
