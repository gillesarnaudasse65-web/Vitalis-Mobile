# Vitalis Run 3 — Health Connect reliability

Date: 2026-09-26. Repository `gillesarnaudasse65-web/Vitalis-Mobile`. Delivery branch `agent/vitalis-run3-health-connect-reliability`, based on merged `main` commit `2187e053ea30d78e893abbd7f72de74697e6e3cc` (Run 1 and Run 2). The application identity and version remain `com.vitalis.healthos`, `3.14.0-coaches-connectors`, versionCode `19`.

## Preconditions and scope

Run 1 PR #6 passed its required CI and was merged as `3b9760a4dab91db6a1661a2aced2385d5b7d9210`. Run 2 PR #7 was retargeted to `main`, rerun against the merged base, passed its required CI and was merged as `2187e053ea30d78e893abbd7f72de74697e6e3cc`. Run 3 begins from that exact commit, so its tests and source include both earlier runs.

This run hardens the foreground Health Connect read path. It does not add Health Connect writes, background synchronization, medical interpretation, a new data provider, release signing or Play publication. It does not silently broaden the permission surface: six declared/requested read permissions that had no reader are removed.

## Reproduction before correction

The regression contract was committed before the implementation. Against the merged Run 2 source, `node --test scripts/test-run3-regressions.cjs` failed all four tests:

| Failing contract | Run 2 behavior | Risk addressed |
|---|---|---|
| Complete paging | Each of the 20 Health Connect queries consumed only `readRecords(...).records` | Later pages were omitted |
| Explicit state and metric semantics | Empty raw records became numeric zero for several metrics | No-data, valid zero and lack of authorization were indistinguishable |
| Latest-request ownership | Concurrent refreshes had no generation/cancellation guard | An older date response could overwrite a newer selection |
| Permission/read alignment | Six read permissions were requested without any corresponding reader | Unnecessary sensitive permission surface |

The final source-contract suites retain these checks. The Kotlin unit suite supplies behavioral coverage for the extracted state, interval, paging, deduplication and generation components.

## Runtime architecture

`MainActivity` remains the Android/WebView coordinator. Health-specific reliability logic is separated into two files:

- `HealthConnectReliability.kt` defines availability, permission/sync states, per-metric status, local-day intervals, the generic all-page reader and request-generation ownership.
- `AndroidHealthConnectDataSource.kt` adapts the generic reader to Health Connect 1.1.0 `ReadRecordsRequest`, carrying each `pageToken` until the response token is null or blank.

Each foreground refresh performs this sequence:

1. Allocate a monotonically increasing generation and cancel the previous refresh job.
2. Re-evaluate provider availability instead of trusting startup state.
3. Re-read granted permissions and resolve the explicit Health Connect state.
4. Query only record types whose read permission is currently granted; partial grants continue to return available metrics.
5. Read every page for both the recent-source window and the selected local day.
6. Deduplicate records by stable Health Connect metadata ID before aggregation; if an ID is unavailable, use a documented composite of record type, data origin and record representation.
7. Build structured metric results and a top-level state payload.
8. Dispatch only if the generation is still current. Cancellation is rethrown rather than converted into an error payload.

A repeated nonblank page token terminates with an explicit error rather than looping indefinitely. A failure on any later page is propagated, so a partial page set is not presented as complete. If the same stable record ID occurs again, the later occurrence replaces the earlier value while preserving deterministic insertion order. Equal-valued records with distinct IDs are retained.

## State and UI contract

The `healthConnectState.code` field uses this finite set:

| Code | Meaning | Retry/action |
|---|---|---|
| `NOT_SUPPORTED` | Health Connect is unavailable on this platform | None |
| `PROVIDER_NOT_INSTALLED` | A provider package is required | Install provider |
| `PROVIDER_UPDATE_REQUIRED` | The installed provider cannot supply the required SDK level | Update provider |
| `PERMISSION_NOT_REQUESTED` | No request has yet been made and the app was never authorized | Request permissions |
| `PERMISSION_DENIED` | A request was denied, or all previously granted access was revoked | Request permissions; reason distinguishes denied/revoked |
| `PARTIAL_PERMISSION` | At least one required permission is granted and at least one is missing | Read granted metrics and offer the missing permissions |
| `AUTHORIZED_NO_DATA` | All required permissions are granted but the selected interval has no supported records | No fabricated value |
| `AUTHORIZED_WITH_DATA` | At least one supported metric contains records | Display structured results |
| `SYNC_ERROR` | Availability, permission enumeration or record reading failed | Retry; do not reuse a partial current result |

Each entry in `metrics` has one of `DATA`, `NO_DATA`, `NOT_AUTHORIZED`, `UNSUPPORTED` or `ERROR`, plus its unit. A `DATA` entry also carries `value`, `sampleCount` and `sourceCount`. Numeric zero is valid only when at least one record produced it. `NO_DATA`, `NOT_AUTHORIZED`, `UNSUPPORTED` and `ERROR` carry no numeric substitute. The classic remote compatibility layer and bundled fallback render the corresponding French state instead of coercing a missing value through `Number(value || 0)`. Local coach prompts also read only `DATA` values.

The existing scalar payload keys remain for compatibility, but no-record scalars are JSON null. The structured `metrics` object is authoritative for absence and authorization semantics.

## Permission and metric matrix

All ten implemented record types use the same complete-page, metadata-ID deduplication path in both the rolling 30-day source scan and selected-day query.

| Metric | Manifest/runtime permission | Health Connect record | Selected-day result | Run 3 decision |
|---|---|---|---|---|
| Steps | `READ_STEPS` | `StepsRecord` | Count sum | Kept; paged and deduplicated |
| Distance | `READ_DISTANCE` | `DistanceRecord` | Kilometre sum | Kept; paged and deduplicated |
| Total calories | None | None | `UNSUPPORTED` | Unused `READ_TOTAL_CALORIES_BURNED` removed |
| Active calories | `READ_ACTIVE_CALORIES_BURNED` | `ActiveCaloriesBurnedRecord` | Kilocalorie sum | Kept; paged and deduplicated |
| Exercise | `READ_EXERCISE` | `ExerciseSessionRecord` | Session-duration sum | Kept; paged and deduplicated |
| Sleep | `READ_SLEEP` | `SleepSessionRecord` | Session-duration sum | Kept; paged and deduplicated |
| Heart rate | `READ_HEART_RATE` | `HeartRateRecord` | Sample arithmetic mean | Kept; paged and deduplicated |
| HRV | None | None | `UNSUPPORTED` | Unused `READ_HEART_RATE_VARIABILITY` removed |
| Respiratory rate | None | None | `UNSUPPORTED` | Unused `READ_RESPIRATORY_RATE` removed |
| Oxygen saturation | `READ_OXYGEN_SATURATION` | `OxygenSaturationRecord` | Latest chronological value | Kept; paged and deduplicated |
| Blood pressure | None | None | `UNSUPPORTED` | Unused `READ_BLOOD_PRESSURE` removed |
| Body temperature | None | None | `UNSUPPORTED` | Unused `READ_BODY_TEMPERATURE` removed |
| Weight | `READ_WEIGHT` | `WeightRecord` | Latest chronological value | Kept; paged and deduplicated |
| Body fat | None | None | `UNSUPPORTED` | Unused `READ_BODY_FAT` removed |
| Hydration | `READ_HYDRATION` | `HydrationRecord` | Litre sum | Kept; paged and deduplicated |
| Nutrition | `READ_NUTRITION` | `NutritionRecord` | Nutrient sums plus existing local manual meals | Kept; paged and deduplicated |

The removed types are not represented as `NO_DATA`: they are explicitly `UNSUPPORTED` because the application has no reader for them. This prevents the UI from implying either authorization or a measured value.

## Selected-day and timezone semantics

The selected day is a calendar date in the device's current `ZoneId`. Its query interval is `[date.atStartOfDay(zone), (date + 1).atStartOfDay(zone))`. The end is exclusive and is not hard-coded to 24 hours. This produces 23 hours across the Europe/Paris spring transition and 25 hours across the autumn transition, while normal, leap-day, month-end and year-end dates advance to the correct next local midnight.

Today's interval also ends at the following local midnight rather than being capped in the interval model. Health Connect naturally returns only records that exist at read time. `periodHours` reflects the actual local interval duration. The Run 2 native selected-date persistence remains authoritative through refreshes and recreation.

Interval records are still aggregated using their full record durations when returned for the interval; explicit proportional clipping at the day boundary is not added in this run. That limitation is retained below.

## Concurrency and repeated synchronization

Every refresh receives a generation. Starting another refresh increments the generation and cancels the prior coroutine. Immediately before WebView dispatch, the job must still be active and own the current generation. A late response from a cancelled or slower historical request therefore cannot replace a newer selected date.

No records or totals are accumulated across refreshes. A repeated read rebuilds its page set and aggregate from scratch. The unit contract verifies that two identical syncs return identical totals and that a later total changes only when the source contains a new stable ID.

## Automated coverage

| Layer | Run 3 coverage |
|---|---|
| JavaScript/source contracts | Paging applied to all relevant calls, explicit state/metric payload, generation guard, unused permissions absent, WebView status rendering and no zero substitution |
| JVM | Provider availability; never requested/denied/partial/full/revoked permissions; normal/leap/month/year/DST intervals; one/two/many/empty-final pages; within/across-page deduplication; equal values with distinct IDs; later replacement of a stable ID; repeated-token guard; later-page permission failure; zero/no-data/auth/unsupported/error metric states; repeated sync; stale response rejection |
| Android 15 instrumentation | Existing offline smoke and Run 2 coach/date flow, plus the Run 3 production-script fixture with partial permissions, visible missing-authorization state, pointer refresh/Today actions and selected-date recreation |

The Run 3 fixture uses the real production injection and Android bridge but synthetic permission/status payloads. It deliberately does not access a user's Health Connect provider or fabricate health records. This proves the state/UI contract, not provider interoperability.

## Build, CI and artifacts

The branch is validated by the existing mandatory workflow: official wrapper validation, JDK 17, Gradle initialization, all Node source-contract tests, Android JVM tests, lint, debug/application-test APK assembly, and `connectedDebugAndroidTest` on an Android 15 Google APIs emulator. Reports and APK artifacts retain the existing 90-day expiry.

Implementation-validation [CI run #66](https://github.com/gillesarnaudasse65-web/Vitalis-Mobile/actions/runs/36270363528) at source head `1e341f220af349749635ef091b992f5eecaf15ac` passed every job: 14 JavaScript tests, 25 JVM tests, lint with 0 errors and 33 warnings (no increase from Run 2), both debug APK builds, and 3 Android 15 instrumentation tests with 0 failures, errors or skips. The instrumentation XML names the retained offline smoke, retained Run 2 coach/date flow and new Run 3 partial-permission/date flow. The final documentation-head run, artifact IDs and checksums are recorded in PR #8 and the completion report, avoiding a self-referential documentation commit.

Local Node suites and JavaScript syntax checks pass. Local Gradle execution is **BLOCKED** because this environment cannot reach the Gradle distribution host; Android results therefore come from GitHub Actions. This is an environment limitation, not recorded as a passing local build.

## Remaining limitations and required real-device checks

- **NOT TESTED on a physical device:** provider installation/update UX, real grant/deny/partial/revoke transitions, OEM Health Connect behavior, large real datasets, concurrent provider writes, source priorities and values returned by actual record implementations.
- **NOT TESTED against real Health Connect data on the emulator:** the deterministic fixture uses typed synthetic state only.
- Deduplication uses Health Connect metadata IDs. The fallback composite exists defensively but cannot guarantee equivalence across providers if a stable ID is absent.
- Raw-record aggregation does not resolve semantically overlapping records from distinct stable IDs or apply Health Connect aggregate priority rules.
- Exercise and sleep durations are not proportionally clipped when an interval record crosses the selected-day boundary.
- Heart rate remains an arithmetic mean of returned samples; it is not time-weighted.
- Hydration/nutrition Health Connect records and the existing manual stores remain distinct provenance sources.
- The foreground-only model has no scheduler, background permission or incremental changes-token synchronization.
- The remote site's independent DOM remains outside this repository. The tests validate the bundled fixture with the actual injected compatibility scripts.
- Run 1's unresolved bridge/subframe trust boundary, scanner, voice, export/deletion, release signing and publication limitations are unchanged.

## Rollback

Run 3 is isolated after base `2187e053ea30d78e893abbd7f72de74697e6e3cc`. Before merge, close PR #8 and delete only `agent/vitalis-run3-health-connect-reliability`. After merge, revert the Run 3 merge commit as one unit; do not reset or rewrite `main`, because the Run 1 and Run 2 merge commits are shared history. Removing only the Kotlin helper files without reverting their `MainActivity` callers would leave the branch uncompilable.
