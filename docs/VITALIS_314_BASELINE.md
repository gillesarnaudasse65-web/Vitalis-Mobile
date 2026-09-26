# Vitalis — verified baseline audit (Run 0)

**Actual version: `3.14.0-coaches-connectors`, versionCode `19`.** The requested baseline was `3.14.0`; the suffix is a verified difference, not an audit change.

## 1. Executive summary

Vitalis is an Android development prototype with a historically successful debug build, a remote WebView UI, a bundled fallback, ten Health Connect record readers, six coach identities, and a local nutrition-estimate store. It is not demonstrated to be a release candidate or a stable personal-use release. No existing automated tests were found across the complete 26-file tracked tree. This run did not compile Android or exercise a device.

The audit found concrete behavioral gaps: overlapping JavaScript capture listeners intercept Nia/Sékou selection; a refresh route defaults to today; Health Connect reads lack pagination and cross-source deduplication; six requested record types are not read; the fallback calculates a different score and returns 70 with no data; local deletion does not cover all stores. Provider-app launch is implemented, but direct OAuth/API connectors are not.

Source protections include HTTPS-only network configuration, disabled WebView file/content access, disabled backup, and Android Keystore encryption of user-supplied keys. These do not remove the risk of exposing the native bridge and key-entry DOM to remotely delivered JavaScript.

Only this audit document is changed. No product correction, UI redesign, version change, release, API call, health-data mutation, or branch/PR deletion is part of Run 0.

## 2. Audit date and evidence vocabulary

- Audit date: **2026-09-26**, Europe/Paris; historical build dates below are UTC.
- `VERIFIED FROM SOURCE CODE`: implementation inspected at the pinned initial commit. Does not imply runtime success.
- `VERIFIED BY AUTOMATED TEST`: narrowly scoped executed probes described in section 18; no existing product test suite.
- `VERIFIED BY BUILD`: historical GitHub Actions result only, explicitly dated.
- `VERIFIED ON EMULATOR` / `VERIFIED ON PHYSICAL DEVICE`: neither achieved.
- `NOT VERIFIED`: no execution evidence or inaccessible evidence.
- `BLOCKED`: a specific environmental or access constraint prevents the check.
- All feature rows below have **unverified Android runtime behavior**, even where source implementation exists.

## 3. Repository and pinned commit

| Item | Evidence/result |
|---|---|
| Repository / owner | `gillesarnaudasse65-web/Vitalis-Mobile` / `gillesarnaudasse65-web` |
| Visibility / default branch | Public / `main`, GitHub repository metadata |
| Initial checked-out branch | `main` |
| Initial HEAD | `fdb8a2ef503872dfbb5bc0147a674ec4bd5b88e1` |
| Initial commit | 2026-07-30 22:36:57 UTC — `Vitalis 3.14: fix coaches and connector activation` |
| Initial working tree | Clean after a fresh clone; no previous local work was present in this checkout |
| Tags / GitHub Releases | No Git tags; releases endpoint returned an empty list |
| Audit delivery branch | `agent/vitalis-run0-baseline-20260926`, based on the pinned initial HEAD |
| Audit commit identification | Use the commit containing this file; its SHA is returned in the completion report (avoids a self-referential commit SHA) |

Previous conversation claims about 3.12/3.13 were treated as history. Current source is 3.14, which supersedes them. The old claim that a development-environment key exists was not used or verified; no secret file was opened. The app actually requests user-supplied keys on the phone.

## 4. Verified application version and complete tree inventory

All 26 tracked paths were inventoried; all text files were inspected. Six WebP files were decoded for integrity, not assessed as human identities. There is one app module, one Kotlin file (1,816 lines), two JavaScript assets (`compat.js`, 1,325 lines; `vitalis-3.12.js`, 647 lines), and one HTML fallback (179 lines).

| Responsibility | Real path / symbol |
|---|---|
| Root build / settings | `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties` |
| App build | `app/build.gradle.kts` |
| Manifest | `app/src/main/AndroidManifest.xml` |
| Native implementation | `app/src/main/java/com/vitalis/healthos/MainActivity.kt` |
| WebView / bridge | Same file: `onCreate`, `VitalisAndroidBridge`, `dispatchWebEvent` |
| Health / permissions | Same file: `healthPermissions`, `permissionLauncher`, `readHealthData` |
| AI models and network | Same file: `requestCoach`, `requestDeveloper`, `requestAi`, `postOpenAi`, `extractResponseText`; `JSONObject`/`JSONArray`, no separate model/service files |
| Key storage | Same file: `writeEncryptedSecret`, `readEncryptedSecret`, `getOrCreateSecretKey` |
| Nutrition storage | Same file: `saveManualMealEstimate`, `manualMealsForDate`, `buildNutritionSummary` |
| File chooser | Same file: `onShowFileChooser`, `fileChooserLauncher` |
| Voice | Same file: `initializeVoiceServices`, `startMicrophoneInternal`, `speakStable`, `onDestroy` |
| Native connector catalogue | Same file: `connectorCatalog`, `handleConnectorAuthorization`, `buildConnectorPayload` |
| Compatibility / scanner | `app/src/main/assets/vitalis/compat.js`: `scanMeal`, capture listeners, legacy coach UI, date and metrics adaptation |
| Current coach layer | `app/src/main/assets/vitalis/vitalis-3.12.js`: `coaches`, `openCoach`, `showCoachCatalog`, `showMealEstimate`, `showConnectors` |
| Offline UI / local profile | `app/src/main/assets/vitalis/index.html`: inline script, `load`, `save`, `coach`, `render`, `clearData` handler |
| Portraits | `app/src/main/assets/vitalis/coaches/{kofi,ama,ayo,nia,sekou,zuri}.webp` |
| Network security / shrinker | `app/src/main/res/xml/network_security_config.xml`, `app/proguard-rules.pro` |
| Resources | `res/values/{strings,colors,themes}.xml`, `res/drawable/ic_launcher_foreground.xml`, `res/mipmap-anydpi-v26/{ic_launcher,ic_launcher_round}.xml` under `app/src/main` |
| CI / docs / ignore | `.github/workflows/build-apk.yml`, `README.md`, `.gitignore` |
| Export / release scripts / test source sets | Not found in tracked tree |
| Dedicated privacy policy / license | Not found; README has limited privacy statements |

The native activity concentrates UI, IO, encryption, health, AI, voice, storage and connector responsibilities. Legacy and current coach/connector/score implementations overlap rather than being one authoritative implementation. No TODO/FIXME, commented-out full implementation, committed build output, APK, keystore or obvious temporary debug log was found in tracked source. Binary assets are the six intentional portraits. Versioned asset names and compatibility flags are retained contracts, not necessarily obsolete files to delete.

Hardcoded endpoints: remote Vitalis site, local asset origin, OpenAI Responses, ChatGPT Codex, Play Store search/details. Hardcoded defaults: profile name `Gilles`; nutrition targets 2093 kcal, 183 g carbohydrate, 209 g protein, 58 g fat, 25 g fibre/sugar, 2300 mg sodium; activity/sleep/water scoring targets. These are not verified personalized targets. Credential placeholders are UI examples, not usable secrets. No secret value is included here.

## 5. Android configuration and baseline comparison

For values not specified in the brief, `REQUIRES VALIDATION` means deployment suitability has not been validated; the source value itself has been verified.

| Expected value | Verified value | Status | Evidence file |
|---|---|---|---|
| Vitalis Health OS | Vitalis Health OS | MATCH | `res/values/strings.xml` |
| `com.vitalis.healthos` | Same namespace and applicationId | MATCH | `app/build.gradle.kts` |
| `3.14.0` | `3.14.0-coaches-connectors` | CHANGED | `app/build.gradle.kts` |
| versionCode 19 | 19 | MATCH | `app/build.gradle.kts` |
| Native Kotlin + WebView | One Kotlin ComponentActivity; programmatic Views/WebView | MATCH | `MainActivity.kt` |
| SDK levels not specified | min 28 / target 35 / compile 36 | REQUIRES VALIDATION | `app/build.gradle.kts` |
| Build tools not specified | No explicit buildToolsVersion | NOT APPLICABLE | `app/build.gradle.kts` |
| Kotlin / AGP not specified | Kotlin 2.0.21 / AGP 8.9.1 | REQUIRES VALIDATION | `build.gradle.kts` |
| Gradle wrapper | No wrapper script, JAR or properties | NOT FOUND | Complete tracked tree |
| CI Gradle | 8.11.1 installed by setup-gradle | REQUIRES VALIDATION | `build-apk.yml` |
| Required JDK | Source/target/jvmTarget 17; CI Temurin 17 | MATCH | App build and workflow |
| Local JDK | OpenJDK 17.0.20; command executed | MATCH | `java -version` |
| Compose / ViewBinding | Neither enabled; no Compose dependencies | NOT APPLICABLE | App build and activity |
| Build types / flavors | Conventional debug/release; no flavors | MATCH | App build |
| Debug | No custom signing; WebView debugging gated by BuildConfig.DEBUG | REQUIRES VALIDATION | App build, `onCreate` |
| Release | R8 minify enabled, optimized defaults + bridge keep rule | MATCH | App build, `proguard-rules.pro` |
| Resource shrinking | Not explicitly enabled | NOT FOUND | App build |
| Release signing | No signingConfigs, persistent key or signing workflow | NOT FOUND | App build, workflow |
| Backup | `allowBackup=false`; no data extraction/backup rules file | MATCH | Manifest |
| Network | Cleartext prohibited; mixed content never allowed | MATCH | Network XML, `onCreate` |
| Health dependency | `androidx.health.connect:connect-client:1.1.0` | MATCH | App build |
| Other dependencies | activity-ktx 1.10.0; lifecycle-runtime-ktx 2.8.7; webkit 1.13.0 | REQUIRES VALIDATION | App build |

Manifest permissions: INTERNET, RECORD_AUDIO, and 16 `android.permission.health.READ_*` permissions listed in section 10. No CAMERA, POST_NOTIFICATIONS, external-storage/media permission or Health Connect write permission. File selection is delegated to the system chooser; a camera capture implementation is not established by that fact. Exported components: launcher MainActivity and permission-usage alias; the alias requires `START_VIEW_PERMISSION_USAGE`. The alias opens the same activity; no dedicated rationale/privacy screen branch is implemented. No app deep-link/OAuth redirect filter or other service/provider/receiver exists. Package visibility declares the Health Connect/provider packages listed in section 13, plus speech/TTS service queries.

## 6. Architecture overview

`MainActivity` loads a remotely managed page or local HTML into WebView. It registers `VitalisAndroid` before navigation. On page finish it injects `compat.js` followed by `vitalis-3.12.js`. The latter replaces several global APIs, but capture listeners registered by the former remain active.

UI calls bridge methods for health permissions/read, AI/key/consent, meal save, voice, navigation and connectors. Native health reads and AI work return JSON through WebView `CustomEvent`s. Events include `vitalis-native-ready`, `vitalis-health-data`, `vitalis-connectors`, `vitalis-sync-state`, `vitalis-health-connect`, `vitalis-ai-response`, `vitalis-voice-input`, and `vitalis-voice-state`.

The remote site's source and deployed DOM are outside this repository and were not audited. APK source inspection cannot establish parity with the current website. No separate backend is in this repository; the Android app calls OpenAI directly with the phone's encrypted user-supplied key. Developer AI is an advisory request plus clipboard/URL handoff, not autonomous repository editing.

## 7. Startup, WebView and lifecycle flow

1. Launcher creates `MainActivity.onCreate`; Health Connect availability is checked once to initialize the client; TTS initialization begins.
2. Programmatic LinearLayout/progress bar and WebViewAssetLoader `/assets/` handler are created.
3. WebView enables JavaScript, DOM storage and database storage. File/content access are false, mixed content is forbidden, cache is LOAD_DEFAULT, media playback does not require a gesture. Debug inspection is debug-build-only.
4. `addJavascriptInterface(VitalisAndroidBridge(), "VitalisAndroid")` registers the bridge.
5. `loadUrl("https://vitalis-health-os.gillesarnaudasse65.chatgpt.site/")` requests the remote UI.
6. `onPageFinished` marks the remote page finished, injects scripts for either recognized host, sends native-ready and reads health data. HTTP >=400/main-frame load errors also route into retry logic.
7. A 30-second timeout and 2-second retry delay allow two retries before fallback. Callbacks are separate Handlers and are not cancelled as a group; error/finish races can affect fallback. No measured startup duration is claimed.
8. `loadOfflineFallback` stops loading and requests `https://appassets.androidplatform.net/assets/vitalis/index.html` once. Local failure replaces content with a retry screen (`recreate`).
9. Remote portrait paths `/__vitalis/coaches/` are intercepted only for six allowlisted filenames and served from APK assets. This establishes a source path, not visual display on a device.
10. UI bridge actions trigger Health Connect/AI; JSON events update the UI.

Navigation permits host equality with the remote or asset host; it does not enforce scheme/port/full-origin equality. Other URLs launch ACTION_VIEW. No custom SSL bypass is implemented; platform default handling remains. There is no bridge removal on untrusted navigation and no native caller-origin/gesture validation. There is no repository CSP for the fallback or inspected guarantee about remote frames. See S-H1/S-H2.

Back navigates WebView history or finishes; overlays have no native back integration. `onDestroy` cancels file callback, destroys recognizer/WebView and shuts down TTS. `onResume` schedules one selected-date refresh after a connector return. No `onPause`/`onStop` microphone stop, WebView lifecycle forwarding, `onSaveInstanceState`/restoreState or configuration-change strategy is present. Rotation/process recreation loses in-memory conversations, pending requests, selected native date and pending connector state. SharedPreferences/localStorage survive, subject to separate origins. Health jobs use lifecycleScope but cancellation can be caught by runCatching; HttpURLConnection blocking IO is not explicitly cancelled on activity destruction. Delayed callbacks are not centrally removed.

## 8. Feature inventory

Exact feature-state vocabulary follows the brief. `IT` below is expanded as **IMPLEMENTED BUT NOT AUTOMATICALLY TESTED**; `PARTIAL` is **PARTIALLY IMPLEMENTED**; `ABSENT` is **NOT FOUND**; `DEVICE` is **REQUIRES PHYSICAL-DEVICE VALIDATION**. No feature qualifies as IMPLEMENTED AND AUTOMATICALLY TESTED end-to-end. Locations abbreviated: `K` = MainActivity.kt, `C` = compat.js, `P` = vitalis-3.12.js, `H` = index.html. Automated coverage for all rows: no existing product tests; isolated audit probes are identified explicitly. Runtime validation for all rows: not performed.

| ID | Feature | Status | Implementation | Known limitation | Release risk / validation |
|---|---|---|---|---|---|
| A | Startup | IT | K.onCreate | Activity recreation and timeout races | High; cold start/recreate |
| B | Remote UI loading | IT | K.WebViewClient | Remote DOM not pinned or included | High; current remote UI |
| C | Offline fallback | PARTIAL | K.loadOfflineFallback; H | Separate storage and score implementation | High; airplane-mode/restart |
| D | Android ↔ JS bridge | IT | K.VitalisAndroidBridge | Origin/gesture boundary not enforced | High; trust and contract checks |
| E | Local preferences | IT | K SharedPreferences; P.setSelectedCoach | Selection differs by web origin | Medium; restart/origin switch |
| F | Local persistence | PARTIAL | K.saveManualMealEstimate; C.record; H.save | Three unsynchronized health/journal stores | High; loss/erase/restore |
| G | Health Connect availability | IT | K.onCreate/getSdkStatus | Client initialized only on creation | Medium; absent/update/provider install |
| H | Health Connect permission request | IT | K.permissionLauncher | Unused permissions; rationale screen absent | High; grant/deny/partial/revoke |
| I | Health Connect reading | PARTIAL | K.readHealthData | 10 of 16 records; no paging/dedup | High; real-record consistency |
| J | Selected-date synchronization | PARTIAL | K.readHealthData; C.actionFor | Capture refresh resets today; race risk | High; isolated route probe reproduced |
| K | Steps | IT | K.readHealthData StepsRecord | Raw cross-source sum/no pagination | High; compare source totals |
| L | Distance | IT | K.readHealthData DistanceRecord | Raw sum/no interval clipping | High; overlapping records |
| M | Calories | PARTIAL | K.readHealthData ActiveCaloriesBurnedRecord | TotalCalories permission not read | High; distinguish total/active |
| N | Exercise sessions | IT | K.readHealthData/buildDetailsPayload | Full durations/overlap; details capped 50 | High; multi-source/date boundaries |
| O | Sleep | IT | K.readHealthData SleepSessionRecord | Full sessions, no explicit day clipping | High; overnight/DST/overlap |
| P | Heart rate | IT | K.readHealthData HeartRateRecord | Sample mean; no deduplication | Medium; sample provenance |
| Q | HRV | PARTIAL | K.healthPermissions | Permission/import only, no read/output | High; missing implementation |
| R | Respiratory rate | PARTIAL | K.healthPermissions | Permission/import only | High; missing implementation |
| S | Oxygen saturation | IT | K.readHealthData OxygenSaturationRecord | Latest within first page | Medium; pagination/latest |
| T | Blood pressure | PARTIAL | K.healthPermissions; C.addMeasure | No HC read; manual text only | High; avoid implied integration |
| U | Body temperature | PARTIAL | K.healthPermissions; C.addMeasure | No HC read; manual text only | High; missing integrated metric |
| V | Weight | IT | K.readHealthData WeightRecord | Latest within first page; manual store split | Medium; source/restart |
| W | Body fat | PARTIAL | K.healthPermissions | Permission/import only | High; missing implementation |
| X | Hydration | PARTIAL | K HydrationRecord; C.addWater; H | HC sum/manual journal not unified | High; reconcile manual/native |
| Y | Nutrition | PARTIAL | K.buildNutritionSummary; P; H | Native meals and journal differ; fixed goals | High; sums/missing nutrients |
| Z | AI coach selection | PARTIAL | P.showCoachCatalog; C capture handler | Nia/Sékou clicks intercepted | High; isolated callback defects reproduced |
| AA | AI coach request | IT | K.requestCoach/requestAi; P.sendCoachQuestion | No history/cancellation/backoff | High; mocked errors/consent/date |
| AB | AI response display | PARTIAL | P vitalis-ai-response | Fallback hides failure cause; pending timeout absent | High; async/UI routing |
| AC | OpenAI key storage | IT | K encrypted secret methods | Encryption exists; entry DOM exposed | High; secure entry/delete |
| AD | AI consent | PARTIAL | K.requestCoach; P.configureHealthAi | Global consent; no inflight revoke/caller trust | High; native boundary |
| AE | Nutrition photo capture | PARTIAL | C.scanMeal; K.onShowFileChooser | HTML hint, no explicit camera output flow | High; camera on device |
| AF | Nutrition gallery selection | DEVICE | K.fileChooserLauncher; C.scanMeal | Chooser callback implementation; not device-run | Medium; choose/cancel/denied URI |
| AG | Nutrition parsing | PARTIAL | P.parseMealEstimate | Loose JSON shape and no plausible upper bounds | High; malformed/huge/unknown values |
| AH | Nutrition editing | IT | P.showMealEstimate | Name/seven nutrient fields only | Medium; form routing/validation |
| AI | Nutrition saving | PARTIAL | K.saveManualMealEstimate | No idempotency/date-at-capture/delete; cap500 | High; duplicate/date/restart |
| AJ | Speech recognition | DEVICE | K.startMicrophoneInternal | Restarts/no lifecycle stop/no final dedup | High; phone engine/background |
| AK | Text-to-speech | DEVICE | K.speakStable | Engine/locale/focus behavior untested | Medium; stop/overlap/language |
| AL | Connector catalogue | PARTIAL | K.connectorCatalog; P.showConnectors; H | 32 native entries vs separate legacy15 | High; consistent catalogue/status |
| AM | Installation detection | IT | K.isPackageInstalled; Manifest queries | Package literals not externally validated | Medium; real installed apps |
| AN | Health Connect connector flow | PARTIAL | K.handleConnectorAuthorization/onResume | Vitalis grant + app launch; sharing still manual | High; actual provider writes |
| AO | Direct OAuth connectors | ABSENT | Complete main tree | No token/redirect/client implementation | High; vendor access prerequisites |
| AP | External app deep links | PARTIAL | K.openInstalledConnector/openExternalUrl | App main launch/store only, no auth deep links | Medium; intents and package failures |
| AQ | Data export | ABSENT | Complete main tree | No comprehensive export/restore | High; recovery before upgrades |
| AR | Local deletion | PARTIAL | H.clearData; K.clearOpenAiKey | Only one journal/key; native meals remain | High; complete erase semantics |
| AS | Privacy controls | PARTIAL | K consent/key; Manifest; H | Limited deletion, no policy, remote key entry | High; trust/consent/erase |
| AT | French UI | IT | C/P/H/resources | French source labels; current remote UI unverified | Medium; all screens/device |
| AU | English UI | PARTIAL | K.speakStable; C action labels | Some English routing/TTS; no full translations | Medium; bilingual design scope |
| AV | Notifications | ABSENT | Manifest and K | No notification permission/channel/scheduler | Low; no current implementation |
| AW | Error reporting | PARTIAL | K events; C/P fallbacks | No crash telemetry; swallowed errors | Medium; error states/diagnostics |
| AX | Release signing | ABSENT | App build/workflow | No release signing config or stable key | High; reproducible signature |
| AY | Application update process | PARTIAL | README; workflow | Manual debug sideload; no verified migration/rollback | High; in-place update/data continuity |

## 9. Six-coach inventory and mapping

| Coach | Stable ID | Present in UI source | Present in Android | Prompt found | Duplicate risk | Test coverage | Status |
|---|---|---|---|---|---|---|---|
| Kofi | general | Yes, current and legacy registries | Default branch in coachInstructions | Yes | No duplicate within current registry; legacy registry duplicated | Registry/asset probe only | IT; device unverified |
| Ama | nutrition | Yes | Explicit branch | Yes | Same legacy duplication | Registry/asset probe only | IT; device unverified |
| Ayo | activity | Yes | Explicit branch | Yes | Same legacy duplication | Registry/asset probe only | IT; device unverified |
| Nia | sleep | Yes | Explicit branch | Yes | Same legacy duplication | Capture routing probe reproduces interception | PARTIAL |
| Sékou | recovery | Yes | Explicit branch | Yes | Same legacy duplication | Capture routing probe reproduces interception | PARTIAL |
| Zuri | mental | Yes | Explicit branch | Yes | Same legacy duplication | Registry/asset probe only | IT; device unverified |

All six current entries have nonblank name, role, intro, prompt and portrait; six unique IDs; no sorting/filter hides entries in `showCoachCatalog`. Names are normalized for accent matching; Sékou's ID is ASCII `recovery`. Android uses role IDs, not portrait basenames. Unknown IDs fall back to Kofi. `setSelectedCoach` writes `vitalis-selected-coach-v312`; Android holds no separate selection. `askCoach` receives role ID and dispatches `agentId` with `requestId`; JS pending requests route by requestId. Requests do not include previous chat messages; visible conversation memory is JS-only.

**Verified defect R-H1:** the earlier deep-detail document capture listener does not exclude `.vitalis-power-overlay-312`. Passing the real card labels `Nia Coach sommeil` and `Sékou Coach récupération` to that actual listener calls `showCategory('sleep')` / `showCategory('recovery')` and `stopImmediatePropagation`. The `h` in `coach` satisfies the overly broad sleep regex `/h|min|score|heure/`. Thus the card's own handler can be prevented. This was an isolated callback execution, not a rendered Android click test.

Other visibility risks: selectors and label matching depend on the mutable remote DOM; stale cached remote HTML; old globals/listeners coexist; `enhanceClassicInterface` hides `.proactive-brief`/`.agent-fab` and rewrites `article.coach-card`; unguarded localStorage access can abort current-layer initialization; selected coach preferences split between remote and local origins. New overlays reuse styles installed lazily by older UI functions, so styling requires visual verification. No unsupported-character or blank-field cause was identified.

## 10. Health Connect matrix

All rows below refer to `K.healthPermissions`, the manifest, and `K.readHealthData`. `Requested` means declaration plus requested runtime set, not permission granted on a phone. None has automated product coverage. Source checks found 16 permissions and 10 record readers.

| Health metric | Permission requested (READ_) | Record type | Read | Aggregation | Tests | Risk |
|---|---|---|---|---|---|---|
| Steps | STEPS | StepsRecord | Yes | Raw count sum | None | Overlap/paging |
| Distance | DISTANCE | DistanceRecord | Yes | Raw km sum | None | Overlap/paging |
| Total calories | TOTAL_CALORIES_BURNED | TotalCaloriesBurnedRecord | No | None | None | Unused permission |
| Active calories | ACTIVE_CALORIES_BURNED | ActiveCaloriesBurnedRecord | Yes | Raw kcal sum | None | Overlap/paging |
| Exercise | EXERCISE | ExerciseSessionRecord | Yes | Full duration sum | None | Overlap/day boundaries |
| Sleep | SLEEP | SleepSessionRecord | Yes | Full duration sum | None | Overnight/overlap |
| Heart rate | HEART_RATE | HeartRateRecord | Yes | Sample arithmetic mean | None | Duplicates/paging |
| HRV | HEART_RATE_VARIABILITY | HeartRateVariabilityRmssdRecord | No | None | None | Unused permission |
| Respiratory rate | RESPIRATORY_RATE | RespiratoryRateRecord | No | None | None | Unused permission |
| Oxygen | OXYGEN_SATURATION | OxygenSaturationRecord | Yes | Latest time in returned records | None | Paging |
| Blood pressure | BLOOD_PRESSURE | BloodPressureRecord | No | None | None | Unused permission |
| Temperature | BODY_TEMPERATURE | BodyTemperatureRecord | No | None | None | Unused permission |
| Weight | WEIGHT | WeightRecord | Yes | Latest time in returned records | None | Paging |
| Body fat | BODY_FAT | BodyFatRecord | No | None | None | Unused permission |
| Hydration | HYDRATION | HydrationRecord | Yes | Raw litre sum | None | Overlap/manual-store split |
| Nutrition | NUTRITION | NutritionRecord | Yes | Raw nutrients + local scanner meals | None | Overlap/duplicate saves |

Availability uses `getSdkStatus`; unavailable provider leads to Play Store; SDK_AVAILABLE obtains a client on creation. No reinitialization after provider installation in the same activity is evident. minSdk28 is configured, but Android/provider compatibility and rationale flows were not run. Each read checks its granted permission, allowing partial reads. `getGrantedPermissions()` itself sits outside runCatching. Revocation can therefore fail outside the handled block; a later read failure can retain prior health payload. No background-reading permission or scheduler exists.

Selected days use `LocalDate.atStartOfDay(ZoneId.systemDefault())` to next day, capped at now for today. This handles local midnight construction, but payload `periodHours=24` remains constant across DST/partial days. Interval records are summed at full duration with no explicit day-boundary clipping. Source discovery separately reads the previous 30 days. No `pageToken` loop, priority-aware `aggregate`, cross-source record deduplication or latest-request guard exists. Concurrent date requests can finish out of order.

Missing permission, no data and actual zero collapse to 0 for steps/calories/water and to null for selected vitals. Score availability often tests `>0`; a measured zero is treated as missing. Native score sums five fixed 20-point categories; offline HTML independently averages available categories and defaults to 70 with none. Neither score was clinically validated; no medical interpretation is made here.

`refreshHealthData()` defaults to `LocalDate.now()`. The first compatibility click handler intercepts “Actualiser les données” and calls it before newer selected-date handlers. Isolated execution confirmed this route. Calendar inference also relies on French text and can ignore native selected date. Manual scanner meals are included even without Health Connect, but a Health Connect exception can prevent them reaching the UI. No Health Connect writes occur.

## 11. Nutrition scanner and AI data flow

### Scanner

`C.scanMeal` creates `<input type=file accept=image/* capture=environment>`. Android `onShowFileChooser` delegates to `params.createIntent()` and handles returned data/clipData URIs. There is no explicit camera ACTION_IMAGE_CAPTURE, output URI/FileProvider or native bitmap capture path. Gallery/file selection is source-implemented; direct camera capture is PARTIAL/DEVICE. No camera/storage runtime permission is requested.

FileReader loads the whole file before resizing; Image/canvas limits the largest side to 1280 and encodes JPEG quality 0.82. Typical re-encoding drops original metadata, but no explicit metadata audit/strip step exists. Decode failure falls back to sending the original data URL, potentially retaining metadata. No predecode file-byte/dimension limit, URI scheme/MIME/authority verification or corruption rejection exists. Native code only checks a `data:image/` prefix and truncates at 6,000,000 characters, potentially corrupting the payload.

Permission denial/chooser cancellation yields an empty URI array; cancellation UX and file-input cleanup are not proven. FileChooser launch failure clears its callback but does not explicitly notify that callback. A “photo recorded” journal entry is written before decoding or AI success, although image bytes are not durably saved. The old HTML preview uses createObjectURL without explicit revocation. No raw-image disk store is implemented in the inspected native path; WebView/process caches are unverified.

Analysis requires a phone key plus native health consent. The AI is prompted for JSON fields; there is no enforced structured response schema. `P.parseMealEstimate` strips code fences and parses JSON, normalizes numeric values/confidence, but does not comprehensively validate object shape or plausible upper bounds. Malformed output is displayed as text; no save form. Low confidence is displayed, not a save restriction. The form edits name and seven nutrients. Selected date is taken at save time from last native payload, not captured with the photo/request; date changes or out-of-order refresh can misattribute the meal.

`K.saveManualMealEstimate` bounds JSON input to 24,000 characters, stores nonnegative finite nutrient values and a confidence, generates `scanner-<milliseconds>`, retains only 500 records in `manual_meal_estimates`, then refreshes. It does not implement idempotency, duplicate-save detection, per-meal deletion, write serialization, export, or transactional persistence. Closing the form after success reduces normal repeated clicks, but native repeated calls still append. Persistence uses SharedPreferences.apply; restart recovery is source-implemented, not device-tested.

| Case | Audit result |
|---|---|
| Camera | PARTIAL: HTML hint only; device chooser may not provide capture |
| Gallery | IT: system chooser/URI bridge; device unverified |
| Cancellation / denied access | Callback branches exist; runtime unverified |
| Oversized image | Resizes after full decode; memory exhaustion risk before resize |
| Corrupt image | Raw data fallback; reliable rejection not implemented |
| Low-confidence response | Displayed and editable; saving allowed |
| Edit/save | Source implemented; no end-to-end validation |
| Duplicate save | No native protection |

### OpenAI

`K.requestAi` posts JSON to `https://api.openai.com/v1/responses` via HttpURLConnection; fixed `OPENAI_MODEL="gpt-5.6"`, `max_output_tokens=900`, `store=false`, instruction string, one user message with text and optional low-detail image. No model availability or API success was tested. No conversation history is sent. No API request was made in Run 0.

Connect/read timeouts are 20/75 seconds. No retry/backoff, explicit cancellation/connection handle, rate-limit queue, HTTP-specific invalid-key UX or offline preflight exists. Non-2xx returns provider message or HTTP status; malformed/empty output becomes an error event. JS coach errors become generic local guidance; the cause can be obscured. Pending UI tasks have no timeout/cancellation and Date.now request IDs can collide under rapid calls.

`sanitizedHealthContext` uses an allowlist of aggregates, nutrition, score, source attribution and timestamps. Every health coach/photo request receives this shared health context regardless of specialty. Selected date/range are omitted although payload may be historical. This limits raw records but does not minimize to each question. Developer requests omit health context, yet user text is unrestricted. Speech uses Android's chosen recognizer, not an OpenAI transcription endpoint; its service/network processing was not inspected.

Key encryption: AndroidKeyStore AES-256-GCM, random IV generated on encryption, IV+ciphertext in `vitalis_secure_preferences`; separate health/developer entries, common Keystore alias. No raw-key getter or logged Authorization value found. Clearing removes encrypted preference entry, not the shared alias or remote credential; revocation at provider remains separate. Decryption errors silently appear as unconfigured. Key entry occurs in a WebView DOM that remote scripts can access. Native health consent is checked before launching requests, but is writable through the same bridge; it does not authenticate the caller. Revocation does not cancel an already-running request.

## 12. Voice feature status

Microphone initially false. Source calls to start come from UI/bridge actions, not app startup; however the bridge does not enforce a native user gesture. `startMicrophone` requests RECORD_AUDIO, checks recognition availability, initializes SpeechRecognizer/listener, uses system locale and partial results. Numeric errors dispatch to JS; nonpermanent errors restart after 900ms, final results after 500ms if still enabled. No-speech has no special bounded retry. No final-result sequence deduplication exists.

Both legacy and current JS voice listeners remain; both stop voice input after a final result and each can update its matching UI. This needs overlap/race validation. Explicit stop cancels listening; onDestroy destroys the recognizer. Backgrounding/closing a coach overlay does not explicitly stop the microphone. No voice recording file/log is implemented. Recognition-service data handling is unverified.

TTS initializes to French, checks readiness/language support, takes at most 8,000 characters, uses QUEUE_FLUSH, provides stop/progress events and shuts down on destroy. Coach responses request fr-FR; English language support exists in the bridge but no full English UI. Unsupported language falls back to French. Missing engine, focus changes, route changes, cancellation and background behavior all require a physical device.

## 13. Connector support summary

32 native catalogue entries were verified. Class A here means **a Health Connect route exists in Vitalis source**, conditional on the provider actually writing data; it is not a current provider compatibility certification. B = launch/setup support; C = direct integration required but absent; D = unsupported Android; E = fully integrated and tested (none); F = unclear (provider compatibility remains unverified across all rows). Package names are source literals, not independently validated store identities.

Every nonempty package list has manifest visibility queries and `isPackageInstalled` detection. All provider entries share `buildConnectorPayload` states: connected only when a package is in read source records; otherwise installed/not_installed. Health Connect adds availability/update states; Apple is unsupported. App installation alone does **not** set native status connected. The 30-day source window means connected does not prove current-day data or current auth. Local scanner source can appear as a dynamic “Health Connect” source despite not originating in Health Connect.

| Service (stable ID) | Android package(s) | Source mode | Classification | HC route | Launch/deep link | Direct OAuth/API | Tests |
|---|---|---|---|---|---|---|---|
| Health Connect (`health_connect`) | com.google.android.apps.healthdata | health_connect | A (conditional) | Permission request | App launch / store | Absent | None |
| Samsung Health (`samsung_health`) | com.sec.android.app.shealth | health_connect | A (conditional) | Grant Vitalis + launch provider | App launch / store | Absent | None |
| Google Fit (`google_fit`) | com.google.android.apps.fitness | health_connect | A (conditional) | Grant Vitalis + launch provider | App launch / store | Absent | None |
| Mibro Fit (`mibro_fit`) | com.xiaoxun.xunoversea, com.zhencheng.mibrofit | bridge | B; HC conditional | Grant Vitalis + launch provider | App launch / store | Absent | None |
| Fitbit (`fitbit`) | com.fitbit.FitbitMobile | health_connect | A (conditional) | Grant Vitalis + launch provider | App launch / store | Absent | None |
| Garmin Connect (`garmin`) | com.garmin.android.apps.connectmobile | provider | B / C | Not implemented | App launch / store | Absent | None |
| Huawei Health (`huawei`) | com.huawei.health | provider | B / C | Not implemented | App launch / store | Absent | None |
| Strava (`strava`) | com.strava | provider | B / C | Not implemented | App launch / store | Absent | None |
| Oura (`oura`) | com.ouraring.oura | provider | B / C | Not implemented | App launch / store | Absent | None |
| WHOOP (`whoop`) | com.whoop.android | provider | B / C | Not implemented | App launch / store | Absent | None |
| Withings (`withings`) | com.withings.wiscale2 | health_connect | A (conditional) | Grant Vitalis + launch provider | App launch / store | Absent | None |
| Health Sync (`health_sync`) | nl.appyhapps.healthsync | bridge | B; HC conditional | Grant Vitalis + launch provider | App launch / store | Absent | None |
| MyFitnessPal (`myfitnesspal`) | com.myfitnesspal.android | health_connect | A (conditional) | Grant Vitalis + launch provider | App launch / store | Absent | None |
| YAZIO (`yazio`) | com.yazio.android | provider | B / C | Not implemented | App launch / store | Absent | None |
| Welmi (`welmi`) | welmi.ai.android | provider | B / C | Not implemented | App launch / store | Absent | None |
| Cronometer (`cronometer`) | com.cronometer.android.gold | provider | B / C | Not implemented | App launch / store | Absent | None |
| Lifesum (`lifesum`) | com.sillens.shapeupclub | provider | B / C | Not implemented | App launch / store | Absent | None |
| FitOn (`fiton`) | com.fiton.android | provider | B / C | Not implemented | App launch / store | Absent | None |
| Fitify (`fitify`) | com.fitifyworkouts.bodyweight.workoutapp | provider | B / C | Not implemented | App launch / store | Absent | None |
| FlexMe (`flexme`) | stretchingworkouts.homeexercises.flexibility | provider | B / C | Not implemented | App launch / store | Absent | None |
| TrainingPeaks (`trainingpeaks`) | com.peaksware.trainingpeaks | provider | B / C | Not implemented | App launch / store | Absent | None |
| Zwift (`zwift`) | com.zwift.zwiftgame, com.zwift.android.prod | provider | B / C | Not implemented | App launch / store | Absent | None |
| Peloton (`peloton`) | com.onepeloton.callisto | provider | B / C | Not implemented | App launch / store | Absent | None |
| Freeletics (`freeletics`) | com.freeletics.lite | provider | B / C | Not implemented | App launch / store | Absent | None |
| Komoot (`komoot`) | de.komoot.android | provider | B / C | Not implemented | App launch / store | Absent | None |
| Headspace (`headspace`) | com.getsomeheadspace.android | provider | B / C | Not implemented | App launch / store | Absent | None |
| Calm (`calm`) | com.calm.android | provider | B / C | Not implemented | App launch / store | Absent | None |
| Sleep Cycle (`sleep_cycle`) | com.northcube.sleepcycle | provider | B / C | Not implemented | App launch / store | Absent | None |
| Welltory (`welltory`) | com.welltory.client.android | provider | B / C | Not implemented | App launch / store | Absent | None |
| Suunto (`suunto`) | com.stt.android.suunto | provider | B / C | Not implemented | App launch / store | Absent | None |
| COROS (`coros`) | com.yf.smart.coros.dist | provider | B / C | Not implemented | App launch / store | Absent | None |
| Apple Health (`apple_health`) | None | unsupported_android | D | None | None | Absent | None |

There is no OAuth authorize/token exchange, redirect handler, provider API client or refresh-token store anywhere in main. `openInstalledConnector` launches the app's main activity, not a provider-specific authorization deep link. Store fallback is a name search. “Autoriser via l’application” does not establish a connection by itself. Health Connect/bridge mode first requests Vitalis permissions, then opens the provider; the user must separately enable provider sharing. Resume triggers a read after 700ms, without waiting for actual provider synchronization.

The fallback `H.renderSources` has a separate 15-name list including **FitCoach**, which is absent from native catalogue. It matches provider names against package substrings; for example `com.sec.android.app.shealth` does not contain “Samsung”. That can show incorrect availability/status. Its cards have no individual native authorize action. This list is not the current 32-entry native connector centre. No provider support was verified on a phone or via live accounts.

## 14. Test inventory

The complete tracked tree and all source sets/modules/workflow were checked, including inline JavaScript. No test framework dependency, test directory, test file, test script/package manifest, emulator configuration, mocked AI/Health Connect test, snapshot, release test or integration suite was found. Zero existing test cases is based on the complete small repository, not one missing folder. No ignored/disabled test markers found; flaky status cannot be assessed without a suite/history.

| Test area | Existing tests | Executed in this run | Result | Missing coverage |
|---|---|---|---|---|
| JVM / Android unit | None found | No | BLOCKED build runner | Mapping, aggregation, persistence, dates, parser |
| Instrumentation / UI / WebView | None found | No | BLOCKED — NO DEVICE/EMULATOR AVAILABLE | Startup, fallback, bridge, permission/device flows |
| JavaScript product suite | None found | No | NOT FOUND | Capture routing, forms, error/cancellation |
| AI mocks / Health Connect fakes | None found | No | NOT FOUND | Consent, API errors, pagination, deduplication |
| Static syntax / data integrity | No committed suite | Yes | PASS, scoped below | Does not establish UI behavior |
| Isolated audit behavior probes | Temporary audit harness only | Yes | Two interception defects + today-refresh route reproduced | Not Android, browser rendering or end-to-end tests |

## 15. Commands executed

Evidence was gathered with `git clone`, `git status --short`, `git log -1 --format=fuller`, `git branch -a`, `git tag`, `git ls-files`, `git rev-list --left-right --count main...<branch>`, `git cherry main <branch>`, `git diff --stat`/`--name-status`, `rg`, full source reads, and GitHub GETs for repository, open PRs, releases, recent runs/jobs/artifacts, protection and rulesets. Initial tree was clean.

| Exact quality command | Started | Final result / duration | Report/artifact |
|---|---|---|---|
| `java -version` | Yes | OpenJDK 17.0.20, exit 0 | Terminal output |
| `./gradlew --version` | No | BLOCKED: file not found, <0.01s | No wrapper |
| `./gradlew clean` | No | BLOCKED: file not found, <0.01s | No task report |
| `./gradlew test` | No | BLOCKED: file not found, <0.01s | No task report |
| `./gradlew lint` | No | BLOCKED: file not found, <0.01s | No task report |
| `./gradlew assembleDebug` | No | BLOCKED: file not found, <0.01s | No APK |
| `gradle --version` | No | BLOCKED: executable absent, <0.01s | No installed alternative |
| `adb devices` | No | BLOCKED: executable absent, <0.01s | No device enumeration possible |
| `./gradlew connectedAndroidTest` | Not attempted | BLOCKED — NO DEVICE/EMULATOR AVAILABLE; runner also absent | No device report |
| `node --check app/src/main/assets/vitalis/compat.js` | Yes | PASS exit 0, ~0.05s | No output/errors |
| `node --check app/src/main/assets/vitalis/vitalis-3.12.js` | Yes | PASS exit 0, ~0.05s | No output/errors |
| `node /workspace/scratch/d5207ddd1114/audit-evidence/probes.cjs` | Yes | Exit 0; scoped outcomes section 18 | Temporary `probes.log` |
| Python ElementTree/Pillow checks | Yes | All XML parse; six WebPs decode | Terminal output |
| Bounded tracked-text secret pattern scan | Yes | No candidate matched; no secret values printed | Terminal summary |

Temporary gates/probe logs are in `/workspace/scratch/d5207ddd1114/audit-evidence/`; they are not product tests or committed artifacts. This document preserves their substantive results. Local JDK exists, but Gradle/wrapper, Android SDK configuration and adb were unavailable; a bounded local tool search found no alternative installation. No dependency version was upgraded or suppression introduced. An unavailable gate is not a failed test and never a PASS.

## 16. Build results

Local debug/release/AAB: **BLOCKED/NOT RUN**. No APK path, size, signing certificate or new artifact. Historical debug build at pinned HEAD: GitHub run `30587764440`, success, 2026-07-30; job `91023058030`, debug compilation step 22:37:15–22:37:59 UTC. This is prior CI evidence, not a newly executed Run 0 build. Compile/target SDK compatibility beyond that historical build was not validated. No release/minified build evidence.

## 17. Lint results

**BLOCKED** locally by missing wrapper/Gradle and SDK. Existing CI has no lint step or reports. No lint warnings/errors were measured. Existing source annotations are narrow SetJavaScriptEnabled/deprecation suppressions; none were added. Dependency vulnerability scan and resolved transitive graph are NOT VERIFIED, not “no vulnerabilities”.

## 18. Automated execution results

- Two external JS assets pass Node syntax checks; one inline HTML script passes `vm.Script` parsing.
- Current registry evaluated: Kofi/general, Ama/nutrition, Ayo/activity, Nia/sleep, Sékou/recovery, Zuri/mental; six distinct IDs, all referenced portraits present.
- Six WebP images passed decode verification; all Android XML files parsed.
- Actual isolated deep-detail click callback, with real card-label text: Nia was intercepted to sleep; Sékou to recovery; both set stopImmediatePropagation. **Defects reproduced**, not feature passes.
- Actual isolated `actionFor('actualiser les donnees')` routed to `refreshHealthData` (native default is today). **Date-preservation risk confirmed in source routing**; no phone data changed.
- Secret scan covered tracked text patterns for private key headers/OpenAI/GitHub tokens; no matches. It was not a full historical/provider secret-scanning audit and cannot certify absence of secrets.

Existing product tests executed/passed/failed: **0 / 0 / 0**. Do not combine these scoped checks into an invented product-suite score.

## 19. Emulator validation

**BLOCKED — NO DEVICE/EMULATOR AVAILABLE.** No adb/SDK configured. No Android permission, WebView, TTS, Health Connect or instrumentation execution occurred.

## 20. Physical-device validation

**NOT VERIFIED.** All runtime flows—including install/upgrade, camera/gallery, provider accounts, coach display/answers, Health Connect data correctness, revocation, voice, offline persistence and deletion—require device evidence. Historical user reports describe failures but are not a current validation run.

## 21. Security findings

Severity is source-level risk prioritization, not proof of exploitation.

### Critical

None identified during this bounded source-level audit. This is not a security certification; historical secrets, remote-site code and resolved dependency vulnerabilities are unverified.

### High

- **S-H1 — Native bridge trust boundary:** `onCreate` exposes `VitalisAndroid` to WebView JavaScript; bridge methods have no per-caller origin verification and permit health reads, key/consent changes, AI calls and external actions. An injected/compromised remote script or frame could invoke privileged methods. Top-level host checks do not authenticate frame callers. Android's native-bridge guidance confirms addJavascriptInterface is exposed to frames. No exploit was executed.
- **S-H2 — Key entry in remote DOM:** `P.configureHealthAi`/`configureDeveloperAi` create password inputs inside the remote page. Other scripts in that page can read the typed plaintext before Keystore storage. Encryption at rest does not protect that entry surface. No raw-key getter was found.
- **S-H3 — Unvalidated intent/navigation input:** bridge `openExternalUrl` accepts arbitrary parsed URIs for ACTION_VIEW; navigation checks host only, no scheme/port policy or intent allowlist. Exploitability depends on installed handlers and malicious script access; device testing needed.
- **S-H4 — Background microphone boundary:** start requires a UI path in current source but bridge callers are not gesture-gated; microphone is not stopped onPause/onStop or overlay close. Continuous restarts make lifecycle testing a privacy prerequisite.

### Medium

- **S-M1 — Payload limits are incomplete:** prompt limits exist, but image truncation can corrupt data, full image decode happens before size checks, keys/requestIds/URL strings and API response readText have no explicit comprehensive size limits. Potential memory/resource exhaustion and incorrect saves.
- **S-M2 — Incomplete erasure and retention:** local deletion leaves native scanner meals, other origin storage, profile and cached health snapshots; 500-entry truncation silently discards older meals/journal entries; no export/restore.
- **S-M3 — Consent/data minimization:** broad reusable health consent, shared context across specialties, mutable via bridge, and no cancellation on revocation. Attribution/timestamps sent; selected date omitted.
- **S-M4 — Reliability failure handling:** permission query outside runCatching; cancellations swallowed; stale payload retained on error; some runCatching errors silently ignored. Delayed callbacks can outlive destroyed views.
- **S-M5 — Distribution integrity:** only debug APK CI; no persistent release signing/verified upgrade/rollback. An ephemeral debug-signature mismatch can force destructive reinstall. No uninstall recommendation is made by this audit.

### Low

- **S-L1 — Build supply chain reproducibility:** action major-version tags rather than commit pins, no dependency verification/lockfiles or wrapper; ubuntu-latest changes over time. No known vulnerability is asserted.
- **S-L2 — Documentation:** README claims deduplication/operation beyond implementation evidence; no full privacy policy. Network/AI errors may be shown verbatim (bounded), but no production crash telemetry/redaction policy exists.

### Positive / informational checks

HTTPS endpoint and cleartext block; mixed content prohibited; file/content access disabled; no SSL-error bypass override; backup disabled; encrypted key preferences; no raw-key getter; escaped UI output in inspected response renderers; no explicit health/voice/key log calls found; bridge keep rule exists for R8. No screenshot FLAG_SECURE found. MainActivity export is needed for launcher; permission alias is protected; no other exported component. Release WebView debugging is gated off, but debug APKs enable it. The app has no explicit native URI-authority validation for chooser returns. The only current credential patterns in source are placeholders/validation prefixes, not exposed secrets.

## 22. Privacy findings

Stores and deletion scope:

| Store / key | Content | Persistence/deletion |
|---|---|---|
| SharedPreferences `vitalis_secure_preferences` | Encrypted health/developer keys | Separate clear methods; Keystore alias remains |
| AndroidKeyStore `vitalis_openai_key_v1` | AES encryption key | No alias-delete action; shared by both entries |
| SharedPreferences `vitalis_preferences` | `ai_health_consent`, `manual_meal_estimates` | Consent false supported; no native meal clear/export |
| localStorage `vitalis-offline-v1` | Profile, cached health payload, fallback entries | clearData clears entries only |
| localStorage `vitalis-native-journal-v1` | Compatibility manual entries, photo filename/time | Max 500; no clear action exposed in this path |
| localStorage `vitalis-selected-coach-v312` | Selected coach | Separate per origin; no synchronized native preference |
| JS memory | Conversations, pending AI/image work | Lost on reload/process recreation; no durable chat history |

Remote/local origins have separate localStorage, so manual work can appear missing when fallback changes. Manual compatibility events do not have a corresponding fallback journal subscriber; native scanner meals, compatibility journal and fallback entries are distinct. Deletion wording cannot stand for erasure of all health data. Export not found. No Health Connect delete/write exists. No cloud-account deletion implemented. README and inline consent are product documentation only; no legal, regulatory or medical compliance is claimed. No personal health data was read or fabricated for this audit.

## 23. GitHub Actions status

One workflow: `.github/workflows/build-apk.yml`, “Build Vitalis APK”. Trigger: manual dispatch, PR to main, push to main. Ubuntu latest, 20-minute timeout; contents:read only; checkout@v4; Temurin Java17 via setup-java@v4; Gradle 8.11.1 via gradle/actions/setup-gradle@v4; `gradle --no-daemon assembleDebug`; upload-artifact@v4 `Vitalis-Mobile-debug-apk`, path `app/build/outputs/apk/debug/app-debug.apk`, missing file is error, retention 30 days. Gradle action provides cache integration; no additional explicit dependency cache, clean, unit-test, lint, instrumented, release, AAB, signing or release-creation step. No explicit secret usage.

Five recent runs inspected: `30587764440` (fdb8a2e), `30587638235` (fbdfb67), `30522385875` (ebf6e57), `30522280422` (72f99c8), `30518428522` (08a2247); all completed/success, all in July 2026. Latest job steps confirm only debug compilation/upload. All five per-run artifact endpoints returned zero artifacts on the audit date. Retention has elapsed; expiry versus manual deletion cannot be distinguished from an empty response. No artifact is available from those runs. Global artifact listing was rejected by the connector's supported-URL policy, so older workflows were not exhaustively inventoried.

Rulesets endpoint returned `[]`. Branch-protection GET returned **403 Resource not accessible by integration**. Whether failing CI prevents merge remains **NOT VERIFIED**; do not infer absence of protection from missing access. No CI run was launched for this audit, and no unrelated rerun was needed to claim baseline completion.

## 24. Release process and maturity

| Stage | Evidence-based assessment / blockers |
|---|---|
| Development prototype | Supported by source and historical debug build; current local rebuild blocked; runtime defects documented |
| Internal alpha | Candidate only; establish reproducible build and run basic device startup/permissions/coaches/scanner checks first |
| Private beta | Not ready: security boundaries, health totals, deletion/date integrity, automated coverage and stable signing unresolved |
| Release candidate | Not ready: beta gates plus full regression, release/minified build, upgrade/rollback and documented privacy flows |
| Stable personal-use release | Not established: important user paths still defective/unverified; data recovery and signature continuity absent |
| Google Play internal-test release | Not ready: signed AAB/release pipeline, store/Health Connect declarations, policy documentation and device gates unverified |
| Public production | Not ready: all above plus deployment monitoring/crash handling, support/distribution process and store review |

No percentage-complete score is justified. Build reliability is only historical. There is no verified stable signing certificate, upgrade migration, rollback/backup restore, changelog per release, monitoring/crash reporting or artifact recovery mechanism beyond a 30-day debug workflow. README contains version notes and sideload instructions. Store compliance was not assessed; nothing here certifies it.

## 25. Open PRs and branches

Open PR query returned only **draft PR #2**, `Integrate Vitalis Developer AI agent`, head `feature/vitalis-developer-ai` / `6fa080efea5a448a5afbb59388d9a535fdb85b44`, created/last updated 2026-07-30. API base metadata showed an older main SHA; branch conclusions below use the freshly cloned Git graph instead.

| Branch suffix under origin | Commits only in current main | Commits only on branch | Observation |
|---|---:|---:|---|
| agent/connectors-voice-controls | 30 | 0 | Ancestor, integrated |
| agent/restore-classic-interface | 38 | 0 | Ancestor, integrated |
| agent/score-deep-details | 23 | 0 | Ancestor, integrated |
| agent/vitalis-3-10-day-nutrition | 10 | 0 | Ancestor, integrated |
| agent/vitalis-3-9-1-classic-ui | 14 | 0 | Ancestor, integrated |
| agent/vitalis-3.12-ai-connectors | 7 | 1 | Divergent commit is patch-equivalent in main (`git cherry` negative) |
| agent/vitalis-3.13-coach-connector-fix | 3 | 0 | Ancestor, integrated |
| agent/vitalis-3.14-coaches-connectors | 1 | 0 | Ancestor, integrated |
| agent/vitalis-ai-3-9-all-functions | 19 | 0 | Ancestor, integrated |
| agent/vitalis-offline-first-3-5 | 43 | 0 | Ancestor, integrated |
| feature/vitalis-developer-ai | 7 | 2 | Two unique patches; not exactly merged |

Main has newer functionality absent from PR2, including six portraits/current layer and connector changes. PR2 contains a separate developer-ai.js approach; the feature intent overlaps main, but the patches are not equivalent. Do **not** describe PR2 as fully merged manually. Recommend owner review for supersession or selective salvage; do not merge wholesale or delete/close automatically. No old branch/PR was changed.

## 26. Critical release blockers (priority order)

1. Secure native-bridge and key-entry boundaries before distributing sensitive-data builds (S-H1/S-H2).
2. Restore reproducible Android quality gates: pinned wrapper/runner, SDK, unit/lint/debug reports; establish device test access.
3. Correct and regression-test coach click interception and selected-date routing (R-H1, R-H2).
4. Validate Health Connect pagination, deduplication, time boundaries, permissions, stale-state handling and real provider data; implement or stop advertising six unread metrics.
5. Establish local-store integrity: consistent score/empty state, manual journal visibility, meal idempotency/date capture, explicit deletion/export/recovery.
6. Validate camera/gallery/image failure/AI consent and voice background behavior; disclose provider OAuth gaps accurately.
7. Establish stable signing, verified upgrade path, retained artifacts, minified release/AAB tests and privacy/distribution documentation.

“Critical release blocker” here describes priority to shipping, not a CRITICAL security vulnerability finding.

## 27. High-priority risks

R-H1 Nia/Sékou interception; R-H2 today-refresh and stale concurrent dates; R-H3 unpaginated/summed overlapping health records; R-H4 inconsistent offline/native score and synthetic no-data 70; R-H5 incomplete privacy deletion; R-H6 unverified capture/voice/connector flows and six unused permissions. Security high findings are in section 21. These are documented, not fixed in Run 0.

## 28. Medium-priority improvements

Bound image/response memory, strict nutrition schema/plausibility, operation cancellation, robust state restoration and single listener ownership; native selected-date persistence; no-data versus zero semantics; user-configurable targets; per-question AI context and date; consent revocation; provider status provenance; predictable retention and recovery. Any implementation belongs to an explicitly scoped subsequent run.

## 29. Low-priority improvements

Update README claims/version organization, audit dependency/action pinning, document provider package aliases and remote DOM contract, improve localization/accessibility/empty states, remove dead duplicate logic only after regression coverage. Full English UI is not currently present; do not describe it as complete.

## 30. Recommended run sequence

1. **Run 1 — Reproducible verification and trust-boundary fixes:** build environment/wrapper, baseline regression harness, bridge/navigation/key-entry security. Preserve all IDs/keys/UI contracts.
2. **Run 2 — UI routing and date correctness:** coach selection, refresh, callback ordering, remote/fallback behavior and score presentation.
3. **Run 3 — Health data correctness:** permissions, paging/deduplication, intervals/timezones, unsupported metrics, revocation and empty states.
4. **Run 4 — Nutrition and local-data integrity:** reliable capture/picker, safe parsing/date/idempotency, consistent stores, deletion/export/recovery.
5. **Run 5 — Voice and connector truthfulness:** device matrix, lifecycle, real authorization routes; OAuth only with provider-approved configuration.
6. **Run 6 — Private release qualification:** stable signing, upgrade/rollback, release/AAB CI, durable artifacts, privacy documentation and device acceptance.

## 31. Exact prerequisites for Run 1

**READY FOR RUN 1: YES, for remediation/environment setup; NOT approval to release.** Baseline source, risks and blocked checks are recorded. Run 1 must start from this audit commit or rebase/re-audit if main changes, preserve applicationId/keys/coach IDs and classic UI, provision JDK17 + pinned Gradle8.11.1 + Android SDK platform36 with required build tools, and establish a compatible emulator/physical device for runtime gates. Provisioning may be its first task; missing tooling is not a reason to invent passed tests or postpone source-only remediation.

Confirm the Run 1 implementation scope before product behavior changes. No real OpenAI key/health records are prerequisites for mocked tests. Use synthetic fixtures only in a future authorized test harness, never fabricate health records as actual user data. Secure API-key entry and bridge isolation need an explicit design preserving existing storage contracts. Current user request authorized audit only, so Run 1 product fixes were not started.

## 32. Explicitly not validated and integrity statement

Not validated: live remote DOM/code/availability; actual rendered classic/fallback parity; any emulator/physical-device flow; current API model/account permissions or answers; user key existence; provider package accuracy/accounts/subscriptions/Health Connect sharing; real health totals; camera/gallery permissions/cancel/large images on phone; background voice; process-death restoration; signed/minified release, AAB, install/upgrade/rollback; branch protection; exhaustive old artifacts; CVE/dependency/history-secret scan; clinical validity; legal/store compliance.

No product feature, UI redesign, version change, stored-data contract change, health-data fabrication, live AI request or secret commit occurred. No unexecuted test was reported as passed. The audit does not erase the documented failures by citing a successful debug compilation. Only this documentation is delivered in the audit commit.

### Primary technical references (reviewed 2026-09-26)

- Android native bridge trust model: https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges
- Health Connect raw reading and pagination: https://developer.android.com/health-and-fitness/health-connect/read-data
- Health Connect aggregation: https://developer.android.com/health-and-fitness/health-connect/aggregate-data
- Latest historical build: https://github.com/gillesarnaudasse65-web/Vitalis-Mobile/actions/runs/30587764440
- Open draft: https://github.com/gillesarnaudasse65-web/Vitalis-Mobile/pull/2

External references explain platform behavior; every finding about Vitalis itself is grounded in the pinned source or specifically scoped execution above.

## Dated appendix — 2026-09-26, Run 1 foundation

The Run 0 audit remains a point-in-time record of the initial commit. Run 1 is tracked in [`VITALIS_RUN1_BUILD_TEST_FOUNDATION.md`](VITALIS_RUN1_BUILD_TEST_FOUNDATION.md). It adds the official pinned Gradle wrapper, CI quality gates and tests, plus minimum WebView navigation and bridge-input guardrails. The confirmed coach, date, Health Connect, scanner and voice defects remain open. The native bridge still lacks per-frame caller-origin isolation and the API key still enters through the remote DOM. Run 1 CI outcomes must be read from its new workflow run; the historical build success above does not validate these changes.

## Dated appendix — 2026-09-26, Run 2 coach and date corrections

Run 2 is documented in [`VITALIS_RUN2_COACH_DATE_WEBVIEW.md`](VITALIS_RUN2_COACH_DATE_WEBVIEW.md). It reproduces the old coach capture and today-refresh defects before fixing them, then tests the same injected scripts with an approved local WebView fixture. The original Run 0 findings remain historical evidence; Health Connect, scanner, voice, OAuth, deletion/export and security residuals remain open. Run 2 PR #7 is stacked on Run 1 PR #6 while #6 remains unmerged.

## Dated appendix — 2026-09-26, Run 3 Health Connect reliability

Run 1 PR #6 and Run 2 PR #7 were subsequently merged into `main`; Run 3 starts from combined merge commit `2187e053ea30d78e893abbd7f72de74697e6e3cc`. The Health Connect findings above remain the historical Run 0 baseline rather than being rewritten. Run 3 adds complete pagination, stable-ID deduplication, explicit availability/permission/metric states, local-day DST handling and stale-response protection; it also removes the six unused read permissions. See [`VITALIS_RUN3_HEALTH_CONNECT.md`](VITALIS_RUN3_HEALTH_CONNECT.md) for the current contract, CI evidence and remaining limitations.
