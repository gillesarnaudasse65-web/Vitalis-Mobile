# Vitalis 3.15.0-rc1 release notes

Status: **release candidate — not a stable production release**

## Included stabilization

- Health Connect reliability, explicit permission/no-data/error states, historical-date reads, refresh, and source attribution.
- Stable coach identifiers and selected-date preservation across navigation and recreation.
- Native photo picker/camera nutrition workflow, bounded image normalization, editable review, idempotent save, local journal edit/delete, and historical-date integrity.
- Voice recognition/TTS lifecycle controls, duplicate-final protection, cancellation, foreground/background handling, and French-first fallback behavior.
- Physical-device hotfix: actionable voice states plus an Android recognition-activity fallback, and native-trusted meal-analysis key/consent gates that preserve the current normalized photo for retry.
- Truthful provider detection and launch/status guidance without claiming direct OAuth or cloud synchronization.
- Native screenshot-protected API-key entry backed by Android Keystore; keys are excluded from WebView state and exports.
- Privacy and local-data controls for consent, export/import, merge/replace, app-data deletion, and separate key deletion.
- Classic, Ocean, Dark, AMOLED, Aurora, and System themes; six accents; twelve interactive widgets; persistent dashboard order, visibility, density, and presets.

## Upgrade and compatibility

- Application ID remains `com.vitalis.healthos`.
- Candidate version is `3.15.0-rc1` (`versionCode 21`).
- Automated synthetic upgrade coverage preserves established Run 5/6 data contracts.
- Production upgrade continuity still requires the established production signing identity and the previous production-signed APK.

## Known limitations and external gates

- A test-signed artifact is for QA only and is **NOT FOR PRODUCTION DISTRIBUTION**.
- Production signing identity, signing-certificate continuity, and production-signed upgrade remain externally gated.
- Physical-device acceptance is mandatory and has not been replaced by emulator evidence.
- Real Health Connect providers, camera, microphone, TTS, font/display scaling, orientation, offline recovery, and OEM behavior require physical-device execution.
- AI nutrition analysis requires a key intentionally supplied by the user. No key is bundled.
- Voice recognition and live meal-photo analysis require a new physical retest; the earlier phone run reported both flows failing even though camera capture itself succeeded.
- Provider launch/detection does not constitute direct provider integration.
- The privacy policy remains a draft requiring qualified legal review.
