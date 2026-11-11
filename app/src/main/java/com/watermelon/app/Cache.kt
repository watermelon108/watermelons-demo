package com.watermelon.app

object Cache {
    private class Entry(val value: Any, val time: Long)

    private const val TTL = 30L * 60L * 1000L
    private val map = HashMap<String, Entry>()

    @Synchronized
    fun <T> get(key: String): T? {
        val e = map[key] ?: return null
        if (System.currentTimeMillis() - e.time > TTL) {
            map.remove(key)
            return null
        }
        @Suppress("UNCHECKED_CAST")
        return e.value as T
    }

    @Synchronized
    fun put(key: String, value: Any) {
        map[key] = Entry(value, System.currentTimeMillis())
    }
}
