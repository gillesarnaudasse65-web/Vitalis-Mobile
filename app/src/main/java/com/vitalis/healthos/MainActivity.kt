package com.vitalis.healthos

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private lateinit var content: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var syncButton: Button
    private lateinit var progress: ProgressBar
    private var healthConnectClient: HealthConnectClient? = null

    private val healthPermissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(HydrationRecord::class)
    )

    private val permissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(healthPermissions)) {
            statusText.text = "Health Connect autorisé"
            refreshHealthData()
        } else {
            statusText.text = "Autorisation partielle : complétez les catégories dans Health Connect"
            refreshHealthData()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#063C30")
        window.navigationBarColor = Color.parseColor("#063C30")

        if (HealthConnectClient.getSdkStatus(this) == HealthConnectClient.SDK_AVAILABLE) {
            healthConnectClient = HealthConnectClient.getOrCreate(this)
        }

        setContentView(buildInterface())
        checkPermissionsAndLoad()
    }

    private fun buildInterface(): View {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#F7F5EE")) }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 42, 36, 64)
        }
        scroll.addView(content)

        content.addView(TextView(this).apply {
            text = "VITALIS HEALTH OS"
            textSize = 12f
            setTextColor(Color.parseColor("#4F766C"))
        })
        content.addView(TextView(this).apply {
            text = "Votre santé, réellement connectée"
            textSize = 27f
            setTextColor(Color.parseColor("#063C30"))
            setPadding(0, 8, 0, 8)
        })

        statusText = TextView(this).apply {
            text = "Vérification de Health Connect…"
            textSize = 15f
            setTextColor(Color.parseColor("#355D53"))
            setPadding(0, 0, 0, 18)
        }
        content.addView(statusText)

        progress = ProgressBar(this).apply { isIndeterminate = true }
        content.addView(progress)

        syncButton = Button(this).apply {
            text = "Autoriser / synchroniser Health Connect"
            isAllCaps = false
            setOnClickListener { requestHealthPermissions() }
        }
        content.addView(syncButton)

        content.addView(sectionTitle("Aujourd’hui"))
        addMetricCard("Pas", "—", "Health Connect", "steps")
        addMetricCard("Sommeil", "—", "Dernières 24 heures", "sleep")
        addMetricCard("Activité", "—", "Séances enregistrées", "exercise")
        addMetricCard("Fréquence cardiaque", "—", "Moyenne disponible", "heart")
        addMetricCard("Hydratation", "—", "Volume enregistré", "water")

        content.addView(sectionTitle("Coach Vitalis"))
        content.addView(card("Kofi", "Je vais analyser uniquement les mesures réellement disponibles. Autorisez Health Connect puis lancez une synchronisation."))

        content.addView(sectionTitle("Connecteurs"))
        content.addView(card("Health Connect", "Connexion Android native et lecture réelle des données autorisées."))
        content.addView(card("Samsung Health", "Compatible lorsque Samsung Health partage ses données avec Health Connect."))
        content.addView(card("Mibro Fit", "Compatible uniquement si Mibro Fit publie les données dans Health Connect. Aucune connexion directe ne sera simulée."))
        content.addView(card("Fitbit, Garmin, Huawei, Strava et autres", "Lecture via Health Connect lorsqu’une application compatible y écrit les mesures. Les API directes nécessitent l’autorisation officielle de chaque fournisseur."))

        content.addView(Button(this).apply {
            text = "Ouvrir les paramètres Health Connect"
            isAllCaps = false
            setOnClickListener { openHealthConnectSettings() }
        })

        return scroll
    }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        textSize = 20f
        setTextColor(Color.parseColor("#063C30"))
        setPadding(0, 28, 0, 12)
    }

    private fun card(title: String, body: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(28, 24, 28, 24)
        setBackgroundColor(Color.WHITE)
        addView(TextView(context).apply {
            text = title
            textSize = 17f
            setTextColor(Color.parseColor("#123C31"))
        })
        addView(TextView(context).apply {
            text = body
            textSize = 14f
            setTextColor(Color.parseColor("#547067"))
            setPadding(0, 6, 0, 0)
        })
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        params.setMargins(0, 0, 0, 12)
        layoutParams = params
    }

    private fun addMetricCard(title: String, value: String, source: String, tagName: String) {
        val card = card(title, "$value\n$source")
        card.tag = tagName
        content.addView(card)
    }

    private fun updateMetric(tagName: String, title: String, value: String, source: String) {
        val card = content.findViewWithTag<LinearLayout>(tagName) ?: return
        (card.getChildAt(0) as? TextView)?.text = title
        (card.getChildAt(1) as? TextView)?.text = "$value\n$source"
    }

    private fun checkPermissionsAndLoad() {
        val client = healthConnectClient
        if (client == null) {
            progress.visibility = View.GONE
            statusText.text = when (HealthConnectClient.getSdkStatus(this)) {
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "Health Connect doit être installé ou mis à jour"
                else -> "Health Connect n’est pas disponible sur cet appareil"
            }
            syncButton.text = "Installer / mettre à jour Health Connect"
            syncButton.setOnClickListener { openHealthConnectStore() }
            return
        }

        lifecycleScope.launch {
            val granted = client.permissionController.getGrantedPermissions()
            if (granted.containsAll(healthPermissions)) {
                statusText.text = "Health Connect autorisé"
                refreshHealthData()
            } else {
                progress.visibility = View.GONE
                statusText.text = "Autorisation requise pour afficher vos données"
            }
        }
    }

    private fun requestHealthPermissions() {
        when (HealthConnectClient.getSdkStatus(this)) {
            HealthConnectClient.SDK_AVAILABLE -> permissionLauncher.launch(healthPermissions)
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> openHealthConnectStore()
            else -> statusText.text = "Health Connect n’est pas disponible sur cet appareil"
        }
    }

    private fun refreshHealthData() {
        val client = healthConnectClient ?: return
        progress.visibility = View.VISIBLE
        statusText.text = "Synchronisation en cours…"

        lifecycleScope.launch {
            runCatching {
                val now = Instant.now()
                val dayStart = now.minus(Duration.ofHours(24))
                val filter = TimeRangeFilter.between(dayStart, now)

                val steps = client.readRecords(ReadRecordsRequest(StepsRecord::class, filter)).records
                val sleep = client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, filter)).records
                val exercise = client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, filter)).records
                val heart = client.readRecords(ReadRecordsRequest(HeartRateRecord::class, filter)).records
                val hydration = client.readRecords(ReadRecordsRequest(HydrationRecord::class, filter)).records

                val stepCount = steps.sumOf { it.count }
                val sleepMinutes = sleep.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
                val exerciseMinutes = exercise.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
                val heartSamples = heart.flatMap { it.samples }
                val avgHeart = if (heartSamples.isEmpty()) null else heartSamples.map { it.beatsPerMinute }.average().roundToInt()
                val waterLitres = hydration.sumOf { it.volume.inLiters }
                val sourcePackages = (steps.map { it.metadata.dataOrigin.packageName } +
                    sleep.map { it.metadata.dataOrigin.packageName } +
                    exercise.map { it.metadata.dataOrigin.packageName } +
                    heart.map { it.metadata.dataOrigin.packageName } +
                    hydration.map { it.metadata.dataOrigin.packageName }).distinct()
                val sourceLabel = if (sourcePackages.isEmpty()) "Aucune donnée reçue" else "Sources : ${sourcePackages.joinToString()}"

                updateMetric("steps", "Pas", stepCount.toString(), sourceLabel)
                updateMetric("sleep", "Sommeil", formatMinutes(sleepMinutes), sourceLabel)
                updateMetric("exercise", "Activité", formatMinutes(exerciseMinutes), "${exercise.size} séance(s) · $sourceLabel")
                updateMetric("heart", "Fréquence cardiaque", avgHeart?.let { "$it bpm" } ?: "Aucune mesure", sourceLabel)
                updateMetric("water", "Hydratation", String.format("%.2f L", waterLitres), sourceLabel)

                val time = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(now)
                statusText.text = "Synchronisé à $time"
            }.onFailure { error ->
                statusText.text = "Synchronisation impossible : ${error.message ?: "erreur inconnue"}"
            }
            progress.visibility = View.GONE
        }
    }

    private fun formatMinutes(total: Long): String {
        val hours = total / 60
        val minutes = total % 60
        return if (hours > 0) "${hours} h ${minutes} min" else "${minutes} min"
    }

    private fun openHealthConnectSettings() {
        try {
            startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            openHealthConnectStore()
        }
    }

    private fun openHealthConnectStore() {
        val packageName = "com.google.android.apps.healthdata"
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
        }
    }
}