package com.vitalis.healthos

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Run6DataControlInstrumentationTest {
    @Test fun nativeImportDeleteAndCredentialSeparationAreEnforced() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences(
            VitalisLocalDataStore.APP_PREFS,
            Context.MODE_PRIVATE
        )
        preferences.edit().clear().commit()
        val secrets = SecureSecretStore(context)
        secrets.delete(AiKeyKind.HEALTH)
        val fixtureKey = "sk-" + "native-data-control".repeat(3)
        assertTrue(secrets.save(AiKeyKind.HEALTH, fixtureKey))

        val store = VitalisLocalDataStore(context)
        val export = VitalisExportCodec.encode(
            VitalisLocalSnapshot(
                preferences = mapOf("selected_health_date_iso" to "2026-09-18"),
                nutrition = JSONArray(),
                selectedCoach = "sleep",
                dashboardSettings = JSONObject().put("period", "day"),
                localJournal = JSONArray().put(JSONObject().put("id", "journal-import-1")),
                consentState = true
            ),
            BuildConfig.VERSION_NAME,
            Instant.parse("2026-09-27T00:00:00Z")
        )
        val validation = store.validateImport(export.toByteArray())
        assertTrue(validation.error.orEmpty(), validation.valid)
        assertTrue(store.import(requireNotNull(validation.payload), VitalisImportMode.REPLACE).success)
        assertEquals("sleep", store.snapshot().selectedCoach)
        assertTrue(store.snapshot().consentState)
        assertEquals(1, store.snapshot().localJournal.length())
        assertTrue(store.localImportGeneration() > store.appliedImportGeneration())

        assertTrue(store.deleteVitalisLocalData().success)
        assertEquals(0, store.snapshot().nutrition.length())
        assertNull(store.snapshot().selectedCoach)
        assertFalse(store.snapshot().consentState)
        assertTrue(secrets.status(AiKeyKind.HEALTH).configured)
        assertEquals(fixtureKey, secrets.readForRequest(AiKeyKind.HEALTH))
        assertTrue(secrets.delete(AiKeyKind.HEALTH))
        assertFalse(secrets.status(AiKeyKind.HEALTH).configured)
    }
}
