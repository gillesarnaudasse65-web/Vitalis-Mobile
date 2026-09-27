package com.vitalis.healthos

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.view.autofill.AutofillManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

class KeySettingsActivity : ComponentActivity() {
    private lateinit var store: SecureSecretStore
    private lateinit var healthStatus: TextView
    private lateinit var developerStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        store = SecureSecretStore(this)
        setContentView(buildContent())
        intent.getStringExtra(EXTRA_KEY_KIND)?.let { requested ->
            showKeyDialog(AiKeyKind.fromWire(requested))
        }
    }

    override fun onResume() {
        super.onResume()
        if (::healthStatus.isInitialized) refreshStatus()
    }

    private fun buildContent(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 48, 40, 56)
            setBackgroundColor(Color.parseColor("#F8F6EF"))
        }
        content.addView(title("Gestion sécurisée des clés IA", 24f))
        content.addView(body(
            "La clé est saisie uniquement dans cet écran Android, chiffrée avec Android Keystore " +
                "et n’est jamais renvoyée à la page Web Vitalis. Les captures d’écran sont bloquées ici."
        ))
        healthStatus = body("")
        developerStatus = body("")
        addKeySection(content, AiKeyKind.HEALTH, healthStatus)
        addKeySection(content, AiKeyKind.DEVELOPER, developerStatus)
        content.addView(button("Fermer") { finish() })
        refreshStatus()
        return ScrollView(this).apply { addView(content) }
    }

    private fun addKeySection(parent: LinearLayout, kind: AiKeyKind, statusView: TextView) {
        parent.addView(title(kind.label, 19f))
        parent.addView(statusView)
        parent.addView(button("Ajouter ou remplacer") { showKeyDialog(kind) })
        parent.addView(button("Supprimer la clé", destructive = true) { confirmDelete(kind) })
    }

    private fun showKeyDialog(kind: AiKeyKind) {
        val input = EditText(this).apply {
            hint = "sk-…"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            setAutofillHints(*emptyArray<String>())
            isLongClickable = false
            setTextIsSelectable(false)
            setSingleLine(true)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Configurer ${kind.label}")
            .setMessage("La valeur existante n’est jamais préremplie. Saisissez une nouvelle clé complète.")
            .setView(input)
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Enregistrer", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val candidate = input.text?.toString().orEmpty()
                if (store.save(kind, candidate)) {
                    input.text?.clear()
                    runCatching { getSystemService(AutofillManager::class.java)?.cancel() }
                    dialog.dismiss()
                    refreshStatus()
                } else input.error = "Format de clé invalide"
            }
        }
        dialog.setOnDismissListener { input.text?.clear() }
        dialog.show()
    }

    private fun confirmDelete(kind: AiKeyKind) {
        AlertDialog.Builder(this)
            .setTitle("Supprimer ${kind.label} ?")
            .setMessage("Cette action supprime uniquement la clé chiffrée de cette installation.")
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Supprimer") { _, _ ->
                store.delete(kind)
                refreshStatus()
            }.show()
    }

    private fun refreshStatus() {
        healthStatus.text = statusLabel(AiKeyKind.HEALTH)
        developerStatus.text = statusLabel(AiKeyKind.DEVELOPER)
    }

    private fun statusLabel(kind: AiKeyKind): String {
        val status = store.status(kind)
        return if (status.configured) "Configurée ${status.maskedSuffix.orEmpty()}" else "Non configurée"
    }

    private fun title(value: String, size: Float) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(Color.parseColor("#123C31"))
        setPadding(0, 16, 0, 8)
    }

    private fun body(value: String) = TextView(this).apply {
        text = value
        textSize = 15f
        setTextColor(Color.parseColor("#405C53"))
        setPadding(0, 4, 0, 12)
    }

    private fun button(label: String, destructive: Boolean = false, action: () -> Unit) =
        Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor(if (destructive) "#A52A24" else "#075F45"))
            setOnClickListener { action() }
        }

    companion object {
        const val EXTRA_KEY_KIND = "com.vitalis.healthos.KEY_KIND"
    }
}
