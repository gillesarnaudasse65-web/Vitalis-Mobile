package com.vitalis.healthos

import android.content.Context
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
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.junit.runner.RunWith

/** Copied into the verified Run 5 worktree by CI to seed a real install-before-upgrade. */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class Run6UpgradeContinuityTest {
    private val instrument = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrument.targetContext

    @Test fun a_seedPreRun6Data() {
        val meal = JSONObject().apply {
            put("id", "meal-upgrade-1")
            put("date", "2026-09-18")
            put("createdAt", "2026-09-18T12:00:00Z")
            put("updatedAt", "2026-09-18T12:00:00Z")
            put("source", "Vitalis Scanner")
            put("mealName", "Repas de continuité")
            put("foodItems", JSONArray())
            put("caloriesKcal", 520.0)
            put("carbohydratesG", 62.0)
            put("proteinG", 21.0)
            put("fatG", 19.0)
            put("fibreG", 13.0)
            put("sugarG", 9.0)
            put("sodiumMg", 640.0)
            put("confidence", 0.71)
            put("estimated", true)
        }
        launchFixture("com.vitalis.healthos.RUN5_FIXTURE").use { scenario ->
            await(scenario) { eval(scenario, "window.__run5Fixture===true") == "true" }
            eval(scenario, "localStorage.setItem('vitalis-selected-coach-v312','sleep');" +
                "localStorage.setItem('vitalis-native-journal-v1','[{\"id\":\"upgrade-journal\"}]');" +
                "localStorage.setItem('vitalis-offline-v1','{\"period\":\"day\"}');true")
            assertEquals("\"sleep\"", eval(scenario, "localStorage.getItem('vitalis-selected-coach-v312')"))
        }

        assertTrue(context.getSharedPreferences("vitalis_preferences", Context.MODE_PRIVATE).edit()
            .putString("selected_health_date_iso", "2026-09-18")
            .putString("manual_meal_estimates", JSONArray().put(meal).toString())
            .putBoolean("ai_health_consent", true)
            .putString("upgrade_connector_preference", "samsung_health")
            .commit())
    }

    @Test fun b_verifyRun6UpgradePreservesData() {
        val preferences = context.getSharedPreferences("vitalis_preferences", Context.MODE_PRIVATE)
        assertEquals("2026-09-18", preferences.getString("selected_health_date_iso", null))
        assertTrue(preferences.getBoolean("ai_health_consent", false))
        assertEquals("samsung_health", preferences.getString("upgrade_connector_preference", null))
        val meals = JSONArray(preferences.getString("manual_meal_estimates", "[]"))
        assertEquals("meal-upgrade-1", meals.getJSONObject(0).getString("id"))

        launchFixture("com.vitalis.healthos.RUN6_FIXTURE").use { scenario ->
            await(scenario, 15_000) { eval(scenario, "window.__run6Fixture===true") == "true" }
            assertEquals("\"sleep\"", eval(scenario, "localStorage.getItem('vitalis-selected-coach-v312')"))
            assertEquals("\"upgrade-journal\"", eval(
                scenario,
                "JSON.parse(localStorage.getItem('vitalis-native-journal-v1'))[0].id"
            ))
            assertEquals("\"day\"", eval(
                scenario,
                "JSON.parse(localStorage.getItem('vitalis-offline-v1')).period"
            ))
        }
    }

    private fun launchFixture(extra: String): ActivityScenario<MainActivity> =
        ActivityScenario.launch(Intent(context, MainActivity::class.java).apply {
            putExtra(extra, true)
            putExtra("com.vitalis.healthos.TEST_TODAY_ISO", "2026-09-27")
        })

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
