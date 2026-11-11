package com.watermelon.app

import java.net.HttpURLConnection
import java.net.URL

object Net {
    // Apne GitHub repo ka update.json raw link
    const val UPDATE_URL = "https://raw.githubusercontent.com/watermelon108/watermelon/main/update.json"

    /** Blocking GET. Sirf background thread se call karein. */
    fun get(url: String): String? = request(url, null)

    /** Blocking POST (JSON body). Sirf background thread se call karein. */
    fun post(url: String, body: String): String? = request(url, body)

    private fun request(url: String, body: String?): String? {
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10000
            c.readTimeout = 15000
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) Watermelon/1.3")
            if (body != null) {
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val text = c.inputStream.bufferedReader().use { it.readText() }
            c.disconnect()
            text
        } catch (e: Exception) {
            null
        }
    }
}
