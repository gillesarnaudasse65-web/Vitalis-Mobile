# Vitalis — physical-device hotfix report

Date: 2026-09-28  
Branch: `agent/vitalis-3.15-rc-validation`  
Scope: voice recognition and meal-photo analysis reliability only.

The phone evidence that opened this hotfix is retained truthfully: the QA RC installed and launched; the camera captured a meal photo; voice recognition did not work; the captured photo was not analyzed. Automated evidence does not clear either physical failure.

## Voice

- Physical failure reproduced: **YES, reported on the Android phone**.
- Root cause addressed: the in-process recognizer had no controlled fallback when the device recognition service was absent, busy, failed to start, or exhausted its bounded retry. Its UI also did not expose actionable recognition states.
- `SpeechRecognizer` availability: checked at runtime; physical provider availability remains device-dependent.
- Fallback: `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` through the Activity Result API, bound to the existing `RecognitionSessionCoordinator` session.
- Permission UX: first denial and permanent denial are distinct; permanent denial exposes **Open App Settings**.
- Error UX: permission, start, listening, processing, no-speech, unavailable, network, busy, failure, and ready states are visible.
- Delivery contract: partial text remains display-only; one non-empty final result is consumed at most once; no automatic coach submission and no continuous listening were added.
- Automated tests: coordinator success, retry/fallback, unavailable/start failure, busy/network, denial/permanent-denial copy, one-final guard, stale session, cancellation, and source contracts.
- Result: **AUTOMATED FIX READY / PHYSICAL RETEST REQUIRED**.

## Nutrition

- Camera: existing camera and picker path retained.
- Normalization: existing bounded local validation/normalization retained; raw images are not logged.
- API-key state: queried from the trusted native `SecureSecretStore`, including after return from `KeySettingsActivity`; no key value enters the WebView.
- Consent state: explicit gate with **Autoriser et analyser**; the same scan ID, selected date, and normalized in-memory photo continue after consent.
- Analysis request: starts only when native key and consent gates are satisfied.
- API result: authentication, quota, network, timeout, server, and invalid-response failures have distinct safe codes/messages.
- Retry: **Ré-analyser** reuses the current normalized photo; a new capture is not required unless the image itself is invalid.
- Automated tests: no-key, key-return state, no-consent, consent-ready, mocked success, HTTP 401/429/5xx, network, timeout, malformed response, same-session retry, selected-date preservation, and idempotent save.
- Result: **AUTOMATED FIX READY / LIVE USER-CONTROLLED AI RETEST REQUIRED**.

## Regression

| Layer | Result |
|---|---|
| Source/JavaScript | 65/65 PASS |
| JVM | 91/91 PASS |
| Voice | 27/27 JVM PASS; Run 5 instrumentation PASS |
| Nutrition | 18/18 JVM PASS; Run 4 instrumentation PASS |
| Instrumentation | 10/10 PASS |
| Synthetic upgrades | 2/2 PASS |
| Lint | 0 errors / 54 unchanged warnings |
| QA APK/AAB | PASS — GitHub Actions run 106 |

GitHub Actions run: `36483529996` (`de3a05b63cd8b29f30dd267cb9ceca8fef8dc2f5`).

QA artifact: `Vitalis-3.15.0-rc1-physical-hotfix-qa`. It is test-signed and **NOT FOR PRODUCTION DISTRIBUTION**.

- APK: `Vitalis-3.15.0-rc1-physical-hotfix-qa.apk`, 981,229 bytes, SHA-256 `23c86bda5f08feea64d362f4e271ad180ead1988fcf5e6d9e0c673342ad8dc3a`.
- AAB: `Vitalis-3.15.0-rc1-physical-hotfix-qa.aab`, 994,120 bytes, SHA-256 `df85dcb1fbfe30c7d3144beaeda4995b6bec79991deb3de64f0d2a419852857c`.
- R8 mapping SHA-256: `d777ee652bc3d8244c4eba17eaf4f189bc7edd6957a56a481c4d716c91f51f2f`.
- Artifact archive digest: `sha256:db233ed82479d642e856ce08ecbd1c15030335fd9cd43a06324323a5674d5082`.

## Physical retest required

- Voice: **YES**.
- Nutrition analysis: **YES**.

Retest voice by tapping the coach microphone, observing permission/listening/processing or a precise error, speaking one phrase, and confirming one final text. If the embedded service is unavailable, confirm that the Android fallback opens and returns one final text.

Retest nutrition by capturing a safe sample meal image, confirming the preview, configuring the key if requested in the protected native screen, granting consent if requested, then obtaining either a reviewable estimate or a precise actionable API error. Do not paste the API key into evidence.

Phone-only download: open GitHub Actions run 106, open **Artifacts**, download `Vitalis-3.15.0-rc1-physical-hotfix-qa`, extract it with Android Files, then open the `.apk`. Because this is QA test signing, Android may require uninstalling the earlier differently signed QA APK; this QA reinstall is not the production v20 → v21 upgrade test and must not be reported as upgrade evidence.

## RC status

**RC FAIL** until both real-phone failures are retested successfully and the independent production-signing/upgrade blockers are cleared.

## Integrity

- No unrelated product feature was added.
- No API key or photo is logged or committed.
- No emulator result substitutes for physical evidence.
- Neither failed physical flow is marked PASS.
