package com.watermelon.app

import android.content.Context
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

data class Scraper(
    val name: String,
    val baseUrl: String,
    val searchPath: String,
    var enabled: Boolean = true
)

/**
 * Melon Codes link format (copy-paste into Watermelon):
 *   wm://add?cfg=<base64url of JSON {"name":"..","baseUrl":"..","searchPath":".."}>
 */
object ScraperStore {
    private const val PREF = "wm_prefs"
    private const val KEY = "scrapers"
    private const val ACTIVE = "active_scraper"

    fun all(ctx: Context): MutableList<Scraper> {
        val raw = ctx.getSharedPreferences(PREF, 0).getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        return MutableList(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Scraper(o.getString("name"), o.getString("baseUrl"), o.getString("searchPath"), o.getBoolean("enabled"))
        }
    }

    fun save(ctx: Context, list: List<Scraper>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject()
                .put("name", it.name)
                .put("baseUrl", it.baseUrl)
                .put("searchPath", it.searchPath)
                .put("enabled", it.enabled))
        }
        ctx.getSharedPreferences(PREF, 0).edit().putString(KEY, arr.toString()).apply()
    }

    fun add(ctx: Context, s: Scraper) {
        val list = all(ctx)
        list.removeAll { it.name == s.name }
        list.add(s)
        save(ctx, list)
    }

    fun remove(ctx: Context, name: String) {
        save(ctx, all(ctx).filter { it.name != name })
    }

    fun activeScrapers(ctx: Context): List<Scraper> = all(ctx).filter { it.enabled }

    /** Selected scraper for home poster; falls back to first active one. */
    fun selectedName(ctx: Context): String? =
        ctx.getSharedPreferences(PREF, 0).getString(ACTIVE, null)

    fun setSelected(ctx: Context, name: String) {
        ctx.getSharedPreferences(PREF, 0).edit().putString(ACTIVE, name).apply()
    }

    fun parseMelonLink(link: String): Scraper? = try {
        val cfg = Uri.parse(link.trim()).getQueryParameter("cfg") ?: return null
        val json = JSONObject(String(Base64.decode(cfg, Base64.URL_SAFE or Base64.NO_WRAP)))
        Scraper(json.getString("name"), json.getString("baseUrl"), json.getString("searchPath"), true)
    } catch (e: Exception) {
        null
    }
}
