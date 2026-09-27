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
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Run4NutritionWebViewTest {
    private val instrument = InstrumentationRegistry.getInstrumentation()

    @Test fun syntheticScanReviewIdempotentSaveRecreationEditAndDelete() {
        val context = instrument.targetContext
        context.getSharedPreferences("vitalis_preferences", Context.MODE_PRIVATE).edit()
            .remove("manual_meal_estimates")
            .remove("pending_nutrition_scan_v1")
            .remove("selected_health_date_iso")
            .commit()
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_RUN4_FIXTURE, true)
            putExtra(MainActivity.EXTRA_TEST_TODAY_ISO, "2026-09-26")
        }
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            ready(scenario)
            eval(scenario, "(function(){var e=document.querySelector('#selectedDate');" +
                "e.value='2026-09-18';e.dispatchEvent(new Event('change',{bubbles:true}));return true})()")
            assertEquals("\"2026-09-18\"", eval(scenario, "window.VitalisDate.get()"))

            eval(scenario, "window.VitalisNutrition.startScan('synthetic_test');true")
            await(scenario, 15_000) {
                eval(scenario, "!!document.querySelector('[data-save-meal-estimate]')") == "true"
            }
            assertEquals("\"520\"", eval(scenario,
                "document.querySelector('[data-meal-field=caloriesKcal]').value"))
            eval(scenario, "window.__run4Scan=window.VitalisNutrition.active();" +
                "var d=document.querySelector('#selectedDate');d.value='2026-09-19';" +
                "d.dispatchEvent(new Event('change',{bubbles:true}));" +
                "document.querySelector('[data-meal-field=caloriesKcal]').value='610';" +
                "document.querySelector('[data-save-meal-estimate]').click();true")
            await(scenario) { mealCount(scenario) == 1 }
            assertEquals("\"2026-09-19\"", eval(scenario, "window.VitalisDate.get()"))
            var meals = meals(scenario)
            assertEquals("2026-09-18", meals.getJSONObject(0).getString("date"))
            assertEquals(610.0, meals.getJSONObject(0).getDouble("caloriesKcal"), 0.0)

            val repeated = JSONObject(decoded(eval(scenario,
                "(function(){var e={mealName:'Bol végétal',foodItems:[],portionDescription:'1 bol'," +
                    "caloriesKcal:610,carbohydratesG:62,proteinG:21,fatG:19,fibreG:13,sugarG:9," +
                    "sodiumMg:640,confidence:.71,uncertaintyNotes:'vérifié',estimated:true};" +
                    "return VitalisAndroid.saveNutritionMeal(window.__run4Scan.scanId,JSON.stringify(e))})()")))
            assertTrue(repeated.getBoolean("ok"))
            assertEquals(1, mealCount(scenario))

            scenario.recreate()
            ready(scenario)
            assertEquals("\"2026-09-19\"", eval(scenario, "window.VitalisDate.get()"))
            assertEquals(1, mealCount(scenario))

            eval(scenario, "window.VitalisNutrition.openManager();" +
                "document.querySelector('[data-edit-meal]').click();" +
                "document.querySelector('[data-meal-field=caloriesKcal]').value='700';" +
                "document.querySelector('[data-save-meal-estimate]').click();true")
            await(scenario) { meals(scenario).getJSONObject(0).getDouble("caloriesKcal") == 700.0 }
            assertEquals(1, mealCount(scenario))

            eval(scenario, "window.confirm=function(){return true};window.VitalisNutrition.openManager();" +
                "document.querySelector('[data-delete-meal]').click();true")
            await(scenario) { mealCount(scenario) == 0 }
            assertEquals(0, mealCount(scenario))
        }
    }

    private fun ready(scenario: ActivityScenario<MainActivity>) {
        await(scenario, 15_000) {
            eval(scenario, "window.__run4Fixture===true&&!!window.VitalisNutrition&&!!window.VitalisDate") == "true"
        }
    }

    private fun meals(scenario: ActivityScenario<MainActivity>) = JSONObject(
        decoded(eval(scenario, "VitalisAndroid.getLocalNutritionMeals('')"))
    ).getJSONArray("meals")

    private fun decoded(encoded: String): String = JSONArray("[$encoded]").getString(0)

    private fun mealCount(scenario: ActivityScenario<MainActivity>): Int = meals(scenario).length()

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
