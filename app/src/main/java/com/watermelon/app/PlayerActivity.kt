package com.watermelon.app

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Handles normal files, HTTP/network streams and M3U8 (HLS) via Media3.
 * Audio language and subtitle tracks are chosen from the built-in
 * track selection dialog (settings icon in the player controls).
 */
class PlayerActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        val url = intent.getStringExtra(EXTRA_URL) ?: return finish()
        val view = findViewById<PlayerView>(R.id.playerView)

        player = ExoPlayer.Builder(this).build().also {
            view.player = it
            it.setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            it.prepare()
            it.playWhenReady = true
        }
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
    }
}
