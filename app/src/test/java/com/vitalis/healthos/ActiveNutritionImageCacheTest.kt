package com.vitalis.healthos

import java.nio.file.Files
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class ActiveNutritionImageCacheTest {
    private val now = Instant.parse("2026-09-30T00:00:00Z")

    @Test fun activeImageRoundTripsAcrossCacheInstances() {
        val directory = Files.createTempDirectory("vitalis-active-image").toFile()
        try {
            val first = ActiveNutritionImageCache(directory, Clock.fixed(now, ZoneOffset.UTC))
            assertTrue(first.save("scan-stable", jpegDataUrl()))
            val recreated = ActiveNutritionImageCache(directory, Clock.fixed(now.plusSeconds(60), ZoneOffset.UTC))
            assertEquals(jpegDataUrl(), recreated.load("scan-stable"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun invalidIdsPayloadsAndNonJpegContentFailClosed() {
        val directory = Files.createTempDirectory("vitalis-active-image-invalid").toFile()
        try {
            val cache = ActiveNutritionImageCache(directory, Clock.fixed(now, ZoneOffset.UTC))
            assertFalse(cache.save("../escape", jpegDataUrl()))
            assertFalse(cache.save("scan-png", "data:image/png;base64," + Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3))))
            assertFalse(cache.save("scan-bad", ActiveNutritionImageCache.JPEG_DATA_PREFIX + "not-base64"))
            assertNull(cache.load("../escape"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun expiredImageIsDeletedInsteadOfDisplayedBroken() {
        val directory = Files.createTempDirectory("vitalis-active-image-expiry").toFile()
        try {
            val initial = ActiveNutritionImageCache(directory, Clock.fixed(now, ZoneOffset.UTC), Duration.ofHours(1))
            assertTrue(initial.save("scan-expired", jpegDataUrl()))
            val expired = ActiveNutritionImageCache(directory, Clock.fixed(now.plusSeconds(3601), ZoneOffset.UTC), Duration.ofHours(1))
            assertNull(expired.load("scan-expired"))
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun cancelAndSaveCleanupAreIdempotent() {
        val directory = Files.createTempDirectory("vitalis-active-image-delete").toFile()
        try {
            val cache = ActiveNutritionImageCache(directory, Clock.fixed(now, ZoneOffset.UTC))
            assertTrue(cache.save("scan-delete", jpegDataUrl()))
            assertTrue(cache.delete("scan-delete"))
            assertTrue(cache.delete("scan-delete"))
            assertNull(cache.load("scan-delete"))
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun jpegDataUrl(): String {
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1, 2, 3, 4)
        return ActiveNutritionImageCache.JPEG_DATA_PREFIX + Base64.getEncoder().encodeToString(bytes)
    }
}
