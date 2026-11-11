package com.watermelon.app

import java.net.HttpURLConnection
import java.net.URL

object Net {
    // Apne GitHub repo ka update.json raw link yahan daalein
    const val UPDATE_URL = "https://raw.githubusercontent.com/watermelon108/watermelon/main/update.json"

    /** Blocking GET. Sirf background thread se call karein. */
    fun get(url: String): String? {
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10000
            c.readTimeout = 15000
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) Watermelon/1.1")
            val text = c.inputStream.bufferedReader().use { it.readText() }
            c.disconnect()
            text
        } catch (e: Exception) {
            null
        }
    }
}
