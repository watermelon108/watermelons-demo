package com.watermelon.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class LibItem(val url: String, val title: String, val tag: String)

object LibraryStore {
    val TAGS = listOf("Watching", "Completed", "On Hold", "Dropped", "Plan to Watch", "Favorites")
    private const val PREF = "wm_library"
    private const val KEY = "items"

    fun all(ctx: Context): MutableList<LibItem> {
        val raw = ctx.getSharedPreferences(PREF, 0).getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = mutableListOf<LibItem>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(LibItem(o.getString("url"), o.getString("title"), o.getString("tag")))
        }
        return list
    }

    fun save(ctx: Context, list: List<LibItem>) {
        val arr = JSONArray()
        for (item in list) {
            arr.put(
                JSONObject()
                    .put("url", item.url)
                    .put("title", item.title)
                    .put("tag", item.tag)
            )
        }
        ctx.getSharedPreferences(PREF, 0).edit().putString(KEY, arr.toString()).apply()
    }

    fun put(ctx: Context, item: LibItem) {
        val list = all(ctx)
        list.removeAll { it.url == item.url }
        list.add(item)
        save(ctx, list)
    }
}
