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
class Run6SecurityPrivacyWebViewTest {
    private val instrument = InstrumentationRegistry.getInstrumentation()

    @Test fun originGateSecretsConsentAndPortableDataAreEnforcedEndToEnd() {
        val intent = Intent(instrument.targetContext, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_RUN6_FIXTURE, true)
            putExtra(MainActivity.EXTRA_TEST_TODAY_ISO, "2026-09-27")
        }
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            await(scenario, 15_000) {
                eval(scenario, "window.__run6Fixture===true&&!!window.VitalisAndroid") == "true"
            }
            assertEquals("undefined", decoded(eval(
                scenario,
                "typeof VitalisAndroid.saveOpenAiKey+','+typeof VitalisAndroid.clearOpenAiKey+','+typeof VitalisAndroid.readOpenAiKey"
            )).split(',').distinct().single())

            eval(scenario, "document.querySelector('#runFixture').click();true")
            val result = JSONObject(decoded(eval(scenario, "document.querySelector('#fixtureResult').textContent")))
            listOf(
                "exactOriginAllowed", "subframeRejected", "deceptiveOriginRejected",
                "dataOriginRejected", "fileOriginRejected", "javascriptOriginRejected",
                "lateResponseRejected", "keySaved", "keyStatusMasked",
                "keyUsableOnlyInternally", "keyDeleted", "exportExcludesSecrets",
                "validImportAccepted", "malformedImportRejected",
                "oversizedImportRejected", "futureImportRejected"
            ).forEach { key -> assertTrue("Expected $key", result.getBoolean(key)) }

            scenario.onActivity { it.clearDebugRecordedDates() }
            eval(scenario, "window.__run6LaunchSubframe();true")
            await(scenario) { decoded(eval(scenario, "String(window.__run6SubframeChannel)")) != "waiting" }
            assertEquals("object", decoded(eval(scenario, "String(window.__run6SubframeChannel)")))
            SystemClock.sleep(300)
            scenario.onActivity { activity ->
                assertTrue("Subframe must not invoke native refresh", activity.debugRecordedDates().isEmpty())
            }
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
