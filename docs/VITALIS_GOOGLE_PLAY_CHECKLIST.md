# Vitalis Google Play preparation checklist

This is release-preparation material, not confirmation of publication or legal compliance.

## App identity and listing

- [x] Package name recorded: `com.vitalis.healthos`.
- [x] RC version recorded: `3.15.0-rc1` (`21`).
- [ ] Final app name, category, descriptions, icon, feature graphic, phone screenshots, and support email approved.
- [ ] Public HTTPS privacy-policy URL published and reachable without authentication.
- [ ] Target countries, age group, ads declaration, content rating, and app-access instructions approved.

## Health Connect

- [ ] Confirm requested Health Connect data types exactly match the production manifest and user-facing features.
- [ ] Complete the Play Console Health apps declaration and provide feature justification for each sensitive data type.
- [ ] Confirm minimum-access wording, permission rationale, revocation behavior, and deletion boundaries.
- [ ] Validate the production-signed build on a supported physical device with no-data, partial-permission, full-permission, and revoked-permission states.

## Data Safety draft inputs

The final answers must be reviewed against the production build and service agreements.

| Data or capability | Current implementation summary | Final action |
|---|---|---|
| Health Connect summaries | Read after Android permission; not written or deleted | Confirm declared data types and purposes |
| AI coaching request | Sent only after explicit consent and request | Confirm processor, retention, and disclosure answers |
| Meal image | Normalized image sent only when the user starts analysis | Confirm transient processing and retention |
| Voice recognition | Android recognition service returns text; Vitalis retains no audio recording | Confirm service/OEM disclosure |
| Local app data | Preferences, journal, meals, consent and dashboard configuration | Confirm collection versus on-device-processing answers |
| API key | Native encrypted storage; excluded from WebView/export | Confirm security-practice answers |
| Provider apps | Installed-state detection and external launch only | Do not claim direct OAuth/API synchronization |
| Export/import/delete | User-controlled portable export and local deletion | Confirm deletion-request wording and exclusions |

## Sensitive permissions and disclosures

- [ ] Health Connect permission explanations reviewed.
- [ ] Camera and photo-picker explanations reviewed.
- [ ] Microphone/speech-recognition explanation reviewed.
- [ ] Internet and external-navigation behavior reviewed.
- [ ] Screenshots contain no API key, personal health data, device identifier, or account data.

## Release evidence

- [ ] Production keystore is supplied through protected CI secrets only.
- [ ] APK/AAB certificate SHA-256 matches the stable production identity.
- [ ] Signed APK, signed AAB, R8 mapping, byte sizes, and SHA-256 checksums retained.
- [ ] Production-signed upgrade and physical-device acceptance pass.
- [ ] Privacy policy completes qualified legal review.

