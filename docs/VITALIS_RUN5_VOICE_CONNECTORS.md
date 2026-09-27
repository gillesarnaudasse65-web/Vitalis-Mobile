# Vitalis Run 5 — Voice lifecycle and connector truthfulness

Date: 2026-09-27  
Branch: `agent/vitalis-run5-voice-connectors`  
Base: merged `main` `7cedb327d1d48aaf277b90c0626d8071778e7e63`

## 1. Prerequisite merges

Run 3 PR #8 was rechecked at `c996458971133c653d11347939668ff3969cb251`: workflow run 70 was green, and the PR was merged as `7e8bc89c1e0ade723fa3edaffba6f39a05d1f2a5`. Run 4 PR #9 was retargeted to `main`; an unchanged-tree commit forced workflow run 77 against the new base. Wrapper, JavaScript, JVM, lint, both APK assemblies and all four API 35 tests passed, then PR #9 was merged as `7cedb327d1d48aaf277b90c0626d8071778e7e63`.

Run 5 was created directly from that latest main SHA. It therefore contains the Run 1 security foundation, Run 2 coach/date corrections, Run 3 Health Connect reliability and Run 4 nutrition reliability.

## 2. Previous voice architecture

The former recognition path used `microphoneEnabled` as both user preference and runtime ownership. One shared `SpeechRecognizer` restarted after every final result and after nearly every non-permission error, with no retry ceiling. Results had only a `partial` boolean, no stable session ID and no native final-consumed guard. Activity destruction stopped recognition, but backgrounding, overlay closure and page hiding did not reliably stop both recognition and speech.

The former TTS path had a readiness boolean and used `QUEUE_FLUSH`, but timestamp utterance IDs were not owned by a state model. Old callbacks could report after a replacement utterance. Locale fallback, stop and shutdown existed without stable state or callback ownership.

Two current JavaScript overlays listened to the same native event. They filled only their own active input and did not auto-submit, but native duplicate finals were not prevented.

## 3. Recognition state model

`RecognitionSessionCoordinator` now owns a stable session ID and these states:

- `IDLE`
- `REQUESTING_PERMISSION`
- `READY`
- `LISTENING`
- `PROCESSING_FINAL`
- `STOPPING`
- `CANCELLED`
- `ERROR`
- `UNAVAILABLE`

Every platform callback closes over the session that created its recognizer and is rejected when a newer session owns the microphone. `finalResultConsumed` permits one final result only. Partial output is labelled `PARTIAL` and is display-only; only `FINAL` fills the coach input. Neither kind is automatically submitted.

## 4. Microphone lifecycle and stop policy

Recognition starts only from an explicit JavaScript bridge action. Permission is checked first, the recognizer starts once, and a valid final result ends the one-shot session. There is no continuous restart after a successful result.

Recognition is cancelled and returned to idle on:

- explicit Stop;
- coach overlay close or backdrop dismissal;
- replacement by another overlay/session;
- `pagehide` or hidden document;
- activity `onStop`/background;
- activity destruction.

Foreground resume does not restart the microphone. Permission denial and permanent denial are distinct machine-readable errors. No audio file is created or stored.

## 5. Bounded retry policy

The exact retry ceiling is one transient retry per session.

| Error class | Android examples | Policy |
|---|---|---|
| Recoverable | network, timeout, audio | Retry once, then end in error |
| Service unavailable | server, recognizer busy | Retry once, then end in error |
| No speech | no match, speech timeout | Return to idle; no retry |
| Permission | insufficient permission | Stop; no retry |
| Fatal/session ending | client/unknown | Stop; no retry |

Pending retry callbacks are removed when the user stops, the activity backgrounds, the activity is destroyed or a newer session starts.

## 6. TTS state model and lifecycle

`TtsSessionCoordinator` exposes:

- `TTS_UNINITIALIZED`
- `TTS_READY`
- `TTS_SPEAKING`
- `TTS_STOPPED`
- `TTS_ERROR`

Every utterance has a UUID. New responses use the existing replace policy (`QUEUE_FLUSH`) and immediately become the only callback owner; completion/error callbacks from replaced utterances are ignored. Explicit stop, overlay close, page hide, `onStop` and destruction stop speech. Destruction also shuts down the engine and clears the session.

The requested locale is used when supported. An unsupported locale falls back once to French; if French is also unavailable, a controlled `unsupported_locale` error is emitted.

## 7. Voice tests

`VoiceReliabilityTest` provides 23 deterministic JVM tests for permission states, unavailable recognition, partial/final behavior, duplicate finals, cancellation, background, newer-session ownership, retry/error classes, TTS initialization, speak, replacement, stop, error and destruction.

`Run5VoiceConnectorWebViewTest` uses no microphone service or TTS engine. A debug-only deterministic fixture exercises the production coordinators and the production injected JavaScript, verifies partial-versus-final UI handling, recreates the activity and checks that the microphone remains off.

## 8. Connector catalogue and capability model

`ConnectorCatalog` remains the single native source of truth with 32 unique stable IDs and unique package aliases. The fallback page no longer contains its former conflicting 15-name list; it renders the native catalogue payload.

Capability is independent from runtime state:

- `HEALTH_CONNECT`: 8 entries;
- `APP_SETUP_ONLY`: 11 entries;
- `DIRECT_OAUTH`: 12 entries, all explicitly not implemented;
- `UNSUPPORTED_PLATFORM`: Apple Health;
- `DIRECT_API` and `UNAVAILABLE`: modelled for future honest classification, with no fabricated integration.

Runtime states are `NOT_INSTALLED`, `INSTALLED`, `SETUP_REQUIRED`, `HEALTH_CONNECT_PERMISSION_REQUIRED`, `HEALTH_CONNECT_AVAILABLE_NO_DATA`, `HEALTH_CONNECT_DATA_AVAILABLE`, `DIRECT_AUTH_REQUIRED`, `DIRECT_AUTHENTICATED`, `API_UNAVAILABLE`, `UNSUPPORTED`, and `UNAVAILABLE`.

## 9. Truthful Health Connect provider logic

Installation is never sufficient for a Connected label. The resolver uses four explicit evidence inputs: provider installation, Health Connect availability, Vitalis permission and attributed provider records.

For a Health Connect provider:

- attributed source records produce `HEALTH_CONNECT_DATA_AVAILABLE`;
- missing Health Connect produces `UNAVAILABLE`;
- missing Vitalis permission produces `HEALTH_CONNECT_PERMISSION_REQUIRED`;
- missing provider app produces `NOT_INSTALLED`;
- permission plus installed provider but no attributed records produces `HEALTH_CONNECT_AVAILABLE_NO_DATA`.

The cautious user label is “Accès Health Connect activé, aucune donnée fournisseur détectée”, not “Connected”. Unknown source packages discovered in actual Health Connect records are dynamically represented as data-available sources.

## 10. App setup and resume behavior

Installed provider applications are launched with their package launch intent. Launch failure and missing applications fall back first to the Play Store app URI, then to the trusted HTTPS Play Store search. Failure of both produces a controlled error.

Opening an application is described only as configuration. Health Connect-capable providers instruct the user to enable sharing in the provider app and return to Vitalis. `onResume` performs a delayed Health Connect/status refresh after a successful provider launch; it never changes the status merely because the app was opened.

## 11. Direct OAuth/API classification

Garmin, Huawei, Strava, Oura, WHOOP, YAZIO, Cronometer, Lifesum, TrainingPeaks, Komoot, Suunto and COROS are classified as future direct OAuth candidates, with `directIntegrationImplemented=false`. No OAuth button claims authentication. The UI states “Connexion directe non implémentée”.

Future implementation requires a provider-approved developer account, client ID, redirect URI, authorization-code exchange, refresh tokens, scopes, secure token storage and compliance with each provider’s API policy. No client ID, secret, token or fabricated authentication was added.

## 12. Apple Health

Apple Health is `UNSUPPORTED_PLATFORM` / `UNSUPPORTED` on Android. Its action remains disabled and its user-facing state is “Non pris en charge sur Android”. Vitalis does not show a direct activation path.

## 13. Security and privacy regression

- The microphone starts only after explicit user action.
- No always-on or background listening exists.
- Audio is not persisted.
- Transcripts are not logged by default.
- Trusted-origin registration, SSL cancellation and external-link rules are unchanged.
- Existing bridge input, Health Connect and nutrition URI controls remain intact.
- Provider launches use explicit installed-package intents or trusted store destinations.
- No provider credential storage exists because no direct integration is implemented.

## 14. Validation and device boundaries

Local JavaScript/source contracts pass 27/27. Local Gradle remains blocked because the execution environment cannot reach the Gradle distribution host. CI provides the authoritative wrapper, JDK 17, JVM, lint, build and emulator execution.

The automated voice fixture is deterministic and does not validate Android’s live recognition service, microphone acoustics, a physical TTS engine, OEM background behavior or real provider applications/accounts. Those physical-device scenarios remain `BLOCKED / NOT TESTED` and must not be inferred from JVM/emulator success.

## 15. CI evidence

GitHub Actions workflow run 82 (`36284004949`) passed at source head `c93aceb7021271ca3aa6368bf5178ee467d65598`:

| Gate | Result |
|---|---|
| JavaScript source contracts | 27/27 passed |
| Full JVM suite | 74/74 passed |
| Voice reliability JVM suite | 23/23 passed |
| Connector catalogue JVM suite | 12/12 passed |
| Android lint | 0 errors, 32 existing warnings; no increase from the Run 4 baseline |
| Debug application and instrumentation APKs | Built successfully |
| API 35 emulator | 5/5 passed, including the deterministic Run 5 voice/connectors fixture and Run 1–4 regressions |

Retained workflow artifacts are `Vitalis-unit-lint-reports` (`10919274964`), `Vitalis-debug-apks` (`10920310602`) and `Vitalis-instrumentation-reports` (`10920375699`), expiring 2026-12-26 under the workflow retention policy.

Two preceding CI attempts exposed test-harness defects rather than ignored failures. Run 80 proved the Run 5 emulator scenario passed but caught a Run 2 date fixture crossing midnight UTC. Run 81 proved that date isolation and Run 5 passed, then caught coordinate/focus variance in the Run 2 DOM-delegation assertion. The fixture now starts from its injected clock while recreation preserves its selected date; the delegation-only assertion uses a DOM click while the surrounding catalogue scenarios retain real pointer injection. Run 82 validates both corrections.

## 16. Remaining blockers and Run 6 prerequisites

- Real microphone permission/grant/revoke and speech accuracy on a physical device.
- Real TTS engine/locale behavior and hardware stop/resume.
- Real Health Connect/provider installation, sharing, return and attribution.
- Direct provider OAuth/API implementations remain absent by design.
- Per-frame bridge caller-origin isolation and remote-DOM API-key entry remain open.
- Nutrition import, global Vitalis export/delete, release signing, AAB, upgrade and rollback validation remain open.

Run 6 may begin: the final Run 5 source CI is green. Its scope is the remaining WebView/bridge hardening, moving key entry out of the remote DOM, complete privacy/data controls, global export/delete, signing foundation and release/AAB qualification.
