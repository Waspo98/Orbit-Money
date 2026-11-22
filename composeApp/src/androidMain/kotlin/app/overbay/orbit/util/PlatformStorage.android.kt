@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package app.overbay.orbit.util

import java.io.File
import okio.FileSystem
import okio.Path.Companion.toPath

actual class PlatformStorage actual constructor() {
    companion object {
        var baseDir: File? = null
        private val ioLock = Any()
    }

    private val fs: FileSystem = FileSystem.SYSTEM
    private val directory: File
        get() = (baseDir ?: File(System.getProperty("java.io.tmpdir", "."), "orbit_money")).apply {
            if (!exists()) mkdirs()
        }

    actual fun saveString(key: String, value: String) {
        synchronized(ioLock) {
            val dir = directory
            val file = File(dir, key)
            val tempFile = File(dir, "$key.${System.nanoTime()}.tmp")
            val okioPath = file.absolutePath.toPath()
            val tempOkioPath = tempFile.absolutePath.toPath()
            try {
                fs.write(tempOkioPath) { writeUtf8(value) }
                try {
                    fs.atomicMove(tempOkioPath, okioPath)
                } catch (_: Exception) {
                    if (fs.exists(okioPath)) fs.delete(okioPath)
                    fs.copy(tempOkioPath, okioPath)
                }
            } finally {
                runCatching { if (fs.exists(tempOkioPath)) fs.delete(tempOkioPath) }
            }
        }
    }

    actual fun loadString(key: String): String? = synchronized(ioLock) {
        val file = File(directory, key)
        val okioPath = file.absolutePath.toPath()
        if (fs.exists(okioPath)) {
            fs.read(okioPath) { readUtf8() }
        } else null
    }

    actual fun delete(key: String): Boolean = synchronized(ioLock) {
        val file = File(directory, key)
        val okioPath = file.absolutePath.toPath()
        if (fs.exists(okioPath)) {
            fs.delete(okioPath)
            true
        } else false
    }

    actual fun exists(key: String): Boolean = synchronized(ioLock) {
        val file = File(directory, key)
        val okioPath = file.absolutePath.toPath()
        fs.exists(okioPath)
    }

    actual fun listKeys(): List<String> = synchronized(ioLock) {
        directory.listFiles()?.map { it.name }?.filterNot { it.endsWith(".tmp") } ?: emptyList()
    }

    actual fun clear() {
        synchronized(ioLock) {
            PlatformStorageEpoch.bump()
            directory.listFiles()?.forEach { it.delete() }
        }
    }
}
