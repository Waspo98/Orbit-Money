@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package app.overbay.orbit.util

import java.io.File
import okio.FileSystem
import okio.Path.Companion.toPath

actual class PlatformStorage actual constructor() {
    companion object {
        var baseDir: File? = null
    }

    private val fs: FileSystem = FileSystem.SYSTEM
    private val directory: File
        get() = baseDir ?: File(System.getProperty("java.io.tmpdir", "."), "orbit_money").apply { mkdirs() }

    actual fun saveString(key: String, value: String) {
        val file = File(directory, key)
        val tempFile = File(directory, "$key.tmp")
        val okioPath = file.absolutePath.toPath()
        val tempOkioPath = tempFile.absolutePath.toPath()
        fs.write(tempOkioPath) { writeUtf8(value) }
        try {
            fs.atomicMove(tempOkioPath, okioPath)
        } catch (_: Exception) {
            if (fs.exists(okioPath)) fs.delete(okioPath)
            fs.copy(tempOkioPath, okioPath)
            if (fs.exists(tempOkioPath)) fs.delete(tempOkioPath)
        }
    }

    actual fun loadString(key: String): String? {
        val file = File(directory, key)
        val okioPath = file.absolutePath.toPath()
        return if (fs.exists(okioPath)) {
            fs.read(okioPath) { readUtf8() }
        } else null
    }

    actual fun delete(key: String): Boolean {
        val file = File(directory, key)
        val okioPath = file.absolutePath.toPath()
        return if (fs.exists(okioPath)) {
            fs.delete(okioPath)
            true
        } else false
    }

    actual fun exists(key: String): Boolean {
        val file = File(directory, key)
        val okioPath = file.absolutePath.toPath()
        return fs.exists(okioPath)
    }

    actual fun listKeys(): List<String> {
        return directory.listFiles()?.map { it.name }?.filterNot { it.endsWith(".tmp") } ?: emptyList()
    }

    actual fun clear() {
        PlatformStorageEpoch.bump()
        directory.listFiles()?.forEach { it.delete() }
    }
}
