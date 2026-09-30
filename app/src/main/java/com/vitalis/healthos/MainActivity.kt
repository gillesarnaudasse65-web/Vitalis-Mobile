package com.vitalis.healthos

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.SslErrorHandler
import android.net.http.SslError
import android.graphics.Bitmap
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Clock
import androidx.core.content.edit
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
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
    private var healthConnectDataSource: AndroidHealthConnectDataSource? = null
    private var healthSyncJob: Job? = null
    private val healthSyncGeneration = AtomicLong(0)
    private var lastSourcePackages: List<String> = emptyList()
    private var lastHealthPayload = JSONObject()
    private var textToSpeech: TextToSpeech? = null
    private var textToSpeechReady = false
    private val ttsSessionCoordinator = TtsSessionCoordinator()
    private var speechRecognizer: SpeechRecognizer? = null
    private var microphoneEnabled = false
    private val recognitionSessionCoordinator = RecognitionSessionCoordinator()
    private val voiceHandler = Handler(Looper.getMainLooper())
    private var pendingVoiceRetry: Runnable? = null
    private var pendingVoiceFallbackSessionId: String? = null
    private var voiceFallbackInProgress = false
    private var microphonePermissionPreviouslyRequested = false
    private var lastHealthConnectPermissionGranted = false
    private var lastHealthConnectAllPermissionsGranted = false
    private var hasResumedOnce = false
    private var webBackInFlight = false
    private var renderProcessRecoveryPending = false
    private lateinit var dateState: SelectedDateState
    private var selectedHealthDate: LocalDate
        get() = dateState.selected
        set(value) { dateState.select(value.toString()) }
    private val debugRefreshRequests = java.util.Collections.synchronizedList(mutableListOf<String>())
    private var pendingConnectorId: String? = null
    private var refreshAfterConnectorReturn = false
    private lateinit var appPreferences: SharedPreferences
    private lateinit var secureSecretStore: SecureSecretStore
    private lateinit var localDataStore: VitalisLocalDataStore
    private lateinit var aiConsentCoordinator: AiConsentCoordinator
    private val activeHealthAiJobs = ConcurrentHashMap<String, Job>()
    private var observedLocalDeleteGeneration = 0L
    private var observedLocalImportGeneration = 0L
    private lateinit var nutritionImageProcessor: SafeNutritionImageProcessor
    private lateinit var activeNutritionImageCache: ActiveNutritionImageCache
    private lateinit var nutritionMealStore: NutritionMealStore
    private val nutritionScanCoordinator = NutritionScanCoordinator()
    private val normalizedNutritionImages = mutableMapOf<String, String>()
    private var activeNutritionAnalysisJob: Job? = null
    private val nutritionAnalysisGeneration = AtomicLong(0)
    private var pendingNutritionCameraFile: File? = null
    private var pendingNutritionCameraUri: Uri? = null
    private var pendingNutritionExport: String? = null

    private val connectorCatalog = ConnectorCatalog.entries

    private val healthPermissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
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

    private val mealPhotoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) cancelCurrentNutritionScan("picker_cancelled")
        else handleNutritionImageUri(uri)
    }

    private val mealCameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { captured ->
        val uri = pendingNutritionCameraUri
        if (captured && uri != null) handleNutritionImageUri(uri)
        else {
            pendingNutritionCameraFile?.delete()
            pendingNutritionCameraFile = null
            pendingNutritionCameraUri = null
            cancelCurrentNutritionScan("camera_cancelled")
        }
    }

    private val nutritionExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val export = pendingNutritionExport
        pendingNutritionExport = null
        if (uri == null || export == null) {
            dispatchNutritionOperation("export", false, "export_cancelled")
            return@registerForActivityResult
        }
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val saved = runCatching {
                contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                    stream.write(export.toByteArray(StandardCharsets.UTF_8))
                } ?: error("unwritable_uri")
            }.isSuccess
            dispatchNutritionOperation(
                "export",
                saved,
                if (saved) null else "export_write_failed"
            )
        }
    }

    private val microphonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val sessionId = recognitionSessionCoordinator.snapshot().sessionId
            ?: return@registerForActivityResult
        val hadRequestedBefore = microphonePermissionPreviouslyRequested
        microphonePermissionPreviouslyRequested = true
        appPreferences.edit { putBoolean(MICROPHONE_PERMISSION_REQUESTED_KEY, true) }
        if (granted) {
            recognitionSessionCoordinator.permissionGranted(sessionId)
            startMicrophoneInternal(sessionId)
        } else {
            microphoneEnabled = false
            val permanentlyDenied = hadRequestedBefore &&
                !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
            recognitionSessionCoordinator.permissionDenied(sessionId, permanentlyDenied)
            dispatchVoiceEvent(
                "microphone",
                if (permanentlyDenied) "permission_permanently_denied" else "permission_denied",
                false
            )
        }
    }

    private val voiceFallbackLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        voiceFallbackInProgress = false
        val sessionId = pendingVoiceFallbackSessionId
        pendingVoiceFallbackSessionId = null
        if (sessionId == null || !recognitionSessionCoordinator.isCurrent(sessionId)) {
            return@registerForActivityResult
        }
        val matches = if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).orEmpty()
        } else emptyList()
        val finalText = matches.firstOrNull().orEmpty()
        if (recognitionSessionCoordinator.consumeFinal(sessionId, finalText)) {
            dispatchSpeechResults(sessionId, matches, VoiceResultKind.FINAL)
            finishVoiceSession(sessionId, "idle")
        } else {
            dispatchVoiceEvent("microphone", "fallback_cancelled", false)
            finishVoiceSession(sessionId, "idle")
        }
    }

    private val permissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        appPreferences.edit {
            putBoolean(HEALTH_PERMISSION_REQUESTED_KEY, true)
            if (granted.isNotEmpty()) putBoolean(HEALTH_PERMISSION_EVER_AUTHORIZED_KEY, true)
        }
        lastHealthConnectPermissionGranted = granted.intersect(healthPermissions).isNotEmpty()
        val allGranted = granted.containsAll(healthPermissions)
        lastHealthConnectAllPermissionsGranted = allGranted
        notifyWeb(
            allGranted,
            if (allGranted) "authorized" else "partial",
            if (allGranted) "Health Connect est autorisé. Synchronisation activée."
            else "Autorisation partielle. Complétez les catégories dans Health Connect."
        )
        readHealthData()
        pendingConnectorId?.also { connectorId ->
            pendingConnectorId = null
            ConnectorCatalog.find(connectorId)?.let(::openInstalledConnector)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getString(STATE_NUTRITION_CAMERA_FILE)?.let { fileName ->
            if (Regex("capture-[A-Za-z0-9-]+\\.jpg").matches(fileName)) {
                pendingNutritionCameraFile = File(File(cacheDir, "nutrition-captures"), fileName)
                pendingNutritionCameraUri = savedInstanceState
                    .getString(STATE_NUTRITION_CAMERA_URI)
                    ?.let(Uri::parse)
            }
        }
        val debugFixture = BuildConfig.DEBUG && (
            intent.getBooleanExtra(EXTRA_RUN2_FIXTURE, false) ||
                intent.getBooleanExtra(EXTRA_RUN3_FIXTURE, false) ||
                intent.getBooleanExtra(EXTRA_RUN4_FIXTURE, false) ||
                intent.getBooleanExtra(EXTRA_RUN5_FIXTURE, false) ||
                intent.getBooleanExtra(EXTRA_RUN6_FIXTURE, false) ||
                intent.getBooleanExtra(EXTRA_FINAL_UX_FIXTURE, false)
            )
        val deviceClock = if (debugFixture) {
            val fixed = BridgeInputPolicy.date(intent.getStringExtra(EXTRA_TEST_TODAY_ISO))
            fixed?.let { Clock.fixed(it.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant(),
                java.time.ZoneId.systemDefault()) } ?: Clock.systemDefaultZone()
        } else Clock.systemDefaultZone()
        appPreferences = getSharedPreferences(APP_PREFS, MODE_PRIVATE)
        microphonePermissionPreviouslyRequested = appPreferences.getBoolean(
            MICROPHONE_PERMISSION_REQUESTED_KEY,
            false
        )
        secureSecretStore = SecureSecretStore(this)
        localDataStore = VitalisLocalDataStore(this)
        aiConsentCoordinator = AiConsentCoordinator(
            appPreferences.getBoolean(AI_HEALTH_CONSENT, false)
        )
        observedLocalDeleteGeneration = localDataStore.localDeleteGeneration()
        observedLocalImportGeneration = localDataStore.appliedImportGeneration()
        nutritionImageProcessor = SafeNutritionImageProcessor(this)
        activeNutritionImageCache = ActiveNutritionImageCache(
            File(cacheDir, ACTIVE_NUTRITION_CACHE_DIRECTORY)
        ).also { it.prune() }
        nutritionMealStore = NutritionMealStore(object : NutritionStringStorage {
            override fun read(): String? = appPreferences.getString(MANUAL_MEALS_KEY, "[]")
            @SuppressLint("UseKtx")
            override fun write(value: String): Boolean =
                appPreferences.edit().putString(MANUAL_MEALS_KEY, value).commit()
            @SuppressLint("UseKtx")
            override fun remove(): Boolean =
                appPreferences.edit().remove(MANUAL_MEALS_KEY).commit()
        }, deviceClock)
        nutritionMealStore.migrate()
        appPreferences.getString(PENDING_NUTRITION_SCAN_KEY, null)?.let { raw ->
            runCatching { JSONObject(raw).optString("scanId") }.getOrNull()
                ?.takeIf(NutritionIds::valid)
                ?.let(::restoreNutritionSession)
        }
        // Instrumentation classes share the debug app's preferences. A previous fixture can
        // finish its WebView callbacks after the next class clears the date, especially across
        // midnight. Start each new fixture from its injected clock; recreation still restores
        // the date selected inside that same scenario.
        val restoredDate = if (debugFixture && savedInstanceState == null) null
            else appPreferences.getString(SELECTED_HEALTH_DATE_KEY, null)
        dateState = SelectedDateState(deviceClock, restoredDate) {
            appPreferences.edit { putString(SELECTED_HEALTH_DATE_KEY, it) }
        }
        window.statusBarColor = Color.parseColor("#063C30")
        window.navigationBarColor = Color.parseColor("#063C30")

        ensureHealthConnectClient()
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
            settings.setSupportMultipleWindows(false)
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.userAgentString = settings.userAgentString + " VitalisAndroid/3.14"
            WebView.setWebContentsDebuggingEnabled(
                ReleaseSecurityPolicy.webViewDebuggingEnabled(BuildConfig.DEBUG)
            )
            registerOriginAwareBridge(this)
            webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(
                    view: WebView?, isDialog: Boolean, isUserGesture: Boolean,
                    resultMsg: android.os.Message?
                ): Boolean = false
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
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    val uri = request.url
                    if (uri.host == LOCAL_ASSET_HOST &&
                        uri.path?.startsWith(ACTIVE_SCAN_ASSET_PATH) == true) {
                        val scanId = uri.lastPathSegment.orEmpty().removeSuffix(".jpg")
                        val bytes = activeNutritionImageCache.bytes(scanId)
                        return if (bytes == null) {
                            WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(),
                                ByteArrayInputStream(ByteArray(0)))
                        } else {
                            WebResourceResponse("image/jpeg", null, ByteArrayInputStream(bytes))
                        }
                    }
                    if (NavigationPolicy.isTrusted(uri.toString()) && uri.host == VITALIS_HOST &&
                        uri.path?.startsWith(COACH_ASSET_PATH) == true) {
                        val fileName = uri.lastPathSegment.orEmpty()
                        if (fileName in COACH_ASSET_FILES) {
                            return runCatching {
                                WebResourceResponse(
                                    "image/webp",
                                    null,
                                    assets.open("vitalis/coaches/$fileName")
                                )
                            }.getOrNull()
                        }
                    }
                    return if (NavigationPolicy.isTrusted(uri.toString()))
                        assetLoader.shouldInterceptRequest(uri) else null
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val decision = NavigationPolicy.decide(request.url.toString())
                    if (!request.isForMainFrame) return decision != NavigationPolicy.Decision.TRUSTED
                    return when (decision) {
                        NavigationPolicy.Decision.TRUSTED -> false
                        NavigationPolicy.Decision.EXTERNAL_HTTPS -> {
                            // The external page never loads inside this privileged WebView.
                            openSafeExternalUrl(request.url.toString())
                            true
                        }
                        NavigationPolicy.Decision.REJECT -> true
                    }
                }

                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    if (!NavigationPolicy.isTrusted(url)) {
                        view.stopLoading()
                        loadOfflineFallback()
                        return
                    }
                }

                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                    handler.cancel()
                    loadOfflineFallback()
                }

                override fun onPageFinished(view: WebView, url: String) {
                    if (!NavigationPolicy.isTrusted(url)) return
                    loading.visibility = android.view.View.GONE
                    val host = requestHost(url)
                    if (host == VITALIS_HOST) {
                        remotePageFinished = true
                        remoteRetryCount = 0
                    }
                    if (host == VITALIS_HOST || host == LOCAL_ASSET_HOST) injectClassicCompatibility(view)
                    view.evaluateJavascript(
                        "window.dispatchEvent(new CustomEvent('vitalis-native-ready',{detail:{platform:'android',version:'3.14'}}));",
                        null
                    )
                    readHealthData()
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (!request.isForMainFrame) return
                    if (!NavigationPolicy.isTrusted(request.url.toString())) return
                    if (request.url.host == VITALIS_HOST) retryClassicInterfaceOrFallback()
                    else if (request.url.host == LOCAL_ASSET_HOST) showConnectionError()
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: WebResourceResponse
                ) {
                    if (request.isForMainFrame && NavigationPolicy.isTrusted(request.url.toString()) &&
                        request.url.host == VITALIS_HOST && errorResponse.statusCode >= 400) {
                        retryClassicInterfaceOrFallback()
                    }
                }

                override fun onRenderProcessGone(
                    view: WebView,
                    detail: RenderProcessGoneDetail
                ): Boolean {
                    if (::webView.isInitialized && view === webView) {
                        runCatching { root.removeView(view) }
                        runCatching { view.destroy() }
                        // WebView destruction can report a renderer loss after the Activity has
                        // already begun teardown. Recreating from that callback leaves a stale
                        // Activity alive long enough to overwrite the next screen's persisted
                        // state. Recover only a live foreground Activity; otherwise defer until
                        // its legitimate next resume.
                        if (isFinishing || isDestroyed ||
                            lifecycle.currentState == Lifecycle.State.DESTROYED) {
                            return true
                        }
                        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                            recreate()
                        } else {
                            renderProcessRecoveryPending = true
                        }
                    }
                    return true
                }
            }
            // Controlled offline start for the debug instrumentation smoke test only.
            loadUrl(when {
                BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_RUN2_FIXTURE, false) -> LOCAL_TEST_URL
                BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_RUN3_FIXTURE, false) ->
                    LOCAL_RUN3_TEST_URL
                BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_RUN4_FIXTURE, false) ->
                    LOCAL_RUN4_TEST_URL
                BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_RUN5_FIXTURE, false) ->
                    LOCAL_RUN5_TEST_URL
                BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_RUN6_FIXTURE, false) ->
                    LOCAL_RUN6_TEST_URL
                BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_FINAL_UX_FIXTURE, false) ->
                    LOCAL_FINAL_UX_TEST_URL
                BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_FORCE_OFFLINE_FOR_TESTS, false) -> LOCAL_URL
                else -> VITALIS_URL
            })
        }
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        if (!(BuildConfig.DEBUG && (intent.getBooleanExtra(EXTRA_FORCE_OFFLINE_FOR_TESTS, false) ||
                    intent.getBooleanExtra(EXTRA_RUN2_FIXTURE, false) ||
                    intent.getBooleanExtra(EXTRA_RUN3_FIXTURE, false) ||
                    intent.getBooleanExtra(EXTRA_RUN4_FIXTURE, false) ||
                    intent.getBooleanExtra(EXTRA_RUN5_FIXTURE, false) ||
                    intent.getBooleanExtra(EXTRA_RUN6_FIXTURE, false) ||
                    intent.getBooleanExtra(EXTRA_FINAL_UX_FIXTURE, false))))
            scheduleClassicInterfaceTimeout()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webBackInFlight) return
                if (!::webView.isInitialized) {
                    finish()
                    return
                }
                webBackInFlight = true
                val currentWebView = webView
                currentWebView.evaluateJavascript(
                    """
                    (function(){
                      var nodes=document.querySelectorAll(
                        '.vux-layer,.vitalis-power-overlay-312,.vitalis-source-overlay,.vitalis-deep-overlay,.vitalis-native-overlay'
                      );
                      if(!nodes.length)return false;
                      var top=nodes[nodes.length-1];
                      var close=top.querySelector('[data-close],.vitalis-native-close,.vitalis-source-close,.vitalis-deep-close');
                      if(close)close.click();else top.remove();
                      return true;
                    })();
                    """.trimIndent()
                ) { handled ->
                    webBackInFlight = false
                    if (isFinishing || isDestroyed || currentWebView !== webView) return@evaluateJavascript
                    if (handled == "true") return@evaluateJavascript
                    if (currentWebView.canGoBack()) currentWebView.goBack() else finish()
                }
            }
        })
    }

    private fun requestHost(url: String): String? = runCatching { Uri.parse(url).host }.getOrNull()

    internal fun debugRecordedDates(): List<String> = synchronized(debugRefreshRequests) {
        debugRefreshRequests.toList()
    }

    internal fun clearDebugRecordedDates() { debugRefreshRequests.clear() }

    private fun registerOriginAwareBridge(view: WebView) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
        WebViewCompat.addWebMessageListener(
            view,
            NATIVE_CHANNEL_NAME,
            OriginBridgePolicy.allowedOriginRules(),
            VitalisWebMessageListener()
        )
    }

    private inner class VitalisWebMessageListener : WebViewCompat.WebMessageListener {
        override fun onPostMessage(
            view: WebView,
            message: WebMessageCompat,
            sourceOrigin: Uri,
            isMainFrame: Boolean,
            replyProxy: JavaScriptReplyProxy
        ) {
            handleOriginAwareMessage(message.data, sourceOrigin.toString(), isMainFrame, replyProxy)
        }
    }

    private fun openSafeExternalUrl(rawUrl: String) {
        if (!BridgeInputPolicy.externalUrl(rawUrl)) return
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(rawUrl))) }
    }

    private fun injectClassicCompatibility(view: WebView) {
        val productionLayers = mutableListOf(
            "vitalis/selected-date.js",
            "vitalis/compat.js",
            "vitalis/vitalis-3.12.js"
        )
        val stabilizedFixture = BuildConfig.DEBUG && (
            intent.getBooleanExtra(EXTRA_RUN2_FIXTURE, false) ||
                intent.getBooleanExtra(EXTRA_RUN3_FIXTURE, false) ||
                intent.getBooleanExtra(EXTRA_RUN4_FIXTURE, false) ||
                intent.getBooleanExtra(EXTRA_RUN5_FIXTURE, false) ||
                intent.getBooleanExtra(EXTRA_RUN6_FIXTURE, false)
            )
        if (!stabilizedFixture) productionLayers.addAll(listOf(
            "vitalis/final-ux-core.js",
            "vitalis/final-ux.js"
        ))

        val scripts = mutableListOf(buildNativeProxyBootstrap())
        val loadedLayers = runCatching {
            productionLayers.map { asset ->
                assets.open(asset).bufferedReader().use { it.readText() }
            }
        }.getOrNull() ?: return
        scripts.addAll(loadedLayers)

        // Keep the deterministic fixture bootstrap byte-for-byte equivalent to the previous
        // path so upgrade-continuity tests preserve their established storage timing.
        if (stabilizedFixture) {
            view.evaluateJavascript(scripts.joinToString("\n;\n"), null)
            return
        }

        // Evaluate the bundled production layers one by one. A runtime failure in a legacy/
        // remote compatibility layer must not prevent the local Final UX dashboard mounting.
        fun evaluateLayer(index: Int) {
            if (index >= scripts.size) {
                if (!stabilizedFixture) verifyFinalUxMounted(view, 0)
                return
            }
            view.evaluateJavascript(scripts[index]) {
                evaluateLayer(index + 1)
            }
        }
        evaluateLayer(0)
    }

    private fun verifyFinalUxMounted(view: WebView, attempt: Int) {
        if (!::webView.isInitialized || view !== webView) return
        view.evaluateJavascript(
            """
                (function(){
                  return !!window.VitalisAndroid &&
                    window.__vitalisSelectedDateRun2 === 'ready' &&
                    window.__vitalisNativeCompatibility === 'ready' &&
                    window.__vitalisConnectorVoiceControls === 'ready' &&
                    window.__vitalisPowerLayer312 === 'ready' &&
                    window.__vitalisFinalUx === 'ready' &&
                    !!window.VitalisDate &&
                    !!window.VitalisCoaches &&
                    !!window.VitalisFinalUX &&
                    !!document.querySelector('#vitalis-final-ux') &&
                    !!document.querySelector('[data-widgets]') &&
                    document.querySelectorAll('[data-widget]').length > 0;
                })();
            """.trimIndent()
        ) { result ->
            if (result == "true") return@evaluateJavascript
            if (attempt >= FINAL_UX_RECOVERY_ATTEMPTS) {
                // A remote page can finish loading while still exposing only an empty shell.
                // In that case use the bundled local Vitalis UI rather than leaving a blank app.
                if (requestHost(view.url.orEmpty()) == VITALIS_HOST) loadOfflineFallback()
                return@evaluateJavascript
            }
            Handler(Looper.getMainLooper()).postDelayed({
                if (!::webView.isInitialized || view !== webView) return@postDelayed
                val recoveryScripts = runCatching {
                    listOf(
                        assets.open("vitalis/selected-date.js").bufferedReader().use { it.readText() },
                        assets.open("vitalis/compat.js").bufferedReader().use { it.readText() },
                        assets.open("vitalis/vitalis-3.12.js").bufferedReader().use { it.readText() },
                        assets.open("vitalis/final-ux-core.js").bufferedReader().use { it.readText() },
                        assets.open("vitalis/final-ux.js").bufferedReader().use { it.readText() }
                    )
                }.getOrNull() ?: return@postDelayed
                val recovery = """
                    if (window.__vitalisSelectedDateRun2 !== 'ready') window.__vitalisSelectedDateRun2 = false;
                    if (window.__vitalisNativeCompatibility !== 'ready' || !window.VitalisNativeActions) window.__vitalisNativeCompatibility = false;
                    if (window.__vitalisConnectorVoiceControls !== 'ready') window.__vitalisConnectorVoiceControls = false;
                    if (window.__vitalisDeepDetails !== 'ready') window.__vitalisDeepDetails = false;
                    if (window.__vitalisSelectedDayAndNutrition !== 'ready') window.__vitalisSelectedDayAndNutrition = false;
                    if (window.__vitalisRealAiCoach !== 'ready') window.__vitalisRealAiCoach = false;
                    if (window.__vitalisCoachRefresh311 !== 'ready') window.__vitalisCoachRefresh311 = false;
                    if (window.__vitalisPowerLayer312 !== 'ready' || !window.VitalisCoaches) window.__vitalisPowerLayer312 = false;
                    if (window.__vitalisFinalUx !== 'ready' || !document.querySelector('#vitalis-final-ux')) {
                      window.__vitalisFinalUx = false;
                      var partialRoot=document.querySelector('#vitalis-final-ux');if(partialRoot)partialRoot.remove();
                    }
                """.trimIndent() + "\n;\n" + recoveryScripts.joinToString("\n;\n")
                view.evaluateJavascript(recovery) {
                    verifyFinalUxMounted(view, attempt + 1)
                }
            }, FINAL_UX_RECOVERY_DELAY_MS)
        }
    }

    override fun onResume() {
        super.onResume()
        if (renderProcessRecoveryPending) {
            renderProcessRecoveryPending = false
            recreate()
            return
        }
        val returningToForeground = hasResumedOnce
        hasResumedOnce = true
        ensureHealthConnectClient()
        if (::localDataStore.isInitialized) {
            val persistedConsent = appPreferences.getBoolean(AI_HEALTH_CONSENT, false)
            if (persistedConsent != aiConsentCoordinator.isConsented()) {
                if (persistedConsent) aiConsentCoordinator.grant()
                else revokeAiConsent("consent_revoked_in_settings")
            }
            val deleteGeneration = localDataStore.localDeleteGeneration()
            if (deleteGeneration != observedLocalDeleteGeneration) {
                observedLocalDeleteGeneration = deleteGeneration
                revokeAiConsent("local_data_deleted")
                stopMicrophone("local_data_deleted", cancelled = true)
                stopSpeaking("local_data_deleted")
                selectedHealthDate = dateState.currentToday()
                readHealthData(selectedHealthDate)
            }
            val importGeneration = localDataStore.localImportGeneration()
            if (importGeneration != observedLocalImportGeneration) {
                applyImportedLocalStateToWeb(importGeneration)
                appPreferences.getString(SELECTED_HEALTH_DATE_KEY, null)?.let { selected ->
                    BridgeInputPolicy.date(selected)?.let {
                        selectedHealthDate = it
                        readHealthData(it)
                    }
                }
            }
            refreshNativeProxyState()
            nutritionScanCoordinator.active()?.takeIf {
                normalizedNutritionImages.containsKey(it.scanId)
            }?.let(::dispatchNutritionReadiness)
        }
        if ((refreshAfterConnectorReturn || returningToForeground) && ::webView.isInitialized) {
            refreshAfterConnectorReturn = false
            Handler(Looper.getMainLooper()).postDelayed(
                { readHealthData(selectedHealthDate) },
                CONNECTOR_RETURN_REFRESH_DELAY_MS
            )
        }
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

    override fun onStop() {
        if (!voiceFallbackInProgress) {
            stopMicrophone("activity_background", cancelled = true)
        }
        stopSpeaking("activity_background")
        super.onStop()
    }

    override fun onDestroy() {
        activeNutritionAnalysisJob?.cancel()
        activeNutritionAnalysisJob = null
        if (isFinishing) pendingNutritionCameraFile?.delete()
        pendingNutritionCameraFile = null
        pendingNutritionCameraUri = null
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        stopMicrophone("activity_destroyed", cancelled = true)
        voiceFallbackInProgress = false
        pendingVoiceFallbackSessionId = null
        speechRecognizer?.destroy()
        speechRecognizer = null
        stopSpeaking("activity_destroyed")
        textToSpeech?.shutdown()
        textToSpeech = null
        ttsSessionCoordinator.destroy()
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        pendingNutritionCameraFile?.name?.let {
            outState.putString(STATE_NUTRITION_CAMERA_FILE, it)
        }
        pendingNutritionCameraUri?.toString()?.let {
            outState.putString(STATE_NUTRITION_CAMERA_URI, it)
        }
        super.onSaveInstanceState(outState)
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

    private fun handleOriginAwareMessage(
        raw: String?,
        sourceOrigin: String,
        isMainFrame: Boolean,
        replyProxy: JavaScriptReplyProxy
    ) {
        val message = raw?.takeIf { it.length <= MAX_BRIDGE_MESSAGE_CHARS }
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
        val id = message?.optString("id")?.takeIf(BridgeInputPolicy::requestId)
        val method = message?.optString("method").orEmpty()
        val args = message?.optJSONArray("args") ?: JSONArray()
        if (id == null || !OriginBridgePolicy.canInvoke(sourceOrigin.trimEnd('/'), isMainFrame, method)) {
            replyProxy.postMessage(bridgeReply(id, false, null, "untrusted_or_invalid_request").toString())
            return
        }
        runOnUiThread {
            val result = runCatching { executeBridgeMethod(method, args) }
            replyProxy.postMessage(
                if (result.isSuccess) bridgeReply(id, true, result.getOrNull(), null).toString()
                else bridgeReply(id, false, null, "operation_failed").toString()
            )
            refreshNativeProxyState()
        }
    }

    private fun executeBridgeMethod(method: String, args: JSONArray): Any? = when (method) {
        "requestHealthConnectPermissions" -> requestHealthConnectPermissionsFromUser()
        "refreshHealthData" -> readHealthData().let { true }
        "refreshHealthDataForDate" -> {
            val date = BridgeInputPolicy.date(args.optString(0)) ?: run {
                dispatchSyncState("error", "Date invalide")
                return false
            }
            readHealthData(date)
            true
        }
        "selectHealthDate" -> dateState.select(args.optString(0))
        "authorizeConnector" -> handleConnectorAuthorization(args.optString(0).trim()).let { true }
        "askKofi" -> requestCoach(args.optString(0), "general", args.optString(1), null).let { true }
        "askCoach" -> requestCoach(args.optString(0), args.optString(1), args.optString(2), null).let { true }
        "askDeveloper" -> requestDeveloper(args.optString(0), args.optString(1)).let { true }
        "analyzeMealImage" -> {
            val requestId = args.optString(1)
            if (BridgeInputPolicy.requestId(requestId)) dispatchAiResponse(
                requestId, false, "", "Utilisez le sélecteur nutrition sécurisé de Vitalis.", "nutrition"
            )
            false
        }
        "saveMealEstimate" -> saveLegacyMealEstimate(args.optString(0))
        "beginNutritionScan" -> beginNutritionScanInternal(
            args.optString(0), args.optString(1), args.optString(2)
        )
        "cancelNutritionScan" -> cancelNutritionScanInternal(
            args.optString(0), "user_cancelled"
        ).let { true }
        "analyzeMealSession" -> analyzeNutritionSession(args.optString(0), args.optString(1)).let { true }
        "saveNutritionMeal" -> saveNutritionMealInternal(args.optString(0), args.optString(1))
        "updateLocalNutritionMeal" -> updateLocalNutritionMealInternal(args.optString(0))
        "deleteLocalNutritionMeal" -> deleteLocalNutritionMealInternal(args.optString(0))
        "exportLocalNutrition" -> exportLocalNutritionInternal()
        "deleteAllLocalNutritionData" -> deleteAllLocalNutritionDataInternal()
        "sendDeveloperRequestToChatGpt" -> sendDeveloperRequestToChatGpt(args.optString(0))
        "speakText" -> speakStable(args.optString(0), args.optString(1).ifBlank { null }).let { true }
        "stopSpeaking" -> stopSpeaking("user_stop").let { true }
        "setMicrophoneEnabled" -> {
            if (args.optBoolean(0)) startMicrophone() else stopMicrophone("user_stop", cancelled = true)
            true
        }
        "startVoiceInput" -> startMicrophone().let { true }
        "stopVoiceInput" -> stopMicrophone("user_stop", cancelled = true).let { true }
        "openOfflineMode" -> loadOfflineFallback().let { true }
        "openClassicInterface" -> openClassicInterface().let { true }
        "openHealthConnectSettings" -> openHealthConnectSettings().let { true }
        "openAppSettings" -> openAppSettings().let { true }
        "openExternalUrl" -> openSafeExternalUrl(args.optString(0)).let { true }
        "openPrivacyDataSettings" -> startActivity(
            Intent(this, PrivacyDataActivity::class.java)
        ).let { true }
        "openKeySettings" -> startActivity(
            Intent(this, KeySettingsActivity::class.java)
                .putExtra(KeySettingsActivity.EXTRA_KEY_KIND, args.optString(0))
        ).let { true }
        "setAiHealthConsent" -> setAiConsent(args.optBoolean(0)).let { true }
        "syncWebLocalData" -> localDataStore.syncWebLocalData(args.optString(0))
        else -> false
    }

    private fun bridgeReply(id: String?, ok: Boolean, value: Any?, error: String?) =
        JSONObject().apply {
            put("id", id ?: JSONObject.NULL)
            put("ok", ok)
            put("value", value ?: JSONObject.NULL)
            put("error", error ?: JSONObject.NULL)
        }

    private fun requestHealthConnectPermissionsFromUser() {
        when (HealthConnectClient.getSdkStatus(this)) {
            HealthConnectClient.SDK_AVAILABLE -> permissionLauncher.launch(healthPermissions)
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                notifyWeb(false, "update_required", "Health Connect doit être installé ou mis à jour.")
                openHealthConnectStore()
            }
            else -> notifyWeb(false, "unavailable", "Health Connect n’est pas disponible sur cet appareil.")
        }
    }

    private fun sendDeveloperRequestToChatGpt(request: String) {
        val cleanRequest = request.trim().take(MAX_DEVELOPER_PROMPT_LENGTH)
        if (cleanRequest.isEmpty()) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Demande Vitalis", cleanRequest))
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(CHATGPT_WORK_URL))) }
    }

    private fun openClassicInterface() {
        fallbackLoaded = false
        remotePageFinished = false
        remoteRetryCount = 0
        loading.visibility = android.view.View.VISIBLE
        webView.loadUrl(VITALIS_URL)
        scheduleClassicInterfaceTimeout()
    }

    private fun openHealthConnectSettings() {
        refreshAfterConnectorReturn = true
        try { startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)) }
        catch (_: ActivityNotFoundException) { openHealthConnectStore() }
    }

    private fun openAppSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            })
        }
    }

    private fun nativeProxyState(): JSONObject {
        val localSnapshot = localDataStore.snapshot()
        return JSONObject().apply {
            put("selectedHealthDate", selectedHealthDate.toString())
            put("todayHealthDate", dateState.currentToday().toString())
            put("connectorStatus", buildConnectorPayload(lastSourcePackages))
            put("lastHealthData", lastHealthPayload)
            put("healthAiConfigured", secureSecretStore.status(AiKeyKind.HEALTH).configured)
            put("developerAiConfigured", secureSecretStore.status(AiKeyKind.DEVELOPER).configured)
            put("aiHealthConsent", hasAiHealthConsentInternal())
            put(
                "pendingNutritionScan",
                nutritionScanCoordinator.active()?.let { session ->
                    nutritionScanSessionJson(session).apply { putNutritionReadiness(session) }
                } ?: JSONObject.NULL
            )
            put("localNutrition", localNutritionMealsPayload(null))
            put("localState", JSONObject().apply {
                put("selectedCoach", localSnapshot.selectedCoach ?: JSONObject.NULL)
                put("dashboardSettings", localSnapshot.dashboardSettings ?: JSONObject.NULL)
                // Absence means this install predates native Web-state synchronization.
                // Preserve Web localStorage so sync() can migrate the upgrade journal.
                put(
                    "localJournal",
                    if (localSnapshot.preferences.containsKey(VitalisLocalDataStore.LOCAL_JOURNAL_KEY)) {
                        localSnapshot.localJournal
                    } else {
                        JSONObject.NULL
                    }
                )
            })
            put("microphoneEnabled", microphoneEnabled)
            put("speaking", textToSpeech?.isSpeaking == true)
            put("run5Fixture", if (isRun5Fixture()) buildRun5FixtureResult() else JSONObject())
            put("run6Fixture", if (isRun6Fixture()) buildRun6FixtureResult() else JSONObject())
        }
    }

    private fun buildNativeProxyBootstrap(): String {
        val state = nativeProxyState().toString()
        return """
            (function(){
              var channel=window.$NATIVE_CHANNEL_NAME;
              if(!channel||typeof channel.postMessage!=="function"){window.VitalisAndroid=null;return;}
              var state=$state;
              var localState=state.localState||{};
              try{
                // Web storage is written synchronously while the hardened native mirror is
                // updated through an asynchronous origin bridge. During an immediate Activity
                // recreation the mirror can therefore be one event behind. Hydrate only missing
                // Web values so an older mirror never rolls back the user's latest choice.
                // Explicit native imports still replace Web state in applyImportedLocalStateToWeb.
                if(localState.selectedCoach&&localStorage.getItem("vitalis-selected-coach-v312")===null)
                  localStorage.setItem("vitalis-selected-coach-v312",localState.selectedCoach);
                if(localState.dashboardSettings&&localStorage.getItem("vitalis-offline-v1")===null)
                  localStorage.setItem("vitalis-offline-v1",JSON.stringify(localState.dashboardSettings));
                if(Array.isArray(localState.localJournal)&&localStorage.getItem("vitalis-native-journal-v1")===null)
                  localStorage.setItem("vitalis-native-journal-v1",JSON.stringify(localState.localJournal));
                window.__vitalisHydratedNativeLocalState=true;
              }catch(_){window.__vitalisHydratedNativeLocalState=false;}
              var sequence=0;
              function call(method,args){
                var id="bridge-"+(++sequence)+"-"+Date.now();
                channel.postMessage(JSON.stringify({id:id,method:method,args:args||[]}));
                return id;
              }
              channel.onmessage=function(event){
                var detail;try{detail=JSON.parse(event.data)}catch(_){return;}
                window.dispatchEvent(new CustomEvent("vitalis-native-result",{detail:detail}));
              };
              function json(value){return JSON.stringify(value===undefined?null:value);}
              function pending(){return json({ok:true,pending:true});}
              var api={
                isNativeApp:function(){return true},getPlatform:function(){return "android"},
                getSelectedHealthDate:function(){return state.selectedHealthDate},
                getTodayHealthDate:function(){return state.todayHealthDate},
                selectHealthDate:function(v){state.selectedHealthDate=v;call("selectHealthDate",[v]);return true},
                getConnectorStatus:function(){return json(state.connectorStatus)},
                getLastHealthData:function(){return json(state.lastHealthData)},
                getRun5FixtureResult:function(){return json(state.run5Fixture)},
                getRun6FixtureResult:function(){return json(state.run6Fixture)},
                hasOpenAiKey:function(){return !!state.healthAiConfigured},
                hasDeveloperAiKey:function(){return !!state.developerAiConfigured},
                hasAiHealthConsent:function(){return !!state.aiHealthConsent},
                getAiConfigurationStatus:function(){return json({healthConfigured:!!state.healthAiConfigured,developerConfigured:!!state.developerAiConfigured,consented:!!state.aiHealthConsent})},
                setAiHealthConsent:function(v){state.aiHealthConsent=!!v;call("setAiHealthConsent",[!!v]);return true},
                openKeySettings:function(kind){call("openKeySettings",[kind||"health"]);return true},
                openPrivacyDataSettings:function(){call("openPrivacyDataSettings",[]);return true},
                requestHealthConnectPermissions:function(){call("requestHealthConnectPermissions",[])},
                refreshHealthData:function(){call("refreshHealthData",[])},
                refreshHealthDataForDate:function(v){call("refreshHealthDataForDate",[v])},
                authorizeConnector:function(v){call("authorizeConnector",[v])},
                askKofi:function(p,r){call("askKofi",[p,r])},
                askCoach:function(p,c,r){call("askCoach",[p,c,r])},
                askDeveloper:function(p,r){call("askDeveloper",[p,r])},
                analyzeMealImage:function(i,r){call("analyzeMealImage",[i,r])},
                saveMealEstimate:function(v){call("saveMealEstimate",[v]);return true},
                beginNutritionScan:function(s,d,o){call("beginNutritionScan",[s,d,o]);return true},
                cancelNutritionScan:function(s){call("cancelNutritionScan",[s])},
                analyzeMealSession:function(s,r){call("analyzeMealSession",[s,r])},
                saveNutritionMeal:function(s,v){call("saveNutritionMeal",[s,v]);return pending()},
                getPendingNutritionScan:function(){return json(state.pendingNutritionScan)},
                getLocalNutritionMeals:function(){return json(state.localNutrition)},
                updateLocalNutritionMeal:function(v){call("updateLocalNutritionMeal",[v]);return pending()},
                deleteLocalNutritionMeal:function(v){call("deleteLocalNutritionMeal",[v]);return pending()},
                exportLocalNutrition:function(){call("exportLocalNutrition",[]);return true},
                deleteAllLocalNutritionData:function(){call("deleteAllLocalNutritionData",[]);return true},
                sendDeveloperRequestToChatGpt:function(v){call("sendDeveloperRequestToChatGpt",[v])},
                speakText:function(v,l){call("speakText",[v,l||""])},
                stopSpeaking:function(){call("stopSpeaking",[])},
                isSpeaking:function(){return !!state.speaking},
                setMicrophoneEnabled:function(v){state.microphoneEnabled=!!v;call("setMicrophoneEnabled",[!!v])},
                isMicrophoneEnabled:function(){return !!state.microphoneEnabled},
                startVoiceInput:function(){state.microphoneEnabled=true;call("startVoiceInput",[])},
                stopVoiceInput:function(){state.microphoneEnabled=false;call("stopVoiceInput",[])},
                openOfflineMode:function(){call("openOfflineMode",[])},
                openClassicInterface:function(){call("openClassicInterface",[])},
                openHealthConnectSettings:function(){call("openHealthConnectSettings",[])},
                openAppSettings:function(){call("openAppSettings",[])},
                openExternalUrl:function(v){call("openExternalUrl",[v])},
                syncWebLocalData:function(v){call("syncWebLocalData",[v]);return true}
              };
              window.__vitalisNativeState=state;
              window.__vitalisUpdateNativeState=function(next){Object.keys(next||{}).forEach(function(k){state[k]=next[k]})};
              window.VitalisAndroid=Object.freeze(api);
              function sync(){
                try{api.syncWebLocalData(JSON.stringify({
                  selectedCoach:localStorage.getItem("vitalis-selected-coach-v312")||null,
                  dashboardSettings:JSON.parse(localStorage.getItem("vitalis-offline-v1")||"null"),
                  localJournal:JSON.parse(localStorage.getItem("vitalis-native-journal-v1")||"[]")
                }))}catch(_){}
              }
              sync();window.addEventListener("pagehide",sync);window.addEventListener("vitalis-local-state-changed",sync);
              window.dispatchEvent(new CustomEvent("vitalis-origin-bridge-ready",{detail:{origin:location.origin}}));
            })();
        """.trimIndent()
    }

    private fun refreshNativeProxyState() {
        if (!::webView.isInitialized) return
        val state = nativeProxyState()
        webView.evaluateJavascript("window.__vitalisUpdateNativeState&&window.__vitalisUpdateNativeState($state);", null)
    }

    private fun applyImportedLocalStateToWeb(generation: Long) {
        if (!::webView.isInitialized) return
        val snapshot = localDataStore.snapshot()
        val payload = JSONObject().apply {
            put("selectedCoach", snapshot.selectedCoach ?: JSONObject.NULL)
            put("dashboardSettings", snapshot.dashboardSettings ?: JSONObject.NULL)
            put("localJournal", snapshot.localJournal)
        }
        webView.evaluateJavascript(
            """
                (function(data){
                  if(data.selectedCoach===null)localStorage.removeItem('vitalis-selected-coach-v312');
                  else localStorage.setItem('vitalis-selected-coach-v312',data.selectedCoach);
                  if(data.dashboardSettings===null)localStorage.removeItem('vitalis-offline-v1');
                  else localStorage.setItem('vitalis-offline-v1',JSON.stringify(data.dashboardSettings));
                  localStorage.setItem('vitalis-native-journal-v1',JSON.stringify(data.localJournal||[]));
                  window.dispatchEvent(new CustomEvent('vitalis-local-state-changed'));
                  location.reload();
                })($payload);
            """.trimIndent(),
        ) {
            localDataStore.markImportApplied(generation)
            observedLocalImportGeneration = generation
        }
    }

    private fun initializeVoiceServices() {
        textToSpeech = TextToSpeech(this) { status ->
            textToSpeechReady = status == TextToSpeech.SUCCESS
            ttsSessionCoordinator.initialized(textToSpeechReady)
            if (textToSpeechReady) {
                textToSpeech?.language = Locale.FRENCH
                textToSpeech?.setSpeechRate(0.96f)
                textToSpeech?.setPitch(1.0f)
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        if (ttsSessionCoordinator.started(utteranceId)) {
                            dispatchVoiceEvent("speech", "speaking", true)
                        }
                    }
                    override fun onDone(utteranceId: String?) {
                        if (ttsSessionCoordinator.completed(utteranceId)) {
                            dispatchVoiceEvent("speech", "complete", false)
                        }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (ttsSessionCoordinator.failed(utteranceId)) {
                            dispatchVoiceEvent("speech", "error", false)
                        }
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
        var availability = engine.setLanguage(locale)
        var usedFallbackLocale = false
        if (availability == TextToSpeech.LANG_MISSING_DATA || availability == TextToSpeech.LANG_NOT_SUPPORTED) {
            usedFallbackLocale = true
            availability = engine.setLanguage(Locale.FRENCH)
        }
        if (availability == TextToSpeech.LANG_MISSING_DATA || availability == TextToSpeech.LANG_NOT_SUPPORTED) {
            ttsSessionCoordinator.initialized(false)
            dispatchVoiceEvent("speech", "unsupported_locale", false)
            return
        }
        engine.setSpeechRate(0.96f)
        engine.setPitch(1.0f)
        val utteranceId = "vitalis-${UUID.randomUUID()}"
        ttsSessionCoordinator.begin(utteranceId, usedFallbackLocale)
        val result = engine.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result == TextToSpeech.ERROR) {
            ttsSessionCoordinator.failed(utteranceId)
            dispatchVoiceEvent("speech", "error", false)
        }
    }

    private fun startMicrophone() {
        stopMicrophone("newer_session", cancelled = true)
        val session = recognitionSessionCoordinator.requestStart()
        val sessionId = session.sessionId ?: return
        dispatchVoiceEvent("microphone", "requesting_permission", false)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        recognitionSessionCoordinator.permissionGranted(sessionId)
        startMicrophoneInternal(sessionId)
    }

    private fun startMicrophoneInternal(sessionId: String) {
        if (!recognitionSessionCoordinator.isCurrent(sessionId)) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            microphoneEnabled = false
            recognitionSessionCoordinator.markUnavailable(sessionId)
            dispatchVoiceEvent("microphone", "unavailable", false)
            launchVoiceFallback(sessionId)
            return
        }
        pendingVoiceRetry?.let(voiceHandler::removeCallbacks)
        pendingVoiceRetry = null
        runCatching { speechRecognizer?.cancel() }
        runCatching { speechRecognizer?.destroy() }
        val recognizer = runCatching {
            SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(recognitionListener(sessionId))
            }
        }.getOrElse {
            recognitionSessionCoordinator.handleError(sessionId, RecognitionErrorCategory.START_FAILURE)
            dispatchVoiceEvent("microphone", "start_failed", false)
            launchVoiceFallback(sessionId)
            return
        }
        speechRecognizer = recognizer
        microphoneEnabled = true
        startRecognitionAttempt(sessionId)
    }

    private fun recognitionListener(sessionId: String) = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            if (recognitionSessionCoordinator.isCurrent(sessionId)) {
                dispatchVoiceEvent("microphone", "ready", true)
            }
        }

        override fun onBeginningOfSpeech() {
            if (recognitionSessionCoordinator.markListening(sessionId)) {
                dispatchVoiceEvent("microphone", "listening", true)
            }
        }

        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            if (recognitionSessionCoordinator.markProcessing(sessionId)) {
                dispatchVoiceEvent("microphone", "processing_final", true)
            }
        }

        override fun onError(error: Int) {
            if (!recognitionSessionCoordinator.isCurrent(sessionId)) return
            val action = recognitionSessionCoordinator.handleError(sessionId, recognitionErrorCategory(error))
            dispatchVoiceEvent("microphone", recognitionErrorStatus(error), false)
            when (action) {
                RecognitionErrorAction.RETRY -> scheduleMicrophoneRetry(sessionId, 700L)
                RecognitionErrorAction.LAUNCH_FALLBACK -> launchVoiceFallback(sessionId)
                RecognitionErrorAction.RETURN_TO_IDLE -> finishVoiceSession(sessionId, "idle")
                RecognitionErrorAction.END_SESSION -> finishVoiceSession(sessionId, "failed")
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val text = matches.firstOrNull().orEmpty()
            if (!recognitionSessionCoordinator.consumeFinal(sessionId, text)) return
            dispatchSpeechResults(sessionId, matches, VoiceResultKind.FINAL)
            finishVoiceSession(sessionId, "idle")
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val text = matches.firstOrNull().orEmpty()
            if (recognitionSessionCoordinator.acceptPartial(sessionId, text)) {
                dispatchSpeechResults(sessionId, matches, VoiceResultKind.PARTIAL)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun startRecognitionAttempt(sessionId: String) {
        if (!recognitionSessionCoordinator.isCurrent(sessionId)) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        }
        runCatching {
            requireNotNull(speechRecognizer) { "recognizer_missing" }.startListening(intent)
            dispatchVoiceEvent("microphone", "starting", true)
        }.onFailure {
            microphoneEnabled = false
            recognitionSessionCoordinator.handleError(sessionId, RecognitionErrorCategory.START_FAILURE)
            dispatchVoiceEvent("microphone", "start_failed", false)
            launchVoiceFallback(sessionId)
        }
    }

    private fun launchVoiceFallback(sessionId: String) {
        if (!recognitionSessionCoordinator.isCurrent(sessionId)) return
        pendingVoiceRetry?.let(voiceHandler::removeCallbacks)
        pendingVoiceRetry = null
        runCatching { speechRecognizer?.cancel() }
        runCatching { speechRecognizer?.destroy() }
        speechRecognizer = null
        microphoneEnabled = false
        if (!recognitionSessionCoordinator.markFallback(sessionId)) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Parlez maintenant")
        }
        pendingVoiceFallbackSessionId = sessionId
        voiceFallbackInProgress = true
        dispatchVoiceEvent("microphone", "fallback_starting", true)
        runCatching { voiceFallbackLauncher.launch(intent) }.onFailure {
            voiceFallbackInProgress = false
            pendingVoiceFallbackSessionId = null
            dispatchVoiceEvent("microphone", "failed", false)
            finishVoiceSession(sessionId, "idle")
        }
    }

    private fun scheduleMicrophoneRetry(sessionId: String, delayMillis: Long) {
        val retry = Runnable {
            pendingVoiceRetry = null
            if (microphoneEnabled && recognitionSessionCoordinator.isCurrent(sessionId)) {
                startRecognitionAttempt(sessionId)
            }
        }
        pendingVoiceRetry = retry
        voiceHandler.postDelayed(retry, delayMillis)
    }

    private fun stopMicrophone(reason: String, cancelled: Boolean) {
        pendingVoiceRetry?.let(voiceHandler::removeCallbacks)
        pendingVoiceRetry = null
        val sessionId = recognitionSessionCoordinator.snapshot().sessionId
        recognitionSessionCoordinator.stop(sessionId, reason, cancelled)
        microphoneEnabled = false
        runCatching { speechRecognizer?.cancel() }
        dispatchVoiceEvent("microphone", if (cancelled) "cancelled" else "stopped", false)
        recognitionSessionCoordinator.finish(sessionId)
        dispatchVoiceEvent("microphone", "idle", false)
    }

    private fun finishVoiceSession(sessionId: String, terminalStatus: String) {
        pendingVoiceRetry?.let(voiceHandler::removeCallbacks)
        pendingVoiceRetry = null
        microphoneEnabled = false
        runCatching { speechRecognizer?.cancel() }
        if (terminalStatus != "idle") {
            dispatchVoiceEvent("microphone", terminalStatus, false)
        }
        recognitionSessionCoordinator.finish(sessionId)
        dispatchVoiceEvent("microphone", "idle", false)
    }

    private fun recognitionErrorCategory(error: Int): RecognitionErrorCategory = when (error) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> RecognitionErrorCategory.PERMISSION
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> RecognitionErrorCategory.NO_SPEECH
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> RecognitionErrorCategory.NETWORK
        SpeechRecognizer.ERROR_AUDIO -> RecognitionErrorCategory.RECOVERABLE
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> RecognitionErrorCategory.MICROPHONE_BUSY
        SpeechRecognizer.ERROR_SERVER -> RecognitionErrorCategory.SERVICE_UNAVAILABLE
        else -> RecognitionErrorCategory.FATAL
    }

    private fun recognitionErrorStatus(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "permission_denied"
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "no_speech"
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network_error"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "microphone_busy"
        SpeechRecognizer.ERROR_SERVER -> "unavailable"
        else -> "failed"
    }

    private fun stopSpeaking(reason: String) {
        textToSpeech?.stop()
        ttsSessionCoordinator.stop()
        dispatchVoiceEvent("speech", "stopped_$reason", false)
        ttsSessionCoordinator.readyAfterStop()
    }

    private fun dispatchSpeechResults(
        sessionId: String,
        matches: List<String>,
        kind: VoiceResultKind
    ) {
        val payload = JSONObject().apply {
            put("text", matches.firstOrNull().orEmpty())
            put("alternatives", JSONArray(matches))
            put("partial", kind == VoiceResultKind.PARTIAL)
            put("kind", kind.name)
            put("sessionId", sessionId)
            put("microphoneEnabled", microphoneEnabled)
        }
        dispatchWebEvent("vitalis-voice-input", payload)
    }

    private fun dispatchVoiceEvent(type: String, status: String, active: Boolean) {
        val recognition = recognitionSessionCoordinator.snapshot()
        val speech = ttsSessionCoordinator.snapshot()
        val payload = JSONObject().apply {
            val presentation = VoiceUserStatusCatalog.forStatus(status)
            put("type", type)
            put("status", status)
            put("message", presentation.label)
            put("action", presentation.action ?: JSONObject.NULL)
            put("active", active)
            put("microphoneEnabled", microphoneEnabled)
            put("speaking", textToSpeech?.isSpeaking == true)
            put("recognitionState", recognition.state.name)
            put("recognitionSessionId", recognition.sessionId ?: JSONObject.NULL)
            put("recognitionRetryCount", recognition.retryCount)
            put("ttsState", speech.state.name)
            put("utteranceId", speech.utteranceId ?: JSONObject.NULL)
            put("ttsFallbackLocale", speech.fallbackLocaleUsed)
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
        if (!BridgeInputPolicy.requestId(requestId)) return
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
        val consentToken = aiConsentCoordinator.begin()
        if (consentToken == null) {
            dispatchAiResponse(
                requestId,
                false,
                "",
                "Consentement requis avant l’analyse des données santé.",
                coachId
            )
            return
        }
        val job = requestAi(
            apiKey = apiKey,
            instructions = coachInstructions(coachId),
            prompt = cleanPrompt,
            requestId = requestId,
            imageDataUrl = imageDataUrl,
            agentId = coachId,
            includeHealthContext = true,
            responseGuard = { aiConsentCoordinator.accepts(consentToken) }
        )
        activeHealthAiJobs[requestId] = job
        job.invokeOnCompletion { activeHealthAiJobs.remove(requestId, job) }
    }

    private fun requestDeveloper(prompt: String, requestId: String) {
        if (!BridgeInputPolicy.requestId(requestId)) return
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
        includeHealthContext: Boolean,
        responseTransform: (String) -> String = { it },
        responseGuard: () -> Boolean = { true },
        onAccepted: (String) -> Unit = {},
        onRejected: (AiRequestFailure) -> Unit = {}
    ): Job {
        val context = if (includeHealthContext) {
            "\n\nDonnées Vitalis disponibles (peuvent être incomplètes) : ${sanitizedHealthContext()}"
        } else {
            "\n\nContexte technique : application Android Vitalis Mobile ${BuildConfig.VERSION_NAME}; " +
                "interface classique à préserver; dépôt gillesarnaudasse65-web/Vitalis-Mobile; " +
                "Health Connect natif; les changements réels exigent validation, modification du dépôt, tests et build GitHub Actions."
        }
        return lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val inputContent = JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "input_text")
                        put(
                            "text",
                            "Demande de l’utilisateur : $prompt$context"
                        )
                    })
                    if (BridgeInputPolicy.mealImage(imageDataUrl)) {
                        put(JSONObject().apply {
                            put("type", "input_image")
                            put("image_url", imageDataUrl)
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
                val answer = responseTransform(extractResponseText(response))
                if (responseGuard()) {
                    onAccepted(answer)
                    dispatchAiResponse(requestId, true, answer, null, agentId)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (responseGuard()) {
                    val failure = AiRequestFailureClassifier.classify(error)
                    onRejected(failure)
                    dispatchAiResponse(
                        requestId,
                        false,
                        "",
                        failure.userMessage,
                        agentId,
                        failure.code
                    )
                }
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
                throw OpenAiHttpException(status)
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
            ?: throw InvalidAiResponseException()
    }

    private fun dispatchAiResponse(
        requestId: String,
        ok: Boolean,
        text: String,
        error: String?,
        agentId: String,
        errorCode: String? = null
    ) {
        dispatchWebEvent("vitalis-ai-response", JSONObject().apply {
            put("requestId", requestId)
            put("ok", ok)
            put("text", text)
            put("error", error ?: JSONObject.NULL)
            put("errorCode", errorCode ?: JSONObject.NULL)
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

    private fun readOpenAiKey(): String? = secureSecretStore.readForRequest(AiKeyKind.HEALTH)

    private fun readDeveloperOpenAiKey(): String? =
        secureSecretStore.readForRequest(AiKeyKind.DEVELOPER)

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

    private fun beginNutritionScanInternal(
        scanId: String,
        selectedDateIso: String,
        sourceValue: String
    ): Boolean {
        if (!NutritionIds.valid(scanId)) return false
        val date = BridgeInputPolicy.date(selectedDateIso) ?: return false
        val source = when (sourceValue.trim().lowercase(Locale.ROOT)) {
            "gallery" -> NutritionImageSource.GALLERY
            "camera" -> NutritionImageSource.CAMERA
            "synthetic_test" -> if (BuildConfig.DEBUG &&
                intent.getBooleanExtra(EXTRA_RUN4_FIXTURE, false)) NutritionImageSource.SYNTHETIC_TEST
                else return false
            else -> return false
        }
        val supersededScanId = nutritionScanCoordinator.active()?.scanId
        invalidateActiveNutritionAnalysis("superseded_by_new_scan")
        supersededScanId?.let { oldScanId ->
            normalizedNutritionImages.remove(oldScanId)
            activeNutritionImageCache.delete(oldScanId)
            nutritionScanCoordinator.cancel(oldScanId)?.also(::dispatchNutritionScanState)
        }
        val session = nutritionScanCoordinator.begin(scanId, date, source, Instant.now())
        persistNutritionSession(session)
        dispatchNutritionScanState(session)
        runOnUiThread {
            when (source) {
                NutritionImageSource.GALLERY -> mealPhotoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
                NutritionImageSource.CAMERA -> launchNutritionCamera()
                NutritionImageSource.SYNTHETIC_TEST -> processSyntheticNutritionImage()
            }
        }
        return true
    }

    private fun launchNutritionCamera() {
        val directory = File(cacheDir, "nutrition-captures").apply { mkdirs() }
        val file = File(directory, "capture-${UUID.randomUUID()}.jpg")
        val uri = runCatching {
            FileProvider.getUriForFile(
                this,
                "$packageName.nutrition.fileprovider",
                file
            )
        }.getOrElse {
            cancelCurrentNutritionScan("camera_unavailable")
            return
        }
        pendingNutritionCameraFile = file
        pendingNutritionCameraUri = uri
        runCatching { mealCameraLauncher.launch(uri) }.onFailure {
            file.delete()
            pendingNutritionCameraFile = null
            pendingNutritionCameraUri = null
            cancelCurrentNutritionScan("camera_unavailable")
        }
    }

    private fun handleNutritionImageUri(uri: Uri) {
        val session = nutritionScanCoordinator.active() ?: return
        nutritionScanCoordinator.markNormalizing(session.scanId)?.also {
            persistNutritionSession(it)
            dispatchNutritionScanState(it)
        }
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = nutritionImageProcessor.process(uri)
            handleNutritionImageResult(session.scanId, result)
            pendingNutritionCameraFile?.delete()
            pendingNutritionCameraFile = null
            pendingNutritionCameraUri = null
        }
    }

    private fun processSyntheticNutritionImage() {
        val session = nutritionScanCoordinator.active() ?: return
        nutritionScanCoordinator.markNormalizing(session.scanId)?.also(::dispatchNutritionScanState)
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val bitmap = createBitmap(16, 12, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.rgb(222, 145, 55))
            }
            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            bitmap.recycle()
            handleNutritionImageResult(
                session.scanId,
                nutritionImageProcessor.processBytes(stream.toByteArray(), "image/png")
            )
        }
    }

    private fun handleNutritionImageResult(scanId: String, result: NutritionImageProcessResult) {
        if (!nutritionScanCoordinator.isCurrent(scanId)) return
        val image = result.image
        if (image == null) {
            nutritionScanCoordinator.fail(scanId, result.errorCode ?: "image_error")?.also {
                persistNutritionSession(it)
                dispatchNutritionScanState(it, result.userMessage)
            }
            return
        }
        if (!activeNutritionImageCache.save(scanId, image.dataUrl)) {
            nutritionScanCoordinator.fail(scanId, "temporary_photo_store_failed")?.also {
                persistNutritionSession(it)
                dispatchNutritionScanState(
                    it,
                    "La photo n’a pas pu être conservée pour cette analyse. Réessayez."
                )
            }
            return
        }
        normalizedNutritionImages[scanId] = image.dataUrl
        val session = nutritionScanCoordinator.markImageReady(scanId, image.metadata) ?: return
        persistNutritionSession(session)
        dispatchNutritionScanState(session)
        dispatchWebEvent("vitalis-nutrition-image-ready", JSONObject().apply {
            put("scanId", scanId)
            put("selectedDate", session.selectedDate.toString())
            put("preview", activeNutritionPreviewUrl(scanId))
            put("metadata", normalizedImageMetadataJson(image.metadata))
            put("state", NutritionUiState.PHOTO_READY.name)
        })
        dispatchNutritionReadiness(session)
    }

    private fun cancelCurrentNutritionScan(reason: String) {
        nutritionScanCoordinator.active()?.scanId?.let { cancelNutritionScanInternal(it, reason) }
    }

    private fun cancelNutritionScanInternal(scanId: String, reason: String) {
        if (!NutritionIds.valid(scanId)) return
        if (nutritionScanCoordinator.active()?.scanId == scanId) invalidateActiveNutritionAnalysis(reason)
        normalizedNutritionImages.remove(scanId)
        activeNutritionImageCache.delete(scanId)
        nutritionScanCoordinator.cancel(scanId)?.also {
            persistNutritionSession(it)
            dispatchNutritionScanState(it)
        }
    }

    private fun analyzeNutritionSession(scanId: String, requestId: String) {
        if (!NutritionIds.valid(scanId) || !BridgeInputPolicy.requestId(requestId)) return
        val session = nutritionScanCoordinator.session(scanId) ?: return
        val imageDataUrl = normalizedNutritionImages[scanId]
        if (!BridgeInputPolicy.mealImage(imageDataUrl) ||
            session.analysisStatus !in setOf(
                NutritionScanStatus.READY_FOR_ANALYSIS,
                NutritionScanStatus.REVIEW,
                NutritionScanStatus.ERROR
            )) {
            dispatchAiResponse(requestId, false, "", "Photo normalisée indisponible.", "nutrition")
            return
        }
        val apiKey = readOpenAiKey()
        if (apiKey == null && !isRun4Fixture()) {
            dispatchNutritionReadiness(session)
            dispatchAiResponse(
                requestId, false, "", "Clé API requise pour analyser cette photo.",
                "nutrition", "ai_key_required"
            )
            return
        }
        if (!hasAiHealthConsentInternal()) {
            dispatchNutritionReadiness(session)
            dispatchAiResponse(
                requestId, false, "", "Consentement requis avant l’envoi externe.",
                "nutrition", "ai_consent_required"
            )
            return
        }
        val consentToken = if (isRun4Fixture()) null else aiConsentCoordinator.begin()
        if (!isRun4Fixture() && consentToken == null) {
            dispatchNutritionReadiness(session)
            dispatchAiResponse(
                requestId, false, "", "Consentement requis avant l’envoi externe.",
                "nutrition", "ai_consent_required"
            )
            return
        }
        activeNutritionAnalysisJob?.cancel()
        val generation = nutritionAnalysisGeneration.incrementAndGet()
        val analyzing = nutritionScanCoordinator.startAnalysis(scanId, requestId) ?: return
        persistNutritionSession(analyzing)
        dispatchNutritionScanState(analyzing)
        if (isRun4Fixture()) {
            val result = NutritionEstimateParser.parseForReview(RUN4_MOCK_ANALYSIS)
            val estimate = result.value ?: return
            nutritionScanCoordinator.acceptAnalysis(scanId, requestId, estimate)?.also {
                persistNutritionSession(it)
                dispatchNutritionScanState(it)
            }
            dispatchAiResponse(
                requestId,
                true,
                NutritionEstimateParser.toReviewJson(result).toString(),
                null,
                "nutrition"
            )
            return
        }
        var parsed: NutritionValidationResult<NutritionEstimate>? = null
        activeNutritionAnalysisJob = requestAi(
            apiKey = apiKey!!,
            instructions = coachInstructions("nutrition"),
            prompt = NUTRITION_ANALYSIS_PROMPT,
            requestId = requestId,
            imageDataUrl = imageDataUrl,
            agentId = "nutrition",
            includeHealthContext = true,
            responseTransform = { raw ->
                NutritionEstimateParser.parseForReview(raw).also { parsed = it }.let { result ->
                    if (!result.reviewable) throw IllegalArgumentException(
                        "Réponse nutritionnelle invalide : ${result.errors.joinToString()}"
                    )
                    NutritionEstimateParser.toReviewJson(result).toString()
                }
            },
            responseGuard = {
                generation == nutritionAnalysisGeneration.get() &&
                    (isRun4Fixture() || aiConsentCoordinator.accepts(consentToken)) &&
                    isCurrentNutritionScan(scanId, requestId)
            },
            onAccepted = {
                parsed?.value?.let { estimate ->
                    nutritionScanCoordinator.acceptAnalysis(scanId, requestId, estimate)?.also { accepted ->
                        persistNutritionSession(accepted)
                        dispatchNutritionScanState(accepted)
                    }
                }
            },
            onRejected = {
                nutritionScanCoordinator.fail(scanId, it.code)?.also { failed ->
                    persistNutritionSession(failed)
                    dispatchNutritionScanState(failed, it.userMessage)
                }
            }
        )
    }

    internal fun isCurrentNutritionScan(scanId: String, requestId: String): Boolean =
        nutritionScanCoordinator.isCurrent(scanId, requestId)

    private fun invalidateActiveNutritionAnalysis(reason: String) {
        activeNutritionAnalysisJob?.cancel()
        activeNutritionAnalysisJob = null
        nutritionAnalysisGeneration.incrementAndGet()
        nutritionScanCoordinator.active()?.takeIf {
            it.analysisStatus == NutritionScanStatus.ANALYZING
        }?.let { session ->
            nutritionScanCoordinator.fail(session.scanId, reason)?.also {
                persistNutritionSession(it)
                dispatchNutritionScanState(it)
            }
        }
    }

    private fun hasAiHealthConsentInternal(): Boolean =
        aiConsentCoordinator.isConsented() || isRun4Fixture()

    private fun setAiConsent(consented: Boolean) {
        appPreferences.edit { putBoolean(AI_HEALTH_CONSENT, consented) }
        if (consented) {
            aiConsentCoordinator.grant()
            dispatchWebEvent("vitalis-ai-consent", JSONObject().put("consented", true))
            nutritionScanCoordinator.active()?.takeIf {
                normalizedNutritionImages.containsKey(it.scanId)
            }?.let(::dispatchNutritionReadiness)
        } else revokeAiConsent("consent_revoked")
    }

    private fun revokeAiConsent(reason: String) {
        appPreferences.edit { putBoolean(AI_HEALTH_CONSENT, false) }
        aiConsentCoordinator.revoke()
        activeHealthAiJobs.values.forEach(Job::cancel)
        activeHealthAiJobs.clear()
        invalidateActiveNutritionAnalysis(reason)
        dispatchWebEvent("vitalis-ai-consent", JSONObject().apply {
            put("consented", false)
            put("reason", reason)
        })
    }

    private fun isRun4Fixture(): Boolean = BuildConfig.DEBUG &&
        intent.getBooleanExtra(EXTRA_RUN4_FIXTURE, false)

    private fun isRun5Fixture(): Boolean = BuildConfig.DEBUG &&
        intent.getBooleanExtra(EXTRA_RUN5_FIXTURE, false)

    private fun isRun6Fixture(): Boolean = BuildConfig.DEBUG &&
        intent.getBooleanExtra(EXTRA_RUN6_FIXTURE, false)

    private fun buildRun5FixtureResult(): JSONObject {
        val oneShot = RecognitionSessionCoordinator { "fixture-voice" }
        oneShot.requestStart()
        oneShot.permissionGranted("fixture-voice")
        oneShot.markListening("fixture-voice")
        val partialAccepted = oneShot.acceptPartial("fixture-voice", "partial fixture")
        val firstFinalAccepted = oneShot.consumeFinal("fixture-voice", "final fixture")
        val duplicateFinalRejected = !oneShot.consumeFinal("fixture-voice", "final fixture")

        var sequence = 0
        val ownership = RecognitionSessionCoordinator { "fixture-${++sequence}" }
        ownership.requestStart()
        ownership.permissionGranted("fixture-1")
        ownership.markListening("fixture-1")
        ownership.requestStart()
        val staleFinalRejected = !ownership.consumeFinal("fixture-1", "stale fixture")

        val cancelled = RecognitionSessionCoordinator { "fixture-cancel" }
        cancelled.requestStart()
        cancelled.permissionGranted("fixture-cancel")
        cancelled.markListening("fixture-cancel")
        cancelled.stop("fixture-cancel", "background", cancelled = true)
        val cancelledFinalRejected = !cancelled.consumeFinal("fixture-cancel", "late fixture")

        val retry = RecognitionSessionCoordinator { "fixture-retry" }
        retry.requestStart()
        retry.permissionGranted("fixture-retry")
        retry.markListening("fixture-retry")
        val firstRetry = retry.handleError(
            "fixture-retry",
            RecognitionErrorCategory.RECOVERABLE
        )
        val secondRetry = retry.handleError(
            "fixture-retry",
            RecognitionErrorCategory.RECOVERABLE
        )

        val tts = TtsSessionCoordinator().apply { initialized(true) }
        tts.begin("fixture-tts-old", false)
        tts.begin("fixture-tts-new", true)
        val oldTtsCallbackRejected = !tts.completed("fixture-tts-old")
        tts.stop()

        fun connectorState(
            id: String,
            installed: Boolean,
            permission: Boolean,
            records: Boolean
        ): String = ConnectorStateResolver.resolve(
            requireNotNull(ConnectorCatalog.find(id)),
            ConnectorEvidence(installed, true, permission, records)
        ).name

        return JSONObject().apply {
            put("partialAccepted", partialAccepted)
            put("firstFinalAccepted", firstFinalAccepted)
            put("duplicateFinalRejected", duplicateFinalRejected)
            put("staleFinalRejected", staleFinalRejected)
            put("cancelledFinalRejected", cancelledFinalRejected)
            put("firstRetry", firstRetry.name)
            put("secondRetry", secondRetry.name)
            put("oldTtsCallbackRejected", oldTtsCallbackRejected)
            put("ttsStopped", tts.snapshot().state == TtsState.TTS_STOPPED)
            put("permissionRequired", connectorState("samsung_health", true, false, false))
            put("healthNoData", connectorState("samsung_health", true, true, false))
            put("providerData", connectorState("samsung_health", true, true, true))
            put("setupOnly", connectorState("fiton", true, false, false))
            put("directUnavailable", connectorState("strava", true, false, false))
            put("appleHealth", connectorState("apple_health", false, false, false))
        }
    }

    private fun buildRun6FixtureResult(): JSONObject {
        val consent = AiConsentCoordinator(true)
        val issuedBeforeRevoke = consent.begin()
        consent.revoke()
        val lateResponseRejected = !consent.accepts(issuedBeforeRevoke)

        val fixtureKey = "sk-" + "run6".repeat(12)
        val saved = secureSecretStore.save(AiKeyKind.HEALTH, fixtureKey)
        val status = secureSecretStore.status(AiKeyKind.HEALTH)
        val usableOnlyInternally = secureSecretStore.readForRequest(AiKeyKind.HEALTH) == fixtureKey
        secureSecretStore.delete(AiKeyKind.HEALTH)
        val deleted = !secureSecretStore.status(AiKeyKind.HEALTH).configured

        val export = VitalisExportCodec.encode(
            VitalisLocalSnapshot(
                preferences = mapOf(
                    SELECTED_HEALTH_DATE_KEY to "2026-09-27",
                    "openai_api_key" to fixtureKey
                ),
                nutrition = JSONArray(),
                selectedCoach = "general",
                dashboardSettings = JSONObject().put("compact", true),
                localJournal = JSONArray(),
                consentState = true
            ),
            BuildConfig.VERSION_NAME,
            Instant.parse("2026-09-27T00:00:00Z")
        )
        val exportBytes = export.toByteArray(StandardCharsets.UTF_8)
        val oversized = ByteArray(VitalisExportCodec.MAX_IMPORT_BYTES + 1)
        val future = JSONObject(export).put("version", VitalisExportCodec.VERSION + 1)
            .toString().toByteArray(StandardCharsets.UTF_8)

        return JSONObject().apply {
            put("exactOriginAllowed", OriginBridgePolicy.canInvoke(
                OriginBridgePolicy.APPASSETS_ORIGIN, true, "refreshHealthData"
            ))
            put("subframeRejected", !OriginBridgePolicy.canInvoke(
                OriginBridgePolicy.APPASSETS_ORIGIN, false, "refreshHealthData"
            ))
            put("deceptiveOriginRejected", !OriginBridgePolicy.canInvoke(
                "https://appassets.androidplatform.net.attacker.invalid", true, "refreshHealthData"
            ))
            put("dataOriginRejected", !OriginBridgePolicy.canInvoke(
                "data:text/html,hello", true, "refreshHealthData"
            ))
            put("fileOriginRejected", !OriginBridgePolicy.canInvoke(
                "file:///tmp/vitalis.html", true, "refreshHealthData"
            ))
            put("javascriptOriginRejected", !OriginBridgePolicy.canInvoke(
                "javascript:alert(1)", true, "refreshHealthData"
            ))
            put("lateResponseRejected", lateResponseRejected)
            put("keySaved", saved)
            put("keyStatusMasked", status.configured && status.maskedSuffix?.contains("run6") == true)
            put("keyUsableOnlyInternally", usableOnlyInternally)
            put("keyDeleted", deleted)
            put("exportExcludesSecrets", !export.contains(fixtureKey) && !export.contains("openai_api_key"))
            put("validImportAccepted", VitalisExportCodec.validate(exportBytes).valid)
            put("malformedImportRejected", !VitalisExportCodec.validate("{".toByteArray()).valid)
            put("oversizedImportRejected", !VitalisExportCodec.validate(oversized).valid)
            put("futureImportRejected", !VitalisExportCodec.validate(future).valid)
        }
    }

    private fun saveLegacyMealEstimate(rawJson: String): Boolean {
        val source = runCatching { JSONObject(rawJson) }.getOrNull() ?: return false
        val scanId = source.optString("scanId")
        if (!NutritionIds.valid(scanId)) return false
        return saveNutritionMealInternal(scanId, rawJson).optBoolean("ok", false)
    }

    private fun saveNutritionMealInternal(scanId: String, rawEstimate: String): JSONObject {
        if (!NutritionIds.valid(scanId) || !BridgeInputPolicy.mealJsonSize(rawEstimate)) {
            return nutritionResult(false, "invalid_payload")
        }
        val session = nutritionScanCoordinator.session(scanId)
            ?: restoreNutritionSession(scanId)
            ?: return nutritionResult(false, "unknown_scan")
        val parsed = NutritionEstimateParser.parseForReview(rawEstimate)
        val estimate = parsed.value
        if (!parsed.savable || estimate == null) {
            return nutritionResult(false, "invalid_estimate", parsed.errors + parsed.warnings)
        }
        nutritionScanCoordinator.startSaving(scanId)?.also(::dispatchNutritionScanState)
        val existing = nutritionMealStore.list().value?.firstOrNull { it.id == session.mealId }
        val now = Instant.now()
        val record = NutritionMealRecord(
            id = session.mealId,
            date = session.selectedDate,
            createdAt = existing?.createdAt ?: session.createdAt,
            updatedAt = now,
            source = "Vitalis Scanner",
            scanId = scanId,
            mealName = estimate.mealName,
            foodItems = estimate.foodItems,
            nutrients = estimate.nutrients,
            confidence = estimate.confidence,
            notes = estimate.uncertaintyNotes,
            estimated = true
        )
        val saved = nutritionMealStore.upsert(record)
        if (!saved.success) {
            nutritionScanCoordinator.fail(scanId, saved.errorCode ?: "save_failed")?.also(::dispatchNutritionScanState)
            return nutritionResult(false, saved.errorCode ?: "save_failed")
        }
        val completed = nutritionScanCoordinator.markSaved(scanId)
        completed?.also {
            persistNutritionSession(it)
            dispatchNutritionScanState(it)
        }
        normalizedNutritionImages.remove(scanId)
        activeNutritionImageCache.delete(scanId)
        readHealthData(selectedHealthDate)
        return nutritionResult(true, null).apply {
            put("meal", NutritionMealCodec.encode(record))
            put("created", existing == null)
        }
    }

    private fun localNutritionMealsPayload(dateIso: String?): JSONObject {
        val date = dateIso?.takeIf { it.isNotBlank() }?.let(BridgeInputPolicy::date)
        if (dateIso != null && dateIso.isNotBlank() && date == null) {
            return nutritionResult(false, "invalid_date")
        }
        val result = nutritionMealStore.list(date)
        return nutritionResult(result.success, result.errorCode).apply {
            put("meals", JSONArray(result.value.orEmpty().map(NutritionMealCodec::encode)))
            put("preservedInvalidRecords", result.preservedInvalidRecords)
        }
    }

    private fun updateLocalNutritionMealInternal(raw: String): JSONObject {
        val parsed = NutritionMealInput.parse(raw, Clock.systemUTC())
        val candidate = parsed.value ?: return nutritionResult(false, parsed.errors.firstOrNull() ?: "invalid_meal")
        val existing = nutritionMealStore.list().value?.firstOrNull { it.id == candidate.id }
            ?: return nutritionResult(false, "meal_not_found")
        val updated = candidate.copy(
            createdAt = existing.createdAt,
            updatedAt = Instant.now(),
            scanId = existing.scanId,
            source = existing.source
        )
        val result = nutritionMealStore.upsert(updated)
        if (result.success) readHealthData(selectedHealthDate)
        return nutritionResult(result.success, result.errorCode).apply {
            if (result.success) put("meal", NutritionMealCodec.encode(updated))
        }
    }

    private fun deleteLocalNutritionMealInternal(mealId: String): JSONObject {
        val result = nutritionMealStore.delete(mealId)
        if (result.value == true) readHealthData(selectedHealthDate)
        return nutritionResult(result.success, result.errorCode).apply {
            put("deleted", result.value == true)
        }
    }

    private fun exportLocalNutritionInternal(): Boolean {
        val result = nutritionMealStore.list()
        val meals = result.value ?: return false
        pendingNutritionExport = NutritionExportCodec.encode(
            meals,
            Instant.now(),
            BuildConfig.VERSION_NAME
        )
        runOnUiThread {
            nutritionExportLauncher.launch("vitalis-nutrition-${LocalDate.now()}.json")
        }
        return true
    }

    private fun deleteAllLocalNutritionDataInternal(): Boolean {
        val result = nutritionMealStore.deleteAll()
        if (result.success) {
            val activeScanId = nutritionScanCoordinator.active()?.scanId
            invalidateActiveNutritionAnalysis("local_data_deleted")
            activeScanId?.let { nutritionScanCoordinator.cancel(it) }
            normalizedNutritionImages.clear()
            activeNutritionImageCache.clear()
            appPreferences.edit { remove(PENDING_NUTRITION_SCAN_KEY) }
            dispatchWebEvent("vitalis-nutrition-local-cleared", JSONObject().put("ok", true))
            readHealthData(selectedHealthDate)
        }
        dispatchNutritionOperation("delete_all", result.success, result.errorCode)
        return result.success
    }

    private fun manualMealsForDate(date: LocalDate): List<JSONObject> =
        nutritionMealStore.list(date).value.orEmpty().map(NutritionMealCodec::encode)

    private fun nutritionResult(ok: Boolean, error: String?, issues: List<String> = emptyList()) =
        JSONObject().apply {
            put("ok", ok)
            put("error", error ?: JSONObject.NULL)
            put("issues", JSONArray(issues))
        }

    private fun persistNutritionSession(session: NutritionScanSession) {
        if (session.analysisStatus in setOf(
                NutritionScanStatus.SAVED,
                NutritionScanStatus.CANCELLED
            )) {
            appPreferences.edit { remove(PENDING_NUTRITION_SCAN_KEY) }
            return
        }
        val payload = JSONObject().apply {
            put("schemaVersion", 2)
            put("scanId", session.scanId)
            put("createdAt", session.createdAt.toString())
            put("selectedDate", session.selectedDate.toString())
            put("imageSource", session.imageSource.name)
            put("analysisStatus", session.analysisStatus.name)
            put("analysisRequestId", session.analysisRequestId ?: JSONObject.NULL)
            put("savedMealId", session.savedMealId ?: JSONObject.NULL)
            put("errorCode", session.errorCode ?: JSONObject.NULL)
            session.normalizedImageMetadata?.let {
                put("normalizedImageMetadata", normalizedImageMetadataJson(it))
            }
            session.draftResult?.let {
                put("draftResult", NutritionEstimateParser.estimateJson(it))
            }
        }
        appPreferences.edit { putString(PENDING_NUTRITION_SCAN_KEY, payload.toString()) }
    }

    private fun restoreNutritionSession(expectedScanId: String): NutritionScanSession? {
        val raw = appPreferences.getString(PENDING_NUTRITION_SCAN_KEY, null) ?: return null
        val source = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val scanId = source.optString("scanId")
        if (scanId != expectedScanId || !NutritionIds.valid(scanId)) return null
        val date = BridgeInputPolicy.date(source.optString("selectedDate")) ?: return null
        val createdAt = runCatching { Instant.parse(source.optString("createdAt")) }.getOrNull()
            ?: return null
        val imageSource = runCatching {
            NutritionImageSource.valueOf(source.optString("imageSource"))
        }.getOrNull() ?: return null
        val persistedStatus = runCatching {
            NutritionScanStatus.valueOf(source.optString("analysisStatus"))
        }.getOrNull() ?: return null
        val cachedImage = activeNutritionImageCache.load(scanId)
        if (cachedImage != null) normalizedNutritionImages[scanId] = cachedImage
        val interrupted = persistedStatus in setOf(
                NutritionScanStatus.NORMALIZING,
                NutritionScanStatus.ANALYZING
            )
        val needsPhoto = persistedStatus in setOf(
            NutritionScanStatus.READY_FOR_ANALYSIS,
            NutritionScanStatus.ANALYZING,
            NutritionScanStatus.REVIEW,
            NutritionScanStatus.ERROR
        )
        val restoredStatus = if (interrupted || (needsPhoto && cachedImage == null)) {
            NutritionScanStatus.ERROR
        } else persistedStatus
        val metadata = source.optJSONObject("normalizedImageMetadata")?.let(::decodeNormalizedImageMetadata)
        val draft = source.optJSONObject("draftResult")?.let {
            NutritionEstimateParser.parseForReview(it.toString()).value
        }
        return nutritionScanCoordinator.restore(
            NutritionScanSession(
                scanId = scanId,
                mealId = "meal-$scanId",
                createdAt = createdAt,
                selectedDate = date,
                imageSource = imageSource,
                normalizedImageMetadata = metadata,
                analysisStatus = restoredStatus,
                analysisRequestId = source.optString("analysisRequestId")
                    .takeIf { source.has("analysisRequestId") && !source.isNull("analysisRequestId") && it.isNotBlank() },
                savedMealId = source.optString("savedMealId")
                    .takeIf { source.has("savedMealId") && !source.isNull("savedMealId") && it.isNotBlank() },
                draftResult = draft,
                errorCode = if (needsPhoto && cachedImage == null) "photo_cache_missing"
                    else if (interrupted) "scan_interrupted"
                    else source.optString("errorCode")
                        .takeIf { source.has("errorCode") && !source.isNull("errorCode") && it.isNotBlank() }
            )
        )
    }

    private fun normalizedImageMetadataJson(metadata: NormalizedImageMetadata) = JSONObject().apply {
        put("sourceMimeType", metadata.sourceMimeType)
        put("sourceBytes", metadata.sourceBytes)
        put("sourceWidth", metadata.sourceWidth)
        put("sourceHeight", metadata.sourceHeight)
        put("normalizedMimeType", metadata.normalizedMimeType)
        put("normalizedBytes", metadata.normalizedBytes)
        put("normalizedWidth", metadata.normalizedWidth)
        put("normalizedHeight", metadata.normalizedHeight)
        put("orientationApplied", metadata.orientationApplied)
    }

    private fun decodeNormalizedImageMetadata(source: JSONObject): NormalizedImageMetadata? =
        runCatching {
            NormalizedImageMetadata(
                sourceMimeType = source.getString("sourceMimeType"),
                sourceBytes = source.getLong("sourceBytes"),
                sourceWidth = source.getInt("sourceWidth"),
                sourceHeight = source.getInt("sourceHeight"),
                normalizedMimeType = source.getString("normalizedMimeType"),
                normalizedBytes = source.getInt("normalizedBytes"),
                normalizedWidth = source.getInt("normalizedWidth"),
                normalizedHeight = source.getInt("normalizedHeight"),
                orientationApplied = source.getBoolean("orientationApplied")
            )
        }.getOrNull()

    private fun activeNutritionPreviewUrl(scanId: String): String =
        "https://$LOCAL_ASSET_HOST$ACTIVE_SCAN_ASSET_PATH$scanId.jpg"

    private fun dispatchNutritionScanState(
        session: NutritionScanSession,
        message: String? = null
    ) {
        dispatchWebEvent(
            "vitalis-nutrition-scan-state",
            nutritionScanSessionJson(session).apply {
                put("message", message ?: JSONObject.NULL)
                putNutritionReadiness(session)
            }
        )
    }

    private fun dispatchNutritionReadiness(session: NutritionScanSession) {
        dispatchWebEvent(
            "vitalis-nutrition-analysis-readiness",
            nutritionScanSessionJson(session).apply { putNutritionReadiness(session) }
        )
    }

    private fun JSONObject.putNutritionReadiness(session: NutritionScanSession) {
        val configured = isRun4Fixture() || secureSecretStore.status(AiKeyKind.HEALTH).configured
        val consented = hasAiHealthConsentInternal()
        val hasPhoto = normalizedNutritionImages.containsKey(session.scanId)
        put("aiConfigured", configured)
        put("consented", consented)
        put(
            "uiState",
            NutritionUiStateResolver.resolve(
                hasPhoto,
                configured,
                consented,
                session.analysisStatus,
                session.errorCode
            ).name
        )
    }

    private fun nutritionScanSessionJson(session: NutritionScanSession) = JSONObject().apply {
        put("scanId", session.scanId)
        put("mealId", session.mealId)
        put("selectedDate", session.selectedDate.toString())
        put("source", session.imageSource.name.lowercase(Locale.ROOT))
        put("status", session.analysisStatus.name.lowercase(Locale.ROOT))
        put("requestId", session.analysisRequestId ?: JSONObject.NULL)
        put("savedMealId", session.savedMealId ?: JSONObject.NULL)
        put("errorCode", session.errorCode ?: JSONObject.NULL)
        session.normalizedImageMetadata?.let { put("image", normalizedImageMetadataJson(it)) }
        if (normalizedNutritionImages.containsKey(session.scanId)) {
            put("previewUrl", activeNutritionPreviewUrl(session.scanId))
        }
        session.draftResult?.let {
            put("draftResult", NutritionEstimateParser.toReviewJson(
                NutritionValidationResult(it)
            ))
        }
    }

    private fun dispatchNutritionOperation(operation: String, ok: Boolean, error: String?) {
        dispatchWebEvent("vitalis-nutrition-operation", JSONObject().apply {
            put("operation", operation)
            put("ok", ok)
            put("error", error ?: JSONObject.NULL)
        })
    }

    private fun handleConnectorAuthorization(connectorId: String) {
        val definition = ConnectorCatalog.find(connectorId)
        if (definition == null) {
            notifyWeb(false, "invalid_connector", "Connecteur inconnu.")
            return
        }
        if (definition.id == "health_connect") {
            when (HealthConnectClient.getSdkStatus(this)) {
                HealthConnectClient.SDK_AVAILABLE -> permissionLauncher.launch(healthPermissions)
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> openHealthConnectStore()
                else -> notifyWeb(false, "unavailable", "Health Connect n’est pas disponible sur cet appareil.")
            }
            return
        }
        if (definition.capability == ConnectorCapability.UNSUPPORTED_PLATFORM) {
            notifyWeb(
                false,
                "unsupported_android",
                "${definition.name} n’est pas accessible depuis Android."
            )
            return
        }
        if (definition.capability == ConnectorCapability.HEALTH_CONNECT) {
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
        if (
            definition.capability in setOf(
                ConnectorCapability.DIRECT_OAUTH,
                ConnectorCapability.DIRECT_API
            ) && !definition.directIntegrationImplemented
        ) {
            notifyWeb(
                false,
                "direct_connection_not_implemented",
                "Connexion directe non implémentée pour ${definition.name}. Vitalis peut seulement ouvrir l’application pour sa configuration."
            )
        }
        openInstalledConnector(definition)
    }

    private fun openInstalledConnector(definition: ConnectorDefinition) {
        val packageName = definition.packages.firstOrNull(::isPackageInstalled)
        if (packageName != null) {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null && runCatching { startActivity(launchIntent) }.isSuccess) {
                refreshAfterConnectorReturn = true
                notifyWeb(
                    true,
                    "connector_app_opened",
                    if (definition.capability == ConnectorCapability.HEALTH_CONNECT)
                        "${definition.name} est ouvert. Activez son partage vers Health Connect, puis revenez dans Vitalis et actualisez."
                    else
                        "${definition.name} est ouvert pour sa configuration. Cela ne signifie pas que le fournisseur est connecté à Vitalis."
                )
                return
            }
        }
        val storeOpened = openConnectorStore(definition.name)
        notifyWeb(
            false,
            if (storeOpened) "connector_not_installed" else "connector_launch_failed",
            if (storeOpened) {
                "${definition.name} n’a pas été détecté ou n’a pas pu être ouvert. La page d’installation est ouverte."
            } else {
                "${definition.name} n’a pas pu être ouvert et aucune boutique compatible n’est disponible."
            }
        )
    }

    @Suppress("DEPRECATION")
    private fun isPackageInstalled(packageName: String): Boolean = runCatching {
        packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)

    private fun openConnectorStore(name: String): Boolean {
        val query = Uri.encode(name)
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$query&c=apps")))
            true
        } catch (_: ActivityNotFoundException) {
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=$query&c=apps")))
            }.isSuccess
        }
    }

    private fun healthConnectAvailability(): HealthConnectAvailability =
        when (HealthConnectClient.getSdkStatus(this)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                if (isPackageInstalled(HEALTH_CONNECT_PACKAGE)) {
                    HealthConnectAvailability.PROVIDER_UPDATE_REQUIRED
                } else {
                    HealthConnectAvailability.PROVIDER_NOT_INSTALLED
                }
            else -> HealthConnectAvailability.NOT_SUPPORTED
        }

    private fun ensureHealthConnectClient() {
        if (
            healthConnectClient == null &&
            HealthConnectClient.getSdkStatus(this) == HealthConnectClient.SDK_AVAILABLE
        ) {
            val client = HealthConnectClient.getOrCreate(this)
            healthConnectClient = client
            healthConnectDataSource = AndroidHealthConnectDataSource(client)
        }
    }

    private fun healthPermissionState(granted: Set<String>): HealthConnectStateModel =
        HealthConnectStateResolver.permissionState(
            availability = healthConnectAvailability(),
            requiredPermissions = healthPermissions,
            grantedPermissions = granted,
            permissionRequested = appPreferences.getBoolean(
                HEALTH_PERMISSION_REQUESTED_KEY,
                false
            ),
            previouslyAuthorized = appPreferences.getBoolean(
                HEALTH_PERMISSION_EVER_AUTHORIZED_KEY,
                false
            )
        )

    private fun healthStateJson(state: HealthConnectStateModel) = JSONObject().apply {
        put("code", state.code.name)
        put("availability", when (state.code) {
            HealthConnectStateCode.NOT_SUPPORTED,
            HealthConnectStateCode.PROVIDER_NOT_INSTALLED -> "UNAVAILABLE"
            HealthConnectStateCode.PROVIDER_UPDATE_REQUIRED -> "PROVIDER_UPDATE_REQUIRED"
            else -> "AVAILABLE"
        })
        put("uiState", HealthConnectUiStateResolver.resolve(state.code).name)
        put("authorized", state.code in setOf(
            HealthConnectStateCode.AUTHORIZED_NO_DATA,
            HealthConnectStateCode.AUTHORIZED_WITH_DATA
        ))
        put("label", state.label)
        put("reason", state.reason ?: JSONObject.NULL)
        put("retryAction", state.retryAction ?: JSONObject.NULL)
        put("missingPermissions", JSONArray(state.missingPermissions))
    }

    private fun healthMetricJson(result: HealthMetricResult) = JSONObject().apply {
        put("status", result.status.name)
        put("value", result.value ?: JSONObject.NULL)
        put("unit", result.unit)
        put("sampleCount", result.sampleCount)
        put("sourceCount", result.sourceCount)
        put("errorCode", result.errorCode ?: JSONObject.NULL)
    }

    private fun unavailableMetric(unit: String, state: HealthConnectStateModel): HealthMetricResult =
        when (state.code) {
            HealthConnectStateCode.PERMISSION_NOT_REQUESTED,
            HealthConnectStateCode.PERMISSION_DENIED,
            HealthConnectStateCode.PARTIAL_PERMISSION ->
                HealthMetricResult.notAuthorized(unit)
            HealthConnectStateCode.SYNC_ERROR -> HealthMetricResult.error(
                unit,
                state.reason ?: "sync_error"
            )
            else -> HealthMetricResult.unsupported(unit)
        }

    private fun healthMetric(
        authorized: Boolean,
        recordCount: Int,
        sourcePackages: List<String>,
        unit: String,
        value: Number?,
        sampleCount: Int = recordCount
    ): HealthMetricResult = when {
        !authorized -> HealthMetricResult.notAuthorized(unit)
        recordCount == 0 || value == null -> HealthMetricResult.noData(unit)
        else -> HealthMetricResult.data(
            value,
            unit,
            sampleCount,
            sourcePackages.filter { it.isNotBlank() }.distinct().size
        )
    }

    internal fun isCurrentHealthSync(generation: Long): Boolean =
        generation == healthSyncGeneration.get()

    private fun dispatchManualOnlyHealthData(
        selectedDate: LocalDate,
        state: HealthConnectStateModel
    ) {
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val interval = HealthDayIntervals.forDate(selectedDate, zone)
        val rangeStart = interval.start
        val rangeEnd = interval.endExclusive
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
            put("periodHours", Duration.between(rangeStart, rangeEnd).toHours())
            put("selectedDate", selectedDate.toString())
            put("rangeStart", rangeStart.toString())
            put("rangeEnd", rangeEnd.toString())
            put("steps", JSONObject.NULL)
            put("sleepMinutes", JSONObject.NULL)
            put("exerciseMinutes", JSONObject.NULL)
            put("averageHeartRate", JSONObject.NULL)
            put("hydrationLitres", JSONObject.NULL)
            put("distanceKm", JSONObject.NULL)
            put("activeCalories", JSONObject.NULL)
            put("oxygenPercent", JSONObject.NULL)
            put("weightKg", JSONObject.NULL)
            put("healthConnectState", healthStateJson(state))
            put("metrics", JSONObject().apply {
                put("steps", healthMetricJson(unavailableMetric("count", state)))
                put("sleepMinutes", healthMetricJson(unavailableMetric("min", state)))
                put("exerciseMinutes", healthMetricJson(unavailableMetric("min", state)))
                put("averageHeartRate", healthMetricJson(unavailableMetric("bpm", state)))
                put("hydrationLitres", healthMetricJson(unavailableMetric("L", state)))
                put("distanceKm", healthMetricJson(unavailableMetric("km", state)))
                put("activeCalories", healthMetricJson(unavailableMetric("kcal", state)))
                put("oxygenPercent", healthMetricJson(unavailableMetric("%", state)))
                put("weightKg", healthMetricJson(unavailableMetric("kg", state)))
                put(
                    "nutrition",
                    healthMetricJson(
                        if (manualMeals.isEmpty()) unavailableMetric("meal", state)
                        else HealthMetricResult.data(
                            manualMeals.size,
                            "meal",
                            manualMeals.size,
                            1
                        )
                    )
                )
                put("totalCalories", healthMetricJson(HealthMetricResult.unsupported("kcal")))
                put("heartRateVariability", healthMetricJson(HealthMetricResult.unsupported("ms")))
                put("respiratoryRate", healthMetricJson(HealthMetricResult.unsupported("breaths/min")))
                put("bloodPressure", healthMetricJson(HealthMetricResult.unsupported("mmHg")))
                put("bodyTemperature", healthMetricJson(HealthMetricResult.unsupported("°C")))
                put("bodyFat", healthMetricJson(HealthMetricResult.unsupported("%")))
            })
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
        dispatchSyncState(state.code.name.lowercase(Locale.US), state.reason)
    }

    private fun readHealthData(selectedDate: LocalDate = selectedHealthDate) {
        selectedHealthDate = selectedDate
        val generation = healthSyncGeneration.incrementAndGet()
        healthSyncJob?.cancel()
        ensureHealthConnectClient()
        if (BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_RUN2_FIXTURE, false)) {
            debugRefreshRequests.add(selectedDate.toString())
            val payload = JSONObject().put("selectedDate", selectedDate.toString())
            lastHealthPayload = payload
            dispatchHealthData(payload)
            dispatchSyncState("complete")
            return
        }
        if (BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_RUN3_FIXTURE, false)) {
            debugRefreshRequests.add(selectedDate.toString())
            val state = HealthConnectStateModel(
                HealthConnectStateCode.PARTIAL_PERMISSION,
                "Autorisation Health Connect partielle",
                retryAction = "request_permissions",
                missingPermissions = listOf("android.permission.health.READ_SLEEP")
            )
            val payload = JSONObject().apply {
                put("selectedDate", selectedDate.toString())
                put("rangeStart", HealthDayIntervals.forDate(
                    selectedDate,
                    ZoneId.systemDefault()
                ).start.toString())
                put("rangeEnd", HealthDayIntervals.forDate(
                    selectedDate,
                    ZoneId.systemDefault()
                ).endExclusive.toString())
                put("healthConnectState", healthStateJson(state))
                put("metrics", JSONObject().apply {
                    put("steps", healthMetricJson(HealthMetricResult.noData("count")))
                    put("sleepMinutes", healthMetricJson(
                        HealthMetricResult.notAuthorized("min")
                    ))
                })
            }
            lastHealthPayload = payload
            dispatchHealthData(payload)
            dispatchSyncState(state.code.name.lowercase(Locale.US))
            return
        }
        if (BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_RUN4_FIXTURE, false)) {
            debugRefreshRequests.add(selectedDate.toString())
            dispatchManualOnlyHealthData(
                selectedDate,
                HealthConnectStateModel(
                    HealthConnectStateCode.NOT_SUPPORTED,
                    "Fixture nutrition locale",
                    reason = "run4_fixture"
                )
            )
            return
        }
        if (BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_RUN5_FIXTURE, false)) {
            debugRefreshRequests.add(selectedDate.toString())
            dispatchManualOnlyHealthData(
                selectedDate,
                HealthConnectStateModel(
                    HealthConnectStateCode.NOT_SUPPORTED,
                    "Fixture voix et connecteurs",
                    reason = "run5_fixture"
                )
            )
            return
        }
        if (BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_RUN6_FIXTURE, false)) {
            debugRefreshRequests.add(selectedDate.toString())
            dispatchManualOnlyHealthData(
                selectedDate,
                HealthConnectStateModel(
                    HealthConnectStateCode.NOT_SUPPORTED,
                    "Fixture sécurité et confidentialité",
                    reason = "run6_fixture"
                )
            )
            return
        }
        val client = healthConnectClient
        val dataSource = healthConnectDataSource
        if (client == null || dataSource == null) {
            dispatchManualOnlyHealthData(
                selectedDate,
                healthPermissionState(emptySet())
            )
            return
        }
        dispatchSyncState("refreshing")
        healthSyncJob = lifecycleScope.launch {
            val granted = try {
                client.permissionController.getGrantedPermissions()
            } catch (error: SecurityException) {
                lastHealthConnectPermissionGranted = false
                if (isCurrentHealthSync(generation)) {
                    appPreferences.edit {
                        putBoolean(HEALTH_PERMISSION_REQUESTED_KEY, true)
                        putBoolean(HEALTH_PERMISSION_EVER_AUTHORIZED_KEY, true)
                    }
                    dispatchManualOnlyHealthData(
                        selectedDate,
                        HealthConnectStateModel(
                            HealthConnectStateCode.PERMISSION_DENIED,
                            "Autorisation Health Connect révoquée",
                            reason = "permission_revoked",
                            retryAction = "request_permissions",
                            missingPermissions = healthPermissions.sorted()
                        )
                    )
                }
                return@launch
            }
            lastHealthConnectPermissionGranted = granted.intersect(healthPermissions).isNotEmpty()
            lastHealthConnectAllPermissionsGranted = granted.containsAll(healthPermissions)
            val permissionState = healthPermissionState(granted)
            val hasAnyHealthPermission = granted.intersect(healthPermissions).isNotEmpty()
            if (!hasAnyHealthPermission) {
                dispatchConnectorStatus(emptyList())
                if (isCurrentHealthSync(generation)) {
                    dispatchManualOnlyHealthData(selectedDate, permissionState)
                }
                return@launch
            }
            try {
                val now = Instant.now()
                val zone = ZoneId.systemDefault()
                val interval = HealthDayIntervals.forDate(selectedDate, zone)
                val rangeStart = interval.start
                val rangeEnd = interval.endExclusive
                val selectedDay = TimeRangeFilter.between(rangeStart, rangeEnd)
                val stepsDay = if (HealthPermission.getReadPermission(StepsRecord::class) in granted) dataSource.readAll(StepsRecord::class, selectedDay) else emptyList()
                val sleepDay = if (HealthPermission.getReadPermission(SleepSessionRecord::class) in granted) dataSource.readAll(SleepSessionRecord::class, selectedDay) else emptyList()
                val exerciseDay = if (HealthPermission.getReadPermission(ExerciseSessionRecord::class) in granted) dataSource.readAll(ExerciseSessionRecord::class, selectedDay) else emptyList()
                val heartDay = if (HealthPermission.getReadPermission(HeartRateRecord::class) in granted) dataSource.readAll(HeartRateRecord::class, selectedDay) else emptyList()
                val hydrationDay = if (HealthPermission.getReadPermission(HydrationRecord::class) in granted) dataSource.readAll(HydrationRecord::class, selectedDay) else emptyList()
                val distanceDay = if (HealthPermission.getReadPermission(DistanceRecord::class) in granted) dataSource.readAll(DistanceRecord::class, selectedDay) else emptyList()
                val activeCaloriesDay = if (HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class) in granted) dataSource.readAll(ActiveCaloriesBurnedRecord::class, selectedDay) else emptyList()
                val oxygenDay = if (HealthPermission.getReadPermission(OxygenSaturationRecord::class) in granted) dataSource.readAll(OxygenSaturationRecord::class, selectedDay) else emptyList()
                val weightDay = if (HealthPermission.getReadPermission(WeightRecord::class) in granted) dataSource.readAll(WeightRecord::class, selectedDay) else emptyList()
                val nutritionDay = if (HealthPermission.getReadPermission(NutritionRecord::class) in granted) dataSource.readAll(NutritionRecord::class, selectedDay) else emptyList()
                val manualMeals = manualMealsForDate(selectedDate)
                val scannerPackage = applicationContext.packageName
                val manualMealTimes = manualMeals.mapNotNull {
                    runCatching { Instant.parse(it.optString("recordedAt")) }.getOrNull()
                }
                val sources = (
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
                val healthRecordCount = stepsDay.size + sleepDay.size + exerciseDay.size +
                    heartDay.size + hydrationDay.size + distanceDay.size +
                    activeCaloriesDay.size + oxygenDay.size + weightDay.size +
                    nutritionDay.size
                val finalState = if (
                    permissionState.code == HealthConnectStateCode.AUTHORIZED_NO_DATA &&
                    healthRecordCount > 0
                ) {
                    permissionState.copy(
                        code = HealthConnectStateCode.AUTHORIZED_WITH_DATA,
                        label = "Health Connect autorisé avec données"
                    )
                } else {
                    permissionState
                }
                val metrics = JSONObject().apply {
                    put("steps", healthMetricJson(healthMetric(
                        HealthPermission.getReadPermission(StepsRecord::class) in granted,
                        stepsDay.size,
                        stepsDay.map { it.metadata.dataOrigin.packageName },
                        "count",
                        stepsDay.takeIf { it.isNotEmpty() }?.sumOf { it.count }
                    )))
                    put("sleepMinutes", healthMetricJson(healthMetric(
                        HealthPermission.getReadPermission(SleepSessionRecord::class) in granted,
                        sleepDay.size,
                        sleepDay.map { it.metadata.dataOrigin.packageName },
                        "min",
                        sleepDay.takeIf { it.isNotEmpty() }
                            ?.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
                    )))
                    put("exerciseMinutes", healthMetricJson(healthMetric(
                        HealthPermission.getReadPermission(ExerciseSessionRecord::class) in granted,
                        exerciseDay.size,
                        exerciseDay.map { it.metadata.dataOrigin.packageName },
                        "min",
                        exerciseDay.takeIf { it.isNotEmpty() }
                            ?.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
                    )))
                    put("averageHeartRate", healthMetricJson(healthMetric(
                        HealthPermission.getReadPermission(HeartRateRecord::class) in granted,
                        samples.size,
                        heartDay.map { it.metadata.dataOrigin.packageName },
                        "bpm",
                        averageHeartRate,
                        samples.size
                    )))
                    put("hydrationLitres", healthMetricJson(healthMetric(
                        HealthPermission.getReadPermission(HydrationRecord::class) in granted,
                        hydrationDay.size,
                        hydrationDay.map { it.metadata.dataOrigin.packageName },
                        "L",
                        hydrationDay.takeIf { it.isNotEmpty() }?.sumOf { it.volume.inLiters }
                    )))
                    put("distanceKm", healthMetricJson(healthMetric(
                        HealthPermission.getReadPermission(DistanceRecord::class) in granted,
                        distanceDay.size,
                        distanceDay.map { it.metadata.dataOrigin.packageName },
                        "km",
                        distanceDay.takeIf { it.isNotEmpty() }?.sumOf { it.distance.inKilometers }
                    )))
                    put("activeCalories", healthMetricJson(healthMetric(
                        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class) in granted,
                        activeCaloriesDay.size,
                        activeCaloriesDay.map { it.metadata.dataOrigin.packageName },
                        "kcal",
                        activeCaloriesDay.takeIf { it.isNotEmpty() }
                            ?.sumOf { it.energy.inKilocalories }
                    )))
                    put("oxygenPercent", healthMetricJson(healthMetric(
                        HealthPermission.getReadPermission(OxygenSaturationRecord::class) in granted,
                        oxygenDay.size,
                        oxygenDay.map { it.metadata.dataOrigin.packageName },
                        "%",
                        oxygenDay.maxByOrNull { it.time }?.percentage?.value
                    )))
                    put("weightKg", healthMetricJson(healthMetric(
                        HealthPermission.getReadPermission(WeightRecord::class) in granted,
                        weightDay.size,
                        weightDay.map { it.metadata.dataOrigin.packageName },
                        "kg",
                        weightDay.maxByOrNull { it.time }?.weight?.inKilograms
                    )))
                    val nutritionCount = nutritionDay.size + manualMeals.size
                    put(
                        "nutrition",
                        healthMetricJson(
                            if (nutritionCount == 0) {
                                healthMetric(
                                    HealthPermission.getReadPermission(NutritionRecord::class) in granted,
                                    0,
                                    emptyList(),
                                    "meal",
                                    null
                                )
                            } else {
                                HealthMetricResult.data(
                                    nutritionCount,
                                    "meal",
                                    nutritionCount,
                                    (
                                        nutritionDay.map { it.metadata.dataOrigin.packageName } +
                                            manualMeals.map { scannerPackage }
                                    ).distinct().size
                                )
                            }
                        )
                    )
                    put("totalCalories", healthMetricJson(HealthMetricResult.unsupported("kcal")))
                    put("heartRateVariability", healthMetricJson(HealthMetricResult.unsupported("ms")))
                    put("respiratoryRate", healthMetricJson(
                        HealthMetricResult.unsupported("breaths/min")
                    ))
                    put("bloodPressure", healthMetricJson(HealthMetricResult.unsupported("mmHg")))
                    put("bodyTemperature", healthMetricJson(HealthMetricResult.unsupported("°C")))
                    put("bodyFat", healthMetricJson(HealthMetricResult.unsupported("%")))
                }
                val payload = JSONObject().apply {
                    put("periodHours", Duration.between(rangeStart, rangeEnd).toHours())
                    put("selectedDate", selectedDate.toString())
                    put("rangeStart", rangeStart.toString())
                    put("rangeEnd", rangeEnd.toString())
                    put("steps", stepsDay.takeIf { it.isNotEmpty() }
                        ?.sumOf { it.count } ?: JSONObject.NULL)
                    put("sleepMinutes", sleepDay.takeIf { it.isNotEmpty() }
                        ?.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
                        ?: JSONObject.NULL)
                    put("exerciseMinutes", exerciseDay.takeIf { it.isNotEmpty() }
                        ?.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
                        ?: JSONObject.NULL)
                    put("averageHeartRate", averageHeartRate ?: JSONObject.NULL)
                    put("hydrationLitres", hydrationDay.takeIf { it.isNotEmpty() }
                        ?.sumOf { it.volume.inLiters } ?: JSONObject.NULL)
                    put("distanceKm", distanceDay.takeIf { it.isNotEmpty() }
                        ?.sumOf { it.distance.inKilometers } ?: JSONObject.NULL)
                    put("activeCalories", activeCaloriesDay.takeIf { it.isNotEmpty() }
                        ?.sumOf { it.energy.inKilocalories } ?: JSONObject.NULL)
                    put("oxygenPercent", oxygenDay.maxByOrNull { it.time }?.percentage?.value ?: JSONObject.NULL)
                    put("weightKg", weightDay.maxByOrNull { it.time }?.weight?.inKilograms ?: JSONObject.NULL)
                    put("healthConnectState", healthStateJson(finalState))
                    put("metrics", metrics)
                    put("nutrition", nutritionSummary)
                    put("details", details)
                    put("score", scoreBreakdown.getInt("overall"))
                    put("scoreBreakdown", scoreBreakdown)
                    put("attribution", attributions)
                    put("sources", JSONArray(sources))
                    put("connectorCount", sources.size)
                    put("syncedAt", now.toString())
                }
                if (!isCurrentHealthSync(generation)) return@launch
                lastSourcePackages = sources
                lastHealthPayload = payload
                dispatchConnectorStatus(sources)
                dispatchHealthData(payload)
                dispatchSyncState(finalState.code.name.lowercase(Locale.US))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: SecurityException) {
                if (isCurrentHealthSync(generation)) {
                    appPreferences.edit {
                        putBoolean(HEALTH_PERMISSION_REQUESTED_KEY, true)
                        putBoolean(HEALTH_PERMISSION_EVER_AUTHORIZED_KEY, true)
                    }
                    val revoked = HealthConnectStateModel(
                        HealthConnectStateCode.PERMISSION_DENIED,
                        "Autorisation Health Connect révoquée",
                        reason = "permission_revoked",
                        retryAction = "request_permissions",
                        missingPermissions = healthPermissions.sorted()
                    )
                    dispatchManualOnlyHealthData(selectedDate, revoked)
                    notifyWeb(false, "permission_revoked", revoked.label)
                }
            } catch (error: Exception) {
                if (isCurrentHealthSync(generation)) {
                    val failure = HealthConnectStateModel(
                        HealthConnectStateCode.SYNC_ERROR,
                        "Erreur de synchronisation Health Connect",
                        reason = error.javaClass.simpleName,
                        retryAction = "retry_sync"
                    )
                    dispatchManualOnlyHealthData(selectedDate, failure)
                    notifyWeb(false, "sync_error", error.message ?: "Synchronisation impossible")
                }
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
        val packages = sourcePackages.filter {
            it.isNotBlank() && it != applicationContext.packageName
        }.distinct()
        val catalogItems = connectorCatalog.map { definition ->
            val detected = definition.packages.firstOrNull { it in packages }
            val installed = definition.packages.firstOrNull(::isPackageInstalled)
            val runtimeState = ConnectorStateResolver.resolve(
                definition,
                ConnectorEvidence(
                    installed = installed != null,
                    healthConnectAvailable = healthStatus == "available",
                    healthConnectPermissionGranted = lastHealthConnectPermissionGranted,
                    providerRecordsDetected = detected != null,
                    healthConnectAllPermissionsGranted = lastHealthConnectAllPermissionsGranted
                )
            )
            JSONObject().apply {
                put("id", definition.id)
                put("name", definition.name)
                put("status", runtimeState.name.lowercase(Locale.US))
                put("runtimeState", runtimeState.name)
                put("capability", definition.capability.name)
                put("mode", definition.capability.name.lowercase(Locale.US))
                put("packageName", detected ?: installed ?: definition.packages.firstOrNull().orEmpty())
                put("detectedData", detected != null)
                put("installed", installed != null)
                put(
                    "action",
                    when {
                        definition.id == "health_connect" -> "authorize_health_connect"
                        definition.capability == ConnectorCapability.UNSUPPORTED_PLATFORM -> "unsupported_android"
                        definition.capability == ConnectorCapability.HEALTH_CONNECT && installed != null ->
                            "authorize_via_health_connect"
                        installed != null -> "open_provider"
                        else -> "install_provider"
                    }
                )
                put("note", definition.note)
                put("userFacingStatus", connectorRuntimeLabel(runtimeState))
                put("directIntegrationImplemented", definition.directIntegrationImplemented)
                put("futureRequirement", definition.futureRequirement ?: JSONObject.NULL)
            }
        }
        val catalogPackages = connectorCatalog.flatMap { it.packages }.toSet()
        val dynamicItems = packages.filterNot { it in catalogPackages }.map { packageName ->
            JSONObject().apply {
                put("id", packageName)
                put("name", sourceLabel(packageName))
                put("status", ConnectorRuntimeState.HEALTH_CONNECT_DATA_AVAILABLE.name.lowercase(Locale.US))
                put("runtimeState", ConnectorRuntimeState.HEALTH_CONNECT_DATA_AVAILABLE.name)
                put("capability", ConnectorCapability.HEALTH_CONNECT.name)
                put("mode", ConnectorCapability.HEALTH_CONNECT.name.lowercase(Locale.US))
                put("packageName", packageName)
                put("detectedData", true)
                put("installed", isPackageInstalled(packageName))
                put("action", "manage_health_connect")
                put("note", "Source détectée automatiquement dans Health Connect.")
                put("userFacingStatus", "Données détectées via Health Connect")
                put("directIntegrationImplemented", false)
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

    private fun connectorRuntimeLabel(state: ConnectorRuntimeState): String = when (state) {
        ConnectorRuntimeState.NOT_INSTALLED -> "Application non installée"
        ConnectorRuntimeState.INSTALLED -> "Application installée"
        ConnectorRuntimeState.SETUP_REQUIRED -> "Configuration requise dans l’application"
        ConnectorRuntimeState.HEALTH_CONNECT_PERMISSION_REQUIRED -> "Autorisation Health Connect requise"
        ConnectorRuntimeState.HEALTH_CONNECT_PARTIAL_PERMISSION -> "Autorisations Health Connect partielles"
        ConnectorRuntimeState.HEALTH_CONNECT_AVAILABLE_NO_DATA -> "Accès Health Connect activé, aucune donnée fournisseur détectée"
        ConnectorRuntimeState.HEALTH_CONNECT_DATA_AVAILABLE -> "Données fournisseur détectées via Health Connect"
        ConnectorRuntimeState.DIRECT_AUTH_REQUIRED -> "Autorisation directe requise"
        ConnectorRuntimeState.DIRECT_AUTHENTICATED -> "Connexion directe authentifiée"
        ConnectorRuntimeState.API_UNAVAILABLE -> "Connexion directe non implémentée"
        ConnectorRuntimeState.UNSUPPORTED -> "Non pris en charge sur Android"
        ConnectorRuntimeState.UNAVAILABLE -> "Indisponible sur cet appareil"
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
        internal const val EXTRA_FORCE_OFFLINE_FOR_TESTS = "com.vitalis.healthos.FORCE_OFFLINE_TEST"
        internal const val EXTRA_RUN2_FIXTURE = "com.vitalis.healthos.RUN2_FIXTURE"
        internal const val EXTRA_RUN3_FIXTURE = "com.vitalis.healthos.RUN3_FIXTURE"
        internal const val EXTRA_RUN4_FIXTURE = "com.vitalis.healthos.RUN4_FIXTURE"
        internal const val EXTRA_RUN5_FIXTURE = "com.vitalis.healthos.RUN5_FIXTURE"
        internal const val EXTRA_RUN6_FIXTURE = "com.vitalis.healthos.RUN6_FIXTURE"
        internal const val EXTRA_FINAL_UX_FIXTURE = "com.vitalis.healthos.FINAL_UX_FIXTURE"
        internal const val EXTRA_TEST_TODAY_ISO = "com.vitalis.healthos.TEST_TODAY_ISO"
        private const val SELECTED_HEALTH_DATE_KEY = "selected_health_date_iso"
        private const val HEALTH_PERMISSION_REQUESTED_KEY =
            "health_connect_permission_requested_v1"
        private const val HEALTH_PERMISSION_EVER_AUTHORIZED_KEY =
            "health_connect_ever_authorized_v1"
        private const val MICROPHONE_PERMISSION_REQUESTED_KEY =
            "microphone_permission_requested_v1"
        private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
        private const val LOCAL_ASSET_HOST = "appassets.androidplatform.net"
        private const val LOCAL_URL = "https://$LOCAL_ASSET_HOST/assets/vitalis/index.html"
        private const val LOCAL_TEST_URL = "https://$LOCAL_ASSET_HOST/assets/vitalis/run2-fixture.html"
        private const val LOCAL_RUN3_TEST_URL =
            "https://$LOCAL_ASSET_HOST/assets/vitalis/run3-health-fixture.html"
        private const val LOCAL_RUN4_TEST_URL =
            "https://$LOCAL_ASSET_HOST/assets/vitalis/run4-nutrition-fixture.html"
        private const val LOCAL_RUN5_TEST_URL =
            "https://$LOCAL_ASSET_HOST/assets/vitalis/run5-voice-connectors-fixture.html"
        private const val LOCAL_RUN6_TEST_URL =
            "https://$LOCAL_ASSET_HOST/assets/vitalis/run6-security-privacy-fixture.html"
        private const val LOCAL_FINAL_UX_TEST_URL =
            "https://$LOCAL_ASSET_HOST/assets/vitalis/final-ux-fixture.html"
        private const val VITALIS_HOST = "vitalis-health-os.gillesarnaudasse65.chatgpt.site"
        private const val VITALIS_URL = "https://$VITALIS_HOST/"
        private const val COACH_ASSET_PATH = "/__vitalis/coaches/"
        private const val ACTIVE_SCAN_ASSET_PATH = "/active-nutrition-scan/"
        private const val ACTIVE_NUTRITION_CACHE_DIRECTORY = "nutrition-active"
        private val COACH_ASSET_FILES = setOf(
            "kofi.webp",
            "ama.webp",
            "ayo.webp",
            "nia.webp",
            "sekou.webp",
            "zuri.webp"
        )
        private const val REMOTE_LOAD_TIMEOUT_MS = 30_000L
        private const val REMOTE_RETRY_DELAY_MS = 2_000L
        private const val FINAL_UX_RECOVERY_ATTEMPTS = 3
        private const val FINAL_UX_RECOVERY_DELAY_MS = 450L
        private const val CONNECTOR_RETURN_REFRESH_DELAY_MS = 700L
        private const val MAX_REMOTE_RETRIES = 2
        private const val MAX_SPEECH_TEXT_LENGTH = 8_000
        private const val MAX_AI_PROMPT_LENGTH = 4_000
        private const val MAX_DEVELOPER_PROMPT_LENGTH = 8_000
        private const val MAX_BRIDGE_MESSAGE_CHARS = 65_536
        private const val NATIVE_CHANNEL_NAME = "VitalisNativeChannel"
        private const val OPENAI_RESPONSES_URL = "https://api.openai.com/v1/responses"
        private const val OPENAI_MODEL = "gpt-5.6"
        private const val CHATGPT_WORK_URL = "https://chatgpt.com/codex"
        private const val APP_PREFS = "vitalis_preferences"
        private const val AI_HEALTH_CONSENT = "ai_health_consent"
        private const val MANUAL_MEALS_KEY = "manual_meal_estimates"
        private const val PENDING_NUTRITION_SCAN_KEY = "pending_nutrition_scan_v1"
        private const val STATE_NUTRITION_CAMERA_FILE = "nutrition_camera_file"
        private const val STATE_NUTRITION_CAMERA_URI = "nutrition_camera_uri"
        private const val COACH_SAFETY_INSTRUCTIONS =
            "Réponds en français clair, professionnel, chaleureux et concret. Analyse uniquement les données fournies, " +
                "indique les données manquantes et cite les connecteurs visibles. Ne pose aucun diagnostic et ne remplace " +
                "jamais un professionnel de santé. Pour un symptôme grave ou urgent, recommande immédiatement de contacter " +
                "les services d’urgence locaux. Donne au maximum trois priorités réalistes et explique brièvement pourquoi."
        private const val NUTRITION_ANALYSIS_PROMPT =
            "Analyse uniquement la photo du repas jointe. Réponds avec un unique objet JSON, sans markdown, " +
                "contenant foodItems (tableau d’objets name, portion, estimatedCalories, confidence), mealName, " +
                "portionDescription, caloriesKcal, carbohydratesG, proteinG, fatG, fibreG, sugarG, sodiumMg, " +
                "confidence entre 0 et 1 et uncertaintyNotes. Utilise null lorsqu’une valeur ne peut pas être " +
                "raisonnablement estimée, n’invente pas de précision et signale les incertitudes."
        private const val RUN4_MOCK_ANALYSIS =
            "{\"foodItems\":[{\"name\":\"Bol de légumes et céréales\",\"portion\":\"1 bol\",\"estimatedCalories\":520,\"confidence\":0.71}]," +
                "\"mealName\":\"Bol végétal\",\"portionDescription\":\"1 bol moyen\",\"caloriesKcal\":520," +
                "\"carbohydratesG\":62,\"proteinG\":21,\"fatG\":19,\"fibreG\":13,\"sugarG\":9," +
                "\"sodiumMg\":640,\"confidence\":0.71,\"uncertaintyNotes\":\"La sauce et les quantités exactes restent à vérifier.\"}"
        private const val DEVELOPER_INSTRUCTIONS =
            "Tu es Vitalis Developer AI, assistant technique senior de l’application Vitalis Mobile. Aide l’utilisateur " +
                "à transformer un besoin en demande de modification structurée : objectif, comportement attendu, fichiers " +
                "ou modules probables, permissions ou connecteurs nécessaires, risques, critères de test et validation. " +
                "Préserve toujours l’interface classique existante sauf demande explicite contraire. Ne prétends jamais " +
                "avoir modifié, compilé ou publié le dépôt : les changements réels sont exécutés dans ChatGPT Work/Codex " +
                "avec accès GitHub et validation de l’utilisateur. N’affiche et ne demande jamais une clé API."
    }
}
