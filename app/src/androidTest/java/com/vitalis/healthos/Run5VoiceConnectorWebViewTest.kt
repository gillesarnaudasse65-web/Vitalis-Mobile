package com.vitalis.healthos

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Run5VoiceConnectorWebViewTest {
    private val instrument = InstrumentationRegistry.getInstrumentation()

    @Test fun deterministicVoiceLifecycleAndTruthfulConnectorStatesReachProductionScripts() {
        val context = instrument.targetContext
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_RUN5_FIXTURE, true)
            putExtra(MainActivity.EXTRA_TEST_TODAY_ISO, "2026-09-27")
        }
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            await(scenario, 15_000) {
                eval(scenario, "window.__run5Fixture===true&&!!window.VitalisAI&&!!window.VitalisConnectorControls") == "true"
            }

            eval(scenario, "document.querySelector('#runFixture').click();true")
            val result = JSONObject(decoded(eval(scenario, "document.querySelector('#fixtureResult').textContent")))
            assertTrue(result.getBoolean("partialAccepted"))
            assertTrue(result.getBoolean("firstFinalAccepted"))
            assertTrue(result.getBoolean("duplicateFinalRejected"))
            assertTrue(result.getBoolean("staleFinalRejected"))
            assertTrue(result.getBoolean("cancelledFinalRejected"))
            assertEquals("RETRY", result.getString("firstRetry"))
            assertEquals("END_SESSION", result.getString("secondRetry"))
            assertTrue(result.getBoolean("oldTtsCallbackRejected"))
            assertTrue(result.getBoolean("ttsStopped"))
            assertEquals("HEALTH_CONNECT_PERMISSION_REQUIRED", result.getString("permissionRequired"))
            assertEquals("HEALTH_CONNECT_AVAILABLE_NO_DATA", result.getString("healthNoData"))
            assertEquals("HEALTH_CONNECT_DATA_AVAILABLE", result.getString("providerData"))
            assertEquals("SETUP_REQUIRED", result.getString("setupOnly"))
            assertEquals("API_UNAVAILABLE", result.getString("directUnavailable"))
            assertEquals("UNSUPPORTED", result.getString("appleHealth"))

            eval(scenario, "window.VitalisAI.open();true")
            await(scenario) { eval(scenario, "!!document.querySelector('[data-agent-input]')") == "true" }
            eval(scenario, "window.dispatchEvent(new CustomEvent('vitalis-voice-input',{detail:{kind:'PARTIAL',partial:true,text:'partiel',sessionId:'fixture'}}));true")
            assertEquals("\"\"", eval(scenario, "document.querySelector('[data-agent-input]').value"))
            eval(scenario, "window.dispatchEvent(new CustomEvent('vitalis-voice-input',{detail:{kind:'FINAL',partial:false,text:'message final',sessionId:'fixture'}}));true")
            assertEquals("\"message final\"", eval(scenario, "document.querySelector('[data-agent-input]').value"))

            val catalog = JSONObject(decoded(eval(scenario, "VitalisAndroid.getConnectorStatus()")))
                .getJSONArray("connectors")
            val apple = (0 until catalog.length()).map { catalog.getJSONObject(it) }
                .first { it.getString("id") == "apple_health" }
            val strava = (0 until catalog.length()).map { catalog.getJSONObject(it) }
                .first { it.getString("id") == "strava" }
            assertEquals("UNSUPPORTED", apple.getString("runtimeState"))
            assertFalse(strava.getBoolean("directIntegrationImplemented"))
            assertFalse(decoded(eval(scenario, "String(VitalisAndroid.isMicrophoneEnabled())")).toBoolean())

            scenario.recreate()
            await(scenario, 15_000) { eval(scenario, "window.__run5Fixture===true") == "true" }
            assertEquals("false", eval(scenario, "String(VitalisAndroid.isMicrophoneEnabled())"))
        }
    }

    private fun decoded(encoded: String): String = JSONArray("[$encoded]").getString(0)

    private fun await(
        scenario: ActivityScenario<MainActivity>,
        timeout: Long = 7_000,
        condition: () -> Boolean
    ) {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            if (condition()) return
            SystemClock.sleep(60)
        }
        fail("WebView condition timed out; URL=" + eval(scenario, "location.href"))
    }

    private fun eval(scenario: ActivityScenario<MainActivity>, js: String): String {
        val latch = CountDownLatch(1)
        val result = AtomicReference<String>()
        scenario.onActivity { activity ->
            requireNotNull(findWebView(activity.findViewById(android.R.id.content)))
                .evaluateJavascript(js) {
                    result.set(it)
                    latch.countDown()
                }
        }
        assertTrue("JavaScript callback timed out", latch.await(5, TimeUnit.SECONDS))
        return result.get() ?: "null"
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }
}
