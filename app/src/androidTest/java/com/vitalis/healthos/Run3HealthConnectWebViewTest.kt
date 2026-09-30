package com.vitalis.healthos

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
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
class Run3HealthConnectWebViewTest {
    private val instrument = InstrumentationRegistry.getInstrumentation()

    @Test fun partialPermissionPayloadAndSelectedDateReachProductionWebViewContract() {
        val context = instrument.targetContext
        context.getSharedPreferences("vitalis_preferences", Context.MODE_PRIVATE).edit()
            .remove("selected_health_date_iso").commit()
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_RUN3_FIXTURE, true)
            putExtra(MainActivity.EXTRA_TEST_TODAY_ISO, "2026-09-26")
        }
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            ready(scenario)
            eval(scenario, "(function(){var e=document.querySelector('#selectedDate');" +
                "e.value='2026-09-18';e.dispatchEvent(new Event('change',{bubbles:true}));" +
                "return true})()")
            await(scenario) { eval(scenario, "window.VitalisDate.get()") == "\"2026-09-18\"" }
            scenario.onActivity { it.clearDebugRecordedDates() }
            tap(scenario, "#refresh")
            await(scenario) {
                recorded(scenario) == listOf("2026-09-18")
            }
            assertEquals(listOf("2026-09-18"), recorded(scenario))
            assertEquals(
                "\"PARTIAL_PERMISSION\"",
                eval(scenario, "document.querySelector('#stateCode').textContent")
            )
            assertEquals(
                "\"android.permission.health.READ_SLEEP\"",
                eval(scenario, "document.querySelector('#missingPermissions').textContent")
            )
            assertEquals(
                "\"2026-09-18\"",
                eval(scenario, "document.querySelector('#selectedLabel').textContent")
            )

            scenario.recreate()
            ready(scenario)
            assertEquals("\"2026-09-18\"", eval(scenario, "window.VitalisDate.get()"))
            scenario.onActivity { it.clearDebugRecordedDates() }
            tap(scenario, "#today")
            await(scenario) { recorded(scenario).isNotEmpty() }
            assertEquals(listOf("2026-09-26"), recorded(scenario))
            assertEquals("\"2026-09-26\"", eval(scenario, "window.VitalisDate.get()"))
        }
    }

    private fun ready(scenario: ActivityScenario<MainActivity>) {
        await(scenario, 15_000) {
            eval(scenario, "window.__run3Ready===true&&!!window.VitalisDate") == "true"
        }
    }

    private fun recorded(scenario: ActivityScenario<MainActivity>): List<String> {
        var result = emptyList<String>()
        scenario.onActivity { result = it.debugRecordedDates() }
        return result
    }

    private fun await(
        scenario: ActivityScenario<MainActivity>,
        timeout: Long = 15_000,
        condition: () -> Boolean
    ) {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            if (condition()) return
            SystemClock.sleep(50)
        }
        fail("WebView condition did not become true; URL=" + eval(scenario, "location.href"))
    }

    private fun eval(scenario: ActivityScenario<MainActivity>, js: String): String {
        val latch = CountDownLatch(1)
        val result = AtomicReference<String>()
        scenario.onActivity { activity ->
            val view = findWebView(activity.findViewById(android.R.id.content))
            requireNotNull(view).evaluateJavascript(js) {
                result.set(it)
                latch.countDown()
            }
        }
        assertTrue("JavaScript callback timed out", latch.await(15, TimeUnit.SECONDS))
        return result.get() ?: "null"
    }

    private fun tap(scenario: ActivityScenario<MainActivity>, selector: String) {
        await(scenario, 20_000) {
            var focused = false
            scenario.onActivity { focused = it.window.decorView.hasWindowFocus() }
            focused
        }
        val encoded = eval(
            scenario,
            "(function(){var e=document.querySelector(" + JSONObject.quote(selector) + ");" +
                "if(!e)return '';e.scrollIntoView({block:'center'});" +
                "var r=e.getBoundingClientRect();" +
                "return JSON.stringify({x:r.left+r.width/2,y:r.top+r.height/2,w:innerWidth})})()"
        )
        val point = JSONObject(JSONArray("[$encoded]").getString(0))
        var x = 0f
        var y = 0f
        scenario.onActivity { activity ->
            val view = requireNotNull(findWebView(activity.findViewById(android.R.id.content)))
            val origin = IntArray(2)
            view.getLocationOnScreen(origin)
            val scale = view.width.toDouble() / point.getDouble("w")
            x = (origin[0] + point.getDouble("x") * scale).toFloat()
            y = (origin[1] + point.getDouble("y") * scale).toFloat()
        }
        val start = SystemClock.uptimeMillis()
        instrument.sendPointerSync(
            MotionEvent.obtain(start, start, MotionEvent.ACTION_DOWN, x, y, 0)
        )
        instrument.sendPointerSync(
            MotionEvent.obtain(start, start + 70, MotionEvent.ACTION_UP, x, y, 0)
        )
        instrument.waitForIdleSync()
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findWebView(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }
}
