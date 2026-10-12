package com.senplay.player

import android.content.Context
import com.senplay.core.PlayRequest

/** Single entry point for starting playback from any screen. */
object PlayerLauncher {
    fun start(context: Context, request: PlayRequest) {
        context.startActivity(
            PlayerActivity.intentFor(
                context = context,
                url = request.url,
                name = request.name,
                subtitle = request.subtitleUrl,
                userAgent = request.userAgent,
                positionMs = request.positionMs,
                queue = request.queue,
                cloudDrive = request.isCloudDrive,
                imageUrl = request.imageUrl,
                durationMs = request.durationMs
            )
        )
    }
}
