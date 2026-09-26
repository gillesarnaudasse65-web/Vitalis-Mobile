package com.vitalis.healthos

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationPolicyTest {
    private val remote = "https://vitalis-health-os.gillesarnaudasse65.chatgpt.site"

    @Test fun exactRemoteOriginAndLocalAssetsAreTrusted() {
        for (url in listOf("$remote/", "$remote/dashboard?day=2026-09-26#score",
            "HTTPS://VITALIS-HEALTH-OS.GILLESARNAUDASSE65.CHATGPT.SITE:443/__vitalis/coaches/kofi.webp",
            "https://appassets.androidplatform.net/assets/vitalis/index.html",
            "https://appassets.androidplatform.net/assets/vitalis/coaches/kofi.webp")) {
            assertEquals(url, NavigationPolicy.Decision.TRUSTED, NavigationPolicy.decide(url))
        }
    }

    @Test fun unrelatedHttpsLeavesPrivilegedWebView() {
        for (url in listOf("https://example.org/", "https://chatgpt.com/codex",
            "https://vitalis-health-os.gillesarnaudasse65.chatgpt.site.attacker.example/")) {
            assertEquals(url, NavigationPolicy.Decision.EXTERNAL_HTTPS, NavigationPolicy.decide(url))
        }
    }

    @Test fun invalidSchemesHostsAndLocalPathsAreRejected() {
        for (url in listOf(null, "", "http://vitalis-health-os.gillesarnaudasse65.chatgpt.site/",
            "javascript:alert(1)", "data:text/html,evil", "file:///etc/passwd", "intent://evil",
            "https://user@vitalis-health-os.gillesarnaudasse65.chatgpt.site/",
            "https://appassets.androidplatform.net/anything-else",
            "https://appassets.androidplatform.net/assets/vitalis/../private",
            "https://vitalis-health-os.gillesarnaudasse65.chatgpt.site:8443/",
            "https://", "https://good.example/" + "x".repeat(8_193))) {
            assertEquals(url, NavigationPolicy.Decision.REJECT, NavigationPolicy.decide(url))
        }
    }
}
