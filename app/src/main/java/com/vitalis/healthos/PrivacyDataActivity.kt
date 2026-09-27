package com.vitalis.healthos

import android.Manifest
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.WebStorage
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.health.connect.client.HealthConnectClient
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

class PrivacyDataActivity : ComponentActivity() {
    private lateinit var localStore: VitalisLocalDataStore
    private lateinit var secretStore: SecureSecretStore
    private lateinit var healthStatus: TextView
    private lateinit var nutritionStatus: TextView
    private lateinit var keyStatus: TextView
    private lateinit var microphoneStatus: TextView
    private lateinit var consentSwitch: Switch
    private var pendingExport: String? = null
    private var pendingExportName = "vitalis-export.json"

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val payload = pendingExport
        pendingExport = null
        if (uri == null || payload == null) return@registerForActivityResult
        val saved = runCatching {
            contentResolver.openOutputStream(uri, "wt")?.use {
                it.write(payload.toByteArray(Charsets.UTF_8))
            } ?: error("unwritable_uri")
        }.isSuccess
        toast(if (saved) "Export Vitalis enregistré" else "Échec de l’export")
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) previewImport(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        localStore = VitalisLocalDataStore(this)
        secretStore = SecureSecretStore(this)
        setContentView(buildContent())
    }

    override fun onResume() {
        super.onResume()
        if (::healthStatus.isInitialized) refreshStatus()
    }

    private fun buildContent() = ScrollView(this).apply {
        addView(LinearLayout(this@PrivacyDataActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 44, 40, 60)
            setBackgroundColor(Color.parseColor("#F8F6EF"))

            addView(title("Confidentialité et données", 25f))
            addView(body("Contrôlez les traitements externes et les données locales appartenant à Vitalis."))

            addView(title("IA et traitement externe"))
            keyStatus = body("")
            addView(keyStatus)
            consentSwitch = Switch(this@PrivacyDataActivity).apply {
                text = "Autoriser l’analyse IA des données santé et des photos choisies"
                isChecked = appPreferences().getBoolean(VitalisLocalDataStore.AI_HEALTH_CONSENT, false)
                setOnCheckedChangeListener { _, checked ->
                    appPreferences().edit { putBoolean(VitalisLocalDataStore.AI_HEALTH_CONSENT, checked) }
                }
            }
            addView(consentSwitch)
            addView(button("Gérer les clés IA") {
                startActivity(Intent(this@PrivacyDataActivity, KeySettingsActivity::class.java))
            })
            addView(button("Révoquer le consentement IA", destructive = true) {
                consentSwitch.isChecked = false
                toast("Consentement révoqué. Les nouvelles demandes IA sont bloquées.")
            })
            addView(button("Supprimer les deux clés IA", destructive = true) { confirmDeleteCredentials() })

            addView(title("Données santé"))
            healthStatus = body("")
            addView(healthStatus)
            addView(body("Vitalis ne conserve pas durablement les enregistrements Health Connect bruts."))
            addView(button("Ouvrir les autorisations Health Connect") { openHealthConnectPermissions() })

            addView(title("Nutrition"))
            nutritionStatus = body("")
            addView(nutritionStatus)
            addView(body("Les repas locaux sont inclus dans l’export global Vitalis."))
            addView(button("Exporter uniquement les repas locaux") { beginNutritionExport() })
            addView(button("Supprimer uniquement les repas locaux", destructive = true) {
                confirmDeleteNutrition()
            })

            addView(title("Voix"))
            microphoneStatus = body("")
            addView(microphoneStatus)
            addView(body(
                "La reconnaissance utilise le service vocal Android après action explicite. " +
                    "Vitalis ne crée ni ne conserve de fichier audio."
            ))

            addView(title("Données globales Vitalis"))
            addView(button("Exporter les données locales Vitalis") { beginExport() })
            addView(button("Importer un export Vitalis") {
                importLauncher.launch(arrayOf("application/json", "text/json"))
            })
            addView(button("Supprimer toutes les données locales Vitalis", destructive = true) {
                confirmDeleteAll()
            })

            addView(title("Application"))
            addView(body(
                "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                    "Type de build : ${BuildConfig.BUILD_TYPE}\n" +
                    "Sauvegarde Android : désactivée"
            ))
            addView(button("Fermer") { finish() })
        })
    }

    private fun refreshStatus() {
        val sdk = when (HealthConnectClient.getSdkStatus(this)) {
            HealthConnectClient.SDK_AVAILABLE -> "Disponible"
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "Mise à jour requise"
            else -> "Indisponible"
        }
        val requested = appPreferences().getBoolean("health_connect_permission_requested_v1", false)
        healthStatus.text = "Health Connect : $sdk · demande d’autorisation : ${if (requested) "effectuée" else "non effectuée"}\nCache santé local persistant : aucun"
        val snapshot = localStore.snapshot()
        nutritionStatus.text = "Repas locaux : ${snapshot.nutrition.length()}"
        val healthKey = secretStore.status(AiKeyKind.HEALTH)
        val developerKey = secretStore.status(AiKeyKind.DEVELOPER)
        keyStatus.text = "Health AI : ${configured(healthKey)}\nDeveloper AI : ${configured(developerKey)}"
        microphoneStatus.text = "Permission microphone : " + if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        ) "accordée" else "non accordée"
        consentSwitch.isChecked = snapshot.consentState
    }

    private fun configured(status: SecretStatus): String =
        if (status.configured) "configurée ${status.maskedSuffix.orEmpty()}" else "non configurée"

    private fun beginExport() {
        pendingExport = localStore.export(BuildConfig.VERSION_NAME)
        pendingExportName = "vitalis-export-${LocalDate.now()}.json"
        exportLauncher.launch(pendingExportName)
    }

    private fun beginNutritionExport() {
        pendingExport = JSONObject().apply {
            put("format", "vitalis-nutrition-export")
            put("version", 1)
            put("nutritionSchemaVersion", NutritionMealRecord.CURRENT_SCHEMA_VERSION)
            put("generatedAt", Instant.now().toString())
            put("appVersion", BuildConfig.VERSION_NAME)
            put("meals", JSONArray(localStore.snapshot().nutrition.toString()))
        }.toString(2)
        pendingExportName = "vitalis-nutrition-${LocalDate.now()}.json"
        exportLauncher.launch(pendingExportName)
    }

    private fun previewImport(uri: Uri) {
        val bytes = runCatching { readBounded(uri) }.getOrElse {
            toast("Fichier import illisible")
            return
        }
        val validation = localStore.validateImport(bytes)
        if (!validation.valid || validation.payload == null) {
            toast("Import refusé : ${validation.error}")
            return
        }
        val payload = validation.payload
        AlertDialog.Builder(this)
            .setTitle("Aperçu de l’import")
            .setMessage(
                "${validation.summary}\n\nMERGE conserve les données locales existantes. " +
                    "REPLACE LOCAL DATA remplace uniquement les données locales Vitalis, jamais les clés IA."
            )
            .setNegativeButton("Annuler", null)
            .setNeutralButton("REPLACE LOCAL DATA") { _, _ -> applyImport(payload, VitalisImportMode.REPLACE) }
            .setPositiveButton("MERGE") { _, _ -> applyImport(payload, VitalisImportMode.MERGE) }
            .show()
    }

    private fun applyImport(payload: JSONObject, mode: VitalisImportMode) {
        val result = localStore.import(payload, mode)
        toast(if (result.success) "Import Vitalis terminé" else "Import refusé : ${result.error}")
        refreshStatus()
    }

    private fun readBounded(uri: Uri): ByteArray {
        val input = contentResolver.openInputStream(uri) ?: error("unreadable_uri")
        return input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8_192)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
                if (output.size() > VitalisExportCodec.MAX_IMPORT_BYTES) error("oversized_import")
            }
            output.toByteArray()
        }
    }

    private fun confirmDeleteCredentials() {
        AlertDialog.Builder(this)
            .setTitle("Supprimer les clés IA ?")
            .setMessage("Les clés Health AI et Developer AI seront supprimées de cette installation.")
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Supprimer") { _, _ ->
                secretStore.delete(AiKeyKind.HEALTH)
                secretStore.delete(AiKeyKind.DEVELOPER)
                refreshStatus()
            }.show()
    }

    private fun confirmDeleteNutrition() {
        AlertDialog.Builder(this)
            .setTitle("Supprimer les repas Vitalis ?")
            .setMessage("Les enregistrements Health Connect et les données des fournisseurs ne seront pas supprimés.")
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Supprimer") { _, _ ->
                appPreferences().edit { remove(VitalisLocalDataStore.MANUAL_MEALS_KEY) }
                refreshStatus()
            }.show()
    }

    private fun confirmDeleteAll() {
        AlertDialog.Builder(this)
            .setTitle("Supprimer toutes les données locales Vitalis ?")
            .setMessage(
                "Cette action supprime repas, journal, préférences, coach, tableau de bord et consentement. " +
                    "Les clés IA et les données externes ne sont pas supprimées par cette action."
            )
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Supprimer") { _, _ ->
                val result = localStore.deleteVitalisLocalData()
                if (result.success) {
                    WebStorage.getInstance().deleteOrigin(OriginBridgePolicy.VITALIS_ORIGIN)
                    WebStorage.getInstance().deleteOrigin(OriginBridgePolicy.APPASSETS_ORIGIN)
                }
                toast(
                    if (result.success) "Données locales Vitalis supprimées. Les autorisations Health Connect sont gérées séparément."
                    else "Échec de la suppression locale"
                )
                refreshStatus()
            }.show()
    }

    private fun openHealthConnectPermissions() {
        try {
            startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                "https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"
            )))
        }
    }

    private fun appPreferences() =
        getSharedPreferences(VitalisLocalDataStore.APP_PREFS, MODE_PRIVATE)

    private fun title(value: String, size: Float = 19f) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(Color.parseColor("#123C31"))
        setPadding(0, 18, 0, 7)
    }

    private fun body(value: String) = TextView(this).apply {
        text = value
        textSize = 14f
        setTextColor(Color.parseColor("#405C53"))
        setPadding(0, 4, 0, 11)
    }

    private fun button(label: String, destructive: Boolean = false, action: () -> Unit) =
        Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor(if (destructive) "#A52A24" else "#075F45"))
            setOnClickListener { action() }
        }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
