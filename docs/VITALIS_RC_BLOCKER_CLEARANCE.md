# Vitalis RC blocker clearance

Starting RC SHA: `f2232765e951cee3a19ba55bae08956fa12b9a9e`  
PR: `#13`  
Scope: release-gate clearance only; no product feature changes.

## Previous RC decision

**RC FAIL**

Automated validation is green at 149/149 on GitHub Actions run 99 attempt 3, but production signing, physical-device acceptance, and production-signed upgrade continuity are not established.

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
- Backup status: **BACKUP_BLOCKED**

Key creation must wait for a recoverable secret-storage route. Losing the production key or its passwords may prevent seamless upgrades permanently. At least two secure backups are required outside the repository.

## Device matrix

The owner confirmed that a physical Android device is available. The current acceptance path does not require ADB or another local shell. No physical result is marked passed until the production-signed APK is installed and the acceptance matrix is actually executed.

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

The last completed RC run before this GitHub-only initialization change was GitHub Actions run 99 attempt 3: 53/53 source tests, 84/84 JVM, 10/10 instrumentation, 2/2 synthetic upgrades, 0 lint errors, and 54 unchanged warnings. This workflow-and-documentation change requires a new CI run before its evidence can replace that baseline.

## Blockers

| Issue | Previous severity | Current result |
|---|---|---|
| Stable production signing identity | P0 | NOT CLEARED |
| Physical Android acceptance | P1 | DEVICE AVAILABLE / NOT EXECUTED |
| Production-signed upgrade continuity | P1 | NOT CLEARED |

## Current decision

**RC FAIL**

The decision can change only after secure signing identity creation and backup, production artifact verification, actual physical acceptance, and the same-certificate upgrade test all pass.

## GitHub-only initialization handoff

The owner requires a GitHub Web-only path with no local shell, keytool, ADB, or Android Studio. Repository inspection found no verified dedicated secret-writing token or external vault integration. The selected fallback is therefore **Mode C**.

`.github/workflows/initialize-production-signing.yml` is manual-only, uses the `production-signing-init` environment, requires explicit first-lineage confirmation and protected initialization password secrets, checks the public pending marker, and then deliberately aborts before key generation with:

`INITIALIZATION BLOCKED — SECURE SECRET TRANSFER CHANNEL REQUIRED`

No private keystore is created or uploaded. GitHub Secrets alone are not treated as a recoverable backup. Current recovery status: **BACKUP_BLOCKED**.

The GitHub Web and phone-only upgrade procedure is recorded in `VITALIS_PHYSICAL_ACCEPTANCE_CHECKLIST.md`. The versionCode 20 production-signed baseline job must not be added or executed until a secure production identity and independent recovery path exist.

## Phone-only encrypted-vault continuation — 2026-09-28

The previous Mode C state above remains part of the audit trail. A technically valid phone-only recovery route is now prepared but has **not yet been executed**:

- standard authenticated age encryption using scrypt and ChaCha20-Poly1305;
- Android graphical recovery through an age-compatible application;
- first-lineage generation only in GitHub runner temporary storage;
- clean-directory decryption and signing self-test before any encrypted transfer;
- durable encrypted copy in the dedicated private `Vitalis-Signing-Vault` repository;
- seven-day encrypted transfer artifact for Android Backup A;
- independent user-controlled Backup B outside GitHub;
- separate non-secret backup-confirmation workflow;
- protected production restore using the encrypted vault copy, not four manually copied generated passwords;
- same-certificate versionCode 20 baseline job from `188a31e2ff34ef102cdcfa861f8de69972bee88e`.

No private repository, secret, key, backup or production artifact is claimed merely because the workflows exist. Until Phase A and Phase B actually pass, the truthful states remain:

- Signing: **BLOCKED**
- Backup: **BACKUP_BLOCKED**
- Production APK/AAB: **NOT GENERATED**
- Production-signed upgrade: **NOT TESTED**
- Physical acceptance: **NOT TESTED**
- RC: **FAIL**

## Physical voice/nutrition hotfix continuation — 2026-09-28

Phone QA evidence subsequently identified two real-device failures: voice recognition did not return usable text, and a successfully captured meal photo did not proceed to analysis. The scoped hotfix at `de3a05b63cd8b29f30dd267cb9ceca8fef8dc2f5` passed GitHub Actions run 106 with 65 source, 91 JVM, 10 instrumentation, and 2 synthetic-upgrade executions; lint remained at 0 errors / 54 warnings. A minified test-signed artifact named `Vitalis-3.15.0-rc1-physical-hotfix-qa` was generated.

This evidence clears only the automated hotfix regression gate. Voice and live nutrition analysis remain **PHYSICAL RETEST REQUIRED**, while production signing, same-certificate upgrade, and complete physical acceptance remain blocked. PR #13 must stay open and the overall decision remains **RC FAIL**.
