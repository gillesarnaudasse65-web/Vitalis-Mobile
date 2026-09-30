package com.vitalis.healthos

import java.io.File
import java.time.Clock
import java.time.Duration
import java.util.Base64

/**
 * Private, temporary storage for the normalized image owned by the active nutrition scan.
 * The cache survives Activity/process recreation, but is removed on save/cancel and expires.
 */
class ActiveNutritionImageCache(
    private val directory: File,
    private val clock: Clock = Clock.systemUTC(),
    private val maxAge: Duration = Duration.ofHours(24)
) {
    init {
        directory.mkdirs()
    }

    fun save(scanId: String, dataUrl: String): Boolean {
        if (!NutritionIds.valid(scanId) || !dataUrl.startsWith(JPEG_DATA_PREFIX)) return false
        val encoded = dataUrl.removePrefix(JPEG_DATA_PREFIX)
        if (encoded.isBlank() || encoded.length > NutritionImagePolicy.MAX_BASE64_CHARACTERS) return false
        val bytes = runCatching { Base64.getDecoder().decode(encoded) }.getOrNull() ?: return false
        if (!isValidJpeg(bytes)) return false
        val target = file(scanId)
        val temporary = File(directory, ".${target.name}.tmp")
        return runCatching {
            directory.mkdirs()
            temporary.outputStream().use { it.write(bytes) }
            check(temporary.length() == bytes.size.toLong())
            if (target.exists() && !target.delete()) error("replace_failed")
            if (!temporary.renameTo(target)) error("commit_failed")
            target.setLastModified(clock.millis())
            true
        }.getOrElse {
            temporary.delete()
            false
        }
    }

    fun load(scanId: String): String? = bytes(scanId)?.let {
        JPEG_DATA_PREFIX + Base64.getEncoder().encodeToString(it)
    }

    fun bytes(scanId: String): ByteArray? {
        if (!NutritionIds.valid(scanId)) return null
        val candidate = file(scanId)
        if (!candidate.isFile || isExpired(candidate)) {
            candidate.delete()
            return null
        }
        val bytes = runCatching { candidate.readBytes() }.getOrNull() ?: return null
        if (!isValidJpeg(bytes)) {
            candidate.delete()
            return null
        }
        return bytes
    }

    fun delete(scanId: String): Boolean =
        !NutritionIds.valid(scanId) || !file(scanId).exists() || file(scanId).delete()

    fun clear() {
        directory.listFiles()?.forEach { file ->
            if (file.isFile && (file.name.endsWith(FILE_SUFFIX) || file.name.endsWith(".tmp"))) {
                file.delete()
            }
        }
    }

    fun prune() {
        directory.listFiles()?.forEach { file ->
            if (file.isFile && (file.name.endsWith(".tmp") || isExpired(file))) file.delete()
        }
    }

    private fun file(scanId: String) = File(directory, "$scanId$FILE_SUFFIX")

    private fun isExpired(file: File): Boolean =
        clock.millis() - file.lastModified() > maxAge.toMillis()

    private fun isValidJpeg(bytes: ByteArray): Boolean =
        bytes.size in 3..NutritionImagePolicy.MAX_NORMALIZED_BYTES &&
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()

    companion object {
        const val JPEG_DATA_PREFIX = "data:image/jpeg;base64,"
        private const val FILE_SUFFIX = ".normalized.jpg"
    }
}
