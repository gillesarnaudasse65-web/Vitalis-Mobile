# Vitalis Run 1 — Build, test and security foundation

Date: 2026-09-26. Base: `main` at `fdb8a2ef503872dfbb5bc0147a674ec4bd5b88e1`. Branch: `agent/vitalis-run1-build-test-security-foundation`. The Run 0 documentation commit `6d8dc763a0ed1ed257d5366b5208bc86fe8eb361` was absent from main and its documentation patch was cherry-picked as `ef050726de20b176967451216b947e35cffa5884`. No unrelated Run 0 branch history was merged.

## Toolchain and wrapper

The four wrapper files come from the official Gradle repository tag `v8.11.1` (`481cb05a490e0ef9f8620f7873b83bd8a72e7c39`); the distribution is `https://services.gradle.org/distributions/gradle-8.11.1-bin.zip` with pinned SHA-256 `f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6`. The official wrapper JAR SHA-256 is `2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046`. The JAR archive passed `unzip -t`; `gradlew` is executable. CI validates the wrapper before running it.

JDK 17, Gradle 8.11.1, Android Gradle Plugin 8.9.1, Kotlin 2.0.21, compileSdk 36 and targetSdk 35 remain pinned. No `local.properties` or machine path was committed. The local environment has JDK 17 but no Android SDK, emulator or Gradle distribution cache. Its network route to the Gradle distribution is unavailable, so local `./gradlew --version`, `tasks`, `clean`, `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest` and `connectedDebugAndroidTest` are **BLOCKED locally**, not passed. The new CI run is the intended build verification source; record its result below after execution.

## Test foundation

- Two Node source-contract tests check six unique coach IDs, nonblank names, expected Kofi/Ama/Ayo/Nia/Sékou/Zuri identities, portraits and Android instruction dispatch against the current JavaScript layer. They passed locally with `node --test scripts/test-source-contracts.cjs` (2 passed, 0 failed).
- Two JVM connector tests characterize the 32 unchanged entries, unique IDs and package names, nonblank labels, valid modes and rejection of unknown IDs. The catalogue was extracted without changing its entries.
- Five JVM bridge-input tests cover strict dates, bounded request IDs, meal payload size, fake key/image shapes and external URL restrictions. The production path rejects oversized meal JSON before parsing and invalid image data URLs before network submission. Android JSON schema and repeat-submit behavior are not fully unit-covered in this foundation.
- Three JVM navigation tests cover exact trusted HTTPS hosts/paths, unrelated HTTPS external handling, malformed and dangerous schemes, deceptive hosts, non-default ports and local path traversal.
- One Android instrumentation smoke test starts MainActivity with a debug-only controlled offline flag, checks package and WebView, and waits for the bundled local page. It requires the CI emulator; it does not contact the remote site.

## CI and artifacts

`.github/workflows/build-apk.yml` triggers for PRs targeting main, pushes to main and manual dispatch with `contents: read`. It validates the wrapper, uses Temurin JDK 17, Android SDK setup and Gradle caching, then runs `./gradlew --version`, Node tests, clean, `testDebugUnitTest`, `lintDebug`, `assembleDebug` and `assembleDebugAndroidTest`. A dependent job runs `connectedDebugAndroidTest` on a Google APIs API 35 emulator with KVM. Any mandatory gate failure fails CI; report upload runs even on failure. Unit/lint and instrumentation reports and both debug APKs use 90-day retention. No release APK, AAB or production signing is created.

Run 1 workflow reference and exact gate outcomes: **PENDING** until a new run executes against this branch. Debug APK path when successful: `app/build/outputs/apk/debug/app-debug.apk`; actual size and artifact URL must be read from that run, not inferred.

## WebView and native boundary

Top-level trust is limited to exact `https://vitalis-health-os.gillesarnaudasse65.chatgpt.site` and `https://appassets.androidplatform.net/assets/vitalis/` paths, with a default HTTPS port or 443, no userinfo or dot segments. Ordinary unrelated HTTPS links leave the privileged WebView through `ACTION_VIEW`; cleartext, script/data/file/unknown schemes and malformed URLs are rejected. Untrusted frame navigation is refused; additional windows and JavaScript popups are disabled. SSL errors are cancelled and the offline fallback is requested. The `VitalisAndroid` bridge is registered for approved pages and removed on an untrusted top-level load or SSL error. Native bridge inputs now have bounded checks, and unknown connector IDs fail closed.

**Residual high-priority risk:** Android `addJavascriptInterface` exposes the object to all frames in a trusted top-level page. Top-level URL checks and subframe navigation restrictions do not prove the caller origin or prevent every embedded frame case. A compatible origin-aware `WebViewCompat.addWebMessageListener` migration, with a controlled legacy JavaScript proxy, remains a dedicated security task. The API key is still entered through the remote DOM. Neither issue is claimed fixed. The bridge's existing public methods, preference names, Keystore alias and user data contracts are unchanged.

## Secrets, logging and deferred defects

Source inspection found no API key, Authorization header, full health record, meal image or voice transcript logging introduced here. Fake test key strings only; no production credentials, tokens, keystores or personal health data. CI commands do not print secrets. The remote DOM key-entry exposure remains open.

Run 0 evidence remains authoritative. **STILL OPEN:** Nia and Sékou card-click interception, selected-day reset, Health Connect pagination and deduplication, scanner duplicate save and capture validation, voice lifecycle, provider OAuth, full deletion/export and production signing. These paths were **NOT RETESTED** by this run. Run 2 should characterize and repair coach clicks and date behavior on a real rendered WebView. Readiness for Run 2 depends on the new CI gates and APK passing; no release readiness is implied.

## Files and integrity

- `gradlew`, `gradlew.bat`, `gradle/wrapper/*`: official pinned wrapper.
- `.github/workflows/build-apk.yml`: mandatory gates, emulator and retained artifacts.
- `app/build.gradle.kts`: JUnit and instrumentation dependencies/runner.
- `NavigationPolicy.kt`, `BridgeInputPolicy.kt`, `ConnectorCatalog.kt`: small pure policy/catalogue extractions.
- `MainActivity.kt`: policy application, bounded inputs, fail-closed connector IDs, controlled offline smoke start.
- `app/src/test/.../*Test.kt`, `app/src/androidTest/.../MainActivitySmokeTest.kt`, `scripts/test-source-contracts.cjs`: foundational tests.
- This file and the dated Run 1 appendix in `VITALIS_314_BASELINE.md`: results and remaining limitations.

Vitalis was not rebuilt; the UI, applicationId, versionName and versionCode were not changed. No out-of-scope defect is represented as fixed, and no unexecuted test is represented as passed.
