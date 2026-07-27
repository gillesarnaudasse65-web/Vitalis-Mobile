package com.vitalis.healthos

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
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
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var healthConnectClient: HealthConnectClient? = null
    private lateinit var statusText: TextView
    private lateinit var stepsValue: TextView
    private lateinit var sleepValue: TextView
    private lateinit var exerciseValue: TextView
    private lateinit var hydrationValue: TextView
    private lateinit var coachText: TextView
    private lateinit var connectButton: Button

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
        statusText.text = if (granted.isEmpty()) {
            "Autorisation refusée — choisissez au moins une catégorie"
        } else {
            "Health Connect autorisé — synchronisation en cours"
        }
        refreshHealthData()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#073F32")
        window.navigationBarColor = Color.parseColor("#073F32")
        setContentView(buildNativeDashboard())
        initializeHealthConnect()
    }

    private fun initializeHealthConnect() {
        when (HealthConnectClient.getSdkStatus(this)) {
            HealthConnectClient.SDK_AVAILABLE -> {
                healthConnectClient = HealthConnectClient.getOrCreate(this)
                statusText.text = "Health Connect disponible"
                refreshHealthData()
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                statusText.text = "Health Connect doit être installé ou mis à jour"
                connectButton.text = "Installer Health Connect"
                connectButton.setOnClickListener { openHealthConnectStore() }
            }
            else -> {
                statusText.text = "Health Connect n’est pas disponible sur cet appareil"
                connectButton.isEnabled = false
            }
        }
    }

    private fun buildNativeDashboard(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F7F5EE"))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(36))
        }
        scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(TextView(this).apply {
            text = "Vitalis Health OS"
            setTextColor(Color.parseColor("#073F32"))
            textSize = 29f
            typeface = Typeface.create("serif", Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Votre santé, directement sur Android"
            setTextColor(Color.parseColor("#6F7C75"))
            textSize = 14f
            setPadding(0, dp(4), 0, dp(20))
        })

        val hero = panel("#073F32").apply {
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        hero.addView(TextView(this).apply {
            text = "PRÊT POUR AUJOURD’HUI"
            setTextColor(Color.parseColor("#8EE0B2"))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
        })
        hero.addView(TextView(this).apply {
            text = "Votre tableau de bord natif"
            setTextColor(Color.WHITE)
            textSize = 25f
            typeface = Typeface.create("serif", Typeface.BOLD)
            setPadding(0, dp(10), 0, dp(7))
        })
        statusText = TextView(this).apply {
            text = "Initialisation de Health Connect…"
            setTextColor(Color.parseColor("#C5DACE"))
            textSize = 14f
        }
        hero.addView(statusText)
        connectButton = Button(this).apply {
            text = "Autoriser Health Connect"
            isAllCaps = false
            setTextColor(Color.parseColor("#073F32"))
            setBackgroundColor(Color.parseColor("#75D69E"))
            setPadding(dp(12), dp(4), dp(12), dp(4))
            setOnClickListener { permissionLauncher.launch(healthPermissions) }
        }
        hero.addView(connectButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(18) })
        root.addView(hero, fullWidthMargins(0, 0, 0, 16))

        stepsValue = metricCard(root, "PAS AUJOURD’HUI", "—", "Objectif 8 000 pas", "#EAF7EF")
        sleepValue = metricCard(root, "SOMMEIL (24 H)", "—", "Durée détectée", "#F0EEFA")
        exerciseValue = metricCard(root, "ACTIVITÉ", "—", "Minutes d’exercice aujourd’hui", "#EAF5F7")
        hydrationValue = metricCard(root, "HYDRATATION", "—", "Objectif 2,0 L", "#EAF4FA")

        val coach = panel("#FFFFFF")
        coach.addView(TextView(this).apply {
            text = "KOFI · COACH VITALIS"
            setTextColor(Color.parseColor("#258055"))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
        })
        coachText = TextView(this).apply {
            text = "Connectez Health Connect pour recevoir une recommandation adaptée à votre journée."
            setTextColor(Color.parseColor("#29483A"))
            textSize = 16f
            setLineSpacing(0f, 1.25f)
            setPadding(0, dp(12), 0, 0)
        }
        coach.addView(coachText)
        root.addView(coach, fullWidthMargins(0, 4, 0, 16))

        root.addView(TextView(this).apply {
            text = "Les données affichées sont lues localement depuis Health Connect. Aucune connexion au site ChatGPT n’est nécessaire."
            setTextColor(Color.parseColor("#7B8981"))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), 0)
        })
        return scroll
    }

    private fun metricCard(root: LinearLayout, title: String, initial: String, subtitle: String, color: String): TextView {
        val card = panel(color)
        card.addView(TextView(this).apply {
            text = title
            setTextColor(Color.parseColor("#48705B"))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
        })
        val value = TextView(this).apply {
            text = initial
            setTextColor(Color.parseColor("#0A4335"))
            textSize = 35f
            typeface = Typeface.create("serif", Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(4))
        }
        card.addView(value)
        card.addView(TextView(this).apply {
            text = subtitle
            setTextColor(Color.parseColor("#718078"))
            textSize = 13f
        })
        root.addView(card, fullWidthMargins(0, 0, 0, 12))
        return value
    }

    private fun panel(color: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(Color.parseColor(color))
            setStroke(dp(1), Color.parseColor("#DDE6E0"))
        }
        elevation = dp(2).toFloat()
    }

    private fun fullWidthMargins(left: Int, top: Int, right: Int, bottom: Int) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(left), dp(top), dp(right), dp(bottom))
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun refreshHealthData() {
        val client = healthConnectClient ?: return
        lifecycleScope.launch {
            runCatching {
                val granted = client.permissionController.getGrantedPermissions()
                if (granted.isEmpty()) {
                    statusText.text = "Appuyez sur « Autoriser Health Connect »"
                    return@launch
                }
                connectButton.text = "Modifier les autorisations"
                statusText.text = "Connecté · données actualisées"

                val now = Instant.now()
                val zone = ZoneId.systemDefault()
                val startToday = LocalDate.now(zone).atStartOfDay(zone).toInstant()
                var steps = 0L

                if (HealthPermission.getReadPermission(StepsRecord::class) in granted) {
                    val records = client.readRecords(
                        ReadRecordsRequest(StepsRecord::class, TimeRangeFilter.between(startToday, now))
                    ).records
                    steps = records.sumOf { it.count }
                    stepsValue.text = String.format(Locale.getDefault(), "%,d", steps)
                } else stepsValue.text = "Non autorisé"

                if (HealthPermission.getReadPermission(SleepSessionRecord::class) in granted) {
                    val records = client.readRecords(
                        ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(now.minus(24, ChronoUnit.HOURS), now))
                    ).records
                    val minutes = records.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
                    sleepValue.text = "${minutes / 60} h ${minutes % 60}"
                } else sleepValue.text = "Non autorisé"

                if (HealthPermission.getReadPermission(ExerciseSessionRecord::class) in granted) {
                    val records = client.readRecords(
                        ReadRecordsRequest(ExerciseSessionRecord::class, TimeRangeFilter.between(startToday, now))
                    ).records
                    val minutes = records.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
                    exerciseValue.text = "$minutes min"
                } else exerciseValue.text = "Non autorisé"

                if (HealthPermission.getReadPermission(HydrationRecord::class) in granted) {
                    val records = client.readRecords(
                        ReadRecordsRequest(HydrationRecord::class, TimeRangeFilter.between(startToday, now))
                    ).records
                    val liters = records.sumOf { it.volume.inLiters }
                    hydrationValue.text = String.format(Locale.getDefault(), "%.1f L", liters)
                } else hydrationValue.text = "Non autorisé"

                coachText.text = when {
                    steps < 2000 -> "Commencez doucement : une marche de 10 minutes aidera votre circulation et votre énergie."
                    steps < 8000 -> "Bonne progression. Une courte marche supplémentaire vous rapprochera de votre objectif de 8 000 pas."
                    else -> "Objectif de pas atteint. Priorisez maintenant l’hydratation et la récupération."
                }
            }.onFailure {
                statusText.text = "Impossible de lire certaines données · vérifiez les autorisations"
                coachText.text = "Health Connect est disponible, mais certaines catégories doivent encore être autorisées."
            }
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
