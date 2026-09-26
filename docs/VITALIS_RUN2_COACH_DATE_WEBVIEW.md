# Vitalis Run 2 — coach interaction and selected date

Date: 2026-09-26. Repository `gillesarnaudasse65-web/Vitalis-Mobile`. Run 2 branch `agent/vitalis-run2-coach-date-webview-fixes`, based on Run 1 commit `d083cb0ffe0254a0ccca2f3af768947d9af45337`. At branch creation, `main` was still `fdb8a2ef503872dfbb5bc0147a674ec4bd5b88e1`, and Run 1 PR #6 was open. Run 2 PR #7 is stacked on the Run 1 branch; merge #6 first, then retarget #7 to main. Neither was merged by this run.

## Reproduction before correction

`node --test scripts/test-run2-regressions.cjs` failed three tests against the Run 1 implementation (0 passed, 3 failed):

| Failing test | Observed Run 1 behavior | Root cause | Correction |
|---|---|---|---|
| Nia/Sékou card capture | The legacy capture callback called `preventDefault` and `stopImmediatePropagation` before the card's handler | Label-based `actionFor` treated a coach card as a generic coach or sleep/recovery action | Legacy router ignores native overlays; the card's delegated handler selects by stable `data-coach-312` ID |
| Deep detail capture | Nia routed to `sleep` and Sékou to `recovery` through a generic document capture listener | It scanned coach-overlay text as metric context; `/h|min|score|heure/` also matches the `h` in “coach” | The detail listener ignores all native overlays, preserving metric cards elsewhere |
| Historical refresh | “Actualiser les données” invoked the no-argument bridge call, selecting today | An older capture listener ran before newer selected-date refresh logic; native no-argument refresh defaulted to `LocalDate.now()` | The refresh routes use `VitalisDate.refresh()`, which sends the persisted normalized selected day; native no-argument refresh also uses that day |

The tests were committed before behavior changed; their final passing results are separate from the initial failure evidence.

## Interaction and state design

The Android classic site supplies the base DOM. `compat.js` and `vitalis-3.12.js` are injected on approved remote and bundled-local pages. The six-card catalogue is rendered dynamically by the latter into `.vitalis-power-overlay-312`, with one delegated click listener on that overlay. The older compatibility layer also owns document-level capture listeners and an older fallback catalogue. The two capture handlers above were the interceptors; no name-specific coach branch was added. The production cards remain semantic buttons with stable IDs, accessible labels and `aria-pressed`. The current selected ID and portraits remain unchanged. A nested control marked `data-coach-action` does not select its parent card. The power layer has an idempotent initialization guard.

`SelectedDateState` holds the native calendar-day selection, using the existing app preferences with the new key `selected_health_date_iso`. It validates and normalizes `YYYY-MM-DD`; absent or invalid storage is replaced with the device-local current day. It is reloaded on activity creation. `selected-date.js` uses the bridge to read/set it and route one refresh call with an explicit date. It also keeps the page's date input and label aligned. The existing per-origin localStorage key `vitalis-selected-date-v1` is a fallback; native state is authoritative when available. JavaScript uses local calendar getters for Today; UTC is used only to validate a date's components, never to convert the selected calendar day. The old page-label observer starts by recording the initial label instead of overriding a restored historical date, and ignores unchanged labels.

The bundled fallback now exposes a date input, selected-date label and explicit Today control. The remote application's DOM is not rebuilt. Existing preference keys, Keystore alias, stable IDs, bridge public methods and user data are retained. The package and version remain `com.vitalis.healthos`, `3.14.0-coaches-connectors`, code 19.

## Deterministic WebView fixture

`run2-fixture.html` loads through the approved `appassets.androidplatform.net/assets/vitalis/` path. It supplies the date input, Today and refresh controls and an entry button; the actual six cards and their handlers are created by the **same injected production scripts** as the application. A debug-only MainActivity flag selects this page and fixes Today at `2026-09-26`. The real `VitalisAndroid` bridge records requested dates and returns synthetic date-only payloads instead of accessing Health Connect in this fixture. The test waits for an explicit page-ready signal and JavaScript callbacks, then sends Android pointer events to card backgrounds, portraits and names. No remote service, API key or personal health record is involved.

### Coverage and evidence

- JavaScript: 8 tests in total, including the two Run 1 registry tests. Run 2 adds three regression tests and three date-contract tests.
- JVM: 14 tests in total, including four new `SelectedDateStateTest` cases covering first launch, invalid/blank storage, persistence/recreation, Today, leap/future/month/year dates and a device-zone boundary near UTC midnight.
- Instrumentation: the Run 1 offline smoke plus one Run 2 production-script WebView flow. That flow exercises all six coach IDs through three touch surfaces each, counts selection mutations, reinjects the production layers, rerenders the catalogue, checks a nested action and Enter/Space keyboard activation, refreshes a historical date twice rapidly, switches coach, moves through background/resume, reloads WebView, recreates MainActivity and activates Today. An input `change` event models selecting the historical day; refresh, coach and Today actions use native pointer events.

| Coach | Stable ID | Card | Portrait | Name | Selection count |
|---|---|---|---|---|---|
| Kofi | `general` | Tested | Tested | Tested | One per action |
| Ama | `nutrition` | Tested | Tested | Tested | One per action |
| Ayo | `activity` | Tested | Tested | Tested | One per action |
| Nia | `sleep` | Tested | Tested | Tested | One per action |
| Sékou | `recovery` | Tested | Tested | Tested | One per action |
| Zuri | `mental` | Tested | Tested | Tested | One per action |

| Date scenario | Expected day | Evidence |
|---|---|---|
| First launch, no storage | 2026-09-26 | JVM and emulator |
| Select historical day | 2026-09-18 | JavaScript and emulator input-change event |
| Refresh after selection | 2026-09-18, one bridge call | JavaScript and emulator pointer tap |
| Two separate rapid refresh taps | 2026-09-18, two bridge calls | Emulator; one per action |
| Switch coach; background/resume | 2026-09-18 | Emulator |
| WebView reload; activity recreation | 2026-09-18 | Emulator |
| Explicit Today | 2026-09-26, one bridge call | JavaScript and emulator pointer tap |
| Blank/invalid stored date | 2026-09-26 | JVM |
| Leap, future, month/year boundary | The selected date | JavaScript and JVM |
| Timezone near midnight | Local day for device zone | JVM fixed clocks |

The fixture's source and CI XML reports provide replayable evidence. It does not prove live-site behavior in a physical Android WebView or the remote site's independent coach registry.

## CI, build and artifacts

Run 1 security/contract tests are retained. Workflow validation includes official wrapper, JDK 17, `./gradlew --version`, `tasks`, `clean`, `testDebugUnitTest`, the Node suites with JUnit XML, `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest`, and `connectedDebugAndroidTest` on an Android 15 emulator. All are mandatory. Reports and debug APKs use 90-day retention. The local environment has no Android SDK/emulator and cannot download Gradle, so Android gates are **BLOCKED locally**; Node tests pass locally.

Run 2 [CI run #57](https://github.com/gillesarnaudasse65-web/Vitalis-Mobile/actions/runs/36251265516) on commit `268088c6d07423c6d983e3c444046a17692e85b2` passed wrapper validation, Gradle initialization, 8 JavaScript tests, 14 JVM tests, lint (0 errors, **33 warnings**), both debug APK builds and two emulator tests (0 failures, 0 skips). Its [debug artifact](https://github.com/gillesarnaudasse65-web/Vitalis-Mobile/actions/runs/36251265516/artifacts/10909256941) includes `debug/app-debug.apk` (11,342,152 bytes) and `androidTest/debug/app-debug-androidTest.apk` (832,239 bytes). [Unit, JavaScript and lint reports](https://github.com/gillesarnaudasse65-web/Vitalis-Mobile/actions/runs/36251265516/artifacts/10909097172) and [instrumentation reports](https://github.com/gillesarnaudasse65-web/Vitalis-Mobile/actions/runs/36251265516/artifacts/10909316338) expire 2026-12-25. The earlier [run #56](https://github.com/gillesarnaudasse65-web/Vitalis-Mobile/actions/runs/36250393269) had one new `UseKtx` lint warning; its preference write was corrected without suppressing lint. The final documentation and duplicate-initialization follow-up commit requires its own green PR run before merge.

## Live-site boundary and remaining work

The public remote page loaded in a desktop browser on 2026-09-26. Its own coach chooser showed Malik, Aïna and Kofi, because the Android-injected catalogue is absent in an ordinary browser. This observation **does not** validate Nia/Sékou or the selected-date bridge on that site. The complete live Android remote flow is **NOT TESTED**; no real key, permission or health data was supplied. No screenshot is claimed; XML, source tests and the fixture are the evidence.

Run 1's exact-host navigation policy, local fallback path, external HTTPS handoff, SSL cancellation, popup blocking, untrusted top-level bridge removal and input bounds were not loosened. The bridge's subframe caller-origin risk and remote-DOM key-entry risk remain open. Health Connect pagination/deduplication/no-data, scanner capture/duplicate save, voice lifecycle, provider OAuth, full deletion/export, release signing and Play publication remain **STILL OPEN / NOT RETESTED**. Run 3 can focus on Health Connect permissions, page traversal, deduplication, day/timezone queries and zero-versus-no-data once final Run 2 CI is green.
