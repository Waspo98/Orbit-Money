@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package app.overbay.orbit.util

import kotlin.concurrent.Volatile

object PlatformStorageEpoch {
    @Volatile
    var epoch: Long = 0L
        private set

    fun bump() {
        epoch += 1L
    }
}

expect class PlatformStorage() {
    fun saveString(key: String, value: String)
    fun loadString(key: String): String?
    fun delete(key: String): Boolean
    fun exists(key: String): Boolean
    fun listKeys(): List<String>
    fun clear()
}
