package com.atlas.sonora

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote

private const val REDIRECT_URI = "atlassonora://callback"

data class NowPlaying(val title: String, val artist: String, val paused: Boolean)

/** Controls playback inside the official Spotify app via the App Remote SDK (Premium required). */
class SpotifyController {
    private var remote: SpotifyAppRemote? = null
    val connected: Boolean get() = remote?.isConnected == true

    fun connect(context: Context, onState: (NowPlaying) -> Unit, onReady: () -> Unit, onError: (String) -> Unit) {
        val params = ConnectionParams.Builder(BuildConfig.SPOTIFY_CLIENT_ID)
            .setRedirectUri(REDIRECT_URI)
            .showAuthView(true)
            .build()
        SpotifyAppRemote.connect(context, params, object : Connector.ConnectionListener {
            override fun onConnected(appRemote: SpotifyAppRemote) {
                remote = appRemote
                appRemote.playerApi.subscribeToPlayerState().setEventCallback { s ->
                    val t = s.track
                    if (t != null) onState(NowPlaying(t.name, t.artist.name, s.isPaused))
                }
                onReady()
            }

            override fun onFailure(error: Throwable) = onError(describe(error))
        })
    }

    fun playUris(uris: List<String>, onError: (String) -> Unit) {
        val api = remote?.playerApi ?: return
        api.play(uris.first())
            .setResultCallback { uris.drop(1).forEach { api.queue(it) } }
            .setErrorCallback { onError(describe(it)) }
    }

    fun togglePause(currentlyPaused: Boolean) {
        val api = remote?.playerApi ?: return
        if (currentlyPaused) api.resume() else api.pause()
    }

    fun next() { remote?.playerApi?.skipNext() }

    fun disconnect() {
        remote?.let { SpotifyAppRemote.disconnect(it) }
        remote = null
    }

    private fun describe(t: Throwable): String = when (t::class.java.simpleName) {
        "CouldNotFindSpotifyApp" -> "Spotify app not found. Install it and log in."
        "NotLoggedInException" -> "Open Spotify and log in first."
        "UserNotAuthorizedException", "AuthenticationFailedException" ->
            "Spotify authorization failed. Check Client ID, package name, SHA-1 and redirect URI in the Spotify dashboard."
        "UnsupportedFeatureVersionException" -> "Please update the Spotify app."
        else -> "Spotify: ${t.message ?: t::class.java.simpleName}"
    }
}

fun openSpotifySearch(context: Context, query: String) {
    val encoded = Uri.encode(query)
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$encoded")).setPackage("com.spotify.music"))
    } catch (_: Exception) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/search/$encoded")))
    }
}

fun openSpotify(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage("com.spotify.music")
    if (launch != null) context.startActivity(launch)
    else context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/")))
}
