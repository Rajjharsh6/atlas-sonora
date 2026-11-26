package com.atlas.sonora

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class TrackPick(
    val title: String, val artist: String, val reason: String, val mood: String,
    val uri: String? = null, val image: String? = null,
) {
    val key: String get() = "$title — $artist"
}

private fun JSONObject.str(k: String): String? = if (isNull(k)) null else optString(k).ifBlank { null }

class SonoraViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("sonora", Context.MODE_PRIVATE)
    private val spotify = SpotifyController()

    var mood by mutableStateOf("Surprise me")
    var prompt by mutableStateOf("")
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf("Your personal music intelligence is ready.")
        private set
    var nowPlaying by mutableStateOf<NowPlaying?>(null)
        private set

    val picks = mutableStateListOf<TrackPick>()
    val liked = mutableStateListOf<String>()
    val disliked = mutableStateListOf<String>()
    private val shown = ArrayDeque<String>()
    private var pendingUris: List<String>? = null
    private var wantSpotify = false

    init {
        liked.addAll(readList("liked")); disliked.addAll(readList("disliked")); shown.addAll(readList("shown"))
        runCatching { picks.addAll(parsePicks(JSONArray(prefs.getString("picks", "[]")))) }
    }

    // ---- storage (ordered JSON lists; migrates the old unordered string sets) ----
    private fun readList(k: String): List<String> = try {
        val a = JSONArray(prefs.getString(k, "[]")); List(a.length()) { a.getString(it) }
    } catch (_: Exception) {
        prefs.getStringSet(k, emptySet())?.toList() ?: emptyList()
    }

    private fun saveList(k: String, v: Collection<String>) =
        prefs.edit().putString(k, JSONArray(v.toList()).toString()).apply()

    private fun parsePicks(a: JSONArray): List<TrackPick> = List(a.length()) { i ->
        val o = a.getJSONObject(i)
        TrackPick(o.optString("title"), o.optString("artist"), o.optString("reason"),
            o.optString("mood"), o.str("uri"), o.str("image"))
    }

    private fun savePicks() {
        val a = JSONArray()
        picks.forEach {
            a.put(JSONObject().put("title", it.title).put("artist", it.artist).put("reason", it.reason)
                .put("mood", it.mood).put("uri", it.uri ?: JSONObject.NULL).put("image", it.image ?: JSONObject.NULL))
        }
        prefs.edit().putString("picks", a.toString()).apply()
    }

    // ---- feedback ----
    fun feedback(track: TrackPick, positive: Boolean) {
        val k = track.key
        if (positive) { liked.remove(k); liked.add(k); disliked.remove(k) }
        else { disliked.remove(k); disliked.add(k); liked.remove(k) }
        saveList("liked", liked); saveList("disliked", disliked)
        message = if (positive) "Added to your taste profile." else "I'll avoid similar picks."
    }

    // ---- AI ----
    fun recommend() {
        if (busy) return
        if (BuildConfig.XAI_API_KEY.isBlank()) { message = "Grok key missing. Add the XAI_API_KEY secret and rebuild."; return }
        busy = true
        message = "Finding music that fits your taste…"
        val slow = viewModelScope.launch {
            delay(8000); message = "Still thinking… the AI is taking a little longer than usual."
        }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { AiClient.recommend(mood, prompt, liked.toList(), disliked.toList(), shown.toList()) }
                picks.clear(); picks.addAll(result)
                result.forEach { shown.remove(it.key); shown.addLast(it.key) }
                while (shown.size > 80) shown.removeFirst()
                saveList("shown", shown); savePicks()
                message = if (result.isEmpty()) "No recommendations returned. Try another mood."
                else "Found ${result.size} picks. Tell me what hits."
            } catch (e: Exception) {
                message = "AI connection failed: ${e.message ?: "unknown error"}"
            } finally { slow.cancel(); busy = false }
        }
    }

    // ---- Spotify playback ----
    fun play(track: TrackPick, context: Context) {
        val uri = track.uri
        if (uri == null) openSpotifySearch(context, "${track.title} ${track.artist}") else playUris(listOf(uri), context)
    }

    fun playAll(context: Context) {
        val uris = picks.mapNotNull { it.uri }
        if (uris.isEmpty()) message = "No playable tracks yet." else playUris(uris, context)
    }

    private fun playUris(uris: List<String>, context: Context) {
        wantSpotify = true
        if (spotify.connected) spotify.playUris(uris) { message = it }
        else { pendingUris = uris; connect(context) }
    }

    fun connect(context: Context) {
        if (spotify.connected) return
        message = "Connecting to Spotify…"
        spotify.connect(context,
            onState = { nowPlaying = it },
            onReady = {
                message = "Connected to Spotify."
                pendingUris?.let { spotify.playUris(it) { e -> message = e } }
                pendingUris = null
            },
            onError = { message = it; pendingUris = null })
    }

    fun togglePlay() = spotify.togglePause(nowPlaying?.paused ?: true)
    fun next() = spotify.next()
    fun onStart(context: Context) { if (wantSpotify) connect(context) }
    fun onStop() { spotify.disconnect(); nowPlaying = null }
}
