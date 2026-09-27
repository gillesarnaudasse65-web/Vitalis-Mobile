package com.vitalis.healthos

import android.content.Context
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

data class VitalisDataOperationResult(
    val success: Boolean,
    val error: String? = null,
    val summary: String? = null
)

class VitalisLocalDataStore(private val context: Context) {
    private val preferences = context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)

    fun snapshot(): VitalisLocalSnapshot = VitalisLocalSnapshot(
        preferences = preferences.all,
        nutrition = safeArray(preferences.getString(MANUAL_MEALS_KEY, null)),
        selectedCoach = preferences.getString(SELECTED_COACH_KEY, null),
        dashboardSettings = safeObject(preferences.getString(DASHBOARD_SETTINGS_KEY, null)),
        localJournal = safeArray(preferences.getString(LOCAL_JOURNAL_KEY, null)),
        consentState = preferences.getBoolean(AI_HEALTH_CONSENT, false)
    )

    fun export(appVersion: String, generatedAt: Instant = Instant.now()): String =
        VitalisExportCodec.encode(snapshot(), appVersion, generatedAt)

    fun validateImport(bytes: ByteArray): VitalisImportValidation = VitalisExportCodec.validate(bytes)

    fun import(root: JSONObject, mode: VitalisImportMode): VitalisDataOperationResult {
        val validation = VitalisExportCodec.validate(root.toString().toByteArray())
        if (!validation.valid || validation.payload == null) {
            return VitalisDataOperationResult(false, validation.error)
        }
        val data = VitalisExportCodec.sanitizedData(validation.payload)
        val importedMeals = normalizeMeals(data.getJSONArray("nutrition"))
            ?: return VitalisDataOperationResult(false, "invalid_nutrition_record")
        val importedJournal = data.getJSONArray("localJournal")
        if (importedJournal.length() > MAX_JOURNAL_ENTRIES) {
            return VitalisDataOperationResult(false, "journal_too_large")
        }
        val selectedCoach = data.optString("selectedCoach").takeIf { it in COACH_IDS }
        val dashboard = data.optJSONObject("dashboardSettings")
        if (dashboard != null && dashboard.toString().length > MAX_DASHBOARD_CHARS) {
            return VitalisDataOperationResult(false, "dashboard_too_large")
        }

        val meals = if (mode == VitalisImportMode.MERGE) {
            mergeById(safeArray(preferences.getString(MANUAL_MEALS_KEY, null)), importedMeals)
        } else importedMeals
        val journal = if (mode == VitalisImportMode.MERGE) {
            mergeJournal(safeArray(preferences.getString(LOCAL_JOURNAL_KEY, null)), importedJournal)
        } else JSONArray(importedJournal.toString())

        val importGeneration = preferences.getLong(LOCAL_IMPORT_GENERATION_KEY, 0L) + 1L
        val deleteGeneration = preferences.getLong(LOCAL_DELETE_GENERATION_KEY, 0L)
        val editor = preferences.edit()
        if (mode == VitalisImportMode.REPLACE) editor.clear()
        val importedPreferences = data.getJSONObject("preferences")
        importedPreferences.keys().forEach { key ->
            when (val value = importedPreferences.opt(key)) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
            }
        }
        editor.putString(MANUAL_MEALS_KEY, meals.toString())
        editor.putString(LOCAL_JOURNAL_KEY, journal.toString())
        if (selectedCoach != null) editor.putString(SELECTED_COACH_KEY, selectedCoach)
        if (dashboard != null) editor.putString(DASHBOARD_SETTINGS_KEY, dashboard.toString())
        editor.putBoolean(AI_HEALTH_CONSENT, data.optBoolean("consentState", false))
        editor.putInt(LOCAL_SCHEMA_VERSION_KEY, CURRENT_LOCAL_SCHEMA_VERSION)
        editor.putLong(LOCAL_IMPORT_GENERATION_KEY, importGeneration)
        editor.putLong(LOCAL_DELETE_GENERATION_KEY, deleteGeneration)
        return if (editor.commit()) {
            VitalisDataOperationResult(true, summary = validation.summary)
        } else VitalisDataOperationResult(false, "write_failed")
    }

    fun syncWebLocalData(raw: String): Boolean {
        if (raw.toByteArray().size > MAX_WEB_STATE_BYTES) return false
        val data = runCatching { JSONObject(raw) }.getOrNull() ?: return false
        val coach = data.optString("selectedCoach").takeIf { it in COACH_IDS }
        val dashboard = data.optJSONObject("dashboardSettings")
        val journal = data.optJSONArray("localJournal")
        if (dashboard != null && dashboard.toString().length > MAX_DASHBOARD_CHARS) return false
        if (journal != null && journal.length() > MAX_JOURNAL_ENTRIES) return false
        return preferences.edit().apply {
            if (coach != null) putString(SELECTED_COACH_KEY, coach)
            if (dashboard != null) putString(DASHBOARD_SETTINGS_KEY, dashboard.toString())
            if (journal != null) putString(LOCAL_JOURNAL_KEY, journal.toString())
            putInt(LOCAL_SCHEMA_VERSION_KEY, CURRENT_LOCAL_SCHEMA_VERSION)
        }.commit()
    }

    fun deleteVitalisLocalData(): VitalisDataOperationResult {
        val generation = preferences.getLong(LOCAL_DELETE_GENERATION_KEY, 0L) + 1L
        val cleared = preferences.edit().clear()
            .putLong(LOCAL_DELETE_GENERATION_KEY, generation)
            .putInt(LOCAL_SCHEMA_VERSION_KEY, CURRENT_LOCAL_SCHEMA_VERSION)
            .commit()
        val captures = java.io.File(context.cacheDir, "nutrition-captures")
        if (captures.exists()) captures.listFiles()?.forEach { file ->
            if (file.isFile && file.name.matches(Regex("capture-[A-Za-z0-9-]+\\.jpg"))) file.delete()
        }
        return if (cleared) VitalisDataOperationResult(
            true,
            summary = "Vitalis local data deleted. Health Connect permissions are managed separately."
        ) else VitalisDataOperationResult(false, "delete_failed")
    }

    fun localDeleteGeneration(): Long = preferences.getLong(LOCAL_DELETE_GENERATION_KEY, 0L)

    fun localImportGeneration(): Long = preferences.getLong(LOCAL_IMPORT_GENERATION_KEY, 0L)

    fun appliedImportGeneration(): Long =
        preferences.getLong(LOCAL_IMPORT_APPLIED_GENERATION_KEY, 0L)

    fun markImportApplied(generation: Long): Boolean = preferences.edit()
        .putLong(LOCAL_IMPORT_APPLIED_GENERATION_KEY, generation)
        .commit()

    private fun normalizeMeals(source: JSONArray): JSONArray? {
        val output = JSONArray()
        for (index in 0 until source.length()) {
            val record = source.optJSONObject(index) ?: return null
            val decoded = NutritionMealCodec.decode(record, Instant.now()) ?: return null
            output.put(NutritionMealCodec.encode(decoded))
        }
        return output
    }

    private fun mergeById(existing: JSONArray, imported: JSONArray): JSONArray {
        val byId = linkedMapOf<String, JSONObject>()
        for (source in listOf(existing, imported)) for (index in 0 until source.length()) {
            val item = source.optJSONObject(index) ?: continue
            val decoded = NutritionMealCodec.decode(item, Instant.now()) ?: continue
            byId[decoded.id] = NutritionMealCodec.encode(decoded)
        }
        return JSONArray(byId.values)
    }

    private fun mergeJournal(existing: JSONArray, imported: JSONArray): JSONArray {
        val items = linkedSetOf<String>()
        for (source in listOf(existing, imported)) for (index in 0 until source.length()) {
            source.optJSONObject(index)?.toString()?.take(MAX_JOURNAL_ITEM_CHARS)?.let(items::add)
        }
        return JSONArray(items.takeLast(MAX_JOURNAL_ENTRIES).map(::JSONObject))
    }

    private fun safeArray(raw: String?): JSONArray =
        runCatching { JSONArray(raw?.takeIf(String::isNotBlank) ?: "[]") }.getOrDefault(JSONArray())

    private fun safeObject(raw: String?): JSONObject? =
        raw?.let { runCatching { JSONObject(it) }.getOrNull() }

    companion object {
        const val APP_PREFS = "vitalis_preferences"
        const val MANUAL_MEALS_KEY = "manual_meal_estimates"
        const val AI_HEALTH_CONSENT = "ai_health_consent"
        const val SELECTED_COACH_KEY = "selected_coach_id_v1"
        const val DASHBOARD_SETTINGS_KEY = "dashboard_settings_v1"
        const val LOCAL_JOURNAL_KEY = "local_journal_v1"
        const val LOCAL_SCHEMA_VERSION_KEY = "local_schema_version"
        const val LOCAL_DELETE_GENERATION_KEY = "local_delete_generation"
        const val LOCAL_IMPORT_GENERATION_KEY = "local_import_generation"
        const val LOCAL_IMPORT_APPLIED_GENERATION_KEY = "local_import_applied_generation"
        const val CURRENT_LOCAL_SCHEMA_VERSION = 1
        private const val MAX_WEB_STATE_BYTES = 512 * 1024
        private const val MAX_DASHBOARD_CHARS = 64 * 1024
        private const val MAX_JOURNAL_ENTRIES = 500
        private const val MAX_JOURNAL_ITEM_CHARS = 8_000
        private val COACH_IDS = setOf("general", "nutrition", "activity", "sleep", "recovery", "mental")
    }
}
