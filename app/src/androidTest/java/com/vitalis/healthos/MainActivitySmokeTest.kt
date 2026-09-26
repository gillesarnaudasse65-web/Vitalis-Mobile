package com.vitalis.healthos

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @Test fun debugOfflineLaunchCreatesWebViewWithoutRemoteSite() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_FORCE_OFFLINE_FOR_TESTS, true)
        }
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            var url: String? = null
            var foundWebView = false
            for (attempt in 0 until 20) {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { activity ->
                    assertEquals("com.vitalis.healthos", activity.packageName)
                    val content = activity.findViewById<ViewGroup>(android.R.id.content)
                    val webView = findWebView(content)
                    foundWebView = webView != null
                    url = webView?.url
                }
                if (url?.startsWith("https://appassets.androidplatform.net/assets/vitalis/index.html") == true)
                    break
                Thread.sleep(250)
            }
            assertTrue("WebView container missing", foundWebView)
            assertTrue("Bundled fallback did not load: $url", url?.startsWith(
                "https://appassets.androidplatform.net/assets/vitalis/index.html") == true)
        }
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount)
            findWebView(view.getChildAt(index))?.let { return it }
        return null
    }
}
