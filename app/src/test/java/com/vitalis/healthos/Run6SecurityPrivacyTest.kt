package com.vitalis.healthos

import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class Run6SecurityPrivacyTest {
    @Test fun exactTrustedOriginsAreAllowedForMainFrameOnly() {
        assertTrue(OriginBridgePolicy.canInvoke(OriginBridgePolicy.VITALIS_ORIGIN, true, "askCoach"))
        assertTrue(OriginBridgePolicy.canInvoke(OriginBridgePolicy.APPASSETS_ORIGIN, true, "refreshHealthData"))
        assertFalse(OriginBridgePolicy.canInvoke(OriginBridgePolicy.VITALIS_ORIGIN, false, "askCoach"))
    }

    @Test fun deceptiveAndUntrustedOriginsAreRejected() {
        listOf(
            "https://evil.example",
            "https://vitalis-health-os.gillesarnaudasse65.chatgpt.site.evil.example",
            "http://vitalis-health-os.gillesarnaudasse65.chatgpt.site",
            "http://localhost",
            "https://localhost",
            "data:text/html,hello",
            "javascript:alert(1)",
            "file:///tmp/a.html",
            "blob:https://vitalis-health-os.gillesarnaudasse65.chatgpt.site/id",
            "",
            "not an origin"
        ).forEach { origin ->
            assertFalse(origin, OriginBridgePolicy.canInvoke(origin, true, "askCoach"))
        }
    }

    @Test fun unknownAndLegacyPlaintextKeyMethodsAreNotRoutable() {
        listOf("saveOpenAiKey", "saveDeveloperAiKey", "readOpenAiKey", "getPlaintextKey")
            .forEach { method ->
                assertFalse(OriginBridgePolicy.canInvoke(OriginBridgePolicy.VITALIS_ORIGIN, true, method))
            }
    }

    @Test fun privilegedCapabilitiesRequireTrustedMainFrame() {
        listOf(
            "requestHealthConnectPermissions", "askCoach", "beginNutritionScan",
            "startVoiceInput", "authorizeConnector", "openPrivacyDataSettings",
            "deleteAllLocalNutritionData", "syncWebLocalData"
        ).forEach { method ->
            assertTrue(OriginBridgePolicy.canInvoke(OriginBridgePolicy.APPASSETS_ORIGIN, true, method))
            assertFalse(OriginBridgePolicy.canInvoke("https://untrusted.example", true, method))
            assertFalse(OriginBridgePolicy.canInvoke(OriginBridgePolicy.APPASSETS_ORIGIN, false, method))
        }
    }

    @Test fun consentRevocationRejectsLateResponses() {
        val coordinator = AiConsentCoordinator(true)
        val request = coordinator.begin()
        assertTrue(coordinator.accepts(request))
        coordinator.revoke()
        assertFalse(coordinator.accepts(request))
        assertNull(coordinator.begin())
        coordinator.grant()
        val newer = coordinator.begin()
        assertNotNull(newer)
        assertFalse(coordinator.accepts(request))
        assertTrue(coordinator.accepts(newer))
    }

    @Test fun exportIsVersionedAndExcludesSecrets() {
        val raw = VitalisExportCodec.encode(snapshot(), "3.15.0-security-release", Instant.EPOCH)
        val root = JSONObject(raw)
        assertEquals("vitalis-export", root.getString("format"))
        assertEquals(1, root.getInt("version"))
        assertEquals("2026-09-18", root.getJSONObject("data")
            .getJSONObject("preferences").getString("selected_health_date_iso"))
        listOf("sk-test", "api_key", "Authorization", "Keystore", "refresh_token")
            .forEach { assertFalse(it, raw.contains(it, ignoreCase = true)) }
    }

    @Test fun validExportCanBeImportedWithPreview() {
        val bytes = VitalisExportCodec.encode(snapshot(), "3.15.0-security-release", Instant.EPOCH)
            .toByteArray()
        val result = VitalisExportCodec.validate(bytes)
        assertTrue(result.error.orEmpty(), result.valid)
        assertEquals("1 repas, 1 entrées de journal", result.summary)
        assertNotNull(result.payload)
    }

    @Test fun malformedOversizedFutureAndSecretBearingImportsAreRejected() {
        assertEquals("malformed_json", VitalisExportCodec.validate("{".toByteArray()).error)
        assertEquals(
            "oversized_import",
            VitalisExportCodec.validate(ByteArray(VitalisExportCodec.MAX_IMPORT_BYTES + 1)).error
        )
        val future = validRoot().put("version", 2).toString().toByteArray()
        assertEquals("future_version", VitalisExportCodec.validate(future).error)
        val secret = validRoot().also {
            it.getJSONObject("data").put("api_key", "sk-should-never-import")
        }.toString().toByteArray()
        assertEquals("forbidden_secret_field", VitalisExportCodec.validate(secret).error)
    }

    @Test fun schemaShapeAndUnknownPreferencesAreRejected() {
        val wrongNutrition = validRoot().also {
            it.getJSONObject("data").put("nutrition", JSONObject())
        }.toString().toByteArray()
        assertEquals("invalid_nutrition", VitalisExportCodec.validate(wrongNutrition).error)
        val unknownPreference = validRoot().also {
            it.getJSONObject("data").getJSONObject("preferences").put("unknown", true)
        }.toString().toByteArray()
        assertEquals("unsupported_preference", VitalisExportCodec.validate(unknownPreference).error)
        val unknownCoach = validRoot().also {
            it.getJSONObject("data").put("selectedCoach", "not-a-coach")
        }.toString().toByteArray()
        assertEquals("invalid_selected_coach", VitalisExportCodec.validate(unknownCoach).error)
        val malformedJournal = validRoot().also {
            it.getJSONObject("data").put("localJournal", JSONArray().put("not-an-object"))
        }.toString().toByteArray()
        assertEquals("invalid_journal", VitalisExportCodec.validate(malformedJournal).error)
    }

    @Test fun releasePoliciesRequireAllSigningInputsAndDisableProductionDebugging() {
        assertTrue(ReleaseSecurityPolicy.webViewDebuggingEnabled(true))
        assertFalse(ReleaseSecurityPolicy.webViewDebuggingEnabled(false))
        assertFalse(ReleaseSecurityPolicy.signingMaterialConfigured(emptyMap()))
        assertTrue(ReleaseSecurityPolicy.signingMaterialConfigured(mapOf(
            "VITALIS_KEYSTORE_PATH" to "/tmp/release.jks",
            "VITALIS_KEYSTORE_PASSWORD" to "configured-in-ci",
            "VITALIS_KEY_ALIAS" to "vitalis",
            "VITALIS_KEY_PASSWORD" to "configured-in-ci"
        )))
    }

    private fun snapshot() = VitalisLocalSnapshot(
        preferences = mapOf(
            "selected_health_date_iso" to "2026-09-18",
            "ignored" to "sk-test-should-not-export"
        ),
        nutrition = JSONArray().put(JSONObject().put("id", "meal-1")),
        selectedCoach = "sleep",
        dashboardSettings = JSONObject().put("period", "day"),
        localJournal = JSONArray().put(JSONObject().put("type", "activity")),
        consentState = true
    )

    private fun validRoot() = JSONObject(VitalisExportCodec.encode(
        snapshot(),
        "3.15.0-security-release",
        Instant.EPOCH
    ))
}
