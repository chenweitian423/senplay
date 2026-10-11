package com.senplay.player

import android.content.Context
import android.content.Intent
import com.senplay.core.PlayRequest

/** Single entry point for starting playback from any screen. */
object PlayerLauncher {
    fun start(context: Context, request: PlayRequest) {
        context.startActivity(
            Intent(context, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_URL, request.url)
                .putExtra(PlayerActivity.EXTRA_NAME, request.name)
                .putExtra(PlayerActivity.EXTRA_SUBTITLE, request.subtitleUrl)
                .putExtra(PlayerActivity.EXTRA_USER_AGENT, request.userAgent)
                .putExtra(PlayerActivity.EXTRA_POSITION_MS, request.positionMs)
        )
    }
}
