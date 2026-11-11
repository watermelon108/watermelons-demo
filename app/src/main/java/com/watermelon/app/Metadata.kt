package com.watermelon.app

import android.os.Handler
import android.os.Looper
import org.json.JSONObject

data class Meta(
    val title: String,
    val poster: String,
    val synopsis: String,
    val score: String,
    val genres: List<String>,
    val episodes: String,
    val year: String,
    val format: String,
    val trailerId: String?
)

object Metadata {
    private const val ENDPOINT = "https://graphql.anilist.co"

    private val QUERY = """
        query (${'$'}search: String) {
          Media(search: ${'$'}search, type: ANIME) {
            title { romaji english }
            coverImage { large }
            description(asHtml: false)
            averageScore
            genres
            episodes
            seasonYear
            format
            trailer { id site }
          }
        }
    """.trimIndent()

    /** Page title se extra text hatata hai, jaise "Season 2 Hindi, Tamil..." */
    fun cleanTitle(raw: String): String =
        raw.substringBefore(" Season")
            .substringBefore(" Hindi")
            .substringBefore(" Tamil")
            .substringBefore(" Telugu")
            .substringBefore(" (")
            .substringBefore(",")
            .trim()

    /** Background thread par chalta hai, callback main thread par aata hai. */
    fun fetchAsync(title: String, cb: (Meta?) -> Unit) {
        Thread {
            val m = fetch(title)
            Handler(Looper.getMainLooper()).post { cb(m) }
        }.start()
    }

    /** Blocking. Sirf background thread se call karein. */
    fun fetch(title: String): Meta? {
        if (title.isBlank()) return null
        val key = "meta:$title"
        Cache.get<Meta>(key)?.let { return it }

        val body = JSONObject()
            .put("query", QUERY)
            .put("variables", JSONObject().put("search", title))
            .toString()
        val text = Net.post(ENDPOINT, body) ?: return null

        return try {
            val media = JSONObject(text).optJSONObject("data")?.optJSONObject("Media") ?: return null
            val names = media.optJSONObject("title")
            val english = str(names, "english")
            val romaji = str(names, "romaji")

            val genres = mutableListOf<String>()
            val arr = media.optJSONArray("genres")
            if (arr != null) {
                for (i in 0 until arr.length()) genres.add(arr.getString(i))
            }

            val score = media.optInt("averageScore", 0)
            val trailer = media.optJSONObject("trailer")
            val trailerId = if (trailer != null && str(trailer, "site") == "youtube") str(trailer, "id") else null

            val meta = Meta(
                title = if (english.isNotEmpty()) english else romaji,
                poster = str(media.optJSONObject("coverImage"), "large"),
                synopsis = str(media, "description").replace(Regex("<[^>]+>"), ""),
                score = if (score == 0) "N/A" else "${score / 10.0}/10",
                genres = genres,
                episodes = if (media.isNull("episodes")) "?" else media.optInt("episodes").toString(),
                year = str(media, "seasonYear").ifEmpty { "N/A" },
                format = str(media, "format"),
                trailerId = trailerId?.ifEmpty { null }
            )
            Cache.put(key, meta)
            meta
        } catch (e: Exception) {
            null
        }
    }

    private fun str(o: JSONObject?, k: String): String {
        if (o == null || o.isNull(k)) return ""
        return o.optString(k)
    }
}
