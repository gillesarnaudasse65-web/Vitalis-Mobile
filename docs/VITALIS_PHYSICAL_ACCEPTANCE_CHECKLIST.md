# Vitalis 3.15 RC1 physical acceptance checklist

This checklist uses GitHub Web and the Android phone only. No terminal, ADB, Android Studio, PowerShell, Command Prompt, or Bash is required.

Do not record personal health values, account identifiers, device serial numbers, API keys, passwords, or other secrets. Use synthetic test data only.

## Prerequisites

- [ ] The production-signing certificate SHA-256 is recorded in `VITALIS_SIGNING_IDENTITY.md`.
- [ ] `Vitalis-upgrade-test-baseline-v20-production-signed` exists and is labeled **NOT FOR PUBLIC DISTRIBUTION**.
- [ ] `Vitalis-3.15.0-rc1-production-signed` exists.
- [ ] Both signature reports show the same certificate SHA-256.
- [ ] The Android phone allows installation from the browser or file manager used for this test.

If any prerequisite is missing, stop and report `NOT TESTED`.

## No-shell upgrade procedure

1. In GitHub Web, open the successful baseline workflow run and download `Vitalis-upgrade-test-baseline-v20-production-signed`.
2. Extract the ZIP using the phone's Files application and open the versionCode 20 APK.
3. Allow installation from that specific browser or file manager if Android asks, then install the baseline.
4. Open Vitalis and create synthetic test state: choose a date, select a theme, arrange at least two widgets, add a sample meal, record consent/settings choices, and add one sample journal entry.
5. Close Vitalis normally. Do not uninstall it and do not clear application storage.
6. In GitHub Web, open the successful production RC workflow run and download `Vitalis-3.15.0-rc1-production-signed`.
7. Extract the ZIP and open the RC versionCode 21 APK.
8. Android must present an update/install action for the existing Vitalis application. Complete it without uninstalling the baseline.
9. Reopen Vitalis and verify that the synthetic state is still present.

If Android requests removal of the existing app, reports an incompatible signature, or installation requires an uninstall, mark the upgrade `FAIL`.

## Result convention

For each section, select exactly one result:

- [ ] PASS
- [ ] FAIL
- [x] NOT TESTED

When changing a section result, clear the other two choices and add only sanitized observations.

## 1. Install

- [ ] PASS
- [ ] FAIL
- [x] NOT TESTED

Expected: versionCode 20 installs and opens as `com.vitalis.healthos`.

Observation:

## 2. Upgrade

- [ ] PASS
- [ ] FAIL
- [x] NOT TESTED

Expected: versionCode 21 installs over versionCode 20 without uninstalling or clearing storage.

Observation:

## 3. Themes

- [ ] PASS
- [ ] FAIL
- [x] NOT TESTED

Expected: all available themes render legibly and the selected theme persists after restart.

Observation:

## 4. Widgets

- [ ] PASS
- [ ] FAIL
- [x] NOT TESTED

Expected: widget details open, configuration works, and the chosen layout persists.

Observation:

## 5. Drag and drop

- [ ] PASS
- [ ] FAIL
- [x] NOT TESTED

Expected: supported dashboard items can be reordered without loss or duplication.

Observation:

## 6. Health Connect

- [ ] PASS
- [ ] FAIL
- [x] NOT TESTED

Expected: permission and availability states are truthful; granted access works and denial remains recoverable.

Observation:

## 7. Scanner

- [ ] PASS
- [ ] FAIL
- [x] NOT TESTED

Expected: scanning opens, returns safely, and handles cancellation or unavailable hardware.

Observation:

## 8. Camera

- [ ] PASS
- [ ] FAIL
- [x] NOT TESTED

Expected: permission, capture, cancellation, and return to Vitalis work without exposing unintended data.

Observation:

## 9. Microphone

- [ ] PASS
- [ ] FAIL
- [x] NOT TESTED

Expected: permission and recording controls work, with a clear unavailable/denied state.

Observation:

## 10. Text-to-speech

- [ ] PASS
- [ ] FAIL
- [x] NOT TESTED

Expected: speech starts and stops correctly and unavailable-engine handling is truthful.

Observation:

## 11. Offline

- [ ] PASS
- [ ] FAIL
- [x] NOT TESTED

Expected: supported local functions remain usable offline and reconnect without losing synthetic state.

Observation:

## 12. Export and import

- [ ] PASS
- [ ] FAIL
- [x] NOT TESTED

Expected: a sanitized export can be created and restored without duplication or corruption.

Observation:

## 13. Privacy

- [ ] PASS
- [ ] FAIL
- [x] NOT TESTED

Expected: consent, delete, sensitive-screen, and provider disclosures behave as documented.

Observation:

## 14. API key settings

- [ ] PASS
- [ ] FAIL
- [x] NOT TESTED

Expected: keys are masked, never exposed in screenshots/exports, and can be removed. Do not record a real key in this checklist.

Observation:

## 15. Restart and persistence

- [ ] PASS
- [ ] FAIL
- [x] NOT TESTED

Expected: selected date, theme, widget layout, sample meal, consent/settings, and journal state survive restart and the 20 → 21 upgrade.

Observation:

## Final physical decision

- [ ] PHYSICAL ACCEPTANCE PASS — every required section passed and no P0/P1 remains.
- [ ] PHYSICAL ACCEPTANCE FAIL — at least one required section failed.
- [x] PHYSICAL ACCEPTANCE NOT TESTED.

Tester/date:

Sanitized notes:
