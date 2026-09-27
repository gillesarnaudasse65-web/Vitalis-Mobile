# Vitalis 3.15.0-rc1 validation

Candidate branch: `agent/vitalis-3.15-rc-validation`  
Base/PR #12 merge: `188a31e2ff34ef102cdcfa861f8de69972bee88e`  
Application: `com.vitalis.healthos`, `3.15.0-rc1` (`21`)

## Validation status

This document separates automated evidence from mandatory external acceptance. Emulator success must not be interpreted as physical-device or production-signing success.

| Area | Automated baseline | RC/physical status |
|---|---|---|
| Source/JavaScript | 45/45 before RC-specific additions | Pending RC CI |
| JVM | 84/84 | Pending RC CI confirmation |
| Instrumentation | 10/10 on API 35 emulator | Physical device NOT TESTED |
| Upgrade | 2/2 synthetic debug-signature executions | Production-signature upgrade BLOCKED |
| Lint | 0 errors / 54 warnings | No increase permitted |
| Release package | Minified APK/AAB/R8 passed | RC package pending CI |

## Production signing

The build accepts a keystore path, store password, alias, and key password only through the release environment. No credential is committed. The last verified Final UX workflow produced a `test-signed` artifact, so availability and continuity of a stable production identity are not established.

**BLOCKED — PRODUCTION SIGNING IDENTITY REQUIRED**

A test-signed RC may be used for QA but is **NOT FOR PRODUCTION DISTRIBUTION**. When credentials are provisioned, retain the APK certificate SHA-256, safe certificate subject/validity, `apksigner` result, AAB signature result, checksums, and sizes without exposing private material.

## Physical-device acceptance matrix

Record manufacturer/model/Android/screen/RAM, but never serial number or IMEI. All rows remain `NOT TESTED` until executed on a real phone.

| Area | Required scenarios | Result / evidence |
|---|---|---|
| Install/lifecycle | clean install, launch, restart, Back, process death | NOT TESTED |
| Themes | Classic, Ocean, Dark, AMOLED, Aurora, System; bars, persistence | NOT TESTED |
| Widgets | 12 card taps, nested actions, feedback, no double navigation | NOT TESTED |
| Customization | long press, drag/drop/scroll, hide/show, accessible reorder, presets, restore, restart | NOT TESTED |
| Health Connect | available, grant, partial/full/revoke, no-data/data, date, refresh, resume | NOT TESTED |
| Providers | installed detection, launch/return, status refresh | NOT TESTED |
| Nutrition | picker/camera/cancels, rotation/large image, review/edit/save/idempotency/delete/date | NOT TESTED |
| Voice/TTS | permission, partial/final/stop, duplicate prevention, lifecycle, French/fallback | NOT TESTED |
| Native key | FLAG_SECURE, hidden entry, save/replace/delete, no WebView plaintext | NOT TESTED |
| Privacy/data | consent revoke/late result, export/import/merge/delete boundaries | NOT TESTED |
| Offline/network | cold offline, labels/local state, reconnect, timeout/retry/stale response | NOT TESTED |
| Scaling/accessibility | font/display scales, orientation, TalkBack labels, non-drag alternatives | NOT TESTED |
| Performance | cold start, dashboard, scroll, theme, details, drag/drop, scanner | NOT TESTED |
| Crash/battery sanity | logcat/ANR review, no residual microphone/poll/wakelock | NOT TESTED |

## Upgrade protocol

1. Obtain the previously distributed production-signed APK and identify its certificate SHA-256.
2. Install it without removing application data.
3. Seed only synthetic/local test values: selected date/coach, meal, theme/accent, widget layout, AI consent, journal, and preset.
4. Install the production-signed RC with `adb install -r`.
5. Reject the candidate on signature mismatch, forced uninstall, startup failure, or data loss.
6. Confirm every seeded item remains correct and exportable.

If no production-signed Vitalis has ever been distributed, document `3.15.0` as the first stable signing identity and retain it for all future updates.

## Privacy and Google Play

The implementation-facing privacy draft covers Health Connect, requested AI processing, meal images, Android voice recognition/TTS, local storage, encrypted API keys, provider boundaries, export/import, and deletion boundaries. It remains marked **DRAFT — REQUIRES LEGAL REVIEW BEFORE PUBLIC RELEASE**.

Google Play preparation is tracked in `VITALIS_GOOGLE_PLAY_CHECKLIST.md`; no publication or legal compliance is claimed.

## Release-blocker classification

| Issue | Severity | Blocking | Required action |
|---|---|---:|---|
| Stable production signing identity not established | P0 | Yes | Provision protected CI credentials and verify certificate continuity |
| Mandatory physical-device acceptance not executed | P1 | Yes | Execute and retain the matrix above on at least one real Android phone |
| Production-signed upgrade continuity not executed | P1 | Yes | Test previous production build to RC using `adb install -r` |
| Qualified legal review pending | External gate | Yes for publication | Review and publish the final privacy policy |
| Final Play declarations/support/listing approval pending | External gate | Yes for publication | Complete Play Console preparation |

## Current decision

**RC FAIL** until the P0/P1 external release gates above are completed. This status does not imply an observed software regression; it applies the mandatory decision rule without inventing signing or physical-device evidence.

## RC blocker-clearance continuation

Previous RC decision: **RC FAIL**.

New decision: **RC FAIL**.

The owner confirmed this will be Vitalis's first production signing lineage and that a physical Android device is available. CI now separates QA test signing from a protected, manual, fail-closed production-signing job. The decision has not changed because the permanent key has not yet been created through a recoverable secret-storage route, backup is not confirmed, physical acceptance has not been executed, and the same-certificate versionCode 20 → 21 upgrade remains untested. See `VITALIS_RC_BLOCKER_CLEARANCE.md`.
