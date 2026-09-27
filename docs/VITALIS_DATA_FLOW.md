# Vitalis data flow

This document describes Run 6 behavior. It does not contain credentials or user data.

## Health Connect to Vitalis

The user explicitly grants Android Health Connect read permissions. Vitalis reads the selected date, computes an in-memory normalized payload, and sends that payload only to the trusted main-frame UI. Raw Health Connect records are not persisted by the global Vitalis store and are not included in export. Provider apps remain the owners of their data.

## Meal image to AI service

The user chooses camera or gallery. Android normalizes a bounded image into temporary app storage, the user explicitly starts analysis, and Vitalis sends that chosen image plus the minimum request context to the configured AI endpoint. A validated estimate is shown for review before local save. Temporary images and pending state are removed after completion/cancellation or global local-data deletion; images are never exported.

## Voice to Android recognizer

The user starts microphone input. Audio is handled by the selected Android recognition service. Vitalis receives partial/final text events but creates no audio file. Session ownership rejects stale or duplicate final results. Backgrounding or explicit stop releases recognition and TTS resources.

## API key to Keystore

The key is entered only in `KeySettingsActivity`. The native layer validates syntax, encrypts it with AES-GCM using alias `vitalis_openai_key_v1` in Android Keystore, and stores only ciphertext/IV in private preferences. The trusted WebView can query configured status; it cannot retrieve, save, or clear plaintext keys. Native request code decrypts a key only immediately before an authorized request.

## Vitalis export to document

The native store creates a versioned, secret-free JSON document. Android’s Storage Access Framework lets the user choose the destination. Import reads a bounded user-selected document, validates and previews it, then applies `MERGE` or explicit `REPLACE LOCAL DATA`. No arbitrary path is written.

## Trusted UI channel

The exact Vitalis HTTPS origin and appassets origin receive `VitalisNativeChannel`. Calls from any subframe are rejected even when its origin is otherwise trusted. Unrelated HTTPS, deceptive hosts, HTTP, localhost, data, JavaScript, file, blob, and malformed origins receive no privileged access.
