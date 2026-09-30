# Vitalis — deep stability report

Audit baseline: GitHub Actions run 111, commit `c087066600181e705c1f7cd70c1569fd2cadcb3d`  
Reliability implementation: commit `2bb9c9326c73e00bd1feaada7332daf16be54e97`  
Scope: reliability hardening only; no product feature or signing-identity change.

## Executive status

- Stable: **NO — physical retest required**
- Physical retest required: **YES**
- PR #13: **KEEP OPEN**

Automated success is necessary but does not establish real-phone stability. The previous phone failures for voice recognition, meal-photo analysis, partial/blank UI, and coach display remain open until the new QA build passes the matrix below.

## Images

- Coach images: all six exact lowercase WebP files are bundled under Android assets and use the `appassets.androidplatform.net` origin as the primary path.
- Nutrition photos: the normalized active-scan JPEG is held in a private, expiring application cache so Activity/process recreation and settings/consent round trips can resume the same scan.
- Fallback: every coach portrait gets a deterministic local SVG initial/name avatar when decoding or routing fails.
- Broken references: image-error handling removes failed portrait sources; legacy object URLs are revoked only after replacement, failure, or modal closure.
- Root causes corrected: remote-origin asset dependence, missing image `onerror` handling, in-memory-only active nutrition image ownership, and unbounded legacy object-URL lifetime.

### Active meal-photo lifecycle

1. Camera output is written to the private FileProvider capture file.
2. Android decodes, bounds, rotates, and normalizes it to JPEG.
3. The normalized JPEG is atomically written to the private `nutrition-active` cache before the raw camera file is deleted.
4. Scan metadata, review draft, and status are stored separately; the normalized image can be reloaded after Activity/process recreation.
5. Key settings, consent settings, retryable network/API errors, and analysis retries retain the same active image.
6. Cancel, supersede, successful meal save, local-data deletion, invalid content, or 24-hour expiry deletes the temporary image.
7. Saved meals do not retain a photo by default; the UI uses a clean meal placeholder instead of a broken image.

## Coaches

- Six coaches: preserved — Kofi, Ama, Ayo, Nia, Sékou, and Zuri.
- Selection: one stable coach ID is used.
- Persistence: native local state hydrates Web storage before coach/dashboard initialization, preventing the Web default from overwriting an explicit selection.
- Portraits: bundled first, deterministic fallback second; no remote-only dependency.
- Chat: existing local and configured-AI paths preserved.
- Voice: the existing session-owned recognizer and controlled intent fallback are preserved; phone retest remains required.

## Health Connect

- SDK: availability, missing provider, and update-required states remain distinct.
- Permissions: never requested, denied/revoked, partial, and full permission sets are distinct.
- Data: authorization and selected-day record availability are distinct; package installation is never data evidence.
- Return flow: foreground return and explicit Health Connect/settings return refresh the native state.
- Source attribution: only selected-day record origins are attributed; Vitalis local nutrition is excluded from external connector discovery.

Public state vocabulary: `AVAILABLE`, `PERMISSION_REQUIRED`, `PARTIAL_PERMISSION`, `AUTHORIZED`, `NO_DATA`, `DATA_AVAILABLE`, `PROVIDER_UPDATE_REQUIRED`, `UNAVAILABLE`, and `ERROR`. The UI currently emits the most specific applicable state and never equates permission with data.

## Connectors

- Catalog: all declared package aliases are covered by Android package queries.
- Installed detection: retained as installation evidence only.
- Data detection: requires Health Connect record origin attribution for the selected day.
- Launch: existing supported app/settings launch paths are preserved.
- Return: Vitalis retains native state and refreshes after foreground return without forcing a whole-app reload.
- Truthfulness: partial permission, no data, unavailable direct API, unsupported platform, and provider data are separate states; “connected” is not inferred from installation.

## WebView

- Bootstrap: native state hydration precedes selected date, compatibility, coaches/power layer, Final UX, health data, and connector state.
- Partial-init recovery: guards now distinguish `initializing` from `ready`; bounded recovery clears only incomplete layers and retries them.
- Remote fallback: a loaded but incomplete remote shell is rejected by the startup readiness check.
- Offline fallback: after bounded failure, Vitalis loads the bundled local interface.
- Renderer failure: Android WebView renderer termination recreates the Activity while native-persisted state remains authoritative.

## Lifecycle

- Restart: selected date, coach, dashboard settings, local nutrition, journal, consent, and active-scan metadata are restored from their owners.
- Background/foreground: voice/TTS resources stop safely; Health Connect and connector evidence refreshes on return.
- Process recreation: pending camera URI/file state and normalized active-scan image are recoverable.
- State persistence: hydration order prevents implicit defaults from silently resetting coach or dashboard state.

## Nutrition

- Photo lifecycle: private temporary persistence for the active scan only; no permanent meal-photo storage by default.
- Analysis: native key/consent readiness remains authoritative.
- Retry: the same normalized photo survives settings, consent, transient API, rate-limit, timeout, network, 5xx, and malformed-response handling.
- Privacy: no image or credential is exported, logged, committed, or uploaded as a build artifact.

## Voice

- SpeechRecognizer: session-owned state machine with exactly-once final consumption.
- Fallback: one controlled `RecognizerIntent` fallback.
- Errors: denied/permanent denial, unavailable, busy, network, no-match, retry exhaustion, cancellation, and success remain visible and bounded.

## UI

- Dashboard: startup readiness requires a mounted Final UX root and rendered widgets; an empty shell cannot be accepted as ready.
- Widgets: all twelve existing widget IDs and destinations are preserved; repeated settings operations are covered by stress tests.
- Themes: Classic, Ocean, Dark, AMOLED, Aurora, and System remain unchanged.
- Safe areas/navigation: existing premium layout and native bar handling remain unchanged; physical verification is still mandatory.

## Tests

| Layer | Result |
|---|---|
| Source/JavaScript | 76/76 PASS |
| JVM | 100/100 PASS |
| Health Connect JVM | 13/13 PASS |
| Voice JVM | 27/27 PASS |
| Nutrition JVM | 18/18 PASS |
| Connectors JVM | 13/13 PASS |
| Instrumentation | 10/10 PASS |
| Synthetic upgrade | 2/2 PASS |
| Lint | 0 errors / 54 unchanged warnings |
| Debug APK | PASS |
| Minified QA APK/AAB/R8 | PASS |

GitHub Actions [run 113](https://github.com/gillesarnaudasse65-web/Vitalis-Mobile/actions/runs/36667274375) passed at commit `2bb9c9326c73e00bd1feaada7332daf16be54e97`: **188/188 automated executions**. Run 112 is retained as useful negative evidence: its synthetic upgrade exposed an absent-native-versus-empty-Web journal migration defect. The fix preserves the legacy Web journal until native synchronization migrates it; run 113 then passed both seed and verify phases.

New coverage includes bundled/missing/offline coach images, native coach hydration, active nutrition image cache and cleanup, startup readiness, package-query completeness, partial connector state, repeated date/scan/connector transitions, repeated widget settings, and test-signing labeling.

## Physical QA artifact

- Name: `Vitalis-3.15.0-rc1-deep-stability-qa`
- Signing: test-signed, **NOT FOR PRODUCTION DISTRIBUTION**
- Download: [GitHub Actions artifact 11077375603](https://github.com/gillesarnaudasse65-web/Vitalis-Mobile/actions/runs/36667274375/artifacts/11077375603)
- APK: 988,469 bytes; SHA-256 `e27a22fd4c67c7da65b9b750427816b6796f82e49b4aef24e4d8118c61884fd7`
- AAB: 1,004,019 bytes; SHA-256 `caad3a2cfe166f040491ca0889fdb9de1275fcb5a602ba42271dfd647dadd29c`
- R8 mapping: 6,079,253 bytes; SHA-256 `7eddecae6c8f0d37f562cd07a43c69b6382fc883a3e95e196c3446c4186b3f3d`
- Verification: APK Signature Scheme v2 PASS; AAB JAR signature PASS.
- Certificate: `CN=Android Debug, O=Android, C=US` — expected QA test identity, explicitly not production.

## Required phone matrix

Use only `PASS`, `FAIL`, or `NOT TESTED`. Five cold launches must render the complete dashboard. Verify six coaches/portraits and repeated switching; lock/unlock and background/foreground; camera/picker, settings and consent return, retry without recapture; Health Connect grant/partial/revoke/return; connector install/launch/return truthfulness; voice and fallback; airplane mode/reconnect; themes/widgets/reorder; and persistence of date, coach, nutrition, journal, theme, accent, preset, and widgets.

## Remaining risks

- Real OEM WebView, renderer, camera, picker, microphone, TTS, and process-kill behavior are not established by CI.
- Real Health Connect provider data, revocation, partial permission, and third-party return behavior require a phone.
- Live nutrition AI success/error/retry still depends on network and a user-configured key/consent.
- Production signing, recovery, same-certificate v20→v21 upgrade, and the broader release acceptance gates remain separate blockers.

## RC decision

**RC FAIL — do not merge PR #13 until the new QA artifact passes real-phone acceptance and the existing production-signing/upgrade gates are cleared.**
