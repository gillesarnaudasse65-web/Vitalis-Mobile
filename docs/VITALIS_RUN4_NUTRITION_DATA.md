# Vitalis Run 4 — Nutrition scanner and local-data integrity

Date: 2026-09-26  
Branch: `agent/vitalis-run4-nutrition-data`  
Stacked base: `agent/vitalis-run3-health-connect-reliability`

## 1. Verified Run 3 prerequisite and source-control state

Run 1 and Run 2 are present in `main` at combined ancestry commit `2187e053ea30d78e893abbd7f72de74697e6e3cc`. Run 3 is PR #8, branch `agent/vitalis-run3-health-connect-reliability`, final remote SHA `c996458971133c653d11347939668ff3969cb251` (tree `35c0d960b16153a3ea18d39ee8d0070cbfd32dd9`). At Run 4 start it was open, cleanly mergeable and not merged. Run 3 Actions run `36271174416` passed wrapper validation, 14 JavaScript tests, 12 isolated Health Connect tests, 26 full JVM tests, lint with zero errors, both debug APK assemblies, and three connected emulator tests.

The verified Run 3 code contains complete Health Connect page-token traversal, stable metadata-ID deduplication, explicit availability and permission states, `DATA` / `NO_DATA` / `NOT_AUTHORIZED` / `UNSUPPORTED` / `ERROR` metric semantics, selected-date local-day queries, DST-aware intervals, generation-based stale-response protection, repeated-sync cancellation, and removal of the six unused sensitive read permissions. Because PR #8 is not merged, Run 4 is intentionally a stacked branch and its PR targets the Run 3 branch.

## 2. Previous scanner architecture

The previous path was:

1. A classic or fallback “Scanner un repas” button was captured by `compat.js`.
2. JavaScript created a hidden `<input type=file accept=image/* capture=environment>`.
3. The generic `WebChromeClient` launched the chooser intent.
4. JavaScript `FileReader` loaded the complete file as a data URL.
5. `Image` decoded it without a native byte/dimension gate.
6. Canvas resized to 1280 px and encoded JPEG at 0.82.
7. Decode failure fell back to the original, unvalidated data URL.
8. `VitalisAndroid.analyzeMealImage` accepted the JavaScript image.
9. Native code checked consent/key and called OpenAI.
10. JavaScript permissively parsed/coerced fields to non-negative zero.
11. The edit form resolved the selected date only when Save was tapped.
12. Native code generated a timestamp ID and appended to `manual_meal_estimates`.
13. The store silently kept only the newest 500 entries.
14. Health refresh combined Health Connect records with native manual meals.
15. `vitalis-native-journal-v1` and `vitalis-offline-v1` remained separate UI journals.

The remote and fallback DOMs differ, but the same injected compatibility/power scripts now route both scanner buttons into the native Run 4 pipeline.

## 3. New scan session and end-to-end flow

`NutritionScanSession` owns a stable `scanId`, derived stable `mealId`, creation time, date captured at scan start, image source, normalized metadata, explicit status, request ID, draft, saved ID and error. The statuses are `IDLE`, `SELECTING_IMAGE`, `NORMALIZING`, `READY_FOR_ANALYSIS`, `ANALYZING`, `REVIEW`, `SAVING`, `SAVED`, `CANCELLED`, and `ERROR`.

The new flow is:

1. JavaScript captures `capturedSelectedDate` and creates one scan ID.
2. Native `beginNutritionScan` records the session before opening any picker.
3. Android Photo Picker or delegated camera capture returns a scoped content URI.
4. Native code reads at most the configured source-byte limit.
5. Magic bytes, declared MIME, decode bounds, dimensions and pixels are validated.
6. `ImageDecoder` applies image orientation and a bounded target size.
7. Alpha is flattened, metadata is discarded, and a bounded JPEG is generated.
8. Only the normalized in-memory data URL can enter the AI request.
9. Consent and key are checked immediately before transmission.
10. The response is strictly parsed into `NutritionEstimate` and sent for review.
11. The user edits every supported nutrient before save.
12. Native code revalidates the edited structure and upserts stable `meal-$scanId`.
13. The meal keeps the session’s captured date while the currently visible date refreshes without being changed.
14. Saved meals support view, edit, delete, JSON export and scoped delete-all.

The legacy `analyzeMealImage` entry point rejects image data and directs callers to the secure flow. No original image or normalized image is written to persistent meal storage.

## 4. Picker and camera behavior

Gallery selection uses `ActivityResultContracts.PickVisualMedia`, which selects the system Photo Picker and compatible fallback supplied by AndroidX. Camera capture uses `TakePicture`, an internal cache file under `nutrition-captures/`, and a non-exported `FileProvider` granting only scoped URI access. No broad storage permission and no `CAMERA` permission were added. Cancellation, unreadable URIs, malformed content, unsupported formats and unavailable camera activity become explicit cancel/error states. Temporary camera files are deleted after processing/cancellation; the pending cache filename and URI survive activity recreation.

## 5. Image validation and normalization limits

| Control | Limit / behavior |
|---|---|
| Accepted formats | JPEG, PNG, WebP by magic bytes |
| Maximum source bytes | 12 MiB |
| Maximum width | 12,000 px |
| Maximum height | 12,000 px |
| Maximum pixels | 40,000,000 |
| Maximum normalized dimension | 1,280 px |
| Output | JPEG, quality 82 initially |
| Maximum normalized bytes | 1,500,000 bytes |
| Maximum base64 characters | 2,100,000 |

The source stream is rejected when it crosses 12 MiB; it is never truncated. Decode bounds are inspected before full decode. The controlled encoder lowers quality and dimensions when necessary, reports the actual final dimensions, and rejects rather than truncating an oversized result. `ImageDecoder` honors encoded orientation. Re-encoding strips EXIF and other source metadata.

## 6. Privacy and consent

The source chooser explicitly says that selection/normalization are local and that external analysis sends the normalized photo to OpenAI only after consent. Consent is checked before request start and again in the response guard. Revocation cancels/invalidates the active request; a late response cannot open review or save itself. Requests use `store:false`. Image bytes, base64, API keys, authorization headers and full nutrition history are not logged. Automated tests use a synthetic bitmap and an in-process mock response; they require no live key and send no network request.

## 7. Structured nutrition model and parsing

`NutritionEstimate` contains food items, meal name, portion description, calories, carbohydrate, protein, fat, fibre, sugar, sodium, confidence, uncertainty notes and `estimated=true`. Each food item has name, portion, estimated calories and confidence.

Native validation accepts a single JSON object (optionally wrapped in a JSON code fence), rejects empty/malformed/natural-language/oversized responses, requires numeric JSON types, rejects negative/non-finite/extreme values and bounds strings/arrays. Ceilings are 20,000 kcal, 2,000 g for each gram nutrient, and 100,000 mg sodium. Confidence is 0–1; values below 0.35 are marked low confidence. A valid partial response may be reviewed, but it is not savable until the user supplies all nutrient values. Invalid responses expose retry/manual/cancel actions and are never persisted automatically.

## 8. Selected-date integrity and concurrency

Date is captured in JavaScript and validated/stored natively before picker launch. Review displays that captured date. Later calendar changes, health refreshes, coach changes or application resume do not alter it. The session payload is persisted sufficiently to retain its stable scan/meal identity and date across recreation. Interrupted analysis/normalization is restored as an explicit error rather than pretending it completed.

Only the active scan can accept a response with its matching request ID. Starting scan B cancels scan A’s ownership and removes A’s normalized image. Generation, consent and scan/request guards discard late results. Cancel and local delete-all invalidate pending work. Re-analysis reuses the same session/meal identity.

## 9. Local meal schema, migration and idempotency

The authoritative record schema is version 2: ID, date, creation/update times, source, optional scan ID, name, food items, nutrient values, confidence, notes, estimated flag and schema version. The preserved SharedPreferences key remains `manual_meal_estimates`.

`NutritionMealCodec` reads both modern names and the former `name`, `selectedDate`, `recordedAt`, `*Grams`, `fiberGrams`, `sodiumMilligrams` and `summary` fields. Valid legacy records are rewritten to schema 2. Unknown/invalid entries are preserved byte-for-JSON-value during migration and unrelated mutations; a malformed whole store is reported and never silently replaced.

`upsert` removes all existing records with the same stable ID, then writes one canonical record. Double/triple taps, repeated bridge calls, retry and post-recreation acknowledgment therefore produce one record. There is no rolling 500-record deletion. Edit preserves ID, creation time, scan ID and source while updating `updatedAt`. Delete removes only the selected local Vitalis record. Both operations refresh the corresponding daily totals.

## 10. Authoritative-source and daily-total policy

- Health Connect nutrition records contribute once through the Run 3 deduplicated native read path.
- Vitalis-owned meals contribute once through `manual_meal_estimates` / `NutritionMealStore`.
- `vitalis-native-journal-v1` is metadata/history only and never contributes numeric nutrients.
- `vitalis-offline-v1` remains fallback UI/profile/cache state and never contributes numeric nutrients.
- Scanner journal metadata uses `mealId` and is upserted rather than duplicated.

`buildNutritionSummary` and detailed nutrition payloads consume the same native Health Connect list plus the date-filtered authoritative local list. This is the deterministic totals boundary.

## 11. View, edit, delete and scoped deletion

“Gérer mes repas” lists native local meals. Edit preserves record identity and recalculates totals; single delete requires confirmation and affects only that meal. “Supprimer toutes les données nutrition Vitalis” requires confirmation, cancels pending analysis, removes `manual_meal_estimates`, clears transient normalized images/pending scan metadata, removes scanner-meal metadata from both compatibility journals, and refreshes totals. It does not delete Health Connect/provider data, unrelated journal entries, accounts or API keys.

## 12. Export and recovery status

Export uses Android’s system `CreateDocument(application/json)` contract, never a raw external path. Format `vitalis-local-nutrition`, version 1, includes generation/application versions, explicit nutrient units and canonical local meals. It excludes keys, authorization data, images, WebView state and Health Connect raw data.

Import/restore is **not implemented in Run 4**. Safe import needs file-size/schema/record validation and an explicit MERGE-versus-REPLACE decision. Export is therefore a portable data foundation, not a complete tested backup/restore promise; import is a Run 6 prerequisite.

## 13. Tests added

- `scripts/test-run4-regressions.cjs`: six source-level regressions for stable date/session ownership, bounded native image processing, strict parsing, idempotent CRUD, export and stale/consent guards.
- `NutritionReliabilityTest`: supported signatures, invalid/oversized/spoofed images, normalization planning, complete/partial/malformed/extreme/low-confidence responses, captured dates, stale/cancelled responses, stable upsert, restart/edit/delete, legacy migration, malformed-store preservation, export and delete-all.
- `Run4NutritionWebViewTest`: production injected scripts, fixed historical date, synthetic image, mocked analysis, editable review, later date change, idempotent repeated save, activity recreation, edit and delete.
- `BridgeInputPolicyTest`: new 32 kB JSON boundary and normalized-base64 character/size checks.

The workflow adds a nutrition-specific isolated JVM gate before the full rerun, retains all Run 1–3 tests, and runs all instrumentation on API 35.

Final validation run `36278580916` (workflow run 74) completed successfully on the Run 4 head. It passed wrapper validation, JDK/Gradle initialization, 20 JavaScript tests, 12 isolated Health Connect tests, 15 isolated nutrition tests, all 41 JVM tests, lint, `assembleDebug`, `assembleDebugAndroidTest`, and all four API 35 connected tests. Lint reports 0 errors and 32 warnings, versus 0 errors and 33 warnings on the final Run 3 baseline; Run 4 adds no lint warning. The synthetic nutrition instrumentation verifies captured historical date, a later visible-date change, normalization, mock analysis, review edit, stable repeated save, activity recreation, persisted edit and delete.

Artifacts are retained for 90 days: `Vitalis-unit-lint-reports` (ID `10917703621`), `Vitalis-debug-apks` (ID `10918076795`) and `Vitalis-instrumentation-reports` (ID `10918440809`). Release APK, AAB and signing remain out of scope.

## 14. Physical-device matrix

No physical device is available in this environment. Photo Picker, real camera capture/cancel, gallery cancel, a real 12 MiB/rotated image, low-memory handling, backgrounding during a live request, process death, real user-key analysis and offline scanner behavior remain **NOT TESTED / BLOCKED** on physical hardware. Emulator tests use only the synthetic fixture and mock AI.

## 15. Remaining limitations and Run 5 prerequisites

- The generic WebView bridge is still exposed to frames on a trusted top-level origin; per-frame origin awareness remains open.
- The API key still enters through remote DOM UI before Android Keystore storage.
- Export has no import/restore companion yet.
- Full process-death restoration cannot recreate an in-memory normalized image or AI draft; interrupted work becomes an explicit recoverable error/new scan.
- Fallback journal presentation is not a second nutrient source; it may not mirror every native management affordance outside the injected layer.
- Physical camera/picker and live OpenAI behavior require device validation.

Run 5 may proceed once Run 4’s mandatory CI/emulator gates are green. Its expected scope remains speech recognition and text-to-speech lifecycle, connector/provider truthfulness, Health Connect-based provider states, app launch/setup, supported OAuth/API classification, and device validation. Voice lifecycle, provider OAuth/APIs, global health export, bridge redesign, release signing and Play publication were intentionally unchanged.
