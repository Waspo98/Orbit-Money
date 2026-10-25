@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package app.overbay.orbit.util

import kotlinx.browser.localStorage

actual class PlatformStorage actual constructor() {
    companion object {
        private val memoryFallback = mutableMapOf<String, String>()
    }

    actual fun saveString(key: String, value: String) {
        try {
            localStorage.setItem(key, value)
        } catch (_: Throwable) {
            memoryFallback[key] = value
        }
    }

    actual fun loadString(key: String): String? {
        return try {
            localStorage.getItem(key) ?: memoryFallback[key]
        } catch (_: Throwable) {
            memoryFallback[key]
        }
    }

    actual fun delete(key: String): Boolean {
        return try {
            val existed = localStorage.getItem(key) != null || memoryFallback.containsKey(key)
            localStorage.removeItem(key)
            memoryFallback.remove(key)
            existed
        } catch (_: Throwable) {
            memoryFallback.remove(key) != null
        }
    }

    actual fun exists(key: String): Boolean {
        return try {
            localStorage.getItem(key) != null || memoryFallback.containsKey(key)
        } catch (_: Throwable) {
            memoryFallback.containsKey(key)
        }
    }

    actual fun listKeys(): List<String> {
        val keys = mutableListOf<String>()
        try {
            val length = localStorage.length
            for (i in 0 until length) {
                localStorage.key(i)?.let { keys.add(it) }
            }
        } catch (_: Throwable) {
            keys.addAll(memoryFallback.keys)
        }
        return keys
    }

    actual fun clear() {
        try {
            localStorage.clear()
        } catch (_: Throwable) {}
        memoryFallback.clear()
    }
}
