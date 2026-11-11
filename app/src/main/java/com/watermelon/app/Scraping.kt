package com.watermelon.app

object Scraping {
    private val mediaRegex = Regex(
        """https?://[^\s"'<>\\]+?\.(?:m3u8|mp4|mkv|webm)(?:\?[^\s"'<>\\]*)?""",
        RegexOption.IGNORE_CASE
    )

    /** Page HTML me se video/stream links nikalta hai. */
    fun findMedia(html: String): List<String> {
        return mediaRegex.findAll(html).map { it.value }.distinct().toList()
    }
}
