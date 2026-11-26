package com.atlas.sonora

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val SYSTEM_PROMPT = """
You are SONORA, a discerning music discovery curator.
Suggest real, released songs that fit the user's mood and taste.
Optimize for fit, quality, variety, and discovery. Avoid disliked songs, songs already suggested,
and overusing the same artist. Mix familiar-sounding discoveries with adventurous picks.
Never invent fake songs or artists; choose verifiable tracks if unsure.
Return ONLY valid JSON:
{"recommendations":[{"title":"song title","artist":"artist name","reason":"one concise sentence","mood":"short mood"}]}
Return 8 recommendations. No markdown fences.
"""

/** Talks to Grok (xAI) and the Spotify Web API directly from the phone (keys come from BuildConfig). */
object AiClient {
    private var token: String? = null
    private var tokenExpiry = 0L

    private fun http(url: String, method: String = "GET", headers: Map<String, String> = emptyMap(), body: String? = null): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 15000; readTimeout = 45000
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            if (body != null) doOutput = true
        }
        if (body != null) c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        c.disconnect()
        if (code !in 200..299) throw IllegalStateException("HTTP $code: ${text.take(160)}")
        return text
    }

    // Grok (xAI) chat completions. Tries the configured model first, then fallbacks if it is retired.
    private fun askGrok(prompt: String): String {
        if (BuildConfig.XAI_API_KEY.isBlank()) throw IllegalStateException("xAI key is missing in this build")
        val models = listOf(BuildConfig.XAI_MODEL, "grok-4-1-fast-non-reasoning", "grok-4.20-0309-non-reasoning", "grok-3").distinct()
        var last: Exception? = null
        for (model in models) {
            val payload = JSONObject().put("model", model).put("temperature", 0.85)
                .put("messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                    .put(JSONObject().put("role", "user").put("content", prompt)))
                .toString()
            try {
                val res = http("https://api.x.ai/v1/chat/completions", "POST",
                    mapOf("Content-Type" to "application/json", "Authorization" to "Bearer ${BuildConfig.XAI_API_KEY}"), payload)
                return JSONObject(res).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            } catch (e: IllegalStateException) {
                last = e
                val m = e.message.orEmpty()
                val modelProblem = m.startsWith("HTTP 404") || (m.startsWith("HTTP 400") && m.contains("model", ignoreCase = true))
                if (!modelProblem) throw e
            }
        }
        throw last ?: IllegalStateException("No Grok model available")
    }

    private fun spotifyToken(): String? {
        val id = BuildConfig.SPOTIFY_CLIENT_ID; val secret = BuildConfig.SPOTIFY_CLIENT_SECRET
        if (id.isBlank() || secret.isBlank()) return null
        val now = System.currentTimeMillis()
        token?.let { if (now < tokenExpiry - 30_000) return it }
        val basic = Base64.encodeToString("$id:$secret".toByteArray(), Base64.NO_WRAP)
        val res = JSONObject(http("https://accounts.spotify.com/api/token", "POST",
            mapOf("Authorization" to "Basic $basic", "Content-Type" to "application/x-www-form-urlencoded"),
            "grant_type=client_credentials"))
        token = res.getString("access_token"); tokenExpiry = now + res.getLong("expires_in") * 1000
        return token
    }

    private fun search(q: String, token: String): JSONObject? {
        val url = "https://api.spotify.com/v1/search?q=${URLEncoder.encode(q, "UTF-8")}&type=track&limit=1"
        val items = JSONObject(http(url, headers = mapOf("Authorization" to "Bearer $token")))
            .getJSONObject("tracks").getJSONArray("items")
        return if (items.length() > 0) items.getJSONObject(0) else null
    }

    private fun resolve(t: TrackPick, token: String): TrackPick = try {
        val title = t.title.replace("\"", ""); val artist = t.artist.replace("\"", "")
        val item = search("track:\"$title\" artist:\"$artist\"", token) ?: search("$title $artist", token)
        if (item == null) t else {
            val imgs = item.getJSONObject("album").getJSONArray("images")
            val img = when { imgs.length() > 1 -> imgs.getJSONObject(1).getString("url")
                imgs.length() == 1 -> imgs.getJSONObject(0).getString("url") else -> null }
            t.copy(uri = item.getString("uri"), image = img)
        }
    } catch (_: Exception) { t }

    suspend fun recommend(mood: String, request: String, liked: List<String>, disliked: List<String>, shown: List<String>): List<TrackPick> =
        coroutineScope {
            val prompt = "Selected mood: $mood\nExtra request: ${request.ifBlank { "none" }}\n" +
                "Liked songs: ${liked.takeLast(60)}\nDisliked songs: ${disliked.takeLast(60)}\n" +
                "Already suggested recently (do not repeat): ${shown.takeLast(40)}\n" +
                "Create a varied, high-quality next-listening queue."
            val raw = askGrok(prompt)
            val start = raw.indexOf('{'); val end = raw.lastIndexOf('}')
            if (start < 0 || end < start) throw IllegalStateException("AI did not return JSON")
            val arr = JSONObject(raw.substring(start, end + 1)).optJSONArray("recommendations") ?: JSONArray()
            var tracks = List(minOf(arr.length(), 10)) { i ->
                val o = arr.getJSONObject(i)
                TrackPick(o.optString("title"), o.optString("artist"), o.optString("reason"), o.optString("mood"))
            }.filter { it.title.isNotBlank() }
            if (tracks.isEmpty()) throw IllegalStateException("No recommendations returned")
            val tk = try { spotifyToken() } catch (_: Exception) { null }
            if (tk != null) {
                val resolved = tracks.map { async(Dispatchers.IO) { resolve(it, tk) } }.awaitAll()
                tracks = resolved.filter { it.uri != null }.ifEmpty { resolved }  // drop unmatched (likely invented) songs
            }
            tracks
        }
}
