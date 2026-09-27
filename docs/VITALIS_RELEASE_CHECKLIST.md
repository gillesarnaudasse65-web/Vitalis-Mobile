# Vitalis release checklist

Every checked gate needs retained evidence from the candidate commit. A test-signed build is not a production-signed release.

## Code and security gates

- [ ] Branch is based on the latest verified `main`; mandatory PR checks are green.
- [ ] All Run 1–Run 6 Node, JVM, and instrumentation suites pass.
- [ ] Lint has zero errors and warning delta is reviewed.
- [ ] Exact-origin/main-frame bridge tests pass, including hostile frames and deceptive hosts.
- [ ] Remote DOM contains no credential input or plaintext key methods.
- [ ] Native key add/replace/delete and consent-revocation ownership tests pass.
- [ ] Cleartext remains disabled, SSL errors cancel, and release WebView debugging is off.
- [ ] Export excludes secrets; import bounds/schema and global-delete tests pass.
- [ ] No verbose/secret-bearing production logging is present.
- [ ] Exported-component, FileProvider, backup, and data-extraction policies are reviewed.

## Build and signing gates

- [ ] JDK 17 and verified Gradle wrapper are used.
- [ ] Debug APK and instrumentation APK build.
- [ ] Minified release APK and AAB build; `mapping.txt` is retained.
- [ ] Production keystore secret is available only through protected CI secrets.
- [ ] APK/AAB signing certificate matches the established production identity.
- [ ] SHA-256, byte size, filename, commit SHA, version name/code, and signing identity are recorded.
- [ ] Artifacts and reports are retained for at least 90 days.

## Privacy and device gates

- [ ] Privacy policy has completed legal review.
- [ ] Privacy/Data screen, document export/import, and destructive confirmations are exercised.
- [ ] Physical-device key entry and `FLAG_SECURE` behavior are verified.
- [ ] Health Connect grant/deny/partial/revoke and real provider attribution are verified.
- [ ] Camera/gallery, microphone, recognition, TTS, backgrounding, and OEM behavior are verified.
- [ ] External/provider wording remains truthful; no unsupported OAuth/API is implied.

## Upgrade and rollback gates

- [ ] Emulator Run 5 → Run 6 `adb install -r` continuity test passes with synthetic data.
- [ ] Production-signed upgrade from the current distributed build passes without reinstall.
- [ ] Selected date/coach, nutrition, consent, connector preference, journal, dashboard, and existing encrypted keys survive.
- [ ] A pre-upgrade global export has been validated and retained by the tester.
- [ ] Rollback build, compatible export versions, and authorized distribution route are documented.
- [ ] Downgrade is not presented as supported unless Android accepts the version/signature and data schema.

## Candidate gates

- [ ] Test-signed release starts on emulator and core classic UI surfaces smoke successfully.
- [ ] Production-signed release repeats release smoke on representative physical devices.
- [ ] Final UX Run is complete without changing security/data semantics.
- [ ] Final RC regression is complete and all blockers have owners.
- [ ] Store listing, Data Safety answers, screenshots, content rating, and support/contact details are reviewed.
