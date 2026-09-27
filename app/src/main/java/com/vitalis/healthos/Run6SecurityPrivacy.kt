package com.vitalis.healthos

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONArray
import org.json.JSONObject

enum class BridgeCapability {
    HEALTH,
    AI,
    NUTRITION,
    VOICE,
    NAVIGATION,
    PRIVACY,
    READ_ONLY
}

data class BridgeMethod(
    val name: String,
    val capability: BridgeCapability,
    val requiresExplicitUserIntent: Boolean = false
)

object OriginBridgePolicy {
    const val VITALIS_ORIGIN = "https://vitalis-health-os.gillesarnaudasse65.chatgpt.site"
    const val APPASSETS_ORIGIN = "https://appassets.androidplatform.net"

    val allowedOrigins = setOf(VITALIS_ORIGIN, APPASSETS_ORIGIN)

    private val methods = listOf(
        BridgeMethod("requestHealthConnectPermissions", BridgeCapability.HEALTH, true),
        BridgeMethod("refreshHealthData", BridgeCapability.HEALTH),
        BridgeMethod("refreshHealthDataForDate", BridgeCapability.HEALTH),
        BridgeMethod("selectHealthDate", BridgeCapability.HEALTH),
        BridgeMethod("authorizeConnector", BridgeCapability.HEALTH, true),
        BridgeMethod("askKofi", BridgeCapability.AI, true),
        BridgeMethod("askCoach", BridgeCapability.AI, true),
        BridgeMethod("askDeveloper", BridgeCapability.AI, true),
        BridgeMethod("analyzeMealImage", BridgeCapability.NUTRITION, true),
        BridgeMethod("saveMealEstimate", BridgeCapability.NUTRITION, true),
        BridgeMethod("beginNutritionScan", BridgeCapability.NUTRITION, true),
        BridgeMethod("cancelNutritionScan", BridgeCapability.NUTRITION),
        BridgeMethod("analyzeMealSession", BridgeCapability.NUTRITION, true),
        BridgeMethod("saveNutritionMeal", BridgeCapability.NUTRITION, true),
        BridgeMethod("updateLocalNutritionMeal", BridgeCapability.NUTRITION, true),
        BridgeMethod("deleteLocalNutritionMeal", BridgeCapability.NUTRITION, true),
        BridgeMethod("exportLocalNutrition", BridgeCapability.NUTRITION, true),
        BridgeMethod("deleteAllLocalNutritionData", BridgeCapability.NUTRITION, true),
        BridgeMethod("speakText", BridgeCapability.VOICE, true),
        BridgeMethod("stopSpeaking", BridgeCapability.VOICE),
        BridgeMethod("setMicrophoneEnabled", BridgeCapability.VOICE, true),
        BridgeMethod("startVoiceInput", BridgeCapability.VOICE, true),
        BridgeMethod("stopVoiceInput", BridgeCapability.VOICE),
        BridgeMethod("openOfflineMode", BridgeCapability.NAVIGATION, true),
        BridgeMethod("openClassicInterface", BridgeCapability.NAVIGATION, true),
        BridgeMethod("openHealthConnectSettings", BridgeCapability.NAVIGATION, true),
        BridgeMethod("openExternalUrl", BridgeCapability.NAVIGATION, true),
        BridgeMethod("sendDeveloperRequestToChatGpt", BridgeCapability.NAVIGATION, true),
        BridgeMethod("openPrivacyDataSettings", BridgeCapability.PRIVACY, true),
        BridgeMethod("openKeySettings", BridgeCapability.PRIVACY, true),
        BridgeMethod("setAiHealthConsent", BridgeCapability.PRIVACY, true),
        BridgeMethod("syncWebLocalData", BridgeCapability.PRIVACY)
    ).associateBy(BridgeMethod::name)

    fun isAllowedOrigin(origin: String?): Boolean = origin in allowedOrigins

    fun canInvoke(origin: String?, isMainFrame: Boolean, method: String): Boolean =
        isMainFrame && isAllowedOrigin(origin) && methods.containsKey(method)

    fun method(name: String): BridgeMethod? = methods[name]

    fun allowedOriginRules(): Set<String> = allowedOrigins
}

data class AiConsentToken(val epoch: Long)

class AiConsentCoordinator(initiallyConsented: Boolean) {
    private val epoch = AtomicLong(0)
    @Volatile private var consented = initiallyConsented

    fun grant() {
        consented = true
        epoch.incrementAndGet()
    }

    fun revoke() {
        consented = false
        epoch.incrementAndGet()
    }

    fun isConsented(): Boolean = consented

    fun begin(): AiConsentToken? = if (consented) AiConsentToken(epoch.get()) else null

    fun accepts(token: AiConsentToken?): Boolean =
        token != null && consented && token.epoch == epoch.get()
}

data class VitalisLocalSnapshot(
    val preferences: Map<String, Any?>,
    val nutrition: JSONArray,
    val selectedCoach: String?,
    val dashboardSettings: JSONObject?,
    val localJournal: JSONArray,
    val consentState: Boolean
)

data class VitalisImportValidation(
    val valid: Boolean,
    val error: String? = null,
    val payload: JSONObject? = null,
    val summary: String? = null
)

enum class VitalisImportMode { MERGE, REPLACE }

object VitalisExportCodec {
    const val FORMAT = "vitalis-export"
    const val VERSION = 1
    const val MAX_IMPORT_BYTES = 1_048_576
    private val allowedPreferenceKeys = setOf(
        "selected_health_date_iso",
        "health_connect_permission_requested_v1",
        "health_connect_ever_authorized_v1"
    )
    private val forbiddenFragments = listOf(
        "api_key", "apikey", "authorization", "bearer", "password",
        "keystore", "secret", "access_token", "refresh_token", "private_key"
    )
    private val coachIds = setOf("general", "nutrition", "activity", "sleep", "recovery", "mental")
    private const val MAX_JOURNAL_ENTRIES = 500
    private const val MAX_JOURNAL_ITEM_CHARS = 8_000
    private const val MAX_DASHBOARD_CHARS = 64 * 1024

    fun encode(snapshot: VitalisLocalSnapshot, appVersion: String, generatedAt: Instant): String {
        val preferences = JSONObject()
        snapshot.preferences.forEach { (key, value) ->
            if (key in allowedPreferenceKeys && value != null) preferences.put(key, value)
        }
        val data = JSONObject().apply {
            put("preferences", preferences)
            put("nutrition", JSONArray(snapshot.nutrition.toString()))
            put("selectedCoach", snapshot.selectedCoach ?: JSONObject.NULL)
            put("dashboardSettings", snapshot.dashboardSettings ?: JSONObject.NULL)
            put("localJournal", JSONArray(snapshot.localJournal.toString()))
            put("consentState", snapshot.consentState)
        }
        return JSONObject().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("generatedAt", generatedAt.toString())
            put("appVersion", appVersion)
            put("data", data)
        }.toString(2)
    }

    fun validate(bytes: ByteArray): VitalisImportValidation {
        if (bytes.size > MAX_IMPORT_BYTES) return VitalisImportValidation(false, "oversized_import")
        val raw = bytes.toString(StandardCharsets.UTF_8)
        val root = runCatching { JSONObject(raw) }.getOrNull()
            ?: return VitalisImportValidation(false, "malformed_json")
        if (root.optString("format") != FORMAT) return VitalisImportValidation(false, "unsupported_format")
        val version = root.optInt("version", -1)
        if (version > VERSION) return VitalisImportValidation(false, "future_version")
        if (version < 1) return VitalisImportValidation(false, "unsupported_version")
        val data = root.optJSONObject("data")
            ?: return VitalisImportValidation(false, "missing_data")
        if (containsForbiddenKey(root)) return VitalisImportValidation(false, "forbidden_secret_field")
        val preferences = data.optJSONObject("preferences")
            ?: return VitalisImportValidation(false, "invalid_preferences")
        if (preferences.keys().asSequence().any { it !in allowedPreferenceKeys }) {
            return VitalisImportValidation(false, "unsupported_preference")
        }
        preferences.opt("selected_health_date_iso")?.takeUnless { it == JSONObject.NULL }?.let {
            if (it !is String || BridgeInputPolicy.date(it) == null) {
                return VitalisImportValidation(false, "invalid_selected_date")
            }
        }
        listOf("health_connect_permission_requested_v1", "health_connect_ever_authorized_v1")
            .forEach { key ->
                if (preferences.has(key) && preferences.opt(key) !is Boolean) {
                    return VitalisImportValidation(false, "invalid_preference_type")
                }
            }
        if (data.opt("nutrition") !is JSONArray) return VitalisImportValidation(false, "invalid_nutrition")
        if (data.opt("localJournal") !is JSONArray) return VitalisImportValidation(false, "invalid_journal")
        val journal = data.getJSONArray("localJournal")
        if (journal.length() > MAX_JOURNAL_ENTRIES || (0 until journal.length()).any {
                val entry = journal.optJSONObject(it)
                entry == null || entry.toString().length > MAX_JOURNAL_ITEM_CHARS
            }) return VitalisImportValidation(false, "invalid_journal")
        val selectedCoach = data.opt("selectedCoach")
        if (selectedCoach != null && selectedCoach != JSONObject.NULL && selectedCoach !is String) {
            return VitalisImportValidation(false, "invalid_selected_coach")
        }
        if (selectedCoach is String && selectedCoach !in coachIds) {
            return VitalisImportValidation(false, "invalid_selected_coach")
        }
        val dashboard = data.opt("dashboardSettings")
        if (dashboard != null && dashboard != JSONObject.NULL && dashboard !is JSONObject) {
            return VitalisImportValidation(false, "invalid_dashboard_settings")
        }
        if (dashboard is JSONObject && dashboard.toString().length > MAX_DASHBOARD_CHARS) {
            return VitalisImportValidation(false, "invalid_dashboard_settings")
        }
        val summary = "${data.optJSONArray("nutrition")?.length() ?: 0} repas, " +
            "${data.optJSONArray("localJournal")?.length() ?: 0} entrées de journal"
        return VitalisImportValidation(true, payload = root, summary = summary)
    }

    fun sanitizedData(root: JSONObject): JSONObject {
        val data = root.getJSONObject("data")
        return JSONObject().apply {
            put("preferences", JSONObject(data.getJSONObject("preferences").toString()))
            put("nutrition", JSONArray(data.getJSONArray("nutrition").toString()))
            put("selectedCoach", data.opt("selectedCoach") ?: JSONObject.NULL)
            put("dashboardSettings", data.opt("dashboardSettings") ?: JSONObject.NULL)
            put("localJournal", JSONArray(data.getJSONArray("localJournal").toString()))
            put("consentState", data.optBoolean("consentState", false))
        }
    }

    private fun containsForbiddenKey(value: Any?): Boolean = when (value) {
        is JSONObject -> value.keys().asSequence().any { key ->
            forbiddenFragments.any { key.lowercase().contains(it) } || containsForbiddenKey(value.opt(key))
        }
        is JSONArray -> (0 until value.length()).any { containsForbiddenKey(value.opt(it)) }
        else -> false
    }
}

object ReleaseSecurityPolicy {
    fun webViewDebuggingEnabled(debugBuild: Boolean): Boolean = debugBuild

    fun signingMaterialConfigured(values: Map<String, String?>): Boolean =
        listOf(
            "VITALIS_KEYSTORE_PATH",
            "VITALIS_KEYSTORE_PASSWORD",
            "VITALIS_KEY_ALIAS",
            "VITALIS_KEY_PASSWORD"
        ).all { !values[it].isNullOrBlank() }
}
