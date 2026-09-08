package com.syncwatch.app.ui.components

import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
@Composable
fun ExoPlayerView(
    mediaUrl: String,
    onPlayerReady: (ExoPlayer) -> Unit,
    onBufferingChanged: (Boolean) -> Unit = {},
    onUserPlayPauseChanged: ((isPlaying: Boolean, positionSec: Double) -> Unit)? = null,
    onUserSeek: ((positionSec: Double) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val exoPlayer = remember(mediaUrl) {
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        ExoPlayer.Builder(context, renderersFactory).build().apply {
            playWhenReady = false
            if (mediaUrl.isNotEmpty()) {
                val uri = Uri.parse(mediaUrl)
                val mediaItemBuilder = MediaItem.Builder().setUri(uri)

                // Detect HLS or DASH or MP4
                if (mediaUrl.endsWith(".m3u8", ignoreCase = true) || mediaUrl.contains(".m3u8?", ignoreCase = true)) {
                    mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
                } else if (mediaUrl.endsWith(".mpd", ignoreCase = true) || mediaUrl.contains(".mpd?", ignoreCase = true)) {
                    mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
                }

                setMediaItem(mediaItemBuilder.build())
                prepare()
            }
        }
    }

    DisposableEffect(exoPlayer) {
        var isHandlingSync = false

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val isBuffering = playbackState == Player.STATE_BUFFERING
                onBufferingChanged(isBuffering)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val posSec = exoPlayer.currentPosition / 1000.0
                onUserPlayPauseChanged?.invoke(isPlaying, posSec)
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    val posSec = newPosition.positionMs / 1000.0
                    onUserSeek?.invoke(posSec)
                }
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
                setShowSubtitleButton(true)
                setControllerShowTimeoutMs(3000)
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
