package com.vitalis.healthos

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var loading: ProgressBar
    private lateinit var root: LinearLayout
    private lateinit var assetLoader: WebViewAssetLoader
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
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

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val selected = if (result.resultCode == RESULT_OK) {
            val data = result.data
            when {
                data?.clipData != null -> Array(data.clipData!!.itemCount) { index ->
                    data.clipData!!.getItemAt(index).uri
                }
                data?.data != null -> arrayOf(data.data!!)
                else -> emptyArray()
            }
        } else emptyArray()
        filePathCallback?.onReceiveValue(selected)
        filePathCallback = null
    }

    private val permissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        val allGranted = granted.containsAll(healthPermissions)
        notifyWeb(
            allGranted,
            if (allGranted) "authorized" else "partial",
            if (allGranted) "Health Connect est autorisé. Synchronisation activée."
            else "Autorisation partielle. Complétez les catégories dans Health Connect."
        )
        readHealthData()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#063C30")
        window.navigationBarColor = Color.parseColor("#063C30")

        if (HealthConnectClient.getSdkStatus(this) == HealthConnectClient.SDK_AVAILABLE) {
            healthConnectClient = HealthConnectClient.getOrCreate(this)
        }

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F8F6EF"))
        }
        loading = ProgressBar(this).apply { isIndeterminate = true }
        root.addView(loading, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 8))

        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mediaPlaybackRequiresUserGesture = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.userAgentString = settings.userAgentString + " VitalisAndroid/3.5"
            WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
            addJavascriptInterface(VitalisAndroidBridge(), "VitalisAndroid")
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView,
                    callback: ValueCallback<Array<Uri>>,
                    params: FileChooserParams
                ): Boolean {
                    filePathCallback?.onReceiveValue(null)
                    filePathCallback = callback
                    return runCatching {
                        fileChooserLauncher.launch(params.createIntent())
                        true
                    }.getOrElse {
                        filePathCallback = null
                        false
                    }
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
                    assetLoader.shouldInterceptRequest(request.url)

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val uri = request.url
                    return if (uri.host == LOCAL_ASSET_HOST || uri.host == VITALIS_HOST) false else {
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                        true
                    }
                }

                override fun onPageFinished(view: WebView, url: String) {
                    loading.visibility = android.view.View.GONE
                    view.evaluateJavascript(
                        "window.dispatchEvent(new CustomEvent('vitalis-native-ready',{detail:{platform:'android',version:'3.5'}}));",
                        null
                    )
                    readHealthData()
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame && request.url.host == LOCAL_ASSET_HOST) showConnectionError()
                }
            }
            loadUrl(LOCAL_URL)
        }
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    override fun onDestroy() {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }

    private fun showConnectionError() {
        root.removeAllViews()
        root.gravity = Gravity.CENTER
        root.setPadding(48, 48, 48, 48)
        root.addView(TextView(this).apply {
            text = "L’interface locale Vitalis n’a pas pu être chargée.\nFermez puis relancez l’application."
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#123C31"))
        })
        root.addView(Button(this).apply {
            text = "Réessayer"
            isAllCaps = false
            setOnClickListener { recreate() }
        })
    }

    inner class VitalisAndroidBridge {
        @JavascriptInterface fun isNativeApp(): Boolean = true
        @JavascriptInterface fun getPlatform(): String = "android"

        @JavascriptInterface
        fun requestHealthConnectPermissions() {
            runOnUiThread {
                when (HealthConnectClient.getSdkStatus(this@MainActivity)) {
                    HealthConnectClient.SDK_AVAILABLE -> permissionLauncher.launch(healthPermissions)
                    HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                        notifyWeb(false, "update_required", "Health Connect doit être installé ou mis à jour.")
                        openHealthConnectStore()
                    }
                    else -> notifyWeb(false, "unavailable", "Health Connect n’est pas disponible sur cet appareil.")
                }
            }
        }

        @JavascriptInterface
        fun refreshHealthData() {
            readHealthData()
        }

        @JavascriptInterface
        fun getConnectorStatus(): String = buildConnectorPayload(emptyList()).toString()

        @JavascriptInterface
        fun openHealthConnectSettings() {
            runOnUiThread {
                try { startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)) }
                catch (_: ActivityNotFoundException) { openHealthConnectStore() }
            }
        }

        @JavascriptInterface
        fun openExternalUrl(url: String) {
            runOnUiThread { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
        }
    }

    private fun readHealthData() {
        val client = healthConnectClient
        if (client == null) {
            dispatchConnectorStatus(emptyList())
            return
        }
        lifecycleScope.launch {
            val granted = client.permissionController.getGrantedPermissions()
            if (granted.intersect(healthPermissions).isEmpty()) {
                dispatchConnectorStatus(emptyList())
                return@launch
            }

            runCatching {
                val now = Instant.now()
                val filter = TimeRangeFilter.between(now.minus(Duration.ofDays(30)), now)
                val steps = if (HealthPermission.getReadPermission(StepsRecord::class) in granted)
                    client.readRecords(ReadRecordsRequest(StepsRecord::class, filter)).records else emptyList()
                val sleep = if (HealthPermission.getReadPermission(SleepSessionRecord::class) in granted)
                    client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, filter)).records else emptyList()
                val exercise = if (HealthPermission.getReadPermission(ExerciseSessionRecord::class) in granted)
                    client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, filter)).records else emptyList()
                val heart = if (HealthPermission.getReadPermission(HeartRateRecord::class) in granted)
                    client.readRecords(ReadRecordsRequest(HeartRateRecord::class, filter)).records else emptyList()
                val hydration = if (HealthPermission.getReadPermission(HydrationRecord::class) in granted)
                    client.readRecords(ReadRecordsRequest(HydrationRecord::class, filter)).records else emptyList()
                val distance = if (HealthPermission.getReadPermission(DistanceRecord::class) in granted)
                    client.readRecords(ReadRecordsRequest(DistanceRecord::class, filter)).records else emptyList()
                val activeCalories = if (HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class) in granted)
                    client.readRecords(ReadRecordsRequest(ActiveCaloriesBurnedRecord::class, filter)).records else emptyList()
                val oxygen = if (HealthPermission.getReadPermission(OxygenSaturationRecord::class) in granted)
                    client.readRecords(ReadRecordsRequest(OxygenSaturationRecord::class, filter)).records else emptyList()
                val weight = if (HealthPermission.getReadPermission(WeightRecord::class) in granted)
                    client.readRecords(ReadRecordsRequest(WeightRecord::class, filter)).records else emptyList()

                val sources = (steps.map { it.metadata.dataOrigin.packageName } +
                    sleep.map { it.metadata.dataOrigin.packageName } +
                    exercise.map { it.metadata.dataOrigin.packageName } +
                    heart.map { it.metadata.dataOrigin.packageName } +
                    hydration.map { it.metadata.dataOrigin.packageName } +
                    distance.map { it.metadata.dataOrigin.packageName } +
                    activeCalories.map { it.metadata.dataOrigin.packageName } +
                    oxygen.map { it.metadata.dataOrigin.packageName } +
                    weight.map { it.metadata.dataOrigin.packageName }).distinct()

                val last24h = TimeRangeFilter.between(now.minus(Duration.ofHours(24)), now)
                val steps24 = if (steps.isNotEmpty())
                    client.readRecords(ReadRecordsRequest(StepsRecord::class, last24h)).records else emptyList()
                val sleep24 = if (sleep.isNotEmpty())
                    client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, last24h)).records else emptyList()
                val exercise24 = if (exercise.isNotEmpty())
                    client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, last24h)).records else emptyList()
                val heart24 = if (heart.isNotEmpty())
                    client.readRecords(ReadRecordsRequest(HeartRateRecord::class, last24h)).records else emptyList()
                val hydration24 = if (hydration.isNotEmpty())
                    client.readRecords(ReadRecordsRequest(HydrationRecord::class, last24h)).records else emptyList()
                val distance24 = if (distance.isNotEmpty())
                    client.readRecords(ReadRecordsRequest(DistanceRecord::class, last24h)).records else emptyList()
                val activeCalories24 = if (activeCalories.isNotEmpty())
                    client.readRecords(ReadRecordsRequest(ActiveCaloriesBurnedRecord::class, last24h)).records else emptyList()
                val oxygen24 = if (oxygen.isNotEmpty())
                    client.readRecords(ReadRecordsRequest(OxygenSaturationRecord::class, last24h)).records else emptyList()

                val payload = JSONObject().apply {
                    put("periodHours", 24)
                    put("steps", steps24.sumOf { it.count })
                    put("sleepMinutes", sleep24.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() })
                    put("exerciseMinutes", exercise24.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() })
                    val samples = heart24.flatMap { it.samples }
                    put("averageHeartRate", if (samples.isEmpty()) JSONObject.NULL else samples.map { it.beatsPerMinute }.average().roundToInt())
                    put("hydrationLitres", hydration24.sumOf { it.volume.inLiters })
                    put("distanceKm", distance24.sumOf { it.distance.inKilometers })
                    put("activeCalories", activeCalories24.sumOf { it.energy.inKilocalories })
                    put("oxygenPercent", oxygen24.lastOrNull()?.percentage?.value ?: JSONObject.NULL)
                    put("weightKg", weight.lastOrNull()?.weight?.inKilograms ?: JSONObject.NULL)
                    put("sources", JSONArray(sources))
                    put("syncedAt", now.toString())
                }
                dispatchHealthData(payload)
                dispatchConnectorStatus(sources)
            }.onFailure { error ->
                notifyWeb(false, "sync_error", error.message ?: "Synchronisation impossible")
            }
        }
    }

    private fun buildConnectorPayload(sourcePackages: List<String>): JSONObject {
        fun statusFor(vararg markers: String): String =
            if (sourcePackages.any { pkg -> markers.any { marker -> pkg.contains(marker, ignoreCase = true) } }) "connected"
            else "available_via_health_connect"

        val healthStatus = when (HealthConnectClient.getSdkStatus(this)) {
            HealthConnectClient.SDK_AVAILABLE -> "available"
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "update_required"
            else -> "unavailable"
        }

        return JSONObject().apply {
            put("healthConnect", healthStatus)
            put("connectors", JSONArray().apply {
                put(connector("Samsung Health", statusFor("samsung", "shealth"), "health_connect"))
                put(connector("Mibro Fit", statusFor("mibro", "zhencheng"), "health_connect"))
                put(connector("Google Fit", statusFor("google.android.apps.fitness", "googlefit"), "health_connect"))
                put(connector("Fitbit", statusFor("fitbit"), "health_connect"))
                put(connector("Garmin", statusFor("garmin"), "health_connect_or_oauth"))
                put(connector("Huawei Health", statusFor("huawei", "healthapp"), "health_connect"))
                put(connector("Strava", statusFor("strava"), "health_connect_or_oauth"))
                put(connector("Oura", statusFor("oura"), "health_connect_or_oauth"))
                put(connector("WHOOP", statusFor("whoop"), "oauth_required"))
                put(connector("Withings", statusFor("withings"), "health_connect_or_oauth"))
                put(connector("FitOn", statusFor("fiton"), "health_connect_if_supported"))
                put(connector("Fitify", statusFor("fitify"), "health_connect_if_supported"))
                put(connector("FitCoach", statusFor("fitcoach"), "health_connect_if_supported"))
                put(connector("FlexMe", statusFor("flexme"), "provider_api_required"))
                put(connector("Welmi", statusFor("welmi"), "provider_api_required"))
            })
            put("sourcePackages", JSONArray(sourcePackages))
        }
    }

    private fun connector(name: String, status: String, mode: String) = JSONObject().apply {
        put("name", name)
        put("status", status)
        put("mode", mode)
    }

    private fun dispatchConnectorStatus(sources: List<String>) {
        val payload = buildConnectorPayload(sources)
        runOnUiThread {
            webView.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('vitalis-connectors',{detail:$payload}));",
                null
            )
        }
    }

    private fun dispatchHealthData(payload: JSONObject) {
        runOnUiThread {
            webView.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('vitalis-health-data',{detail:${payload}}));",
                null
            )
        }
    }

    private fun openHealthConnectStore() {
        val packageName = "com.google.android.apps.healthdata"
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))) }
        catch (_: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
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
        private const val LOCAL_ASSET_HOST = "appassets.androidplatform.net"
        private const val LOCAL_URL = "https://$LOCAL_ASSET_HOST/assets/vitalis/index.html"
        private const val VITALIS_HOST = "vitalis-health-os.gillesarnaudasse65.chatgpt.site"
    }
}
