# Vitalis Run 6 — security and release foundation

Status: implementation complete in source; production signing and physical-device validation remain external gates.

## Security architecture

Before Run 6, the remote WebView DOM rendered OpenAI password inputs and a global `addJavascriptInterface` object was visible to every frame. Run 6 removes the DOM credential fields and the JavaScript save/delete secret methods. `KeySettingsActivity` is now the only key-entry surface. It uses obscured input, disables autofill and text selection, never prepopulates a key, clears editable input, and applies `FLAG_SECURE`.

The encrypted preference names and Android Keystore alias remain unchanged (`vitalis_secure_preferences`, `openai_api_key`, `openai_developer_api_key`, `vitalis_openai_key_v1`), so an existing encrypted key remains readable. JavaScript receives only configured state and never a key value.

`WebViewCompat.addWebMessageListener` provides the native channel. Only these exact origins are registered:

- `https://vitalis-health-os.gillesarnaudasse65.chatgpt.site`
- `https://appassets.androidplatform.net`

Every call is checked again for exact origin, main-frame ownership, known method, bounded JSON, and method-specific payload/state validation. The injected `window.VitalisAndroid` compatibility proxy preserves the existing frontend contract without exposing plaintext key operations. Capabilities are classified as health, AI, nutrition, voice, navigation, privacy, and read-only.

## Consent

Health-AI consent remains an app preference. Revocation increments an ownership epoch, cancels tracked health-AI and nutrition jobs, blocks new health-AI work, updates the WebView state, and causes a late result with an older token to be discarded. Revocation does not delete credentials; deletion is a separate explicit action.

## Data controls

`PrivacyDataActivity` shows AI status/consent, Health Connect availability and permission routing, nutrition count/export/delete, microphone status and explanation, global export/import/delete, and version/build information.

The global export format is `vitalis-export`, version 1. It includes allowlisted Vitalis preferences, local nutrition, selected coach, dashboard settings, local journal, and consent state. It excludes plaintext or encrypted credentials, Keystore material, authorization headers, raw Health Connect source records, temporary images, audio, provider tokens, and signing material. The Android document picker controls the destination.

Global deletion clears Vitalis preferences, meals, journal, selected coach, dashboard state, consent, temporary nutrition captures, pending scanner state, and both trusted-origin WebStorage stores. AI credentials are intentionally preserved unless the separate credential-deletion action is selected. Health Connect records, permissions, and provider-owned data are never deleted; the UI links to Android’s Health Connect settings.

Import is bounded to 1 MiB, parses JSON only, checks format/version/schema, rejects secret-like fields and unsupported preferences, validates every meal, and previews the change. `MERGE` deduplicates by stable IDs. `REPLACE LOCAL DATA` replaces only Vitalis-owned local data and never credentials. Validation occurs before mutation; malformed legacy records are preserved by the nutrition migration layer rather than silently deleted.

## Storage versions

| Store | Version | Behavior |
|---|---:|---|
| Global export | 1 | Same-version import; future versions rejected |
| Native local metadata | 1 | Written on sync/import/delete without rewriting valid legacy data |
| Nutrition meal record | 2 | Legacy fields decoded and normalized; invalid raw records preserved |
| Pending nutrition scan | 1 | Temporary state; cleared on global delete |
| Dashboard settings | 1 (container metadata) | Opaque bounded JSON |
| Local journal | 1 (container metadata) | Bounded JSON entries, maximum 500 |

## Release and signing

The release build enables R8 and resource shrinking and disables WebView debugging through `BuildConfig.DEBUG`. Signing values are read only from environment variables:

- `VITALIS_KEYSTORE_PATH`
- `VITALIS_KEYSTORE_PASSWORD`
- `VITALIS_KEY_ALIAS`
- `VITALIS_KEY_PASSWORD`

CI accepts `VITALIS_KEYSTORE_BASE64` as the encoded keystore secret, decodes it only into runner temporary storage, and passes the remaining secrets to Gradle. No key or password belongs in Git. When those secrets are absent, CI builds an explicitly labelled **test-signed** minified APK/AAB using the debug identity. That proves compilation and emulator installability, not production signing continuity. A stable production signature remains required before production upgrade qualification.

CI produces debug/test APKs, minified release APK, AAB, `mapping.txt`, test/lint/instrumentation reports, release smoke evidence, upgrade evidence, and SHA-256 checksums with 90-day retention.

## Upgrade and rollback

The emulator upgrade test installs the verified Run 5 merge commit (`dd78d30244bdedb62346ab6f02f3063a590c989c`), seeds a selected date, selected coach, meal, consent, connector preference, journal, and dashboard state, then installs Run 6 with `adb install -r` under the same debug signing identity. A post-upgrade instrumentation check verifies that the package starts and those values remain. This is valid debug-signature upgrade evidence; it is not a claim about a production key that has not been supplied.

Normal rollback is export-first. Android ordinarily rejects a lower version code, and schema-forward data may not be understood by an older build. Supported recovery is: export before upgrade, retain the matching signed APK/AAB and mapping file, restore a known-good build through the authorized distribution channel, then import only a format version that build supports. A destructive uninstall is not the normal rollback path because it loses app-local data.

## Security audit

- Cleartext traffic is disabled; mixed content and WebView file/content access are disabled.
- SSL errors are cancelled; there is no trust-all certificate, hostname override, or debug certificate bypass.
- Approved production endpoints are the exact Vitalis HTTPS origin, OpenAI Responses API, ChatGPT Work handoff, Google Play details pages, and Android/Health Connect system intents.
- Release production sources contain no `Log.d`, `Log.v`, `println`, or `console.log` diagnostics. Authorization values and health prompts are not logged.
- `MainActivity` is exported only as the launcher. The Health permission-usage alias is protected by Android’s `START_VIEW_PERMISSION_USAGE`. Key/privacy activities and FileProvider are non-exported.
- Android backup and device-transfer extraction are disabled for all data domains.
- Screenshot blocking is limited to credential entry/management; it is not applied to the entire health UI.

## Unresolved limitations

- Production signing secrets are not present in the repository and must be provisioned securely.
- Physical-device key entry, screenshot blocking, Health Connect/provider behavior, camera, microphone/TTS, and OEM behavior require device testing.
- The privacy policy draft requires legal review.
- Direct provider OAuth/API integrations remain out of scope and are not implied by installed-app detection.
- Final UX polish and final release-candidate regression belong to the next run.
