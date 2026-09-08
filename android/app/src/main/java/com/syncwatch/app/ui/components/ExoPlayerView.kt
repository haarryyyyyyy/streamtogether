package com.syncwatch.app.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
@Composable
fun ExoPlayerView(
    mediaUrl: String,
    onPlayerReady: (ExoPlayer) -> Unit,
    onBufferingChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val exoPlayer = remember(mediaUrl) {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = false
            if (mediaUrl.isNotEmpty()) {
                val mediaItem = MediaItem.Builder()
                    .setUri(mediaUrl)
                    .build()
                setMediaItem(mediaItem)
                prepare()
            }
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val isBuffering = playbackState == Player.STATE_BUFFERING
                onBufferingChanged(isBuffering)
            }

            override fun onPlayerError(error: PlaybackException) {
                error.printStackTrace()
            }
        }
        exoPlayer.addListener(listener)
        onPlayerReady(exoPlayer)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
                setShowNextButton(false)
                setShowPreviousButton(false)
                setShowFastForwardButton(true)
                setShowRewindButton(true)
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { playerView ->
            if (playerView.player != exoPlayer) {
                playerView.player = exoPlayer
            }
        },
        modifier = modifier
    )
}
