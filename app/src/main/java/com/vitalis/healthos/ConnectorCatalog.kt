package com.vitalis.healthos

internal data class ConnectorDefinition(
        val id: String,
        val name: String,
        val packages: List<String>,
        val mode: String,
        val note: String
    )

internal object ConnectorCatalog {
    val entries = listOf(
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
        ConnectorDefinition("health_sync", "Health Sync", listOf("nl.appyhapps.healthsync"), "bridge", "Passerelle autorisée vers Health Connect pour les fournisseurs compatibles."),
        ConnectorDefinition("myfitnesspal", "MyFitnessPal", listOf("com.myfitnesspal.android"), "health_connect", "Activez Health Connect dans MyFitnessPal pour partager les données nutritionnelles disponibles."),
        ConnectorDefinition("yazio", "YAZIO", listOf("com.yazio.android"), "provider", "L’accès nutritionnel dépend des autorisations du fournisseur."),
        ConnectorDefinition("welmi", "Welmi", listOf("welmi.ai.android"), "provider", "Ouvrez Welmi pour gérer le compte et les options de partage proposées."),
        ConnectorDefinition("cronometer", "Cronometer", listOf("com.cronometer.android.gold"), "provider", "L’accès nutritionnel dépend des autorisations du fournisseur."),
        ConnectorDefinition("lifesum", "Lifesum", listOf("com.sillens.shapeupclub"), "provider", "L’accès nutritionnel dépend des autorisations du fournisseur."),
        ConnectorDefinition("fiton", "FitOn", listOf("com.fiton.android"), "provider", "Ouvrez FitOn pour gérer le compte et les intégrations proposées."),
        ConnectorDefinition("fitify", "Fitify", listOf("com.fitifyworkouts.bodyweight.workoutapp"), "provider", "Ouvrez Fitify pour gérer le compte et les intégrations proposées."),
        ConnectorDefinition("flexme", "FlexMe", listOf("stretchingworkouts.homeexercises.flexibility"), "provider", "Ouvrez FlexMe pour gérer le compte et les options de partage proposées."),
        ConnectorDefinition("trainingpeaks", "TrainingPeaks", listOf("com.peaksware.trainingpeaks"), "provider", "La connexion complète dépend de l’autorisation officielle TrainingPeaks."),
        ConnectorDefinition("zwift", "Zwift", listOf("com.zwift.zwiftgame", "com.zwift.android.prod"), "provider", "La connexion complète dépend des intégrations officielles Zwift."),
        ConnectorDefinition("peloton", "Peloton", listOf("com.onepeloton.callisto"), "provider", "Ouvrez Peloton pour gérer le compte et les intégrations proposées."),
        ConnectorDefinition("freeletics", "Freeletics", listOf("com.freeletics.lite"), "provider", "Ouvrez Freeletics pour gérer le compte et les intégrations proposées."),
        ConnectorDefinition("komoot", "Komoot", listOf("de.komoot.android"), "provider", "La connexion complète dépend de l’autorisation officielle Komoot."),
        ConnectorDefinition("headspace", "Headspace", listOf("com.getsomeheadspace.android"), "provider", "Ouvrez Headspace pour gérer le compte et les options de partage proposées."),
        ConnectorDefinition("calm", "Calm", listOf("com.calm.android"), "provider", "Ouvrez Calm pour gérer le compte et les options de partage proposées."),
        ConnectorDefinition("sleep_cycle", "Sleep Cycle", listOf("com.northcube.sleepcycle"), "provider", "Ouvrez Sleep Cycle pour gérer le compte et les options de partage proposées."),
        ConnectorDefinition("welltory", "Welltory", listOf("com.welltory.client.android"), "provider", "Ouvrez Welltory pour gérer le compte et les intégrations proposées."),
        ConnectorDefinition("suunto", "Suunto", listOf("com.stt.android.suunto"), "provider", "Une autorisation Suunto officielle peut être nécessaire."),
        ConnectorDefinition("coros", "COROS", listOf("com.yf.smart.coros.dist"), "provider", "Une autorisation COROS officielle peut être nécessaire."),
        ConnectorDefinition("apple_health", "Apple Health", emptyList(), "unsupported_android", "Apple Health n’est pas accessible depuis Android. Utilisez un service intermédiaire officiellement compatible.")
    )

    fun find(id: String?): ConnectorDefinition? =
        id?.takeIf { it.isNotBlank() }?.let { stableId -> entries.firstOrNull { it.id == stableId } }
}
