package com.vitalis.healthos

import java.net.URI
import java.util.Locale

/** Top-level navigation only. addJavascriptInterface is still visible to subframes. */
internal object NavigationPolicy {
    private const val REMOTE_HOST = "vitalis-health-os.gillesarnaudasse65.chatgpt.site"
    private const val ASSET_HOST = "appassets.androidplatform.net"

    enum class Decision { TRUSTED, EXTERNAL_HTTPS, REJECT }

    fun decide(rawUrl: String?): Decision {
        if (rawUrl.isNullOrBlank() || rawUrl.length > 8_192) return Decision.REJECT
        val uri = try { URI(rawUrl.trim()) } catch (_: Exception) { return Decision.REJECT }
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.rawUserInfo != null ||
            uri.port !in listOf(-1, 443)) return Decision.REJECT
        val host = uri.host?.lowercase(Locale.ROOT) ?: return Decision.REJECT
        val path = uri.rawPath ?: return Decision.REJECT
        if (path.startsWith("//") || path.split('/').any { it == "." || it == ".." }) return Decision.REJECT
        return when (host) {
            REMOTE_HOST -> Decision.TRUSTED
            ASSET_HOST -> if (path == "/assets/vitalis/index.html" ||
                path.startsWith("/assets/vitalis/")) Decision.TRUSTED else Decision.REJECT
            else -> if (uri.rawQuery == null || uri.rawQuery.length <= 4_096) Decision.EXTERNAL_HTTPS
                else Decision.REJECT
        }
    }

    fun isTrusted(rawUrl: String?): Boolean = decide(rawUrl) == Decision.TRUSTED
}
