# Vitalis RC blocker clearance

Starting RC SHA: `f2232765e951cee3a19ba55bae08956fa12b9a9e`  
PR: `#13`  
Scope: release-gate clearance only; no product feature changes.

## Previous RC decision

**RC FAIL**

Automated validation was green at 147/147, but production signing, physical-device acceptance, and production-signed upgrade continuity were not established.

## Signing lineage

The owner confirmed that Vitalis has never had a production signing identity. The next valid identity will therefore establish the first permanent production lineage.

The workflow now separates:

- an automatic test-signed QA package, clearly marked **NOT FOR PRODUCTION DISTRIBUTION**;
- a manually requested, protected `production` job that fails when any required secret is absent and never falls back to Android Debug signing.

Required protected secret names:

- `VITALIS_KEYSTORE_BASE64`
- `VITALIS_KEYSTORE_PASSWORD`
- `VITALIS_KEY_ALIAS`
- `VITALIS_KEY_PASSWORD`

The temporary keystore is permission-restricted and deleted from the runner after the production build. No private material or password belongs in Git history, logs, screenshots, or documentation.

### Production identity status

- Certificate subject: PENDING SECURE KEY CREATION
- Certificate SHA-256: PENDING SECURE KEY CREATION
- Validity: PENDING SECURE KEY CREATION
- Backup status: **NOT CONFIRMED**

Key creation must wait for a recoverable secret-storage route. Losing the production key or its passwords may prevent seamless upgrades permanently. At least two secure backups are required outside the repository.

## Device matrix

The owner confirmed that a physical Android device and an ADB-capable workstation are available. No physical result is marked passed until the production-signed APK is installed and the acceptance matrix is actually executed.

| Device | Android | Display/RAM | Result |
|---|---|---|---|
| PENDING SAFE METADATA | PENDING | PENDING | NOT TESTED |

Required evidence covers installation/lifecycle, six themes, twelve widgets, customization and persistence, Health Connect, providers, nutrition, voice/TTS, native key/FLAG_SECURE, privacy/import/export/delete, offline/reconnect, process lifecycle, scaling/TalkBack, and qualitative performance. Evidence must contain no IMEI, serial number, personal account identifier, key, or unsanitized health values.

## Production-signed upgrade

Because this is the first production signing lineage, the valid protocol is:

1. build a controlled versionCode 20 baseline from the verified pre-RC source using the same newly established production certificate;
2. label it `UPGRADE TEST BASELINE — NOT CURRENT DISTRIBUTION BUILD`;
3. install it and seed only safe test data;
4. install `3.15.0-rc1` (`21`) over it with `adb install -r`, without uninstalling;
5. verify signature acceptance and preservation of selected date/coach, theme, widget layout, meal, consent, and journal.

Current result: **NOT TESTED — PRODUCTION IDENTITY REQUIRED**.

## Automated status

The last completed RC run before this clearance change was GitHub Actions run 97: 51/51 source tests, 84/84 JVM, 10/10 instrumentation, 2/2 synthetic upgrades, 0 lint errors, and 54 unchanged warnings. The workflow-only clearance change requires a new CI run before its evidence can replace that baseline.

## Blockers

| Issue | Previous severity | Current result |
|---|---|---|
| Stable production signing identity | P0 | NOT CLEARED |
| Physical Android acceptance | P1 | DEVICE AVAILABLE / NOT EXECUTED |
| Production-signed upgrade continuity | P1 | NOT CLEARED |

## Current decision

**RC FAIL**

The decision can change only after secure signing identity creation and backup, production artifact verification, actual physical acceptance, and the same-certificate upgrade test all pass.

## Secure Windows handoff

The non-secret identity policy, exact interactive `keytool` command, backup gate, Base64 conversion, GitHub environment-secret names, and production workflow procedure are recorded in `VITALIS_SIGNING_IDENTITY.md`.

Next owner action: create the key locally on the trusted Windows workstation, confirm two protected backups and password-vault storage, configure the four GitHub `production` environment secrets, then run the manual production workflow. Do not send any password, Base64 keystore value, or keystore file through chat.
