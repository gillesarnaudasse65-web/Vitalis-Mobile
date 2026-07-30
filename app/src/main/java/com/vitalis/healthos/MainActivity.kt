package com.vitalis.healthos

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var loading: ProgressBar
    private lateinit var root: LinearLayout
    private lateinit var assetLoader: WebViewAssetLoader
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var fallbackLoaded = false
    private var remotePageFinished = false
    private var remoteRetryCount = 0
    private var healthConnectClient: HealthConnectClient? = null
    private var lastSourcePackages: List<String> = emptyList()
    private var lastHealthPayload = JSONObject()
    private var textToSpeech: TextToSpeech? = null
    private var textToSpeechReady = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var microphoneEnabled = false
    private var selectedHealthDate: LocalDate = LocalDate.now()
    private var pendingConnectorId: String? = null

    private data class ConnectorDefinition(
        val id: String,
        val name: String,
        val packages: List<String>,
        val mode: String,
        val note: String
    )

    private val connectorCatalog = listOf(
        ConnectorDefinition("health_connect", "Health Connect", listOf("com.google.android.apps.healthdata"), "health_connect", "Autorisation Android native et centralisation des données."),
        ConnectorDefinition("samsung_health", "Samsung Health", listOf("com.sec.android.app.shealth"), "health_connect", "Activez le partage Health Connect dans Samsung Health."),
        ConnectorDefinition("google_fit", "Google Fit", listOf("com.google.android.apps.fitness"), "health_connect", "Utilisez Health Connect lorsque Google Fit le propose."),
        ConnectorDefinition("mibro_fit", "Mibro Fit", listOf("com.xiaoxun.xunoversea", "com.zhencheng.mibrofit"), "bridge", "Le partage dépend de Mibro Fit, Google Fit ou Health Connect."),
        ConnectorDefinition("fitbit", "Fitbit", listOf("com.fitbit.FitbitMobile"), "health_connect", "Activez Health Connect depuis les réglages Fitbit."),
        ConnectorDefinition("garmin", "Garmin Connect", listOf("com.garmin.android.apps.connectmobile"), "provider", "Une autorisation Garmin officielle est requise si aucune donnée Health Connect n’est publiée."),
        ConnectorDefinition("huawei", "Huawei Health", listOf("com.huawei.health"), "provider", "Une autorisation Huawei officielle est requise si aucune donnée Health Connect n’est publiée."),
        ConnectorDefinition("strava", "Strava", listOf("com.strava"), "provider", "La connexion complète nécessite l’autorisation OAuth Strava."),
        ConnectorDefinition("oura", "Oura", listOf("com.ouraring.oura"), "provider", "La connexion complète nécessite l’autorisation officielle Oura."),
        ConnectorDefinition("whoop", "WHOOP", listOf("com.whoop.android"), "provider", "La connexion complète nécessite l’autorisation officielle WHOOP."),
        ConnectorDefinition("withings", "Withings", listOf("com.withings.wiscale2"), "health_connect", "Activez Health Connect dans Withings lorsque disponible."),
        ConnectorDefinition("myfitnesspal", "MyFitnessPal", listOf("com.myfitnesspal.android"), "provider", "L’accès nutritionnel dépend des autorisations du fournisseur."),
        ConnectorDefinition("yazio", "YAZIO", listOf("com.yazio.android"), "provider", "L’accès nutritionnel dépend des autorisations du fournisseur."),
        ConnectorDefinition("cronometer", "Cronometer", listOf("com.cronometer.android.gold"), "provider", "L’accès nutritionnel dépend des autorisations du fournisseur."),
        ConnectorDefinition("lifesum", "Lifesum", listOf("com.sillens.shapeupclub"), "provider", "L’accès nutritionnel dépend des autorisations du fournisseur."),
        ConnectorDefinition("suunto", "Suunto", listOf("com.stt.android.suunto"), "provider", "Une autorisation Suunto officielle peut être nécessaire."),
        ConnectorDefinition("coros", "COROS", listOf("com.yf.smart.coros.dist"), "provider", "Une autorisation COROS officielle peut être nécessaire.")
    )

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
        pendingConnectorId?.also { connectorId ->
            pendingConnectorId = null
            connectorCatalog.firstOrNull { it.id == connectorId }?.let(::openInstalledConnector)
        }
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
            settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            settings.userAgentString = settings.userAgentString + " VitalisAndroid/3.12"
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
                    if (host == VITALIS_HOST) {
                        remotePageFinished = true
                        remoteRetryCount = 0
                    }
                    if (host == VITALIS_HOST || host == LOCAL_ASSET_HOST) injectClassicCompatibility(view)
                    view.evaluateJavascript(
                        "window.dispatchEvent(new CustomEvent('vitalis-native-ready',{detail:{platform:'android',version:'3.12'}}));",
                        null
                    )
                    readHealthData()
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (!request.isForMainFrame) return
                    if (request.url.host == VITALIS_HOST) retryClassicInterfaceOrFallback()
                    else if (request.url.host == LOCAL_ASSET_HOST) showConnectionError()
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: WebResourceResponse
                ) {
                    if (request.isForMainFrame && request.url.host == VITALIS_HOST && errorResponse.statusCode >= 400) {
                        retryClassicInterfaceOrFallback()
                    }
                }
            }
            loadUrl(VITALIS_URL)
        }
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        scheduleClassicInterfaceTimeout()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    private fun requestHost(url: String): String? = runCatching { Uri.parse(url).host }.getOrNull()

    private fun injectClassicCompatibility(view: WebView) {
        val script = runCatching {
            listOf("vitalis/compat.js", "vitalis/vitalis-3.12.js").joinToString("\n;\n") { asset ->
                assets.open(asset).bufferedReader().use { it.readText() }
            }
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

    private fun scheduleClassicInterfaceTimeout() {
        Handler(Looper.getMainLooper()).postDelayed({
            if (!remotePageFinished && !fallbackLoaded && ::webView.isInitialized) {
                retryClassicInterfaceOrFallback()
            }
        }, REMOTE_LOAD_TIMEOUT_MS)
    }

    private fun retryClassicInterfaceOrFallback() {
        if (remotePageFinished || fallbackLoaded || !::webView.isInitialized) return
        if (remoteRetryCount < MAX_REMOTE_RETRIES) {
            remoteRetryCount += 1
            Handler(Looper.getMainLooper()).postDelayed({
                if (!remotePageFinished && !fallbackLoaded && ::webView.isInitialized) {
                    loading.visibility = android.view.View.VISIBLE
                    webView.stopLoading()
                    webView.loadUrl(VITALIS_URL)
                    scheduleClassicInterfaceTimeout()
                }
            }, REMOTE_RETRY_DELAY_MS)
        } else {
            loadOfflineFallback()
        }
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
        fun refreshHealthDataForDate(dateIso: String) {
            val requestedDate = runCatching { LocalDate.parse(dateIso.trim()) }.getOrNull()
            if (requestedDate == null) {
                dispatchSyncState("error", "Date invalide : $dateIso")
                return
            }
            readHealthData(requestedDate)
        }

        @JavascriptInterface
        fun getConnectorStatus(): String = buildConnectorPayload(lastSourcePackages).toString()

        @JavascriptInterface
        fun authorizeConnector(connectorId: String) {
            runOnUiThread { handleConnectorAuthorization(connectorId.trim()) }
        }

        @JavascriptInterface
        fun getLastHealthData(): String = lastHealthPayload.toString()

        @JavascriptInterface
        fun hasOpenAiKey(): Boolean = readOpenAiKey() != null

        @JavascriptInterface
        fun saveOpenAiKey(apiKey: String): Boolean {
            val cleanKey = apiKey.trim()
            if (!cleanKey.startsWith("sk-") || cleanKey.length < 30) return false
            return runCatching {
                writeEncryptedSecret(OPENAI_SECRET_NAME, cleanKey)
                true
            }.getOrDefault(false)
        }

        @JavascriptInterface
        fun clearOpenAiKey() {
            getSharedPreferences(SECURE_PREFS, MODE_PRIVATE)
                .edit()
                .remove(OPENAI_SECRET_NAME)
                .apply()
        }

        @JavascriptInterface
        fun hasDeveloperAiKey(): Boolean = readDeveloperOpenAiKey() != null

        @JavascriptInterface
        fun saveDeveloperAiKey(apiKey: String): Boolean {
            val cleanKey = apiKey.trim()
            if (!cleanKey.startsWith("sk-") || cleanKey.length < 30) return false
            return runCatching {
                writeEncryptedSecret(OPENAI_DEVELOPER_SECRET_NAME, cleanKey)
                true
            }.getOrDefault(false)
        }

        @JavascriptInterface
        fun clearDeveloperAiKey() {
            getSharedPreferences(SECURE_PREFS, MODE_PRIVATE)
                .edit()
                .remove(OPENAI_DEVELOPER_SECRET_NAME)
                .apply()
        }

        @JavascriptInterface
        fun hasAiHealthConsent(): Boolean =
            getSharedPreferences(APP_PREFS, MODE_PRIVATE).getBoolean(AI_HEALTH_CONSENT, false)

        @JavascriptInterface
        fun setAiHealthConsent(consented: Boolean) {
            getSharedPreferences(APP_PREFS, MODE_PRIVATE)
                .edit()
                .putBoolean(AI_HEALTH_CONSENT, consented)
                .apply()
        }

        @JavascriptInterface
        fun askKofi(prompt: String, requestId: String) {
            requestCoach(prompt, "general", requestId, null)
        }

        @JavascriptInterface
        fun askCoach(prompt: String, coachId: String, requestId: String) {
            requestCoach(prompt, coachId, requestId, null)
        }

        @JavascriptInterface
        fun askDeveloper(prompt: String, requestId: String) {
            requestDeveloper(prompt, requestId)
        }

        @JavascriptInterface
        fun analyzeMealImage(imageDataUrl: String, requestId: String) {
            requestCoach(
                "Analyse cette photo de repas et réponds uniquement avec un objet JSON valide, sans balises Markdown, " +
                    "contenant exactement : name (texte), foods (tableau de textes), caloriesKcal, carbohydratesGrams, " +
                    "proteinGrams, fatGrams, fiberGrams, sugarGrams, sodiumMilligrams (nombres positifs), confidence " +
                    "(nombre de 0 à 1), summary (texte court) et improvement (texte court). Donne une estimation prudente " +
                    "et mets 0 lorsqu’une valeur ne peut pas être estimée.",
                "nutrition",
                requestId,
                imageDataUrl
            )
        }

        @JavascriptInterface
        fun saveMealEstimate(estimateJson: String): Boolean =
            saveManualMealEstimate(estimateJson)

        @JavascriptInterface
        fun sendDeveloperRequestToChatGpt(request: String) {
            val cleanRequest = request.trim().take(MAX_DEVELOPER_PROMPT_LENGTH)
            if (cleanRequest.isEmpty()) return
            runOnUiThread {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Demande Vitalis", cleanRequest))
                runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(CHATGPT_WORK_URL)))
                }
            }
        }

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
                remoteRetryCount = 0
                loading.visibility = android.view.View.VISIBLE
                webView.loadUrl(VITALIS_URL)
                scheduleClassicInterfaceTimeout()
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

    private fun requestCoach(
        prompt: String,
        coachId: String,
        requestId: String,
        imageDataUrl: String?
    ) {
        val cleanPrompt = prompt.trim().take(MAX_AI_PROMPT_LENGTH)
        if (cleanPrompt.isEmpty()) {
            dispatchAiResponse(requestId, false, "", "Question vide.", coachId)
            return
        }
        val apiKey = readOpenAiKey()
        if (apiKey == null) {
            dispatchAiResponse(requestId, false, "", "Clé OpenAI non configurée.", coachId)
            return
        }
        val consented = getSharedPreferences(APP_PREFS, MODE_PRIVATE)
            .getBoolean(AI_HEALTH_CONSENT, false)
        if (!consented) {
            dispatchAiResponse(
                requestId,
                false,
                "",
                "Consentement requis avant l’analyse des données santé.",
                coachId
            )
            return
        }
        requestAi(
            apiKey = apiKey,
            instructions = coachInstructions(coachId),
            prompt = cleanPrompt,
            requestId = requestId,
            imageDataUrl = imageDataUrl,
            agentId = coachId,
            includeHealthContext = true
        )
    }

    private fun requestDeveloper(prompt: String, requestId: String) {
        val cleanPrompt = prompt.trim().take(MAX_DEVELOPER_PROMPT_LENGTH)
        if (cleanPrompt.isEmpty()) {
            dispatchAiResponse(requestId, false, "", "Demande vide.", "developer")
            return
        }
        val apiKey = readDeveloperOpenAiKey()
        if (apiKey == null) {
            dispatchAiResponse(
                requestId,
                false,
                "",
                "Clé « Vitalis Developer AI » non configurée sur cet appareil.",
                "developer"
            )
            return
        }
        requestAi(
            apiKey = apiKey,
            instructions = DEVELOPER_INSTRUCTIONS,
            prompt = cleanPrompt,
            requestId = requestId,
            imageDataUrl = null,
            agentId = "developer",
            includeHealthContext = false
        )
    }

    private fun requestAi(
        apiKey: String,
        instructions: String,
        prompt: String,
        requestId: String,
        imageDataUrl: String?,
        agentId: String,
        includeHealthContext: Boolean
    ) {
        val context = if (includeHealthContext) {
            "\n\nDonnées Vitalis disponibles (peuvent être incomplètes) : ${sanitizedHealthContext()}"
        } else {
            "\n\nContexte technique : application Android Vitalis Mobile ${BuildConfig.VERSION_NAME}; " +
                "interface classique à préserver; dépôt gillesarnaudasse65-web/Vitalis-Mobile; " +
                "Health Connect natif; les changements réels exigent validation, modification du dépôt, tests et build GitHub Actions."
        }
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val inputContent = JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "input_text")
                        put(
                            "text",
                            "Demande de l’utilisateur : $prompt$context"
                        )
                    })
                    if (!imageDataUrl.isNullOrBlank() && imageDataUrl.startsWith("data:image/")) {
                        put(JSONObject().apply {
                            put("type", "input_image")
                            put("image_url", imageDataUrl.take(MAX_IMAGE_DATA_URL_LENGTH))
                            put("detail", "low")
                        })
                    }
                }
                val body = JSONObject().apply {
                    put("model", OPENAI_MODEL)
                    put("max_output_tokens", 900)
                    put("store", false)
                    put("instructions", instructions)
                    put("input", JSONArray().put(JSONObject().apply {
                        put("role", "user")
                        put("content", inputContent)
                    }))
                }
                val response = postOpenAi(apiKey, body)
                extractResponseText(response)
            }.onSuccess { answer ->
                dispatchAiResponse(requestId, true, answer, null, agentId)
            }.onFailure { error ->
                dispatchAiResponse(
                    requestId,
                    false,
                    "",
                    error.message?.take(300) ?: "Le service IA est momentanément indisponible.",
                    agentId
                )
            }
        }
    }

    private fun postOpenAi(apiKey: String, body: JSONObject): JSONObject {
        val connection = (URL(OPENAI_RESPONSES_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 75_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            connection.outputStream.use { stream ->
                stream.write(body.toString().toByteArray(StandardCharsets.UTF_8))
            }
            val status = connection.responseCode
            val source = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = source?.use { stream ->
                BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).readText()
            }.orEmpty()
            if (status !in 200..299) {
                val apiMessage = runCatching {
                    JSONObject(responseText).optJSONObject("error")?.optString("message")
                }.getOrNull()
                throw IllegalStateException(apiMessage?.takeIf { it.isNotBlank() } ?: "Erreur OpenAI HTTP $status")
            }
            return JSONObject(responseText)
        } finally {
            connection.disconnect()
        }
    }

    private fun extractResponseText(response: JSONObject): String {
        val output = response.optJSONArray("output") ?: JSONArray()
        val parts = mutableListOf<String>()
        for (index in 0 until output.length()) {
            val content = output.optJSONObject(index)?.optJSONArray("content") ?: continue
            for (contentIndex in 0 until content.length()) {
                val item = content.optJSONObject(contentIndex) ?: continue
                if (item.optString("type") == "output_text") {
                    item.optString("text").takeIf { it.isNotBlank() }?.let(parts::add)
                }
            }
        }
        return parts.joinToString("\n").trim()
            .takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("La réponse IA reçue est vide.")
    }

    private fun dispatchAiResponse(
        requestId: String,
        ok: Boolean,
        text: String,
        error: String?,
        agentId: String
    ) {
        dispatchWebEvent("vitalis-ai-response", JSONObject().apply {
            put("requestId", requestId)
            put("ok", ok)
            put("text", text)
            put("error", error ?: JSONObject.NULL)
            put("agentId", agentId)
            put("model", if (ok) OPENAI_MODEL else JSONObject.NULL)
        })
    }

    private fun sanitizedHealthContext(): JSONObject {
        val source = lastHealthPayload
        return JSONObject().apply {
            listOf(
                "periodHours", "steps", "sleepMinutes", "exerciseMinutes", "averageHeartRate",
                "hydrationLitres", "distanceKm", "activeCalories", "oxygenPercent", "weightKg",
                "nutrition", "score", "scoreBreakdown", "attribution", "connectorCount", "syncedAt"
            ).forEach { key ->
                if (source.has(key)) put(key, source.opt(key))
            }
        }
    }

    private fun readOpenAiKey(): String? =
        runCatching { readEncryptedSecret(OPENAI_SECRET_NAME) }
            .getOrNull()
            ?.takeIf { it.startsWith("sk-") && it.length >= 30 }

    private fun readDeveloperOpenAiKey(): String? =
        runCatching { readEncryptedSecret(OPENAI_DEVELOPER_SECRET_NAME) }
            .getOrNull()
            ?.takeIf { it.startsWith("sk-") && it.length >= 30 }

    private fun coachInstructions(coachId: String): String {
        val identity = when (coachId.trim().lowercase(Locale.ROOT)) {
            "nutrition" -> "Tu es Ama, coach nutrition. Concentre-toi sur les repas, macronutriments, portions, habitudes et objectifs réalistes."
            "activity" -> "Tu es Ayo, coach activité. Concentre-toi sur les pas, séances, progression, charge et programme sportif adapté."
            "sleep" -> "Tu es Nia, coach sommeil. Concentre-toi sur la durée, la régularité, l’hygiène du sommeil et la récupération nocturne."
            "recovery" -> "Tu es Sékou, coach récupération. Concentre-toi sur la récupération, la fréquence cardiaque, l’hydratation et la charge d’activité."
            "mental" -> "Tu es Zuri, coach bien-être mental. Concentre-toi sur le stress, la respiration, les habitudes et l’équilibre quotidien."
            else -> "Tu es Kofi, coach santé global. Fais la synthèse des données Vitalis et priorise les actions à plus fort impact."
        }
        return "$identity $COACH_SAFETY_INSTRUCTIONS"
    }

    private fun writeEncryptedSecret(name: String, value: String) {
        val cipher = Cipher.getInstance(KEYSTORE_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val encoded = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        getSharedPreferences(SECURE_PREFS, MODE_PRIVATE).edit().putString(name, encoded).apply()
    }

    private fun readEncryptedSecret(name: String): String? {
        val encoded = getSharedPreferences(SECURE_PREFS, MODE_PRIVATE).getString(name, null) ?: return null
        val separator = encoded.indexOf(':')
        if (separator <= 0 || separator >= encoded.lastIndex) return null
        val iv = Base64.decode(encoded.substring(0, separator), Base64.NO_WRAP)
        val encrypted = Base64.decode(encoded.substring(separator + 1), Base64.NO_WRAP)
        val cipher = Cipher.getInstance(KEYSTORE_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generateKey()
        }
    }

    private fun saveManualMealEstimate(rawJson: String): Boolean {
        val parsed = runCatching { JSONObject(rawJson.take(MAX_MEAL_ESTIMATE_JSON_LENGTH)) }.getOrNull()
            ?: return false
        val name = parsed.optString("name").trim().take(120).ifBlank { "Repas analysé" }
        val selectedDate = parsed.optString("selectedDate")
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: selectedHealthDate
        val record = JSONObject().apply {
            put("id", "scanner-${System.currentTimeMillis()}")
            put("name", name)
            put("selectedDate", selectedDate.toString())
            put("recordedAt", Instant.now().toString())
            put("caloriesKcal", nonNegativeNumber(parsed, "caloriesKcal"))
            put("carbohydratesGrams", nonNegativeNumber(parsed, "carbohydratesGrams"))
            put("proteinGrams", nonNegativeNumber(parsed, "proteinGrams"))
            put("fatGrams", nonNegativeNumber(parsed, "fatGrams"))
            put("fiberGrams", nonNegativeNumber(parsed, "fiberGrams"))
            put("sugarGrams", nonNegativeNumber(parsed, "sugarGrams"))
            put("sodiumMilligrams", nonNegativeNumber(parsed, "sodiumMilligrams"))
            put("confidence", parsed.optDouble("confidence", 0.0).coerceIn(0.0, 1.0))
            put("summary", parsed.optString("summary").trim().take(500))
            put("improvement", parsed.optString("improvement").trim().take(500))
            put("source", "Vitalis Scanner")
        }
        return runCatching {
            val preferences = getSharedPreferences(APP_PREFS, MODE_PRIVATE)
            val existing = runCatching {
                JSONArray(preferences.getString(MANUAL_MEALS_KEY, "[]"))
            }.getOrDefault(JSONArray())
            val updated = JSONArray()
            val first = (existing.length() - MAX_MANUAL_MEALS + 1).coerceAtLeast(0)
            for (index in first until existing.length()) updated.put(existing.optJSONObject(index))
            updated.put(record)
            preferences.edit().putString(MANUAL_MEALS_KEY, updated.toString()).apply()
            selectedHealthDate = selectedDate
            readHealthData(selectedDate)
            true
        }.getOrDefault(false)
    }

    private fun nonNegativeNumber(source: JSONObject, key: String): Double {
        val value = source.optDouble(key, 0.0)
        return if (value.isFinite()) value.coerceAtLeast(0.0) else 0.0
    }

    private fun manualMealsForDate(date: LocalDate): List<JSONObject> {
        val raw = getSharedPreferences(APP_PREFS, MODE_PRIVATE).getString(MANUAL_MEALS_KEY, "[]")
        val records = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return buildList {
            for (index in 0 until records.length()) {
                val item = records.optJSONObject(index) ?: continue
                if (item.optString("selectedDate") == date.toString()) add(item)
            }
        }
    }

    private fun handleConnectorAuthorization(connectorId: String) {
        val definition = connectorCatalog.firstOrNull { it.id == connectorId }
        if (definition == null || definition.id == "health_connect") {
            when (HealthConnectClient.getSdkStatus(this)) {
                HealthConnectClient.SDK_AVAILABLE -> permissionLauncher.launch(healthPermissions)
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> openHealthConnectStore()
                else -> notifyWeb(false, "unavailable", "Health Connect n’est pas disponible sur cet appareil.")
            }
            return
        }
        if (definition.mode == "health_connect" || definition.mode == "bridge") {
            when (HealthConnectClient.getSdkStatus(this)) {
                HealthConnectClient.SDK_AVAILABLE -> {
                    pendingConnectorId = definition.id
                    permissionLauncher.launch(healthPermissions)
                }
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> openHealthConnectStore()
                else -> notifyWeb(false, "unavailable", "Health Connect n’est pas disponible sur cet appareil.")
            }
            return
        }
        openInstalledConnector(definition)
    }

    private fun openInstalledConnector(definition: ConnectorDefinition) {
        val packageName = definition.packages.firstOrNull(::isPackageInstalled)
        if (packageName != null) {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                startActivity(launchIntent)
                notifyWeb(
                    true,
                    "connector_app_opened",
                    if (definition.mode == "health_connect" || definition.mode == "bridge")
                        "${definition.name} est ouvert. Activez son partage vers Health Connect, puis revenez dans Vitalis et actualisez."
                    else
                        "${definition.name} est ouvert. L’accès direct exige l’autorisation officielle proposée par ce fournisseur."
                )
                return
            }
        }
        openConnectorStore(definition.name)
        notifyWeb(
            false,
            "connector_not_installed",
            "${definition.name} n’a pas été détecté. La page d’installation est ouverte."
        )
    }

    @Suppress("DEPRECATION")
    private fun isPackageInstalled(packageName: String): Boolean = runCatching {
        packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)

    private fun openConnectorStore(name: String) {
        val query = Uri.encode(name)
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$query&c=apps")))
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=$query&c=apps")))
        }
    }

    private fun dispatchManualOnlyHealthData(selectedDate: LocalDate, syncStatus: String) {
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val rangeStart = selectedDate.atStartOfDay(zone).toInstant()
        val nextDayStart = selectedDate.plusDays(1).atStartOfDay(zone).toInstant()
        val rangeEnd = if (selectedDate == today && now.isBefore(nextDayStart)) now else nextDayStart
        val manualMeals = manualMealsForDate(selectedDate)
        val scannerPackage = applicationContext.packageName
        val nutritionSummary = buildNutritionSummary(emptyList(), manualMeals)
        val details = buildDetailsPayload(
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            manualMeals
        )
        val scoreBreakdown = buildScoreBreakdown(
            steps = 0,
            sleepMinutes = 0,
            exerciseMinutes = 0,
            hydrationLitres = 0.0,
            averageHeartRate = null,
            nutrition = nutritionSummary
        )
        val sources = if (manualMeals.isEmpty()) emptyList() else listOf(scannerPackage)
        val manualTimes = manualMeals.mapNotNull {
            runCatching { Instant.parse(it.optString("recordedAt")) }.getOrNull()
        }
        val attributions = JSONObject().apply {
            put("steps", attribution(emptyList()))
            put("sleepMinutes", attribution(emptyList()))
            put("exerciseMinutes", attribution(emptyList()))
            put("averageHeartRate", attribution(emptyList()))
            put("hydrationLitres", attribution(emptyList()))
            put("distanceKm", attribution(emptyList()))
            put("activeCalories", attribution(emptyList()))
            put("oxygenPercent", attribution(emptyList()))
            put("weightKg", attribution(emptyList()))
            put("nutrition", attribution(manualTimes.map { scannerPackage to it }))
        }
        val payload = JSONObject().apply {
            put("periodHours", 24)
            put("selectedDate", selectedDate.toString())
            put("rangeStart", rangeStart.toString())
            put("rangeEnd", rangeEnd.toString())
            put("steps", 0)
            put("sleepMinutes", 0)
            put("exerciseMinutes", 0)
            put("averageHeartRate", JSONObject.NULL)
            put("hydrationLitres", 0.0)
            put("distanceKm", 0.0)
            put("activeCalories", 0.0)
            put("oxygenPercent", JSONObject.NULL)
            put("weightKg", JSONObject.NULL)
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
        dispatchSyncState(syncStatus)
    }

    private fun readHealthData(selectedDate: LocalDate = LocalDate.now()) {
        selectedHealthDate = selectedDate
        val client = healthConnectClient
        if (client == null) {
            dispatchManualOnlyHealthData(selectedDate, "unavailable")
            return
        }
        dispatchSyncState("refreshing")
        lifecycleScope.launch {
            val granted = client.permissionController.getGrantedPermissions()
            val hasAnyHealthPermission = granted.intersect(healthPermissions).isNotEmpty()
            if (!hasAnyHealthPermission) {
                dispatchConnectorStatus(emptyList())
            }
            runCatching {
                val now = Instant.now()
                val zone = ZoneId.systemDefault()
                val today = LocalDate.now(zone)
                val rangeStart = selectedDate.atStartOfDay(zone).toInstant()
                val nextDayStart = selectedDate.plusDays(1).atStartOfDay(zone).toInstant()
                val rangeEnd = if (selectedDate == today && now.isBefore(nextDayStart)) now else nextDayStart
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
                val recentSources = (steps.map { it.metadata.dataOrigin.packageName } + sleep.map { it.metadata.dataOrigin.packageName } + exercise.map { it.metadata.dataOrigin.packageName } + heart.map { it.metadata.dataOrigin.packageName } + hydration.map { it.metadata.dataOrigin.packageName } + distance.map { it.metadata.dataOrigin.packageName } + activeCalories.map { it.metadata.dataOrigin.packageName } + oxygen.map { it.metadata.dataOrigin.packageName } + weight.map { it.metadata.dataOrigin.packageName } + nutrition.map { it.metadata.dataOrigin.packageName }).filter { it.isNotBlank() }.distinct()
                val selectedDay = TimeRangeFilter.between(rangeStart, rangeEnd)
                val stepsDay = if (HealthPermission.getReadPermission(StepsRecord::class) in granted) client.readRecords(ReadRecordsRequest(StepsRecord::class, selectedDay)).records else emptyList()
                val sleepDay = if (HealthPermission.getReadPermission(SleepSessionRecord::class) in granted) client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, selectedDay)).records else emptyList()
                val exerciseDay = if (HealthPermission.getReadPermission(ExerciseSessionRecord::class) in granted) client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, selectedDay)).records else emptyList()
                val heartDay = if (HealthPermission.getReadPermission(HeartRateRecord::class) in granted) client.readRecords(ReadRecordsRequest(HeartRateRecord::class, selectedDay)).records else emptyList()
                val hydrationDay = if (HealthPermission.getReadPermission(HydrationRecord::class) in granted) client.readRecords(ReadRecordsRequest(HydrationRecord::class, selectedDay)).records else emptyList()
                val distanceDay = if (HealthPermission.getReadPermission(DistanceRecord::class) in granted) client.readRecords(ReadRecordsRequest(DistanceRecord::class, selectedDay)).records else emptyList()
                val activeCaloriesDay = if (HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class) in granted) client.readRecords(ReadRecordsRequest(ActiveCaloriesBurnedRecord::class, selectedDay)).records else emptyList()
                val oxygenDay = if (HealthPermission.getReadPermission(OxygenSaturationRecord::class) in granted) client.readRecords(ReadRecordsRequest(OxygenSaturationRecord::class, selectedDay)).records else emptyList()
                val weightDay = if (HealthPermission.getReadPermission(WeightRecord::class) in granted) client.readRecords(ReadRecordsRequest(WeightRecord::class, selectedDay)).records else emptyList()
                val nutritionDay = if (HealthPermission.getReadPermission(NutritionRecord::class) in granted) client.readRecords(ReadRecordsRequest(NutritionRecord::class, selectedDay)).records else emptyList()
                val manualMeals = manualMealsForDate(selectedDate)
                val scannerPackage = applicationContext.packageName
                val manualMealTimes = manualMeals.mapNotNull {
                    runCatching { Instant.parse(it.optString("recordedAt")) }.getOrNull()
                }
                val sources = (
                    recentSources +
                        stepsDay.map { it.metadata.dataOrigin.packageName } +
                        sleepDay.map { it.metadata.dataOrigin.packageName } +
                        exerciseDay.map { it.metadata.dataOrigin.packageName } +
                        heartDay.map { it.metadata.dataOrigin.packageName } +
                        hydrationDay.map { it.metadata.dataOrigin.packageName } +
                        distanceDay.map { it.metadata.dataOrigin.packageName } +
                        activeCaloriesDay.map { it.metadata.dataOrigin.packageName } +
                        oxygenDay.map { it.metadata.dataOrigin.packageName } +
                        weightDay.map { it.metadata.dataOrigin.packageName } +
                        nutritionDay.map { it.metadata.dataOrigin.packageName } +
                        if (manualMeals.isNotEmpty()) listOf(scannerPackage) else emptyList()
                    ).filter { it.isNotBlank() }.distinct()
                val attributions = JSONObject().apply {
                    put("steps", attribution(stepsDay.map { it.metadata.dataOrigin.packageName to it.endTime }))
                    put("sleepMinutes", attribution(sleepDay.map { it.metadata.dataOrigin.packageName to it.endTime }))
                    put("exerciseMinutes", attribution(exerciseDay.map { it.metadata.dataOrigin.packageName to it.endTime }))
                    put("averageHeartRate", attribution(heartDay.map { it.metadata.dataOrigin.packageName to it.endTime }))
                    put("hydrationLitres", attribution(hydrationDay.map { it.metadata.dataOrigin.packageName to it.endTime }))
                    put("distanceKm", attribution(distanceDay.map { it.metadata.dataOrigin.packageName to it.endTime }))
                    put("activeCalories", attribution(activeCaloriesDay.map { it.metadata.dataOrigin.packageName to it.endTime }))
                    put("oxygenPercent", attribution(oxygenDay.map { it.metadata.dataOrigin.packageName to it.time }))
                    put("weightKg", attribution(weightDay.map { it.metadata.dataOrigin.packageName to it.time }))
                    put(
                        "nutrition",
                        attribution(
                            nutritionDay.map { it.metadata.dataOrigin.packageName to it.endTime } +
                                manualMealTimes.map { scannerPackage to it }
                        )
                    )
                }
                val samples = heartDay.flatMap { it.samples }
                val averageHeartRate = if (samples.isEmpty()) null else samples.map { it.beatsPerMinute }.average().roundToInt()
                val nutritionSummary = buildNutritionSummary(nutritionDay, manualMeals)
                val details = buildDetailsPayload(
                    stepsDay,
                    sleepDay,
                    exerciseDay,
                    heartDay,
                    hydrationDay,
                    distanceDay,
                    activeCaloriesDay,
                    oxygenDay,
                    weightDay,
                    nutritionDay,
                    manualMeals
                )
                val scoreBreakdown = buildScoreBreakdown(
                    stepsDay.sumOf { it.count },
                    sleepDay.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() },
                    exerciseDay.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() },
                    hydrationDay.sumOf { it.volume.inLiters },
                    averageHeartRate,
                    nutritionSummary
                )
                val payload = JSONObject().apply {
                    put("periodHours", 24)
                    put("selectedDate", selectedDate.toString())
                    put("rangeStart", rangeStart.toString())
                    put("rangeEnd", rangeEnd.toString())
                    put("steps", stepsDay.sumOf { it.count })
                    put("sleepMinutes", sleepDay.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() })
                    put("exerciseMinutes", exerciseDay.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() })
                    put("averageHeartRate", averageHeartRate ?: JSONObject.NULL)
                    put("hydrationLitres", hydrationDay.sumOf { it.volume.inLiters })
                    put("distanceKm", distanceDay.sumOf { it.distance.inKilometers })
                    put("activeCalories", activeCaloriesDay.sumOf { it.energy.inKilocalories })
                    put("oxygenPercent", oxygenDay.maxByOrNull { it.time }?.percentage?.value ?: JSONObject.NULL)
                    put("weightKg", weightDay.maxByOrNull { it.time }?.weight?.inKilograms ?: JSONObject.NULL)
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
                dispatchSyncState(if (hasAnyHealthPermission) "complete" else "permission_required")
            }.onFailure { error ->
                dispatchSyncState("error", error.message)
                notifyWeb(false, "sync_error", error.message ?: "Synchronisation impossible")
            }
        }
    }

    private fun buildNutritionSummary(
        records: List<NutritionRecord>,
        manualMeals: List<JSONObject>
    ): JSONObject {
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
            put("mealCount", records.size + manualMeals.size)
            put(
                "caloriesKcal",
                records.sumOf { it.energy?.inKilocalories ?: 0.0 } +
                    manualMeals.sumOf { it.optDouble("caloriesKcal", 0.0) }
            )
            put(
                "carbohydratesGrams",
                records.sumOf { it.totalCarbohydrate?.inGrams ?: 0.0 } +
                    manualMeals.sumOf { it.optDouble("carbohydratesGrams", 0.0) }
            )
            put(
                "proteinGrams",
                records.sumOf { it.protein?.inGrams ?: 0.0 } +
                    manualMeals.sumOf { it.optDouble("proteinGrams", 0.0) }
            )
            put(
                "fatGrams",
                records.sumOf { it.totalFat?.inGrams ?: 0.0 } +
                    manualMeals.sumOf { it.optDouble("fatGrams", 0.0) }
            )
            put(
                "fiberGrams",
                records.sumOf { it.dietaryFiber?.inGrams ?: 0.0 } +
                    manualMeals.sumOf { it.optDouble("fiberGrams", 0.0) }
            )
            put(
                "sugarGrams",
                records.sumOf { it.sugar?.inGrams ?: 0.0 } +
                    manualMeals.sumOf { it.optDouble("sugarGrams", 0.0) }
            )
            put(
                "sodiumMilligrams",
                records.sumOf { (it.sodium?.inGrams ?: 0.0) * 1000.0 } +
                    manualMeals.sumOf { it.optDouble("sodiumMilligrams", 0.0) }
            )
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
        nutrition: List<NutritionRecord>,
        manualMeals: List<JSONObject>
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
        val connectedMeals = nutrition.sortedByDescending { it.endTime }.take(50).map { record ->
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
        }
        val scannedMeals = manualMeals.map { record ->
            JSONObject().apply {
                put("name", record.optString("name", "Repas analysé"))
                put("mealType", 0)
                put("caloriesKcal", record.optDouble("caloriesKcal", 0.0))
                put("carbohydratesGrams", record.optDouble("carbohydratesGrams", 0.0))
                put("proteinGrams", record.optDouble("proteinGrams", 0.0))
                put("fatGrams", record.optDouble("fatGrams", 0.0))
                put("fiberGrams", record.optDouble("fiberGrams", 0.0))
                put("sugarGrams", record.optDouble("sugarGrams", 0.0))
                put("sodiumMilligrams", record.optDouble("sodiumMilligrams", 0.0))
                put("confidence", record.optDouble("confidence", 0.0))
                put("summary", record.optString("summary"))
                put("improvement", record.optString("improvement"))
                put("startTime", record.optString("recordedAt"))
                put("endTime", record.optString("recordedAt"))
                put("connector", "Vitalis Scanner")
                put("packageName", applicationContext.packageName)
            }
        }
        put("nutrition", JSONArray((connectedMeals + scannedMeals).take(100)))
        put("sleep", JSONArray(sleep.sortedByDescending { it.endTime }.take(50).map { record ->
            JSONObject().apply {
                put("durationMinutes", Duration.between(record.startTime, record.endTime).toMinutes())
                put("startTime", record.startTime.toString())
                put("endTime", record.endTime.toString())
                put("connector", sourceLabel(record.metadata.dataOrigin.packageName))
            }
        }))
        put("hydration", JSONArray(hydration.sortedByDescending { it.endTime }.take(50).map { record ->
            JSONObject().apply {
                put("litres", record.volume.inLiters)
                put("time", record.endTime.toString())
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
        val catalogItems = connectorCatalog.map { definition ->
            val detected = definition.packages.firstOrNull { it in packages }
            val installed = definition.packages.firstOrNull(::isPackageInstalled)
            JSONObject().apply {
                put("id", definition.id)
                put("name", definition.name)
                put(
                    "status",
                    when {
                        definition.id == "health_connect" && healthStatus == "unavailable" -> "unavailable"
                        detected != null -> "connected"
                        installed != null -> "installed"
                        definition.id == "health_connect" -> healthStatus
                        else -> "not_installed"
                    }
                )
                put("mode", definition.mode)
                put("packageName", detected ?: installed ?: definition.packages.firstOrNull().orEmpty())
                put("detectedData", detected != null)
                put("installed", installed != null)
                put(
                    "action",
                    when {
                        definition.id == "health_connect" -> "authorize_health_connect"
                        installed != null && (definition.mode == "health_connect" || definition.mode == "bridge") ->
                            "authorize_via_health_connect"
                        installed != null -> "open_provider"
                        else -> "install_provider"
                    }
                )
                put("note", definition.note)
            }
        }
        val catalogPackages = connectorCatalog.flatMap { it.packages }.toSet()
        val dynamicItems = packages.filterNot { it in catalogPackages }.map { packageName ->
            connector(sourceLabel(packageName), "connected", "health_connect", packageName).apply {
                put("id", packageName)
                put("detectedData", true)
                put("installed", isPackageInstalled(packageName))
                put("action", "manage_health_connect")
                put("note", "Source détectée automatiquement dans Health Connect.")
            }
        }
        return JSONObject().apply {
            put("healthConnect", healthStatus)
            put("unlimitedDiscovery", true)
            put("connectorCount", packages.size)
            put("catalogCount", catalogItems.size + dynamicItems.size)
            put("connectors", JSONArray(catalogItems + dynamicItems))
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
        private const val REMOTE_LOAD_TIMEOUT_MS = 30_000L
        private const val REMOTE_RETRY_DELAY_MS = 2_000L
        private const val MAX_REMOTE_RETRIES = 2
        private const val MAX_SPEECH_TEXT_LENGTH = 8_000
        private const val MAX_AI_PROMPT_LENGTH = 4_000
        private const val MAX_DEVELOPER_PROMPT_LENGTH = 8_000
        private const val MAX_IMAGE_DATA_URL_LENGTH = 6_000_000
        private const val MAX_MEAL_ESTIMATE_JSON_LENGTH = 24_000
        private const val MAX_MANUAL_MEALS = 500
        private const val OPENAI_RESPONSES_URL = "https://api.openai.com/v1/responses"
        private const val OPENAI_MODEL = "gpt-5.6"
        private const val CHATGPT_WORK_URL = "https://chatgpt.com/codex"
        private const val APP_PREFS = "vitalis_preferences"
        private const val SECURE_PREFS = "vitalis_secure_preferences"
        private const val OPENAI_SECRET_NAME = "openai_api_key"
        private const val OPENAI_DEVELOPER_SECRET_NAME = "openai_developer_api_key"
        private const val AI_HEALTH_CONSENT = "ai_health_consent"
        private const val MANUAL_MEALS_KEY = "manual_meal_estimates"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEYSTORE_ALIAS = "vitalis_openai_key_v1"
        private const val KEYSTORE_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val COACH_SAFETY_INSTRUCTIONS =
            "Réponds en français clair, professionnel, chaleureux et concret. Analyse uniquement les données fournies, " +
                "indique les données manquantes et cite les connecteurs visibles. Ne pose aucun diagnostic et ne remplace " +
                "jamais un professionnel de santé. Pour un symptôme grave ou urgent, recommande immédiatement de contacter " +
                "les services d’urgence locaux. Donne au maximum trois priorités réalistes et explique brièvement pourquoi."
        private const val DEVELOPER_INSTRUCTIONS =
            "Tu es Vitalis Developer AI, assistant technique senior de l’application Vitalis Mobile. Aide l’utilisateur " +
                "à transformer un besoin en demande de modification structurée : objectif, comportement attendu, fichiers " +
                "ou modules probables, permissions ou connecteurs nécessaires, risques, critères de test et validation. " +
                "Préserve toujours l’interface classique existante sauf demande explicite contraire. Ne prétends jamais " +
                "avoir modifié, compilé ou publié le dépôt : les changements réels sont exécutés dans ChatGPT Work/Codex " +
                "avec accès GitHub et validation de l’utilisateur. N’affiche et ne demande jamais une clé API."
    }
}
