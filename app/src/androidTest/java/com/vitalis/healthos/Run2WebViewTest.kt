package com.vitalis.healthos

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class Run2WebViewTest {
    private val instrument = InstrumentationRegistry.getInstrumentation()

    @Test fun productionScriptsSelectEveryCoachAndPreserveDateThroughRecreation() {
        val context = instrument.targetContext
        context.getSharedPreferences("vitalis_preferences", Context.MODE_PRIVATE).edit()
            .remove("selected_health_date_iso").commit()
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_RUN2_FIXTURE, true)
            putExtra(MainActivity.EXTRA_TEST_TODAY_ISO, "2026-09-26")
        }
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            ready(scenario)
            assertEquals("\"2026-09-26\"", eval(scenario, "window.VitalisDate.get()"))
            // Reinject both actual production layers into this document; their guards must no-op.
            eval(scenario, "(function(){['compat.js','vitalis-3.12.js'].forEach(function(src){" +
                "var s=document.createElement('script');s.src=src;s.onload=function(){" +
                "window.__run2ScriptLoads=(window.__run2ScriptLoads||0)+1};" +
                "document.head.appendChild(s)});return true})()")
            await(scenario) { eval(scenario, "window.__run2ScriptLoads") == "2" }
            val coaches = listOf("general", "nutrition", "activity", "sleep", "recovery", "mental")
            for (id in coaches) {
                for (part in listOf("card", "portrait", "name")) {
                    tap(scenario, "#openCoaches")
                    await(scenario) { eval(scenario, "document.querySelectorAll('[data-coach-312]').length") == "6" }
                    assertEquals("All six coach buttons must be present", "6",
                        eval(scenario, "document.querySelectorAll('[data-coach-312]').length"))
                    assertEquals("1", eval(scenario,
                        "document.querySelectorAll('[data-coach-312].selected[aria-pressed=true]').length"))
                    val selector = "[data-coach-312='$id']"
                    assertEquals("true", eval(scenario,
                        "(function(){var e=document.querySelector(${JSONObject.quote(selector)});" +
                            "return !!e&&!e.disabled&&!!e.getAttribute('aria-label')&&" +
                            "getComputedStyle(e).visibility==='visible'&&e.getBoundingClientRect().width>0})()"))
                    eval(scenario, "window.__selectionChanges=0;true")
                    tap(scenario, when (part) {
                        "portrait" -> "$selector img"
                        "name" -> "$selector strong"
                        else -> selector
                    }, part == "card")
                    await(scenario) { eval(scenario,
                        "document.documentElement.getAttribute('data-vitalis-selected-coach')") == "\"$id\"" }
                    await(scenario) { eval(scenario, "window.__selectionChanges") == "1" }
                    assertEquals("Exactly one coach selection: $id $part", "1",
                        eval(scenario, "window.__selectionChanges"))
                    assertEquals("1", eval(scenario,
                        "document.querySelectorAll('.vitalis-coach-overlay-312').length"))
                    tap(scenario, ".vitalis-coach-overlay-312 .vitalis-native-close")
                }
            }

            // The production catalogue uses delegation, so replacement nodes retain one handler.
            tap(scenario, "#openCoaches")
            eval(scenario, "(function(){var g=document.querySelector('.vitalis-coach-grid-312');" +
                "g.innerHTML=g.innerHTML;window.__selectionChanges=0;return true})()")
            // This assertion targets delegated DOM event ownership after node replacement.
            // Other catalogue assertions above retain real pointer injection; using click()
            // here removes emulator coordinate/focus variance from this JavaScript contract.
            eval(scenario, "document.querySelector('[data-coach-312=\"sleep\"] strong').click();true")
            await(scenario) { eval(scenario, "window.__selectionChanges") == "1" }
            tap(scenario, ".vitalis-coach-overlay-312 .vitalis-native-close")

            // A nested control may handle its own action without selecting the parent coach.
            tap(scenario, "#openCoaches")
            eval(scenario, "(function(){var c=document.querySelector('[data-coach-312=\"recovery\"]');" +
                "var n=document.createElement('span');n.setAttribute('role','button');" +
                "n.setAttribute('data-coach-action','');n.textContent='Action interne';" +
                "n.style.cssText='display:block;padding:12px;background:#eee';" +
                "n.onclick=function(){window.__nestedActions=(window.__nestedActions||0)+1};" +
                "c.appendChild(n);window.__selectionChanges=0;return true})()")
            tap(scenario, "[data-coach-action]")
            assertEquals("1", eval(scenario, "window.__nestedActions"))
            assertEquals("0", eval(scenario, "window.__selectionChanges"))
            assertEquals("1", eval(scenario, "document.querySelectorAll('.vitalis-power-overlay-312').length"))
            tap(scenario, ".vitalis-power-overlay-312 .vitalis-native-close")

            // Native keyboard activation of the same semantic button.
            for (keyCode in listOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE)) {
                tap(scenario, "#openCoaches")
                eval(scenario, "document.querySelector('[data-coach-312=\"mental\"]').focus();" +
                    "window.__selectionChanges=0;true")
                instrument.sendKeyDownUpSync(keyCode)
                await(scenario) { eval(scenario,
                    "document.documentElement.getAttribute('data-vitalis-selected-coach')") == "\"mental\"" &&
                    eval(scenario, "document.querySelectorAll('.vitalis-coach-overlay-312').length") == "1" }
                assertEquals("1", eval(scenario, "window.__selectionChanges"))
                tap(scenario, ".vitalis-coach-overlay-312 .vitalis-native-close")
            }

            // Real touch on refresh; the date input emits the same production change event as a picker.
            eval(scenario, "(function(){var e=document.querySelector('#selectedDate');" +
                "e.value='2026-09-18';e.dispatchEvent(new Event('change',{bubbles:true}));return true})()")
            await(scenario) { eval(scenario, "window.VitalisDate.get()") == "\"2026-09-18\"" }
            assertEquals("\"2026-09-18\"", eval(scenario,
                "document.querySelector('#selectedLabel').textContent"))
            scenario.onActivity { it.clearDebugRecordedDates() }
            tap(scenario, "#refresh")
            await(scenario) { recorded(scenario).isNotEmpty() }
            assertEquals(listOf("2026-09-18"), recorded(scenario))
            scenario.onActivity { it.clearDebugRecordedDates() }
            tap(scenario, "#refresh")
            tap(scenario, "#refresh")
            await(scenario) { recorded(scenario).size == 2 }
            assertEquals(listOf("2026-09-18", "2026-09-18"), recorded(scenario))

            tap(scenario, "#openCoaches")
            tap(scenario, "[data-coach-312='sleep']")
            assertEquals("\"2026-09-18\"", eval(scenario, "window.VitalisDate.get()"))
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            assertEquals("\"2026-09-18\"", eval(scenario, "window.VitalisDate.get()"))
            // Clear the previous document's signal before reload, so ready() cannot
            // return while the old page is still visible during navigation.
            eval(scenario, "window.__run2Ready=false;true")
            scenario.onActivity { activity ->
                findWebView(activity.findViewById(android.R.id.content))?.reload()
            }
            ready(scenario)
            assertEquals("\"2026-09-18\"", eval(scenario, "window.VitalisDate.get()"))
            scenario.recreate()
            ready(scenario)
            assertEquals("\"2026-09-18\"", eval(scenario, "window.VitalisDate.get()"))
            assertEquals("\"2026-09-18\"", eval(scenario,
                "document.querySelector('#selectedLabel').textContent"))
            scenario.onActivity { it.clearDebugRecordedDates() }
            tap(scenario, "#today")
            await(scenario) { recorded(scenario).isNotEmpty() }
            assertEquals(listOf("2026-09-26"), recorded(scenario))
            assertEquals("\"2026-09-26\"", eval(scenario, "window.VitalisDate.get()"))
        }
    }

    private fun ready(scenario: ActivityScenario<MainActivity>) {
        await(scenario, 15_000) { eval(scenario, "window.__run2Ready===true") == "true" }
    }

    private fun recorded(scenario: ActivityScenario<MainActivity>): List<String> {
        var result = emptyList<String>()
        scenario.onActivity { result = it.debugRecordedDates() }
        return result
    }

    private fun await(scenario: ActivityScenario<MainActivity>, timeout: Long = 15_000,
                      condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            if (condition()) return
            SystemClock.sleep(50)
        }
        fail("WebView condition did not become true; URL=${eval(scenario, "location.href")}")
    }

    private fun eval(scenario: ActivityScenario<MainActivity>, js: String): String {
        val latch = CountDownLatch(1)
        val result = AtomicReference<String>()
        scenario.onActivity { activity ->
            val view = findWebView(activity.findViewById(android.R.id.content))
            requireNotNull(view).evaluateJavascript(js) { result.set(it); latch.countDown() }
        }
        assertTrue("JavaScript callback timed out", latch.await(15, TimeUnit.SECONDS))
        return result.get() ?: "null"
    }

    private fun tap(scenario: ActivityScenario<MainActivity>, selector: String, background: Boolean = false) {
        // A fresh Google APIs emulator can briefly place a launcher ANR window above the app.
        // Wait for our actual window before injecting a UID-targeted touch; do not mask a lost focus.
        await(scenario, 20_000) {
            var focused = false
            scenario.onActivity { focused = it.window.decorView.hasWindowFocus() }
            focused
        }
        val expression = "(function(){var e=document.querySelector(${JSONObject.quote(selector)});" +
            "if(!e)return '';e.scrollIntoView({block:'center'});var r=e.getBoundingClientRect();" +
            "return JSON.stringify({x:r.left+${if (background) "8" else "r.width/2"}," +
            "y:r.top+${if (background) "8" else "r.height/2"},w:innerWidth})})()"
        val encoded = eval(scenario, expression)
        val point = JSONObject(JSONArray("[$encoded]").getString(0))
        var x = 0f; var y = 0f
        scenario.onActivity { activity ->
            val view = requireNotNull(findWebView(activity.findViewById(android.R.id.content)))
            val origin = IntArray(2)
            view.getLocationOnScreen(origin)
            val scale = view.width.toDouble() / point.getDouble("w")
            x = (origin[0] + point.getDouble("x") * scale).toFloat()
            y = (origin[1] + point.getDouble("y") * scale).toFloat()
        }
        val start = SystemClock.uptimeMillis()
        instrument.sendPointerSync(MotionEvent.obtain(start,start,MotionEvent.ACTION_DOWN,x,y,0))
        instrument.sendPointerSync(MotionEvent.obtain(start,start+70,MotionEvent.ACTION_UP,x,y,0))
        instrument.waitForIdleSync()
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount)
            findWebView(view.getChildAt(i))?.let { return it }
        return null
    }
}
