# Vitalis privacy policy

> **DRAFT — REQUIRES LEGAL REVIEW BEFORE PUBLIC RELEASE**

Vitalis is an Android health companion. This draft describes the behavior of the Run 6 source and is not legal advice.

## Data accessed and purpose

With your Android permission, Vitalis reads supported Health Connect categories to display summaries for a date you select and to provide requested wellness coaching. Vitalis does not write or delete provider records. Health Connect permissions remain controlled separately in Android settings.

If you enable Health AI consent and make a request, Vitalis may send the health summary needed for that request to the configured AI service. If you choose a meal photo and start analysis, Vitalis may send the normalized image for nutrition estimation. Estimates and coaching are informational and are not medical diagnosis.

Voice input uses an Android speech-recognition service after your explicit action. That service’s own privacy terms may apply. Vitalis does not create or keep an audio recording; it receives recognized text. Text-to-speech uses the Android TTS service.

Installed provider apps may be detected to explain available setup paths. This does not mean Vitalis has direct OAuth/API access or that data is synchronized. Provider accounts, tokens, and cloud data are controlled by those providers.

## Local storage and credentials

Vitalis stores app preferences, selected coach/date, dashboard state, local journal, local nutrition estimates, consent state, and temporary scanner state on the device. AI keys are entered in a screenshot-protected native screen and encrypted using Android Keystore. The complete key is never returned to the WebView, copied to the clipboard, logged, or exported.

Android backup and device-transfer extraction are disabled for Vitalis data. A user-created export is the supported portable recovery mechanism for Vitalis-owned local data.

## Controls

The native **Privacy & Data** screen lets you:

- grant or revoke Health-AI consent;
- view key status and delete AI credentials separately;
- open Android Health Connect permissions;
- view, export, or delete local nutrition data;
- export all supported Vitalis-owned local data;
- preview and merge or replace local data from a valid Vitalis export;
- delete all Vitalis-owned local data.

Global local deletion removes Vitalis preferences, local meals/journal, UI state, consent, and temporary scanner material. It does not delete AI credentials unless you choose the separate credential action. It does not delete Health Connect records, provider data, provider accounts, or Android-level permissions. The screen states this boundary and links to Health Connect settings.

## Export scope

The global export includes only allowlisted Vitalis-owned settings and local content. It excludes AI keys and ciphertext, Keystore material, authorization headers, raw Health Connect records, temporary images, microphone/audio data, provider credentials, external cloud data, and signing keys. It is not a complete backup of every health source.

## Network destinations

Vitalis uses HTTPS for the exact Vitalis UI origin and, only for user-requested functions, the OpenAI Responses API and approved external navigation such as ChatGPT Work or Google Play. Cleartext network traffic is disabled. Final public disclosures, controller/contact details, retention periods, lawful bases, processor terms, regional rights, and age requirements must be supplied and legally reviewed before release.
