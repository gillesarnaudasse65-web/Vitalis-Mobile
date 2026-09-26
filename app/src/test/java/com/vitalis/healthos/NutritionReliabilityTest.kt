package com.vitalis.healthos

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NutritionReliabilityTest {
    private val now = Instant.parse("2026-09-18T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test fun detectsSupportedImageSignatures() {
        assertEquals(NutritionImageFormat.JPEG, NutritionImageFormat.detect(byteArrayOf(-1, -40, -1, 0)))
        assertEquals(NutritionImageFormat.PNG, NutritionImageFormat.detect(
            byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
        ))
        assertEquals(NutritionImageFormat.WEBP, NutritionImageFormat.detect(
            "RIFF0000WEBP".toByteArray()
        ))
        assertNull(NutritionImageFormat.detect(byteArrayOf(1, 2, 3)))
        assertNull(NutritionImageFormat.detect(byteArrayOf()))
    }

    @Test fun rejectsZeroCorruptSpoofedAndOversizeImages() {
        fun validate(candidate: NutritionImageCandidate) = NutritionImagePolicy.validate(candidate)
        assertEquals("zero_byte", validate(candidate(bytes = 0)).errorCode)
        assertEquals("corrupt_or_unsupported", validate(candidate(format = null)).errorCode)
        assertEquals("mime_spoof", validate(candidate(mime = "image/png")).errorCode)
        assertEquals("source_too_large", validate(candidate(
            bytes = NutritionImagePolicy.MAX_SOURCE_BYTES + 1
        )).errorCode)
        assertEquals("dimensions_too_large", validate(candidate(
            width = NutritionImagePolicy.MAX_SOURCE_WIDTH + 1
        )).errorCode)
        assertEquals("pixel_count_too_large", validate(candidate(width = 10_000, height = 5_000)).errorCode)
    }

    @Test fun acceptsBoundedImageAndPlansOrientationResize() {
        assertTrue(NutritionImagePolicy.validate(candidate()).accepted)
        val landscape = NutritionImagePolicy.plan(4_000, 2_000)
        assertEquals(1_280, landscape.targetWidth)
        assertEquals(640, landscape.targetHeight)
        val rotated = NutritionImagePolicy.plan(4_000, 2_000, 90)
        assertEquals(640, rotated.targetWidth)
        assertEquals(1_280, rotated.targetHeight)
    }

    @Test fun parsesCompleteStructuredEstimateAndCodeFence() {
        val result = NutritionEstimateParser.parseForReview("```json\n${validEstimate()}\n```")
        assertTrue(result.reviewable)
        assertTrue(result.savable)
        assertEquals(520.0, result.value!!.nutrients.caloriesKcal!!, 0.0)
        assertEquals("Bol végétal", result.value!!.mealName)
    }

    @Test fun partialEstimateIsReviewableButNotSavable() {
        val result = NutritionEstimateParser.parseForReview(
            """{"mealName":"À vérifier","confidence":0.2,"foodItems":[]}"""
        )
        assertTrue(result.reviewable)
        assertFalse(result.savable)
        assertTrue(result.warnings.contains("low_confidence"))
        assertTrue(result.warnings.contains("missing:caloriesKcal"))
    }

    @Test fun rejectsMalformedTextEmptyWrongTypesAndExtremeValues() {
        assertEquals("empty_response", NutritionEstimateParser.parseForReview(" ").errors.single())
        assertEquals("malformed_json", NutritionEstimateParser.parseForReview("a meal, about 500 kcal").errors.single())
        assertFalse(NutritionEstimateParser.parseForReview("{" + "x".repeat(32_001) + "}").reviewable)
        val negative = NutritionEstimateParser.parseForReview(validEstimate().replace("\"caloriesKcal\":520", "\"caloriesKcal\":-1"))
        assertTrue(negative.errors.contains("negative:caloriesKcal"))
        val huge = NutritionEstimateParser.parseForReview(validEstimate().replace("\"sodiumMg\":640", "\"sodiumMg\":100001"))
        assertTrue(huge.errors.contains("too_large:sodiumMg"))
        val wrong = NutritionEstimateParser.parseForReview(validEstimate().replace("\"proteinG\":21", "\"proteinG\":\"21\""))
        assertTrue(wrong.errors.contains("wrong_type:proteinG"))
    }

    @Test fun rejectsHugeStringsAndNonFiniteSyntax() {
        val hugeName = JSONObject(validEstimate()).put("mealName", "x".repeat(161)).toString()
        assertTrue(NutritionEstimateParser.parseForReview(hugeName).errors.contains("too_long:mealName"))
        assertFalse(NutritionEstimateParser.parseForReview(
            validEstimate().replace("\"fatG\":19", "\"fatG\":NaN")
        ).savable)
        assertFalse(NutritionEstimateParser.parseForReview(
            validEstimate().replace("\"fatG\":19", "\"fatG\":Infinity")
        ).savable)
    }

    @Test fun selectedDateIsCapturedAndNewScanOwnsResults() {
        val coordinator = NutritionScanCoordinator()
        val a = coordinator.begin("scan-a", LocalDate.parse("2026-09-18"), NutritionImageSource.GALLERY, now)
        coordinator.markImageReady(a.scanId, metadata())
        coordinator.startAnalysis(a.scanId, "request-a")
        val b = coordinator.begin("scan-b", LocalDate.parse("2026-09-19"), NutritionImageSource.CAMERA, now.plusSeconds(1))
        assertNull(coordinator.acceptAnalysis(a.scanId, "request-a", estimate()))
        assertEquals(LocalDate.parse("2026-09-18"), coordinator.session(a.scanId)!!.selectedDate)
        assertEquals(LocalDate.parse("2026-09-19"), b.selectedDate)
    }

    @Test fun cancelledRequestCannotAcceptLateResult() {
        val coordinator = NutritionScanCoordinator()
        coordinator.begin("scan-cancel", LocalDate.parse("2026-02-28"), NutritionImageSource.GALLERY, now)
        coordinator.markImageReady("scan-cancel", metadata())
        coordinator.startAnalysis("scan-cancel", "request-cancel")
        coordinator.cancel("scan-cancel")
        assertNull(coordinator.acceptAnalysis("scan-cancel", "request-cancel", estimate()))
    }

    @Test fun stableMealIdUpsertMakesDoubleAndTripleSaveIdempotent() {
        val storage = MemoryStorage()
        val store = NutritionMealStore(storage, clock)
        val original = record("meal-scan-one", "scan-one", 520.0)
        repeat(3) { assertTrue(store.upsert(original).success) }
        assertEquals(1, store.list().value!!.size)
        assertEquals(520.0, store.list().value!!.single().nutrients.caloriesKcal!!, 0.0)
    }

    @Test fun restartEditAndDeletePreserveIdentity() {
        val storage = MemoryStorage()
        NutritionMealStore(storage, clock).upsert(record("meal-scan-two", "scan-two", 520.0))
        val afterRestart = NutritionMealStore(storage, clock)
        val edited = afterRestart.list().value!!.single().copy(
            nutrients = estimate().nutrients.copy(caloriesKcal = 610.0),
            updatedAt = now.plusSeconds(30)
        )
        assertTrue(afterRestart.upsert(edited).success)
        assertEquals(1, afterRestart.list().value!!.size)
        assertEquals(610.0, afterRestart.list().value!!.single().nutrients.caloriesKcal!!, 0.0)
        assertEquals(true, afterRestart.delete(edited.id).value)
        assertTrue(afterRestart.list().value!!.isEmpty())
    }

    @Test fun migrationKeepsLegacyMealsAndPreservesUnknownInvalidRecords() {
        val legacy = NutritionMealCodec.encode(record("legacy-one", null, 400.0)).apply {
            remove("schemaVersion")
            remove("date")
        }
        val invalid = JSONObject().put("futureRecord", true)
        val storage = MemoryStorage(JSONArray().put(legacy).put(invalid).toString())
        val result = NutritionMealStore(storage, clock).migrate()
        assertTrue(result.success)
        assertEquals(1, result.value)
        assertEquals(1, result.preservedInvalidRecords)
        val raw = JSONArray(storage.value)
        assertEquals(2, raw.length())
        assertEquals(2, raw.getJSONObject(0).getInt("schemaVersion"))
        assertTrue(raw.getJSONObject(1).getBoolean("futureRecord"))
    }

    @Test fun malformedStoreIsNeverSilentlyOverwritten() {
        val storage = MemoryStorage("not-json")
        val result = NutritionMealStore(storage, clock).upsert(record("meal-safe", "scan-safe", 400.0))
        assertFalse(result.success)
        assertEquals("malformed_store", result.errorCode)
        assertEquals("not-json", storage.value)
    }

    @Test fun exportIsVersionedAndContainsNoImagesOrSecrets() {
        val export = NutritionExportCodec.encode(listOf(record("meal-export", "scan-export", 520.0)), now, "3.14.0-coaches-connectors")
        val json = JSONObject(export)
        assertEquals("vitalis-local-nutrition", json.getString("format"))
        assertEquals(1, json.getInt("formatVersion"))
        assertEquals("kcal", json.getJSONObject("nutrientUnits").getString("caloriesKcal"))
        assertFalse(export.contains("image", ignoreCase = true))
        assertFalse(export.contains("Authorization", ignoreCase = true))
        assertFalse(export.contains("api_key", ignoreCase = true))
    }

    @Test fun deleteAllOnlyClearsMealStorage() {
        val storage = MemoryStorage()
        val store = NutritionMealStore(storage, clock)
        store.upsert(record("meal-delete", "scan-delete", 520.0))
        assertEquals(true, store.deleteAll().value)
        assertNull(storage.value)
    }

    private fun candidate(
        mime: String = "image/jpeg",
        format: NutritionImageFormat? = NutritionImageFormat.JPEG,
        bytes: Long = 200_000,
        width: Int = 2_000,
        height: Int = 1_000
    ) = NutritionImageCandidate(mime, format, bytes, width, height)

    private fun metadata() = NormalizedImageMetadata(
        "image/jpeg", 200_000, 2_000, 1_000, "image/jpeg", 100_000, 1_280, 640, true
    )

    private fun estimate() = NutritionEstimateParser.parseForReview(validEstimate()).value!!

    private fun record(id: String, scanId: String?, calories: Double) = NutritionMealRecord(
        id = id,
        date = LocalDate.parse("2026-09-18"),
        createdAt = now,
        updatedAt = now,
        source = "Vitalis Scanner",
        scanId = scanId,
        mealName = "Bol végétal",
        foodItems = estimate().foodItems,
        nutrients = estimate().nutrients.copy(caloriesKcal = calories),
        confidence = 0.71,
        notes = "À vérifier",
        estimated = true
    )

    private fun validEstimate() = """{
        "foodItems":[{"name":"Bol de légumes","portion":"1 bol","estimatedCalories":520,"confidence":0.71}],
        "mealName":"Bol végétal","portionDescription":"1 bol moyen","caloriesKcal":520,
        "carbohydratesG":62,"proteinG":21,"fatG":19,"fibreG":13,"sugarG":9,
        "sodiumMg":640,"confidence":0.71,"uncertaintyNotes":"Sauce à vérifier"
    }""".trimIndent()

    private class MemoryStorage(initial: String? = "[]") : NutritionStringStorage {
        var value: String? = initial
        override fun read(): String? = value
        override fun write(value: String): Boolean { this.value = value; return true }
        override fun remove(): Boolean { value = null; return true }
    }
}
