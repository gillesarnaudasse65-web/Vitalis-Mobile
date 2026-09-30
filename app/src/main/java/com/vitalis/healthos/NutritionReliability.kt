package com.vitalis.healthos

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

enum class NutritionImageSource { GALLERY, CAMERA, SYNTHETIC_TEST }

enum class NutritionScanStatus {
    IDLE,
    SELECTING_IMAGE,
    NORMALIZING,
    READY_FOR_ANALYSIS,
    ANALYZING,
    REVIEW,
    SAVING,
    SAVED,
    CANCELLED,
    ERROR
}

data class NormalizedImageMetadata(
    val sourceMimeType: String,
    val sourceBytes: Long,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val normalizedMimeType: String,
    val normalizedBytes: Int,
    val normalizedWidth: Int,
    val normalizedHeight: Int,
    val orientationApplied: Boolean
)

data class NutritionFoodItem(
    val name: String,
    val portion: String? = null,
    val estimatedCalories: Double? = null,
    val confidence: Double? = null
)

data class NutritionNutrients(
    val caloriesKcal: Double?,
    val carbohydratesG: Double?,
    val proteinG: Double?,
    val fatG: Double?,
    val fibreG: Double?,
    val sugarG: Double?,
    val sodiumMg: Double?
) {
    fun complete(): Boolean = listOf(
        caloriesKcal,
        carbohydratesG,
        proteinG,
        fatG,
        fibreG,
        sugarG,
        sodiumMg
    ).all { it != null }
}

data class NutritionEstimate(
    val foodItems: List<NutritionFoodItem>,
    val mealName: String,
    val portionDescription: String?,
    val nutrients: NutritionNutrients,
    val confidence: Double,
    val uncertaintyNotes: String?,
    val estimated: Boolean = true
)

data class NutritionValidationResult<T>(
    val value: T?,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    val reviewable: Boolean get() = value != null
    val savable: Boolean get() = value != null && errors.isEmpty() &&
        (value !is NutritionEstimate || value.nutrients.complete())
}

data class NutritionScanSession(
    val scanId: String,
    val mealId: String,
    val createdAt: Instant,
    val selectedDate: LocalDate,
    val imageSource: NutritionImageSource,
    val normalizedImageMetadata: NormalizedImageMetadata? = null,
    val analysisStatus: NutritionScanStatus = NutritionScanStatus.SELECTING_IMAGE,
    val analysisRequestId: String? = null,
    val draftResult: NutritionEstimate? = null,
    val savedMealId: String? = null,
    val errorCode: String? = null
)

class NutritionScanCoordinator {
    private val sessions = linkedMapOf<String, NutritionScanSession>()
    private var activeScanId: String? = null

    fun begin(
        scanId: String,
        selectedDate: LocalDate,
        source: NutritionImageSource,
        createdAt: Instant
    ): NutritionScanSession {
        require(NutritionIds.valid(scanId))
        val session = NutritionScanSession(
            scanId = scanId,
            mealId = "meal-$scanId",
            createdAt = createdAt,
            selectedDate = selectedDate,
            imageSource = source
        )
        sessions[scanId] = session
        activeScanId = scanId
        return session
    }

    fun session(scanId: String): NutritionScanSession? = sessions[scanId]

    fun active(): NutritionScanSession? = activeScanId?.let(sessions::get)

    fun restore(session: NutritionScanSession): NutritionScanSession {
        require(NutritionIds.valid(session.scanId))
        sessions[session.scanId] = session
        if (session.analysisStatus !in setOf(
                NutritionScanStatus.SAVED,
                NutritionScanStatus.CANCELLED
            )) activeScanId = session.scanId
        return session
    }

    fun markNormalizing(scanId: String): NutritionScanSession? = update(scanId) {
        it.copy(analysisStatus = NutritionScanStatus.NORMALIZING, errorCode = null)
    }

    fun markImageReady(scanId: String, metadata: NormalizedImageMetadata): NutritionScanSession? =
        update(scanId) {
            it.copy(
                normalizedImageMetadata = metadata,
                analysisStatus = NutritionScanStatus.READY_FOR_ANALYSIS,
                errorCode = null
            )
        }

    fun startAnalysis(scanId: String, requestId: String): NutritionScanSession? {
        if (!NutritionIds.valid(requestId) || activeScanId != scanId) return null
        return update(scanId) {
            it.copy(
                analysisStatus = NutritionScanStatus.ANALYZING,
                analysisRequestId = requestId,
                errorCode = null
            )
        }
    }

    fun acceptAnalysis(
        scanId: String,
        requestId: String,
        estimate: NutritionEstimate
    ): NutritionScanSession? {
        val current = sessions[scanId] ?: return null
        if (activeScanId != scanId || current.analysisStatus != NutritionScanStatus.ANALYZING ||
            current.analysisRequestId != requestId) return null
        return update(scanId) {
            it.copy(analysisStatus = NutritionScanStatus.REVIEW, draftResult = estimate)
        }
    }

    fun startSaving(scanId: String): NutritionScanSession? = updateCurrent(scanId) {
        it.copy(analysisStatus = NutritionScanStatus.SAVING)
    }

    fun markSaved(scanId: String): NutritionScanSession? = updateCurrent(scanId) {
        it.copy(analysisStatus = NutritionScanStatus.SAVED, savedMealId = it.mealId)
    }?.also { if (activeScanId == scanId) activeScanId = null }

    fun fail(scanId: String, errorCode: String): NutritionScanSession? = update(scanId) {
        it.copy(analysisStatus = NutritionScanStatus.ERROR, errorCode = errorCode.take(80))
    }

    fun cancel(scanId: String): NutritionScanSession? {
        val result = update(scanId) {
            it.copy(analysisStatus = NutritionScanStatus.CANCELLED, analysisRequestId = null)
        }
        if (activeScanId == scanId) activeScanId = null
        return result
    }

    fun isCurrent(scanId: String, requestId: String? = null): Boolean {
        val session = sessions[scanId] ?: return false
        return activeScanId == scanId &&
            session.analysisStatus !in setOf(NutritionScanStatus.CANCELLED, NutritionScanStatus.ERROR) &&
            (requestId == null || session.analysisRequestId == requestId)
    }

    private fun updateCurrent(
        scanId: String,
        transform: (NutritionScanSession) -> NutritionScanSession
    ): NutritionScanSession? = if (activeScanId == scanId) update(scanId, transform) else null

    private fun update(
        scanId: String,
        transform: (NutritionScanSession) -> NutritionScanSession
    ): NutritionScanSession? {
        val current = sessions[scanId] ?: return null
        return transform(current).also { sessions[scanId] = it }
    }
}

internal object NutritionIds {
    private val safe = Regex("[A-Za-z0-9][A-Za-z0-9_.:-]{0,119}")
    fun valid(value: String?): Boolean = value != null && safe.matches(value)
}

enum class NutritionImageFormat(val mimeType: String) {
    JPEG("image/jpeg"),
    PNG("image/png"),
    WEBP("image/webp");

    companion object {
        fun detect(bytes: ByteArray): NutritionImageFormat? = when {
            bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() &&
                bytes[2] == 0xFF.toByte() -> JPEG
            bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(
                byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
            ) -> PNG
            bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> WEBP
            else -> null
        }

        fun fromMime(raw: String?): NutritionImageFormat? = entries.firstOrNull {
            it.mimeType == raw?.lowercase(Locale.ROOT)
        }
    }
}

data class NutritionImageCandidate(
    val declaredMimeType: String?,
    val detectedFormat: NutritionImageFormat?,
    val sourceBytes: Long,
    val width: Int,
    val height: Int
)

data class NutritionImageValidation(
    val accepted: Boolean,
    val errorCode: String? = null,
    val userMessage: String? = null
)

data class NutritionNormalizationPlan(
    val targetWidth: Int,
    val targetHeight: Int,
    val outputMimeType: String = "image/jpeg",
    val quality: Int = NutritionImagePolicy.OUTPUT_QUALITY,
    val orientationDegrees: Int = 0
)

object NutritionImagePolicy {
    const val MAX_SOURCE_BYTES = 12L * 1024L * 1024L
    const val MAX_SOURCE_WIDTH = 12_000
    const val MAX_SOURCE_HEIGHT = 12_000
    const val MAX_SOURCE_PIXELS = 40_000_000L
    const val NORMALIZED_MAX_DIMENSION = 1_280
    const val OUTPUT_QUALITY = 82
    const val MAX_NORMALIZED_BYTES = 1_500_000
    const val MAX_BASE64_CHARACTERS = 2_100_000

    fun validate(candidate: NutritionImageCandidate): NutritionImageValidation {
        if (candidate.sourceBytes <= 0L) return reject("zero_byte", "L’image sélectionnée est vide.")
        if (candidate.sourceBytes > MAX_SOURCE_BYTES) return reject(
            "source_too_large",
            "L’image dépasse la limite de 12 Mo."
        )
        val format = candidate.detectedFormat
            ?: return reject("corrupt_or_unsupported", "Le fichier n’est pas une image JPEG, PNG ou WebP valide.")
        val declared = candidate.declaredMimeType?.lowercase(Locale.ROOT)
        if (declared != null && declared != "image/*") {
            val declaredFormat = NutritionImageFormat.fromMime(declared)
                ?: return reject("unsupported_mime", "Format d’image non pris en charge.")
            if (declaredFormat != format) return reject(
                "mime_spoof",
                "Le contenu du fichier ne correspond pas à son type déclaré."
            )
        }
        if (candidate.width <= 0 || candidate.height <= 0) return reject(
            "decode_bounds_failed",
            "Les dimensions de l’image sont illisibles."
        )
        if (candidate.width > MAX_SOURCE_WIDTH || candidate.height > MAX_SOURCE_HEIGHT) return reject(
            "dimensions_too_large",
            "Les dimensions de l’image sont trop grandes."
        )
        val pixels = candidate.width.toLong() * candidate.height.toLong()
        if (pixels > MAX_SOURCE_PIXELS) return reject(
            "pixel_count_too_large",
            "L’image contient trop de pixels pour être traitée en sécurité."
        )
        return NutritionImageValidation(true)
    }

    fun plan(width: Int, height: Int, orientationDegrees: Int = 0): NutritionNormalizationPlan {
        require(width > 0 && height > 0)
        val swap = orientationDegrees.mod(180) != 0
        val orientedWidth = if (swap) height else width
        val orientedHeight = if (swap) width else height
        val scale = minOf(1.0, NORMALIZED_MAX_DIMENSION.toDouble() / maxOf(orientedWidth, orientedHeight))
        return NutritionNormalizationPlan(
            targetWidth = maxOf(1, (orientedWidth * scale).toInt()),
            targetHeight = maxOf(1, (orientedHeight * scale).toInt()),
            orientationDegrees = orientationDegrees
        )
    }

    private fun reject(code: String, message: String) =
        NutritionImageValidation(false, code, message)
}

object NutritionEstimateParser {
    const val MAX_RESPONSE_CHARACTERS = 32_000
    const val MAX_MEAL_NAME = 160
    const val MAX_PORTION_DESCRIPTION = 240
    const val MAX_NOTE = 2_000
    const val MAX_FOOD_ITEMS = 50
    const val MAX_FOOD_TEXT = 160

    val NUTRIENT_LIMITS = mapOf(
        "caloriesKcal" to 20_000.0,
        "carbohydratesG" to 2_000.0,
        "proteinG" to 2_000.0,
        "fatG" to 2_000.0,
        "fibreG" to 2_000.0,
        "sugarG" to 2_000.0,
        "sodiumMg" to 100_000.0
    )

    fun parseForReview(raw: String): NutritionValidationResult<NutritionEstimate> {
        if (raw.isBlank()) return invalid("empty_response")
        if (raw.length > MAX_RESPONSE_CHARACTERS) return invalid("response_too_large")
        val cleaned = raw.trim()
            .replace(Regex("^```(?:json)?\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*```$"), "")
        val source = runCatching { JSONObject(cleaned) }.getOrNull()
            ?: return invalid("malformed_json")
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val name = strictString(source, listOf("mealName", "name"), MAX_MEAL_NAME, errors)
            ?: "Repas à corriger".also { warnings += "missing:mealName" }
        val portion = strictOptionalString(
            source,
            listOf("portionDescription", "portion"),
            MAX_PORTION_DESCRIPTION,
            errors
        )
        val uncertainty = strictOptionalString(
            source,
            listOf("uncertaintyNotes", "summary", "improvement"),
            MAX_NOTE,
            errors
        )
        val foodItems = parseFoodItems(source, errors, warnings)
        val nutrients = NutritionNutrients(
            caloriesKcal = number(source, listOf("caloriesKcal"), "caloriesKcal", errors, warnings),
            carbohydratesG = number(
                source,
                listOf("carbohydratesG", "carbohydratesGrams"),
                "carbohydratesG",
                errors,
                warnings
            ),
            proteinG = number(source, listOf("proteinG", "proteinGrams"), "proteinG", errors, warnings),
            fatG = number(source, listOf("fatG", "fatGrams"), "fatG", errors, warnings),
            fibreG = number(source, listOf("fibreG", "fiberG", "fiberGrams"), "fibreG", errors, warnings),
            sugarG = number(source, listOf("sugarG", "sugarGrams"), "sugarG", errors, warnings),
            sodiumMg = number(
                source,
                listOf("sodiumMg", "sodiumMilligrams"),
                "sodiumMg",
                errors,
                warnings
            )
        )
        val confidence = optionalNumber(source, "confidence", 1.0, errors)
            ?: 0.0.also { warnings += "missing:confidence" }
        if (confidence < 0.35) warnings += "low_confidence"
        return NutritionValidationResult(
            NutritionEstimate(
                foodItems = foodItems,
                mealName = name,
                portionDescription = portion,
                nutrients = nutrients,
                confidence = confidence,
                uncertaintyNotes = uncertainty
            ),
            errors.distinct(),
            warnings.distinct()
        )
    }

    fun toReviewJson(result: NutritionValidationResult<NutritionEstimate>): JSONObject =
        JSONObject().apply {
            put("reviewable", result.reviewable)
            put("savable", result.savable)
            put("errors", JSONArray(result.errors))
            put("warnings", JSONArray(result.warnings))
            result.value?.let { put("estimate", estimateJson(it)) }
        }

    internal fun estimateJson(estimate: NutritionEstimate): JSONObject = JSONObject().apply {
        put("mealName", estimate.mealName)
        put("portionDescription", estimate.portionDescription ?: JSONObject.NULL)
        put("foodItems", JSONArray(estimate.foodItems.map { item ->
            JSONObject().apply {
                put("name", item.name)
                put("portion", item.portion ?: JSONObject.NULL)
                put("estimatedCalories", item.estimatedCalories ?: JSONObject.NULL)
                put("confidence", item.confidence ?: JSONObject.NULL)
            }
        }))
        put("caloriesKcal", estimate.nutrients.caloriesKcal ?: JSONObject.NULL)
        put("carbohydratesG", estimate.nutrients.carbohydratesG ?: JSONObject.NULL)
        put("proteinG", estimate.nutrients.proteinG ?: JSONObject.NULL)
        put("fatG", estimate.nutrients.fatG ?: JSONObject.NULL)
        put("fibreG", estimate.nutrients.fibreG ?: JSONObject.NULL)
        put("sugarG", estimate.nutrients.sugarG ?: JSONObject.NULL)
        put("sodiumMg", estimate.nutrients.sodiumMg ?: JSONObject.NULL)
        put("confidence", estimate.confidence)
        put("uncertaintyNotes", estimate.uncertaintyNotes ?: JSONObject.NULL)
        put("estimated", true)
    }

    private fun parseFoodItems(
        source: JSONObject,
        errors: MutableList<String>,
        warnings: MutableList<String>
    ): List<NutritionFoodItem> {
        val modern = source.opt("foodItems")
        val legacy = source.opt("foods")
        val array = when {
            modern is JSONArray -> modern
            legacy is JSONArray -> legacy
            modern != null && modern !== JSONObject.NULL -> {
                errors += "wrong_type:foodItems"
                return emptyList()
            }
            legacy != null && legacy !== JSONObject.NULL -> {
                errors += "wrong_type:foods"
                return emptyList()
            }
            else -> {
                warnings += "missing:foodItems"
                return emptyList()
            }
        }
        if (array.length() > MAX_FOOD_ITEMS) {
            errors += "too_many:foodItems"
            return emptyList()
        }
        return buildList {
            for (index in 0 until array.length()) {
                when (val item = array.opt(index)) {
                    is String -> {
                        val name = item.trim()
                        if (name.isBlank() || name.length > MAX_FOOD_TEXT) errors += "invalid:foodItems[$index]"
                        else add(NutritionFoodItem(name))
                    }
                    is JSONObject -> {
                        val itemErrors = mutableListOf<String>()
                        val name = strictString(item, listOf("name"), MAX_FOOD_TEXT, itemErrors)
                        val portion = strictOptionalString(item, listOf("portion"), MAX_FOOD_TEXT, itemErrors)
                        val calories = optionalNumber(item, "estimatedCalories", 20_000.0, itemErrors)
                        val confidence = optionalNumber(item, "confidence", 1.0, itemErrors)
                        if (name == null) itemErrors += "missing:name"
                        if (itemErrors.isNotEmpty()) errors += itemErrors.map { "foodItems[$index]:$it" }
                        else add(NutritionFoodItem(name!!, portion, calories, confidence))
                    }
                    else -> errors += "wrong_type:foodItems[$index]"
                }
            }
        }
    }

    private fun number(
        source: JSONObject,
        aliases: List<String>,
        canonical: String,
        errors: MutableList<String>,
        warnings: MutableList<String>
    ): Double? {
        val key = aliases.firstOrNull { source.has(it) && !source.isNull(it) }
        if (key == null) {
            warnings += "missing:$canonical"
            return null
        }
        return strictNumber(source.opt(key), NUTRIENT_LIMITS.getValue(canonical), canonical, errors)
    }

    private fun optionalNumber(
        source: JSONObject,
        key: String,
        ceiling: Double,
        errors: MutableList<String>
    ): Double? {
        if (!source.has(key) || source.isNull(key)) return null
        return strictNumber(source.opt(key), ceiling, key, errors)
    }

    private fun strictNumber(
        raw: Any?,
        ceiling: Double,
        key: String,
        errors: MutableList<String>
    ): Double? {
        if (raw !is Number) {
            errors += "wrong_type:$key"
            return null
        }
        val value = raw.toDouble()
        if (!value.isFinite()) errors += "not_finite:$key"
        else if (value < 0.0) errors += "negative:$key"
        else if (value > ceiling) errors += "too_large:$key"
        else return value
        return null
    }

    private fun strictString(
        source: JSONObject,
        aliases: List<String>,
        max: Int,
        errors: MutableList<String>
    ): String? {
        val key = aliases.firstOrNull { source.has(it) && !source.isNull(it) } ?: return null
        val raw = source.opt(key)
        if (raw !is String) {
            errors += "wrong_type:$key"
            return null
        }
        val clean = raw.trim()
        if (clean.isBlank()) return null
        if (clean.length > max) {
            errors += "too_long:$key"
            return null
        }
        return clean
    }

    private fun strictOptionalString(
        source: JSONObject,
        aliases: List<String>,
        max: Int,
        errors: MutableList<String>
    ): String? = strictString(source, aliases, max, errors)

    private fun <T> invalid(code: String) =
        NutritionValidationResult<T>(null, errors = listOf(code))
}

data class NutritionMealRecord(
    val id: String,
    val date: LocalDate,
    val createdAt: Instant,
    val updatedAt: Instant,
    val source: String,
    val scanId: String?,
    val mealName: String,
    val foodItems: List<NutritionFoodItem>,
    val nutrients: NutritionNutrients,
    val confidence: Double,
    val notes: String?,
    val estimated: Boolean,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 2
    }
}

data class NutritionStoreResult<T>(
    val value: T?,
    val errorCode: String? = null,
    val preservedInvalidRecords: Int = 0
) {
    val success: Boolean get() = value != null && errorCode == null
}

interface NutritionStringStorage {
    fun read(): String?
    fun write(value: String): Boolean
    fun remove(): Boolean
}

class NutritionMealStore(
    private val storage: NutritionStringStorage,
    private val clock: Clock = Clock.systemUTC()
) {
    fun list(date: LocalDate? = null): NutritionStoreResult<List<NutritionMealRecord>> {
        val array = parseArray() ?: return NutritionStoreResult(null, "malformed_store")
        val valid = mutableListOf<NutritionMealRecord>()
        var invalid = 0
        for (index in 0 until array.length()) {
            val record = array.optJSONObject(index)?.let { NutritionMealCodec.decode(it, clock.instant()) }
            if (record == null) invalid++ else if (date == null || record.date == date) valid += record
        }
        return NutritionStoreResult(valid, preservedInvalidRecords = invalid)
    }

    fun upsert(record: NutritionMealRecord): NutritionStoreResult<NutritionMealRecord> {
        val checked = NutritionMealCodec.validate(record)
        if (checked.isNotEmpty()) return NutritionStoreResult(null, checked.first())
        val array = parseArray() ?: return NutritionStoreResult(null, "malformed_store")
        val updated = JSONArray()
        var invalid = 0
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index)
            if (item == null) {
                invalid++
                updated.put(array.opt(index))
            } else if (item.optString("id") != record.id) {
                if (NutritionMealCodec.decode(item, clock.instant()) == null) invalid++
                updated.put(item)
            }
        }
        updated.put(NutritionMealCodec.encode(record))
        return if (storage.write(updated.toString())) {
            NutritionStoreResult(record, preservedInvalidRecords = invalid)
        } else NutritionStoreResult(null, "write_failed", invalid)
    }

    fun delete(id: String): NutritionStoreResult<Boolean> {
        if (!NutritionIds.valid(id)) return NutritionStoreResult(null, "invalid_id")
        val array = parseArray() ?: return NutritionStoreResult(null, "malformed_store")
        val updated = JSONArray()
        var found = false
        var invalid = 0
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index)
            if (item?.optString("id") == id) found = true
            else {
                if (item == null || NutritionMealCodec.decode(item, clock.instant()) == null) invalid++
                updated.put(array.opt(index))
            }
        }
        if (!found) return NutritionStoreResult(false, preservedInvalidRecords = invalid)
        return if (storage.write(updated.toString())) NutritionStoreResult(true, preservedInvalidRecords = invalid)
        else NutritionStoreResult(null, "write_failed", invalid)
    }

    fun deleteAll(): NutritionStoreResult<Boolean> =
        if (storage.remove()) NutritionStoreResult(true) else NutritionStoreResult(null, "delete_failed")

    fun migrate(): NutritionStoreResult<Int> {
        val array = parseArray() ?: return NutritionStoreResult(null, "malformed_store")
        val output = JSONArray()
        var migrated = 0
        var invalid = 0
        for (index in 0 until array.length()) {
            val original = array.opt(index)
            val objectValue = original as? JSONObject
            val decoded = objectValue?.let { NutritionMealCodec.decode(it, clock.instant()) }
            if (decoded == null) {
                invalid++
                output.put(original)
            } else {
                if (objectValue.optInt("schemaVersion", 1) < NutritionMealRecord.CURRENT_SCHEMA_VERSION) migrated++
                output.put(NutritionMealCodec.encode(decoded))
            }
        }
        return if (migrated == 0 || storage.write(output.toString())) {
            NutritionStoreResult(migrated, preservedInvalidRecords = invalid)
        } else NutritionStoreResult(null, "write_failed", invalid)
    }

    private fun parseArray(): JSONArray? = runCatching {
        JSONArray(storage.read()?.takeIf { it.isNotBlank() } ?: "[]")
    }.getOrNull()
}

object NutritionMealCodec {
    fun decode(source: JSONObject, fallbackNow: Instant): NutritionMealRecord? {
        val id = source.optString("id").takeIf(NutritionIds::valid) ?: return null
        val dateRaw = source.optString("date", source.optString("selectedDate"))
        val date = runCatching { LocalDate.parse(dateRaw) }.getOrNull() ?: return null
        val createdAt = instant(source.optString("createdAt", source.optString("recordedAt"))) ?: fallbackNow
        val updatedAt = instant(source.optString("updatedAt")) ?: createdAt
        val mealName = source.optString("mealName", source.optString("name")).trim()
        if (mealName.isBlank() || mealName.length > NutritionEstimateParser.MAX_MEAL_NAME) return null
        val nutrients = NutritionNutrients(
            caloriesKcal = legacyNumber(source, "caloriesKcal", 20_000.0),
            carbohydratesG = legacyNumber(source, "carbohydratesG", "carbohydratesGrams", 2_000.0),
            proteinG = legacyNumber(source, "proteinG", "proteinGrams", 2_000.0),
            fatG = legacyNumber(source, "fatG", "fatGrams", 2_000.0),
            fibreG = legacyNumber(source, "fibreG", "fiberGrams", 2_000.0),
            sugarG = legacyNumber(source, "sugarG", "sugarGrams", 2_000.0),
            sodiumMg = legacyNumber(source, "sodiumMg", "sodiumMilligrams", 100_000.0)
        )
        if (!nutrients.complete()) return null
        val confidence = source.optDouble("confidence", 0.0)
        if (!confidence.isFinite() || confidence !in 0.0..1.0) return null
        val foodItems = decodeFoodItems(source.optJSONArray("foodItems")) ?: return null
        val scanId = source.stringOrNull("scanId")
        if (scanId != null && !NutritionIds.valid(scanId)) return null
        return NutritionMealRecord(
            id = id,
            date = date,
            createdAt = createdAt,
            updatedAt = updatedAt,
            source = source.optString("source", "Vitalis Scanner").take(80),
            scanId = scanId,
            mealName = mealName,
            foodItems = foodItems,
            nutrients = nutrients,
            confidence = confidence,
            notes = (source.stringOrNull("notes") ?: source.stringOrNull("summary"))
                ?.trim()?.take(2_000)?.ifBlank { null },
            estimated = source.optBoolean("estimated", true),
            schemaVersion = NutritionMealRecord.CURRENT_SCHEMA_VERSION
        )
    }

    fun encode(record: NutritionMealRecord): JSONObject = JSONObject().apply {
        put("id", record.id)
        put("date", record.date.toString())
        put("selectedDate", record.date.toString())
        put("createdAt", record.createdAt.toString())
        put("updatedAt", record.updatedAt.toString())
        put("recordedAt", record.createdAt.toString())
        put("source", record.source)
        put("scanId", record.scanId ?: JSONObject.NULL)
        put("mealName", record.mealName)
        put("name", record.mealName)
        put("foodItems", JSONArray(record.foodItems.map { item ->
            JSONObject().apply {
                put("name", item.name)
                put("portion", item.portion ?: JSONObject.NULL)
                put("estimatedCalories", item.estimatedCalories ?: JSONObject.NULL)
                put("confidence", item.confidence ?: JSONObject.NULL)
            }
        }))
        put("caloriesKcal", record.nutrients.caloriesKcal)
        put("carbohydratesG", record.nutrients.carbohydratesG)
        put("carbohydratesGrams", record.nutrients.carbohydratesG)
        put("proteinG", record.nutrients.proteinG)
        put("proteinGrams", record.nutrients.proteinG)
        put("fatG", record.nutrients.fatG)
        put("fatGrams", record.nutrients.fatG)
        put("fibreG", record.nutrients.fibreG)
        put("fiberGrams", record.nutrients.fibreG)
        put("sugarG", record.nutrients.sugarG)
        put("sugarGrams", record.nutrients.sugarG)
        put("sodiumMg", record.nutrients.sodiumMg)
        put("sodiumMilligrams", record.nutrients.sodiumMg)
        put("confidence", record.confidence)
        put("notes", record.notes ?: JSONObject.NULL)
        put("estimated", record.estimated)
        put("schemaVersion", NutritionMealRecord.CURRENT_SCHEMA_VERSION)
    }

    fun validate(record: NutritionMealRecord): List<String> = buildList {
        if (!NutritionIds.valid(record.id)) add("invalid_id")
        if (record.scanId != null && !NutritionIds.valid(record.scanId)) add("invalid_scan_id")
        if (record.mealName.isBlank() || record.mealName.length > NutritionEstimateParser.MAX_MEAL_NAME) add("invalid_name")
        val values = mapOf(
            "caloriesKcal" to record.nutrients.caloriesKcal,
            "carbohydratesG" to record.nutrients.carbohydratesG,
            "proteinG" to record.nutrients.proteinG,
            "fatG" to record.nutrients.fatG,
            "fibreG" to record.nutrients.fibreG,
            "sugarG" to record.nutrients.sugarG,
            "sodiumMg" to record.nutrients.sodiumMg
        )
        values.forEach { (key, value) ->
            val ceiling = NutritionEstimateParser.NUTRIENT_LIMITS.getValue(key)
            if (value == null || !value.isFinite() || value < 0.0 || value > ceiling) add("invalid:$key")
        }
        if (!record.confidence.isFinite() || record.confidence !in 0.0..1.0) add("invalid:confidence")
        if (record.foodItems.size > NutritionEstimateParser.MAX_FOOD_ITEMS) add("too_many:foodItems")
    }

    private fun instant(raw: String): Instant? = runCatching { Instant.parse(raw) }.getOrNull()

    private fun legacyNumber(source: JSONObject, key: String, ceiling: Double): Double? =
        legacyNumber(source, key, key, ceiling)

    private fun legacyNumber(source: JSONObject, modern: String, legacy: String, ceiling: Double): Double? {
        val key = when {
            source.has(modern) && !source.isNull(modern) -> modern
            source.has(legacy) && !source.isNull(legacy) -> legacy
            else -> return null
        }
        val raw = source.opt(key)
        if (raw !is Number) return null
        return raw.toDouble().takeIf { it.isFinite() && it in 0.0..ceiling }
    }

    private fun decodeFoodItems(array: JSONArray?): List<NutritionFoodItem>? {
        if (array == null) return emptyList()
        if (array.length() > NutritionEstimateParser.MAX_FOOD_ITEMS) return null
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: return null
                val name = item.optString("name").trim()
                if (name.isBlank() || name.length > NutritionEstimateParser.MAX_FOOD_TEXT) return null
                add(
                    NutritionFoodItem(
                        name = name,
                        portion = item.stringOrNull("portion")?.trim()?.ifBlank { null },
                        estimatedCalories = item.opt("estimatedCalories").let {
                            (it as? Number)?.toDouble()?.takeIf { value -> value.isFinite() && value in 0.0..20_000.0 }
                        },
                        confidence = item.opt("confidence").let {
                            (it as? Number)?.toDouble()?.takeIf { value -> value.isFinite() && value in 0.0..1.0 }
                        }
                    )
                )
            }
        }
    }

    private fun JSONObject.stringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null
}

object NutritionMealInput {
    fun parse(raw: String, clock: Clock): NutritionValidationResult<NutritionMealRecord> {
        if (raw.isBlank() || raw.length > 32_000) return NutritionValidationResult(null, listOf("invalid_payload"))
        val source = runCatching { JSONObject(raw) }.getOrNull()
            ?: return NutritionValidationResult(null, listOf("malformed_json"))
        val record = NutritionMealCodec.decode(source, clock.instant())
            ?: return NutritionValidationResult(null, listOf("invalid_meal"))
        val errors = NutritionMealCodec.validate(record)
        return NutritionValidationResult(record.takeIf { errors.isEmpty() }, errors)
    }
}

object NutritionExportCodec {
    const val FORMAT_VERSION = 1

    val nutrientUnits = linkedMapOf(
        "caloriesKcal" to "kcal",
        "carbohydratesG" to "g",
        "proteinG" to "g",
        "fatG" to "g",
        "fibreG" to "g",
        "sugarG" to "g",
        "sodiumMg" to "mg"
    )

    fun encode(meals: List<NutritionMealRecord>, generatedAt: Instant, applicationVersion: String): String =
        JSONObject().apply {
            put("format", "vitalis-local-nutrition")
            put("formatVersion", FORMAT_VERSION)
            put("generatedAt", generatedAt.toString())
            put("applicationVersion", applicationVersion)
            put("nutrientUnits", JSONObject(nutrientUnits as Map<*, *>))
            put("meals", JSONArray(meals.map(NutritionMealCodec::encode)))
        }.toString(2)
}
