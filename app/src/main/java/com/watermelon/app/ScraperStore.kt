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
        val list = mutableListOf<Scraper>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                Scraper(
                    o.getString("name"),
                    o.getString("baseUrl"),
                    o.getString("searchPath"),
                    o.getBoolean("enabled")
                )
            )
        }
        return list
    }

    fun save(ctx: Context, list: List<Scraper>) {
        val arr = JSONArray()
        for (s in list) {
            arr.put(
                JSONObject()
                    .put("name", s.name)
                    .put("baseUrl", s.baseUrl)
                    .put("searchPath", s.searchPath)
                    .put("enabled", s.enabled)
            )
        }
        ctx.getSharedPreferences(PREF, 0).edit().putString(KEY, arr.toString()).apply()
    }

    fun add(ctx: Context, s: Scraper) {
        val list = all(ctx)
        list.removeAll { it.name == s.name }
        list.add(s)
        save(ctx, list)
    }

    fun remove(ctx: C
