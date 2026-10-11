package com.senplay

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.senplay.core.LibraryStore
import com.senplay.core.PlayRequest
import com.senplay.deeplink.Route
import com.senplay.deeplink.SchemeRouter
import com.senplay.player.PlayerActivity
import com.senplay.ui.HomeScreen
import com.senplay.ui.SenPlayTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        setContent {
            SenPlayTheme {
                HomeScreen(store = LibraryStore.get(this))
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val store = LibraryStore.get(this)

        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val data = intent.data
                when (val route = SchemeRouter.parse(data)) {
                    is Route.Play -> {
                        if (route.request.saveToUrlList && data != null) {
                            store.addUrl(com.senplay.core.UrlListItem(route.request.url, route.request.name, route.request.userAgent))
                        }
                        launchPlayer(route.request.copy(positionMs = route.request.positionMs.coerceAtLeast(store.savedPosition(route.request.url))))
                    }

                    is Route.Download -> {
                        store.addUrl(com.senplay.core.UrlListItem(route.url, route.name, route.userAgent))
                    }

                    is Route.ImportServer -> {
                        store.saveServer(route.server)
                    }

                    is Route.ImportIcon -> {
                        store.addUrl(com.senplay.core.UrlListItem(route.iconset, "Icon Library"))
                    }

                    Route.None -> {
                        // A plain media link handed over by another app.
                        if (data != null && isDirectMedia(data)) {
                            launchPlayer(
                                PlayRequest(
                                    url = data.toString(),
                                    name = intent.getStringExtra(Intent.EXTRA_TITLE)
                                        ?: data.lastPathSegment.orEmpty().ifBlank { "Video" }
                                )
                            )
                        }
                    }
                }
            }

            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
                val match = Regex("https?://\\S+").find(text) ?: return
                launchPlayer(PlayRequest(url = match.value, name = match.value.substringAfterLast('/')))
            }
        }
    }

    private fun isDirectMedia(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme == "content" || scheme == "file") return true
        return scheme == "http" || scheme == "https"
    }

    private fun launchPlayer(request: PlayRequest) {
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_URL, request.url)
                .putExtra(PlayerActivity.EXTRA_NAME, request.name)
                .putExtra(PlayerActivity.EXTRA_SUBTITLE, request.subtitleUrl)
                .putExtra(PlayerActivity.EXTRA_USER_AGENT, request.userAgent)
                .putExtra(PlayerActivity.EXTRA_POSITION_MS, request.positionMs)
        )
    }
}
