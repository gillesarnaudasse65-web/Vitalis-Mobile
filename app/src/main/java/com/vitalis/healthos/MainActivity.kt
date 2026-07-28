package com.vitalis.healthos

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var loading: ProgressBar
    private lateinit var root: LinearLayout
    private lateinit var assetLoader: WebViewAssetLoader
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var fallbackLoaded = false
    private var remotePageFinished = false
    private var healthConnectClient: HealthConnectClient? = null
    private var lastSourcePackages: List<String> = emptyList()
    private var lastHealthPayload = JSONObject()
    private var textToSpeech: TextToSpeech? = null
    private var textToSpeechReady = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var microphoneEnabled = false

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
        val selected = if (result.resultCode == Activity.RESULT_OK) {
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

    private val microphonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startMicrophoneInternal()
        else dispatchVoiceEvent("microphone", "permission_denied", false)
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
        initializeVoiceServices()

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
            settings.userAgentString = settings.userAgentString + " VitalisAndroid/3.8"
            WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
            addJavascriptInterface(VitalisAndroidBridge(), "VitalisAndroid")
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView,
                    callback: ValueCallback<Array<Uri>>,
                    params: WebChromeClient.FileChooserParams
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
                    val host = requestHost(url)
                    if (host == VITALIS_HOST) remotePageFinished = true
                    if (host == VITALIS_HOST || host == LOCAL_ASSET_HOST) injectClassicCompatibility(view)
                    view.evaluateJavascript(
                        "window.dispatchEvent(new CustomEvent('vitalis-native-ready',{detail:{platform:'android',version:'3.8'}}));",
                        null
                    )
                    readHealthData()
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (!request.isForMainFrame) return
                    if (request.url.host == VITALIS_HOST) loadOfflineFallback()
                    else if (request.url.host == LOCAL_ASSET_HOST) showConnectionError()
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: WebResourceResponse
                ) {
                    if (request.isForMainFrame && request.url.host == VITALIS_HOST && errorResponse.statusCode >= 400) {
                        loadOfflineFallback()
                    }
                }
            }
            loadUrl(VITALIS_URL)
        }
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        Handler(Looper.getMainLooper()).postDelayed({
            if (!remotePageFinished && !fallbackLoaded && ::webView.isInitialized) loadOfflineFallback()
        }, REMOTE_LOAD_TIMEOUT_MS)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    private fun requestHost(url: String): String? = runCatching { Uri.parse(url).host }.getOrNull()

    private fun injectClassicCompatibility(view: WebView) {
        val script = runCatching {
            assets.open("vitalis/compat.js").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return
        view.evaluateJavascript(script, null)
    }

    private fun loadOfflineFallback() {
        if (fallbackLoaded || !::webView.isInitialized) return
        fallbackLoaded = true
        loading.visibility = android.view.View.GONE
        webView.stopLoading()
        webView.loadUrl(LOCAL_URL)
    }

    override fun onDestroy() {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        stopMicrophone()
        speechRecognizer?.destroy()
        speechRecognizer = null
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
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
        fun getConnectorStatus(): String = buildConnectorPayload(lastSourcePackages).toString()

        @JavascriptInterface
        fun getLastHealthData(): String = lastHealthPayload.toString()

        @JavascriptInterface
        fun speakText(text: String, language: String?) {
            runOnUiThread { speakStable(text, language) }
        }

        @JavascriptInterface
        fun stopSpeaking() {
            runOnUiThread {
                textToSpeech?.stop()
                dispatchVoiceEvent("speech", "stopped", false)
            }
        }

        @JavascriptInterface
        fun isSpeaking(): Boolean = textToSpeech?.isSpeaking == true

        @JavascriptInterface
        fun setMicrophoneEnabled(enabled: Boolean) {
            runOnUiThread { if (enabled) startMicrophone() else stopMicrophone() }
        }

        @JavascriptInterface
        fun isMicrophoneEnabled(): Boolean = microphoneEnabled

        @JavascriptInterface
        fun startVoiceInput() {
            runOnUiThread { startMicrophone() }
        }

        @JavascriptInterface
        fun stopVoiceInput() {
            runOnUiThread { stopMicrophone() }
        }

        @JavascriptInterface
        fun openOfflineMode() {
            runOnUiThread { loadOfflineFallback() }
        }

        @JavascriptInterface
        fun openClassicInterface() {
            runOnUiThread {
                fallbackLoaded = false
                remotePageFinished = false
                loading.visibility = android.view.View.VISIBLE
                webView.loadUrl(VITALIS_URL)
            }
        }

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

    private fun initializeVoiceServices() {
        textToSpeech = TextToSpeech(this) { status ->
            textToSpeechReady = status == TextToSpeech.SUCCESS
            if (textToSpeechReady) {
                textToSpeech?.language = Locale.FRENCH
                textToSpeech?.setSpeechRate(0.96f)
                textToSpeech?.setPitch(1.0f)
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        dispatchVoiceEvent("speech", "speaking", true)
                    }
                    override fun onDone(utteranceId: String?) {
                        dispatchVoiceEvent("speech", "complete", false)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        dispatchVoiceEvent("speech", "error", false)
                    }
                })
                dispatchVoiceEvent("speech", "ready", false)
            } else dispatchVoiceEvent("speech", "unavailable", false)
        }
    }

    private fun speakStable(text: String, language: String?) {
        val cleanText = text.trim().take(MAX_SPEECH_TEXT_LENGTH)
        if (cleanText.isEmpty()) return
        if (!textToSpeechReady) {
            dispatchVoiceEvent("speech", "not_ready", false)
            return
        }
        val locale = when {
            language?.startsWith("en", ignoreCase = true) == true -> Locale.ENGLISH
            language?.startsWith("fr", ignoreCase = true) == true -> Locale.FRENCH
            else -> Locale.getDefault()
        }
        val engine = textToSpeech ?: return
        val availability = engine.setLanguage(locale)
        if (availability == TextToSpeech.LANG_MISSING_DATA || availability == TextToSpeech.LANG_NOT_SUPPORTED) engine.language = Locale.FRENCH
        engine.setSpeechRate(0.96f)
        engine.setPitch(1.0f)
        engine.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "vitalis-${System.currentTimeMillis()}")
    }

    private fun startMicrophone() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        startMicrophoneInternal()
    }

    private fun startMicrophoneInternal() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            microphoneEnabled = false
            dispatchVoiceEvent("microphone", "unavailable", false)
            return
        }
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) = dispatchVoiceEvent("microphone", "ready", true)
                    override fun onBeginningOfSpeech() = dispatchVoiceEvent("microphone", "listening", true)
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = dispatchVoiceEvent("microphone", "processing", true)
                    override fun onError(error: Int) {
                        val permanent = error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS || error == SpeechRecognizer.ERROR_CLIENT
                        dispatchVoiceEvent("microphone", "error_$error", microphoneEnabled)
                        if (microphoneEnabled && !permanent) restartMicrophone(900L)
                        else if (permanent) microphoneEnabled = false
                    }
                    override fun onResults(results: Bundle?) {
                        dispatchSpeechResults(results, false)
                        if (microphoneEnabled) restartMicrophone(500L)
                    }
                    override fun onPartialResults(partialResults: Bundle?) = dispatchSpeechResults(partialResults, true)
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
            }
        }
        microphoneEnabled = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        }
        runCatching {
            speechRecognizer?.startListening(intent)
            dispatchVoiceEvent("microphone", "starting", true)
        }.onFailure {
            microphoneEnabled = false
            dispatchVoiceEvent("microphone", "start_failed", false)
        }
    }

    private fun restartMicrophone(delayMillis: Long) {
        Handler(Looper.getMainLooper()).postDelayed({ if (microphoneEnabled) startMicrophoneInternal() }, delayMillis)
    }

    private fun stopMicrophone() {
        microphoneEnabled = false
        runCatching { speechRecognizer?.cancel() }
        dispatchVoiceEvent("microphone", "off", false)
    }

    private fun dispatchSpeechResults(bundle: Bundle?, partial: Boolean) {
        val matches = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        val payload = JSONObject().apply {
            put("text", matches.firstOrNull().orEmpty())
            put("alternatives", JSONArray(matches))
            put("partial", partial)
            put("microphoneEnabled", microphoneEnabled)
        }
        dispatchWebEvent("vitalis-voice-input", payload)
    }

    private fun dispatchVoiceEvent(type: String, status: String, active: Boolean) {
        val payload = JSONObject().apply {
            put("type", type)
            put("status", status)
            put("active", active)
            put("microphoneEnabled", microphoneEnabled)
            put("speaking", textToSpeech?.isSpeaking == true)
        }
        dispatchWebEvent("vitalis-voice-state", payload)
    }

    private fun dispatchWebEvent(name: String, payload: JSONObject) {
        if (!::webView.isInitialized) return
        runOnUiThread {
            webView.evaluateJavascript("window.dispatchEvent(new CustomEvent('${name}',{detail:$payload}));", null)
        }
    }

    private fun readHealthData() {
        val client = healthConnectClient
        if (client == null) {
            dispatchConnectorStatus(emptyList())
            dispatchSyncState("unavailable")
            return
        }
        dispatchSyncState("refreshing")
        lifecycleScope.launch {
            val granted = client.permissionController.getGrantedPermissions()
            if (granted.intersect(healthPermissions).isEmpty()) {
                dispatchConnectorStatus(emptyList())
                dispatchSyncState("permission_required")
                return@launch
            }
            runCatching {
                val now = Instant.now()
                val filter = TimeRangeFilter.between(now.minus(Duration.ofDays(30)), now)
                val steps = if (HealthPermission.getReadPermission(StepsRecord::class) in granted) client.readRecords(ReadRecordsRequest(StepsRecord::class, filter)).records else emptyList()
                val sleep = if (HealthPermission.getReadPermission(SleepSessionRecord::class) in granted) client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, filter)).records else emptyList()
                val exercise = if (HealthPermission.getReadPermission(ExerciseSessionRecord::class) in granted) client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, filter)).records else emptyList()
                val heart = if (HealthPermission.getReadPermission(HeartRateRecord::class) in granted) client.readRecords(ReadRecordsRequest(HeartRateRecord::class, filter)).records else emptyList()
                val hydration = if (HealthPermission.getReadPermission(HydrationRecord::class) in granted) client.readRecords(ReadRecordsRequest(HydrationRecord::class, filter)).records else emptyList()
                val distance = if (HealthPermission.getReadPermission(DistanceRecord::class) in granted) client.readRecords(ReadRecordsRequest(DistanceRecord::class, filter)).records else emptyList()
                val activeCalories = if (HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class) in granted) client.readRecords(ReadRecordsRequest(ActiveCaloriesBurnedRecord::class, filter)).records else emptyList()
                val oxygen = if (HealthPermission.getReadPermission(OxygenSaturationRecord::class) in granted) client.readRecords(ReadRecordsRequest(OxygenSaturationRecord::class, filter)).records else emptyList()
                val weight = if (HealthPermission.getReadPermission(WeightRecord::class) in granted) client.readRecords(ReadRecordsRequest(WeightRecord::class, filter)).records else emptyList()
                val nutrition = if (HealthPermission.getReadPermission(NutritionRecord::class) in granted) client.readRecords(ReadRecordsRequest(NutritionRecord::class, filter)).records else emptyList()
                val sources = (steps.map { it.metadata.dataOrigin.packageName } + sleep.map { it.metadata.dataOrigin.packageName } + exercise.map { it.metadata.dataOrigin.packageName } + heart.map { it.metadata.dataOrigin.packageName } + hydration.map { it.metadata.dataOrigin.packageName } + distance.map { it.metadata.dataOrigin.packageName } + activeCalories.map { it.metadata.dataOrigin.packageName } + oxygen.map { it.metadata.dataOrigin.packageName } + weight.map { it.metadata.dataOrigin.packageName } + nutrition.map { it.metadata.dataOrigin.packageName }).filter { it.isNotBlank() }.distinct()
                val last24h = TimeRangeFilter.between(now.minus(Duration.ofHours(24)), now)
                val steps24 = if (steps.isNotEmpty()) client.readRecords(ReadRecordsRequest(StepsRecord::class, last24h)).records else emptyList()
                val sleep24 = if (sleep.isNotEmpty()) client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, last24h)).records else emptyList()
                val exercise24 = if (exercise.isNotEmpty()) client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, last24h)).records else emptyList()
                val heart24 = if (heart.isNotEmpty()) client.readRecords(ReadRecordsRequest(HeartRateRecord::class, last24h)).records else emptyList()
                val hydration24 = if (hydration.isNotEmpty()) client.readRecords(ReadRecordsRequest(HydrationRecord::class, last24h)).records else emptyList()
                val distance24 = if (distance.isNotEmpty()) client.readRecords(ReadRecordsRequest(DistanceRecord::class, last24h)).records else emptyList()
                val activeCalories24 = if (activeCalories.isNotEmpty()) client.readRecords(ReadRecordsRequest(ActiveCaloriesBurnedRecord::class, last24h)).records else emptyList()
                val oxygen24 = if (oxygen.isNotEmpty()) client.readRecords(ReadRecordsRequest(OxygenSaturationRecord::class, last24h)).records else emptyList()
                val nutrition24 = if (nutrition.isNotEmpty()) client.readRecords(ReadRecordsRequest(NutritionRecord::class, last24h)).records else emptyList()
                val attributions = JSONObject().apply {
                    put("steps", attribution(steps24.map { it.metadata.dataOrigin.packageName to it.endTime }))
                    put("sleepMinutes", attribution(sleep24.map { it.metadata.dataOrigin.packageName to it.endTime }))
                    put("exerciseMinutes", attribution(exercise24.map { it.metadata.dataOrigin.packageName to it.endTime }))
                    put("averageHeartRate", attribution(heart24.map { it.metadata.dataOrigin.packageName to it.endTime }))
                    put("hydrationLitres", attribution(hydration24.map { it.metadata.dataOrigin.packageName to it.time }))
                    put("distanceKm", attribution(distance24.map { it.metadata.dataOrigin.packageName to it.endTime }))
                    put("activeCalories", attribution(activeCalories24.map { it.metadata.dataOrigin.packageName to it.endTime }))
                    put("oxygenPercent", attribution(oxygen24.map { it.metadata.dataOrigin.packageName to it.time }))
                    put("weightKg", attribution(weight.map { it.metadata.dataOrigin.packageName to it.time }))
                    put("nutrition", attribution(nutrition24.map { it.metadata.dataOrigin.packageName to it.endTime }))
                }
                val samples = heart24.flatMap { it.samples }
                val averageHeartRate = if (samples.isEmpty()) null else samples.map { it.beatsPerMinute }.average().roundToInt()
                val nutritionSummary = buildNutritionSummary(nutrition24)
                val details = buildDetailsPayload(steps, sleep, exercise, heart, hydration, distance, activeCalories, oxygen, weight, nutrition)
                val scoreBreakdown = buildScoreBreakdown(
                    steps24.sumOf { it.count },
                    sleep24.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() },
                    exercise24.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() },
                    hydration24.sumOf { it.volume.inLiters },
                    averageHeartRate,
                    nutritionSummary
                )
                val payload = JSONObject().apply {
                    put("periodHours", 24)
                    put("steps", steps24.sumOf { it.count })
                    put("sleepMinutes", sleep24.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() })
                    put("exerciseMinutes", exercise24.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() })
                    put("averageHeartRate", averageHeartRate ?: JSONObject.NULL)
                    put("hydrationLitres", hydration24.sumOf { it.volume.inLiters })
                    put("distanceKm", distance24.sumOf { it.distance.inKilometers })
                    put("activeCalories", activeCalories24.sumOf { it.energy.inKilocalories })
                    put("oxygenPercent", oxygen24.maxByOrNull { it.time }?.percentage?.value ?: JSONObject.NULL)
                    put("weightKg", weight.maxByOrNull { it.time }?.weight?.inKilograms ?: JSONObject.NULL)
                    put("nutrition", nutritionSummary)
                    put("details", details)
                    put("score", scoreBreakdown.getInt("overall"))
                    put("scoreBreakdown", scoreBreakdown)
                    put("attribution", attributions)
                    put("sources", JSONArray(sources))
                    put("connectorCount", sources.size)
                    put("syncedAt", now.toString())
                }
                lastSourcePackages = sources
                lastHealthPayload = payload
                dispatchConnectorStatus(sources)
                dispatchHealthData(payload)
                dispatchSyncState("complete")
            }.onFailure { error ->
                dispatchSyncState("error", error.message)
                notifyWeb(false, "sync_error", error.message ?: "Synchronisation impossible")
            }
        }
    }

    private fun buildNutritionSummary(records: List<NutritionRecord>): JSONObject {
        val goals = JSONObject().apply {
            put("caloriesKcal", 2093.0)
            put("carbohydratesGrams", 183.0)
            put("proteinGrams", 209.0)
            put("fatGrams", 58.0)
            put("fiberGrams", 25.0)
            put("sugarGrams", 25.0)
            put("sodiumMilligrams", 2300.0)
        }
        return JSONObject().apply {
            put("mealCount", records.size)
            put("caloriesKcal", records.sumOf { it.energy?.inKilocalories ?: 0.0 })
            put("carbohydratesGrams", records.sumOf { it.totalCarbohydrate?.inGrams ?: 0.0 })
            put("proteinGrams", records.sumOf { it.protein?.inGrams ?: 0.0 })
            put("fatGrams", records.sumOf { it.totalFat?.inGrams ?: 0.0 })
            put("fiberGrams", records.sumOf { it.dietaryFiber?.inGrams ?: 0.0 })
            put("sugarGrams", records.sumOf { it.sugar?.inGrams ?: 0.0 })
            put("sodiumMilligrams", records.sumOf { (it.sodium?.inGrams ?: 0.0) * 1000.0 })
            put("goals", goals)
        }
    }

    private fun buildDetailsPayload(
        steps: List<StepsRecord>,
        sleep: List<SleepSessionRecord>,
        exercise: List<ExerciseSessionRecord>,
        heart: List<HeartRateRecord>,
        hydration: List<HydrationRecord>,
        distance: List<DistanceRecord>,
        activeCalories: List<ActiveCaloriesBurnedRecord>,
        oxygen: List<OxygenSaturationRecord>,
        weight: List<WeightRecord>,
        nutrition: List<NutritionRecord>
    ): JSONObject = JSONObject().apply {
        put("activity", JSONArray(exercise.sortedByDescending { it.endTime }.take(50).map { record ->
            JSONObject().apply {
                put("title", record.title ?: exerciseTypeLabel(record.exerciseType))
                put("type", exerciseTypeLabel(record.exerciseType))
                put("typeCode", record.exerciseType)
                put("notes", record.notes ?: JSONObject.NULL)
                put("durationMinutes", Duration.between(record.startTime, record.endTime).toMinutes())
                put("startTime", record.startTime.toString())
                put("endTime", record.endTime.toString())
                put("connector", sourceLabel(record.metadata.dataOrigin.packageName))
                put("packageName", record.metadata.dataOrigin.packageName)
            }
        }))
        put("nutrition", JSONArray(nutrition.sortedByDescending { it.endTime }.take(50).map { record ->
            JSONObject().apply {
                put("name", record.name ?: "Repas")
                put("mealType", record.mealType)
                put("caloriesKcal", record.energy?.inKilocalories ?: JSONObject.NULL)
                put("carbohydratesGrams", record.totalCarbohydrate?.inGrams ?: JSONObject.NULL)
                put("proteinGrams", record.protein?.inGrams ?: JSONObject.NULL)
                put("fatGrams", record.totalFat?.inGrams ?: JSONObject.NULL)
                put("saturatedFatGrams", record.saturatedFat?.inGrams ?: JSONObject.NULL)
                put("fiberGrams", record.dietaryFiber?.inGrams ?: JSONObject.NULL)
                put("sugarGrams", record.sugar?.inGrams ?: JSONObject.NULL)
                put("sodiumMilligrams", record.sodium?.inGrams?.times(1000.0) ?: JSONObject.NULL)
                put("startTime", record.startTime.toString())
                put("endTime", record.endTime.toString())
                put("connector", sourceLabel(record.metadata.dataOrigin.packageName))
                put("packageName", record.metadata.dataOrigin.packageName)
            }
        }))
        put("sleep", JSONArray(sleep.sortedByDescending { it.endTime }.take(50).map { record ->
            JSONObject().apply {
                put("durationMinutes", Duration.between(record.startTime, record.endTime).toMinutes())
                put("startTime", record.startTime.toString())
                put("endTime", record.endTime.toString())
                put("connector", sourceLabel(record.metadata.dataOrigin.packageName))
            }
        }))
        put("hydration", JSONArray(hydration.sortedByDescending { it.time }.take(50).map { record ->
            JSONObject().apply {
                put("litres", record.volume.inLiters)
                put("time", record.time.toString())
                put("connector", sourceLabel(record.metadata.dataOrigin.packageName))
            }
        }))
        put("steps", JSONArray(steps.sortedByDescending { it.endTime }.take(50).map { record ->
            JSONObject().apply {
                put("count", record.count)
                put("startTime", record.startTime.toString())
                put("endTime", record.endTime.toString())
                put("connector", sourceLabel(record.metadata.dataOrigin.packageName))
            }
        }))
        put("heartRate", JSONArray(heart.sortedByDescending { it.endTime }.take(50).map { record ->
            val values = record.samples.map { it.beatsPerMinute }
            JSONObject().apply {
                put("averageBpm", if (values.isEmpty()) JSONObject.NULL else values.average().roundToInt())
                put("minimumBpm", values.minOrNull() ?: JSONObject.NULL)
                put("maximumBpm", values.maxOrNull() ?: JSONObject.NULL)
                put("sampleCount", values.size)
                put("startTime", record.startTime.toString())
                put("endTime", record.endTime.toString())
                put("connector", sourceLabel(record.metadata.dataOrigin.packageName))
            }
        }))
        put("distance", JSONArray(distance.sortedByDescending { it.endTime }.take(50).map { record ->
            JSONObject().apply {
                put("kilometres", record.distance.inKilometers)
                put("startTime", record.startTime.toString())
                put("endTime", record.endTime.toString())
                put("connector", sourceLabel(record.metadata.dataOrigin.packageName))
            }
        }))
        put("activeCalories", JSONArray(activeCalories.sortedByDescending { it.endTime }.take(50).map { record ->
            JSONObject().apply {
                put("kilocalories", record.energy.inKilocalories)
                put("startTime", record.startTime.toString())
                put("endTime", record.endTime.toString())
                put("connector", sourceLabel(record.metadata.dataOrigin.packageName))
            }
        }))
        put("oxygen", JSONArray(oxygen.sortedByDescending { it.time }.take(50).map { record ->
            JSONObject().apply {
                put("percentage", record.percentage.value)
                put("time", record.time.toString())
                put("connector", sourceLabel(record.metadata.dataOrigin.packageName))
            }
        }))
        put("weight", JSONArray(weight.sortedByDescending { it.time }.take(50).map { record ->
            JSONObject().apply {
                put("kilograms", record.weight.inKilograms)
                put("time", record.time.toString())
                put("connector", sourceLabel(record.metadata.dataOrigin.packageName))
            }
        }))
    }

    private fun buildScoreBreakdown(
        steps: Long,
        sleepMinutes: Long,
        exerciseMinutes: Long,
        hydrationLitres: Double,
        averageHeartRate: Int?,
        nutrition: JSONObject
    ): JSONObject {
        val components = JSONArray()
        fun add(key: String, label: String, score: Int, available: Boolean, current: String, target: String, explanation: String) {
            components.put(JSONObject().apply {
                put("key", key)
                put("label", label)
                put("earnedPoints", if (available) (score.coerceIn(0, 100) / 5.0).roundToInt() else 0)
                put("maxPoints", 20)
                put("percentage", if (available) score.coerceIn(0, 100) else 0)
                put("available", available)
                put("current", current)
                put("target", target)
                put("explanation", explanation)
            })
        }
        val activityAvailable = steps > 0 || exerciseMinutes > 0
        val activityScore = (((steps / 8000.0).coerceAtMost(1.0) + (exerciseMinutes / 30.0).coerceAtMost(1.0)) * 50.0).roundToInt()
        add("activity", "Activité", activityScore, activityAvailable, "$steps pas • $exerciseMinutes min", "8 000 pas • 30 min", "Combine les pas et les minutes d’activité des dernières 24 heures.")
        val sleepHours = sleepMinutes / 60.0
        add("sleep", "Sommeil", ((sleepHours / 8.0).coerceAtMost(1.0) * 100).roundToInt(), sleepMinutes > 0, "%.1f h".format(Locale.US, sleepHours), "8 h", "Évalue la durée de sommeil disponible dans Health Connect.")
        add("hydration", "Hydratation", ((hydrationLitres / 2.5).coerceAtMost(1.0) * 100).roundToInt(), hydrationLitres > 0, "%.2f L".format(Locale.US, hydrationLitres), "2,5 L", "Compare l’eau enregistrée à l’objectif quotidien.")
        val mealCount = nutrition.optInt("mealCount")
        val goals = nutrition.optJSONObject("goals") ?: JSONObject()
        fun ratio(valueKey: String): Double {
            val goal = goals.optDouble(valueKey, 0.0)
            return if (goal > 0) (nutrition.optDouble(valueKey, 0.0) / goal).coerceAtMost(1.0) else 0.0
        }
        val nutritionScore = ((ratio("carbohydratesGrams") + ratio("proteinGrams") + ratio("fatGrams") + ratio("fiberGrams")) * 25.0).roundToInt()
        val nutritionCurrent = mealCount.toString() + " repas • " + nutrition.optDouble("caloriesKcal", 0.0).roundToInt() + " kcal"
        add("nutrition", "Nutrition", nutritionScore, mealCount > 0, nutritionCurrent, "Objectifs nutritionnels", "Analyse les macronutriments et les repas transmis par les connecteurs.")
        val recoveryAvailable = averageHeartRate != null
        val recoveryScore = if (averageHeartRate == null) 0 else when (averageHeartRate) {
            in 50..90 -> 100
            in 40..110 -> 75
            else -> 45
        }
        add("recovery", "Récupération", recoveryScore, recoveryAvailable, averageHeartRate?.let { "$it bpm" } ?: "—", "Zone personnelle", "Indicateur de bien-être basé sur la fréquence cardiaque disponible, sans valeur diagnostique.")
        var total = 0
        for (index in 0 until components.length()) total += components.getJSONObject(index).getInt("earnedPoints")
        return JSONObject().apply {
            put("overall", total.coerceIn(0, 100))
            put("maximum", 100)
            put("components", components)
            put("method", "5 catégories de 20 points : activité, sommeil, hydratation, nutrition et récupération.")
            put("medicalDisclaimer", "Score de bien-être informatif, non diagnostique.")
        }
    }

    private fun exerciseTypeLabel(type: Int): String = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Marche"
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Course"
        ExerciseSessionRecord.EXERCISE_TYPE_SOCCER -> "Football"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Vélo"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "Natation"
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "Renforcement"
        ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "Musculation"
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Yoga"
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Randonnée"
        else -> "Autre activité"
    }

    private fun attribution(records: List<Pair<String, Instant>>): JSONObject {
        val valid = records.filter { it.first.isNotBlank() }
        val latest = valid.maxByOrNull { it.second }
        val packages = valid.map { it.first }.distinct()
        return JSONObject().apply {
            put("lastConnector", latest?.first?.let(::sourceLabel) ?: JSONObject.NULL)
            put("lastPackage", latest?.first ?: JSONObject.NULL)
            put("lastRecordAt", latest?.second?.toString() ?: JSONObject.NULL)
            put("contributors", JSONArray(packages.map(::sourceLabel)))
            put("contributorPackages", JSONArray(packages))
        }
    }

    @Suppress("DEPRECATION")
    private fun sourceLabel(packageName: String): String = runCatching {
        val info = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(info).toString()
    }.getOrElse { packageName }

    private fun buildConnectorPayload(sourcePackages: List<String>): JSONObject {
        val healthStatus = when (HealthConnectClient.getSdkStatus(this)) {
            HealthConnectClient.SDK_AVAILABLE -> "available"
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "update_required"
            else -> "unavailable"
        }
        val packages = sourcePackages.filter { it.isNotBlank() }.distinct()
        return JSONObject().apply {
            put("healthConnect", healthStatus)
            put("unlimitedDiscovery", true)
            put("connectorCount", packages.size)
            put("connectors", JSONArray().apply {
                packages.forEach { packageName -> put(connector(sourceLabel(packageName), "connected", "health_connect", packageName)) }
            })
            put("sourcePackages", JSONArray(packages))
        }
    }

    private fun connector(name: String, status: String, mode: String, packageName: String) = JSONObject().apply {
        put("name", name)
        put("status", status)
        put("mode", mode)
        put("packageName", packageName)
    }

    private fun dispatchSyncState(status: String, message: String? = null) {
        val payload = JSONObject().apply {
            put("status", status)
            put("message", message ?: JSONObject.NULL)
            put("syncedAt", if (status == "complete") Instant.now().toString() else JSONObject.NULL)
        }
        dispatchWebEvent("vitalis-sync-state", payload)
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
        private const val VITALIS_URL = "https://$VITALIS_HOST/"
        private const val REMOTE_LOAD_TIMEOUT_MS = 12_000L
        private const val MAX_SPEECH_TEXT_LENGTH = 8_000
    }
}
