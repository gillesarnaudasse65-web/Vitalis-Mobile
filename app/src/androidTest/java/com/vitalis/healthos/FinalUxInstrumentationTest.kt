package com.vitalis.healthos

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.provider.MediaStore
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
class FinalUxInstrumentationTest {
    private val instrument = InstrumentationRegistry.getInstrumentation()

    @Test fun everyCoachAndSourcesPathRemainsSelectableThroughTheRealClickPipeline() {
        val intent = Intent(instrument.targetContext, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_FINAL_UX_FIXTURE, true)
            putExtra(MainActivity.EXTRA_TEST_TODAY_ISO, "2026-09-27")
        }
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            ready(scenario)
            eval(scenario, "VitalisFinalUX.restoreDefaults();true")
            val coachIds = listOf("general", "nutrition", "activity", "sleep", "recovery", "mental")

            repeat(4) {
                for (coachId in coachIds) {
                    assertEquals("true", eval(scenario,
                        "!!document.querySelector('[data-coach-select=\"$coachId\"]')"))
                    eval(scenario,
                        "document.querySelector('[data-coach-select=\"$coachId\"]').click();true")
                    await(scenario) {
                        decoded(eval(scenario, "VitalisCoaches.selected().id")) == coachId
                    }
                    assertEquals("0", eval(scenario,
                        "document.querySelectorAll('.vitalis-power-overlay-312,.vux-layer').length"))
                }
            }

            eval(scenario, "document.querySelector('[data-nav=sources]').click();true")
            await(scenario) {
                eval(scenario, "!!document.querySelector('.vitalis-power-overlay-312')") == "true"
            }
            eval(scenario,
                "document.querySelector('.vitalis-power-overlay-312 .vitalis-native-close').click();true")

            eval(scenario,
                "window.__savedVitalisShowSources=VitalisConnectorControls.showSources;" +
                    "VitalisConnectorControls.showSources=function(){throw new Error('synthetic failure')};" +
                    "document.querySelector('[data-nav=sources]').click();true")
            await(scenario) {
                eval(scenario, "!!document.querySelector('[data-view=sources-details]')") == "true"
            }
            eval(scenario,
                "document.querySelector('[data-view=sources-details] [data-close]').click();" +
                    "VitalisConnectorControls.showSources=window.__savedVitalisShowSources;true")

            eval(scenario,
                "VitalisConnectorControls.showSources=function(){};" +
                    "document.querySelector('[data-nav=sources]').click();true")
            await(scenario) {
                eval(scenario, "!!document.querySelector('[data-view=sources-details]')") == "true"
            }
            eval(scenario,
                "document.querySelector('[data-view=sources-details] [data-close]').click();" +
                    "VitalisConnectorControls.showSources=window.__savedVitalisShowSources;true")
        }
    }

    @Test fun androidBackClosesFinalUxAndCoachLayersBeforeLeavingTheApp() {
        val intent = Intent(instrument.targetContext, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_FINAL_UX_FIXTURE, true)
            putExtra(MainActivity.EXTRA_TEST_TODAY_ISO, "2026-09-27")
        }
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            ready(scenario)
            eval(scenario, "VitalisFinalUX.openCustomize();true")
            await(scenario) { eval(scenario, "!!document.querySelector('.vux-layer')") == "true" }
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            await(scenario) { eval(scenario, "document.querySelectorAll('.vux-layer').length") == "0" }
            assertEquals("true", eval(scenario, "!!document.querySelector('#vitalis-final-ux')"))

            eval(scenario, "VitalisCoaches.open();true")
            await(scenario) {
                eval(scenario, "!!document.querySelector('.vitalis-power-overlay-312')") == "true"
            }
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            await(scenario) {
                eval(scenario, "document.querySelectorAll('.vitalis-power-overlay-312').length") == "0"
            }
            assertEquals("true", eval(scenario, "!!document.querySelector('#vitalis-final-ux')"))
        }
    }

    @Test fun themesWidgetsDetailsAndCustomizationRemainInteractiveAndPersistent() {
        val intent = Intent(instrument.targetContext, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_FINAL_UX_FIXTURE, true)
            putExtra(MainActivity.EXTRA_TEST_TODAY_ISO, "2026-09-27")
        }
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            ready(scenario)
            eval(scenario, "VitalisFinalUX.restoreDefaults();true")
            await(scenario) { eval(scenario, "document.querySelectorAll('[data-widget]').length>=10") == "true" }
            assertEquals("82", decoded(eval(
                scenario, "document.querySelector('.vux-score-ring strong').textContent"
            )))
            assertEquals("true", eval(scenario, "!!document.querySelector('[data-widget=activity]')"))
            assertEquals("true", eval(scenario, "!!document.querySelector('[data-widget=sleep]')"))
            assertEquals("function", decoded(eval(
                scenario, "typeof document.querySelector('[data-widget=score]').onclick"
            )))
            screenshot("classic-dashboard")

            openDetailAndReturn(scenario, "score", "score-details")
            openDetailAndReturn(scenario, "activity", "activity-details")
            openDetailAndReturn(scenario, "sleep", "sleep-details")
            openDetailAndReturn(scenario, "nutrition", "nutrition-details")
            for (widget in listOf("heart", "hydration", "recovery", "mental", "body")) {
                openDetailAndReturn(scenario, widget, null)
            }

            eval(scenario, "document.querySelector('[data-widget=coach]').click();true")
            await(scenario) { eval(scenario, "!!document.querySelector('.vitalis-power-overlay-312')") == "true" }
            eval(scenario, "document.querySelector('.vitalis-power-overlay-312 .vitalis-native-close').click();true")
            eval(scenario, "document.querySelector('[data-widget=sources]').click();true")
            await(scenario) { eval(scenario, "!!document.querySelector('.vitalis-power-overlay-312')") == "true" }
            eval(scenario, "document.querySelector('.vitalis-power-overlay-312 .vitalis-native-close').click();true")

            val before = JSONObject(decoded(eval(scenario, "JSON.stringify(VitalisFinalUX.snapshot())")))
                .getJSONObject("settings").getJSONObject("manualHydration")
                .optDouble("2026-09-27", 0.0)
            eval(scenario, "document.querySelector('[data-widget=hydration] [data-amount=\".25\"]').click();true")
            val after = JSONObject(decoded(eval(scenario, "JSON.stringify(VitalisFinalUX.snapshot())")))
                .getJSONObject("settings").getJSONObject("manualHydration")
                .getDouble("2026-09-27")
            assertEquals(before + 0.25, after, 0.001)
            assertEquals("0", eval(scenario, "document.querySelectorAll('.vux-layer').length"))

            eval(scenario, "window.__dateSelectCalls=[];" +
                "window.__originalDateSelect=VitalisDate.select;" +
                "VitalisDate.select=function(value){window.__dateSelectCalls.push(value);" +
                "return window.__originalDateSelect(value)};" +
                "document.querySelector('[data-date-input]').value='2026-09-18';" +
                "document.querySelector('[data-date-input]').dispatchEvent(new Event('change',{bubbles:true}));true")
            await(scenario) { decoded(eval(scenario, "VitalisFinalUX.snapshot().date")) == "2026-09-18" }
            assertEquals(
                "[\"2026-09-18\"]",
                decoded(eval(scenario, "JSON.stringify(window.__dateSelectCalls)"))
            )
            eval(scenario, "VitalisDate.select=window.__originalDateSelect;true")
            eval(scenario, "VitalisFinalUX.openDetail('activity');true")
            assertEquals("2026-09-18", decoded(eval(scenario, "VitalisFinalUX.snapshot().date")))
            eval(scenario, "document.querySelector('.vux-layer [data-close]').click();true")
            assertEquals("2026-09-18", decoded(eval(scenario, "VitalisFinalUX.snapshot().date")))

            for (theme in listOf("classic", "ocean", "dark", "amoled", "aurora")) {
                eval(scenario, "VitalisFinalUX.setTheme('$theme');true")
                await(scenario) { decoded(eval(scenario, "document.documentElement.getAttribute('data-vitalis-theme')")) == theme }
                screenshot("$theme-dashboard")
            }
            val performance = JSONObject(decoded(eval(
                scenario, "JSON.stringify(VitalisFinalUX.snapshot().metrics)"
            )))
            assertTrue(performance.optDouble("initialRenderMs", -1.0) in 0.0..5000.0)
            assertTrue(performance.optDouble("themeSwitchMs", -1.0) in 0.0..1000.0)
            assertTrue(performance.optDouble("cardInteractionMs", -1.0) in 0.0..1000.0)
            writePerformanceEvidence(performance.toString(2))
            eval(scenario, "VitalisFinalUX.setTheme('system');VitalisFinalUX.setAccent('violet');true")
            scenario.recreate()
            ready(scenario)
            assertEquals("system", decoded(eval(scenario, "VitalisFinalUX.getSettings().theme")))
            assertEquals("violet", decoded(eval(scenario, "VitalisFinalUX.getSettings().accent")))
            assertEquals("2026-09-18", decoded(eval(scenario, "VitalisFinalUX.snapshot().date")))

            val originalIndex = eval(scenario, "VitalisFinalUX.getSettings().order.indexOf('sleep')")
            eval(scenario, "VitalisFinalUX.moveWidget('sleep',-2);true")
            val movedIndex = eval(scenario, "VitalisFinalUX.getSettings().order.indexOf('sleep')")
            assertNotEquals(originalIndex, movedIndex)
            scenario.recreate()
            ready(scenario)
            assertEquals(movedIndex, eval(scenario, "VitalisFinalUX.getSettings().order.indexOf('sleep')"))

            eval(scenario, "VitalisFinalUX.hideWidget('heart',true);true")
            assertEquals("false", eval(scenario, "!!document.querySelector('[data-widget=heart]')"))
            eval(scenario, "VitalisFinalUX.openCustomize();true")
            screenshot("customize-dashboard")
            eval(scenario, "document.querySelector('.vux-layer [data-close]').click();VitalisFinalUX.openAppearance();true")
            screenshot("appearance-settings")
            eval(scenario, "document.querySelector('.vux-layer [data-close]').click();VitalisFinalUX.restoreDefaults();true")
            assertEquals("true", eval(scenario, "!!document.querySelector('[data-widget=heart]')"))

            eval(scenario, "document.querySelector('[data-widget=coach]').scrollIntoView({block:'center'});true")
            screenshot("coach-card")
            eval(scenario, "document.querySelector('[data-widget=sources]').scrollIntoView({block:'center'});true")
            screenshot("health-connect-status")

            eval(scenario, "window.__vitalisFinalUxSyntheticData.metrics.averageHeartRate.status='NO_DATA';" +
                "window.__vitalisFinalUxSyntheticData.averageHeartRate=null;VitalisFinalUX.setTheme('classic');true")
            screenshot("empty-state")
            assertEquals("Aucune fréquence cardiaque", decoded(eval(
                scenario, "document.querySelector('[data-widget=heart] .vux-secondary').textContent"
            )))
            eval(scenario, "window.__vitalisFinalUxSyntheticData.metrics.averageHeartRate.status='ERROR';" +
                "VitalisFinalUX.setTheme('classic');true")
            screenshot("error-state")
            assertEquals("Lecture impossible", decoded(eval(
                scenario, "document.querySelector('[data-widget=heart] .vux-secondary').textContent"
            )))

            eval(scenario, "document.querySelector('#vitalis-final-ux').style.maxWidth='360px';" +
                "document.querySelector('#vitalis-final-ux').style.margin='auto';true")
            screenshot("small-screen")
        }
    }

    private fun openDetailAndReturn(
        scenario: ActivityScenario<MainActivity>,
        widget: String,
        screenshotName: String?
    ) {
        eval(scenario, "VitalisFinalUX.openDetail('$widget');true")
        await(scenario) { eval(scenario, "!!document.querySelector('[data-view=${widget}-details]')") == "true" }
        if (screenshotName != null) screenshot(screenshotName)
        eval(scenario, "document.querySelector('.vux-layer [data-close]').click();true")
        await(scenario) { eval(scenario, "document.querySelectorAll('.vux-layer').length") == "0" }
    }

    private fun screenshot(name: String) {
        instrument.waitForIdleSync()
        SystemClock.sleep(120)
        val bitmap = instrument.uiAutomation.takeScreenshot()
        val resolver = instrument.targetContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/VitalisFinalUX")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        resolver.openOutputStream(uri).use { output ->
            assertNotNull(output)
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output!!))
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        bitmap.recycle()
    }

    private fun writePerformanceEvidence(json: String) {
        val resolver = instrument.targetContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "performance.json")
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/VitalisFinalUX")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        resolver.openOutputStream(uri).use { output ->
            assertNotNull(output)
            output!!.write(json.toByteArray())
        }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }

    private fun ready(scenario: ActivityScenario<MainActivity>) {
        await(scenario, 20_000) {
            eval(scenario, "!!window.VitalisFinalUX&&!!document.querySelector('#vitalis-final-ux')") == "true"
        }
    }

    private fun decoded(encoded: String): String = JSONArray("[$encoded]").getString(0)

    private fun await(
        scenario: ActivityScenario<MainActivity>,
        timeout: Long = 15_000,
        condition: () -> Boolean
    ) {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            if (condition()) return
            SystemClock.sleep(60)
        }
        fail("Final UX condition timed out; URL=" + eval(scenario, "location.href"))
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
        assertTrue("JavaScript callback timed out", latch.await(15, TimeUnit.SECONDS))
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
