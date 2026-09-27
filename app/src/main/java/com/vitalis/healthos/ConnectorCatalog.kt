package com.vitalis.healthos

internal enum class ConnectorCapability {
    HEALTH_CONNECT,
    APP_SETUP_ONLY,
    DIRECT_OAUTH,
    DIRECT_API,
    UNSUPPORTED_PLATFORM,
    UNAVAILABLE
}

internal enum class ConnectorRuntimeState {
    NOT_INSTALLED,
    INSTALLED,
    SETUP_REQUIRED,
    HEALTH_CONNECT_PERMISSION_REQUIRED,
    HEALTH_CONNECT_AVAILABLE_NO_DATA,
    HEALTH_CONNECT_DATA_AVAILABLE,
    DIRECT_AUTH_REQUIRED,
    DIRECT_AUTHENTICATED,
    API_UNAVAILABLE,
    UNSUPPORTED,
    UNAVAILABLE
}

internal data class ConnectorDefinition(
    val id: String,
    val name: String,
    val packages: List<String>,
    val capability: ConnectorCapability,
    val note: String,
    val directIntegrationImplemented: Boolean = false,
    val futureRequirement: String? = null
)

internal data class ConnectorEvidence(
    val installed: Boolean,
    val healthConnectAvailable: Boolean,
    val healthConnectPermissionGranted: Boolean,
    val providerRecordsDetected: Boolean
)

internal object ConnectorStateResolver {
    fun resolve(
        definition: ConnectorDefinition,
        evidence: ConnectorEvidence
    ): ConnectorRuntimeState = when (definition.capability) {
        ConnectorCapability.UNSUPPORTED_PLATFORM -> ConnectorRuntimeState.UNSUPPORTED
        ConnectorCapability.UNAVAILABLE -> ConnectorRuntimeState.UNAVAILABLE
        ConnectorCapability.HEALTH_CONNECT -> when {
            evidence.providerRecordsDetected -> ConnectorRuntimeState.HEALTH_CONNECT_DATA_AVAILABLE
            !evidence.healthConnectAvailable -> ConnectorRuntimeState.UNAVAILABLE
            !evidence.healthConnectPermissionGranted ->
                ConnectorRuntimeState.HEALTH_CONNECT_PERMISSION_REQUIRED
            definition.id != "health_connect" && !evidence.installed ->
                ConnectorRuntimeState.NOT_INSTALLED
            else -> ConnectorRuntimeState.HEALTH_CONNECT_AVAILABLE_NO_DATA
        }
        ConnectorCapability.APP_SETUP_ONLY -> if (evidence.installed) {
            ConnectorRuntimeState.SETUP_REQUIRED
        } else {
            ConnectorRuntimeState.NOT_INSTALLED
        }
        ConnectorCapability.DIRECT_OAUTH,
        ConnectorCapability.DIRECT_API -> when {
            !evidence.installed -> ConnectorRuntimeState.NOT_INSTALLED
            !definition.directIntegrationImplemented -> ConnectorRuntimeState.API_UNAVAILABLE
            else -> ConnectorRuntimeState.DIRECT_AUTH_REQUIRED
        }
    }
}

internal object ConnectorCatalog {
    private const val OAUTH_REQUIREMENT =
        "Developer account, client ID, redirect URI, authorization code exchange, refresh tokens, scopes, secure token storage and provider policy approval."

    val entries = listOf(
        hc("health_connect", "Health Connect", listOf("com.google.android.apps.healthdata"), "Accès Android centralisé ; une autorisation Vitalis reste nécessaire."),
        hc("samsung_health", "Samsung Health", listOf("com.sec.android.app.shealth"), "Activez le partage Health Connect dans Samsung Health."),
        hc("google_fit", "Google Fit", listOf("com.google.android.apps.fitness"), "Utilisez Health Connect lorsque Google Fit le propose."),
        hc("mibro_fit", "Mibro Fit", listOf("com.xiaoxun.xunoversea", "com.zhencheng.mibrofit"), "Activez une passerelle officielle vers Health Connect si disponible."),
        hc("fitbit", "Fitbit", listOf("com.fitbit.FitbitMobile"), "Activez Health Connect depuis les réglages Fitbit."),
        oauth("garmin", "Garmin Connect", listOf("com.garmin.android.apps.connectmobile")),
        oauth("huawei", "Huawei Health", listOf("com.huawei.health")),
        oauth("strava", "Strava", listOf("com.strava")),
        oauth("oura", "Oura", listOf("com.ouraring.oura")),
        oauth("whoop", "WHOOP", listOf("com.whoop.android")),
        hc("withings", "Withings", listOf("com.withings.wiscale2"), "Activez Health Connect dans Withings lorsque disponible."),
        hc("health_sync", "Health Sync", listOf("nl.appyhapps.healthsync"), "Configurez la passerelle vers Health Connect, puis revenez dans Vitalis."),
        hc("myfitnesspal", "MyFitnessPal", listOf("com.myfitnesspal.android"), "Activez Health Connect pour les données nutritionnelles disponibles."),
        oauth("yazio", "YAZIO", listOf("com.yazio.android")),
        setup("welmi", "Welmi", listOf("welmi.ai.android")),
        oauth("cronometer", "Cronometer", listOf("com.cronometer.android.gold")),
        oauth("lifesum", "Lifesum", listOf("com.sillens.shapeupclub")),
        setup("fiton", "FitOn", listOf("com.fiton.android")),
        setup("fitify", "Fitify", listOf("com.fitifyworkouts.bodyweight.workoutapp")),
        setup("flexme", "FlexMe", listOf("stretchingworkouts.homeexercises.flexibility")),
        oauth("trainingpeaks", "TrainingPeaks", listOf("com.peaksware.trainingpeaks")),
        setup("zwift", "Zwift", listOf("com.zwift.zwiftgame", "com.zwift.android.prod")),
        setup("peloton", "Peloton", listOf("com.onepeloton.callisto")),
        setup("freeletics", "Freeletics", listOf("com.freeletics.lite")),
        oauth("komoot", "Komoot", listOf("de.komoot.android")),
        setup("headspace", "Headspace", listOf("com.getsomeheadspace.android")),
        setup("calm", "Calm", listOf("com.calm.android")),
        setup("sleep_cycle", "Sleep Cycle", listOf("com.northcube.sleepcycle")),
        setup("welltory", "Welltory", listOf("com.welltory.client.android")),
        oauth("suunto", "Suunto", listOf("com.stt.android.suunto")),
        oauth("coros", "COROS", listOf("com.yf.smart.coros.dist")),
        ConnectorDefinition(
            id = "apple_health",
            name = "Apple Health",
            packages = emptyList(),
            capability = ConnectorCapability.UNSUPPORTED_PLATFORM,
            note = "Apple Health est indisponible comme source directe sur Android."
        )
    )

    fun find(id: String?): ConnectorDefinition? =
        id?.takeIf { it.isNotBlank() }?.let { stableId -> entries.firstOrNull { it.id == stableId } }

    private fun hc(id: String, name: String, packages: List<String>, note: String) =
        ConnectorDefinition(id, name, packages, ConnectorCapability.HEALTH_CONNECT, note)

    private fun setup(id: String, name: String, packages: List<String>) =
        ConnectorDefinition(
            id,
            name,
            packages,
            ConnectorCapability.APP_SETUP_ONLY,
            "Ouvrez l’application pour configurer ses propres options. Cela ne connecte pas automatiquement Vitalis."
        )

    private fun oauth(id: String, name: String, packages: List<String>) =
        ConnectorDefinition(
            id,
            name,
            packages,
            ConnectorCapability.DIRECT_OAUTH,
            "Connexion directe non implémentée. L’application peut uniquement être ouverte pour sa configuration.",
            directIntegrationImplemented = false,
            futureRequirement = OAUTH_REQUIREMENT
        )
}
