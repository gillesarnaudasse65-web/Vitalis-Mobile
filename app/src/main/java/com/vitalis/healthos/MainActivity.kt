package com.vitalis.healthos

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private var healthConnectClient: HealthConnectClient? = null

    private val healthPermissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(RespiratoryRateRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(BloodPressureRecord::class),
        HealthPermission.getReadPermission(BodyTemperatureRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
        HealthPermission.getReadPermission(NutritionRecord::class),
        HealthPermission.getReadPermission(HydrationRecord::class)
    )

    private val permissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        val allGranted = granted.containsAll(healthPermissions)
        notifyWeb(
            granted = allGranted,
            status = if (allGranted) "authorized" else "partial",
            message = if (allGranted) {
                "Health Connect est autorisé. Vitalis peut synchroniser les catégories choisies."
            } else {
                "Autorisation partielle. Vous pouvez compléter les catégories dans Health Connect."
            }
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (HealthConnectClient.getSdkStatus(this) == HealthConnectClient.SDK_AVAILABLE) {
            healthConnectClient = HealthConnectClient.getOrCreate(this)
        }

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            configureBridgeForHost(VITALIS_HOST)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val uri = request.url
                    val host = uri.host.orEmpty()
                    return if (uri.scheme == "https" && isTrustedAppHost(host)) {
                        configureBridgeForHost(host)
                        false
                    } else {
                        startActivity(Intent(Intent.ACTION_VIEW, uri))
                        true
                    }
                }

                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    configureBridgeForHost(Uri.parse(url).host.orEmpty())
                    super.onPageStarted(view, url, favicon)
                }
            }
            loadUrl(VITALIS_URL)
        }
        setContentView(webView)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    private fun isTrustedAppHost(host: String): Boolean {
        return host == VITALIS_HOST ||
            host.endsWith(".chatgpt.site") ||
            host == "chatgpt.com" ||
            host.endsWith(".chatgpt.com") ||
            host == "openai.com" ||
            host.endsWith(".openai.com")
    }

    private fun configureBridgeForHost(host: String) {
        webView.removeJavascriptInterface("VitalisAndroid")
        if (host == VITALIS_HOST) {
            webView.addJavascriptInterface(VitalisAndroidBridge(), "VitalisAndroid")
        }
    }

    inner class VitalisAndroidBridge {
        @JavascriptInterface
        fun isNativeApp(): Boolean = true

        @JavascriptInterface
        fun requestHealthConnectPermissions() {
            runOnUiThread {
                when (HealthConnectClient.getSdkStatus(this@MainActivity)) {
                    HealthConnectClient.SDK_AVAILABLE -> permissionLauncher.launch(healthPermissions)
                    HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                        notifyWeb(false, "update_required", "Health Connect doit être installé ou mis à jour.")
                        openHealthConnectStore()
                    }
                    else -> notifyWeb(
                        false,
                        "unavailable",
                        "Health Connect n’est pas disponible sur cet appareil Android."
                    )
                }
            }
        }

        @JavascriptInterface
        fun openHealthConnectSettings() {
            runOnUiThread {
                try {
                    startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
                } catch (_: ActivityNotFoundException) {
                    openHealthConnectStore()
                }
            }
        }
    }

    private fun openHealthConnectStore() {
        val packageName = "com.google.android.apps.healthdata"
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
        } catch (_: ActivityNotFoundException) {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                )
            )
        }
    }

    private fun notifyWeb(granted: Boolean, status: String, message: String) {
        val detail = JSONObject().apply {
            put("granted", granted)
            put("status", status)
            put("message", message)
        }.toString()
        runOnUiThread {
            webView.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('vitalis-health-connect',{detail:$detail}));",
                null
            )
        }
    }

    companion object {
        private const val VITALIS_HOST = "vitalis-health-os.gillesarnaudasse65.chatgpt.site"
        private const val VITALIS_URL = "https://$VITALIS_HOST"
    }
}
