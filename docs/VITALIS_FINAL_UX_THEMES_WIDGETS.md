# Vitalis Final UX — themes, widgets and interaction contract

Date: 2026-09-27  
Branch: `agent/vitalis-final-ux-themes-widgets`  
Base: `86508cabed0903d26304bc8ed5266ec3e804acaf`

## Scope and invariants

This pass adds a presentation layer to the existing Vitalis application. It does not replace the Android shell, stabilized Health Connect model, nutrition store, coach IDs, voice lifecycle, connector state model, bridge policy, native API-key flow, privacy tools, or release pipeline. The application identity remains `com.vitalis.healthos`, version `3.15.0-security-release` (`20`).

The new layer is dependency-free and is injected after `selected-date.js`, `compat.js`, and `vitalis-3.12.js`. The local fallback also loads it directly. Its settings live below `vitalis-offline-v1.finalUx`, so the existing native export/import path includes the layout without creating a second authoritative preference store.

## Design system

The reusable tokens are CSS custom properties in `final-ux.js`.

| Family | Tokens / roles |
|---|---|
| Color | background, surface, elevated, subtle, primary, secondary, accent, primary/secondary/muted text, success, warning, error, information, border, two chart colors |
| Type | responsive display and hero metrics, section/widget headings, body, caption, and button weights/sizes |
| Space | `xs`, `sm`, `md`, `lg`, `xl` |
| Shape | compact, card, hero, and pill radii |
| Elevation | flat, card, and floating treatments |
| Motion | fast 120 ms, standard 220 ms, emphasized 360 ms; OS and explicit reduced-motion overrides |

One outlined SVG icon family covers score, activity, heart, sleep, hydration, nutrition, recovery, mind, body, coach, sources, sync, settings, calendar, customization, and navigation.

## Themes and accents

| Theme | Intent | Effective behavior |
|---|---|---|
| Classic | Bright, familiar green Vitalis identity | Safe light fallback |
| Ocean | Calm blue/teal health technology | Light surfaces with restrained saturation |
| Dark | Charcoal night presentation | High-contrast off-white text and elevated cards |
| AMOLED | True-black OLED presentation | Near-black surfaces, thin borders, minimal shadow |
| Aurora | Blue/teal/violet premium atmosphere | Controlled translucent depth without low-contrast text |
| System | Follow Android/browser preference | Maps dynamically to Classic or Dark using `prefers-color-scheme` |

Accents are Vitalis Green, Ocean Blue, Aqua, Indigo, Violet, and Coral. Theme, accent, explicit reduced motion, density, hidden widgets, widget order, preset, and manual hydration adjustments use the same versioned settings object. Corrupt or unknown values are sanitized to safe defaults.

## Dashboard information architecture

The dashboard has a compact date/settings header, daily context, score hero, responsive health grid, coach guidance, quick actions, source status, and thumb-reachable bottom navigation. Standard phones use a practical two-column metric grid; screens up to 370 px collapse to one column; 700 px and 1000 px breakpoints provide three- and four-column layouts; short landscape screens reduce vertical expansion.

### Stable widget IDs

| ID | Widget | Primary presentation | Full-card destination |
|---|---|---|---|
| `score` | Score Vitalis | Existing score, ring, component availability | Score details |
| `activity` | Activity | Steps, distance, active calories, progress, trend | Activity details |
| `heart` | Heart | Average/min/max, optional SpO₂ and HRV, trend | Heart details |
| `sleep` | Sleep | Duration, main interval, trend | Sleep details |
| `hydration` | Hydration | Intake, 2.5 L target, progress, trend | Hydration details |
| `nutrition` | Nutrition | Calories and macro summary | Nutrition day details |
| `recovery` | Recovery | Existing supported indicator only | Recovery details |
| `mental` | Mental wellbeing | Latest check-in and existing Zuri route | Wellbeing details |
| `body` | Body | Weight, body fat where available, trend | Body metrics |
| `coach` | Coach | Current stable coach | Existing coach conversation |
| `quick` | Quick actions | Five real actions | Per-action destination |
| `sources` | Health sources | Truthful status, sync time, source summary | Existing sources/connectors view |

No widget creates a clinical score or new recovery algorithm. Missing values remain unavailable and are never converted into display zeroes.

## Click map and nested actions

| Surface | Action |
|---|---|
| Date arrows / date pill | Previous/next date or native date picker |
| Score, Activity, Heart, Sleep, Recovery, Mental, Body | Matching detail view |
| Hydration card | Hydration details |
| Hydration `+150`, `+250`, `+500` | Immediate dated journal addition with Undo |
| Nutrition card | Nutrition day details |
| Nutrition Scanner / Meals | Stabilized Run 4 scanner / meal manager |
| Coach card | Existing selected coach |
| Health Connect card | Existing truthful sources view |
| Quick actions | Scanner, coach, water, measurement, or health refresh |
| Mini chart area | Parent metric detail view |

The root action listener stops propagation before executing nested actions. Card listeners ignore buttons, inputs, selects, and drag handles. Keyboard Enter/Space operates focused cards. Press scale/opacity, selected states, drag lift, drop target, and focus rings provide immediate feedback.

## Detail views

Score, Activity, Heart, Sleep, Hydration, Nutrition, Recovery, Mental wellbeing, and Body use one modal detail pattern: Back, metric/date header, Today shortcut, hero metric, trend or explicit insufficient-history state, supporting measurements, and source. The authoritative Run 2 selected date is read on entry and is not reset on return, theme changes, or widget reordering.

Coach and Sources deliberately route to their existing stabilized overlays instead of duplicating those flows.

## Quick actions

The action grid is capped at five visible actions. Every control has a real implementation: Scan Meal, Talk to Coach, Add 250 ml, Add Measurement, and Sync Health. The water action records exactly once and exposes Undo; the scanner continues to use the Run 4 native capture/picker path.

## Customization and presets

Users may show/hide each widget, choose comfortable or compact density, reorder, apply a preset, or restore defaults. Desktop/mouse reordering uses HTML drag/drop. Touch reordering starts after a 420 ms long press on the dedicated handle, shows lifted/drop states, and saves on drop. Screen-reader and keyboard-accessible Move Up/Move Down buttons are always available.

Presets are Balanced, Fitness, Recovery, Nutrition, and Minimal. Applying one changes order and visibility only; health, meal, coach, and journal data are untouched.

## Data states

| State | Rendering contract |
|---|---|
| `DATA` | Render the real value, including a legitimate numeric zero |
| `NO_DATA` | Em dash plus a metric-specific empty explanation |
| `ERROR` | “Lecture impossible”; visually distinct error border |
| `PERMISSION_REQUIRED` | Permission explanation; visually distinct warning border |
| `UNSUPPORTED` | Explicit “Non pris en charge” |
| `LOADING` | Lightweight “Chargement…” placeholder; never a stale zero |

Health source labels remain evidence-based: Ready, Partial permissions, No data, Permission required, or read error. Offline mode adds an explicit cached-data banner and retains the last-updated/source timestamp.

## Accessibility and responsive behavior

Interactive dashboard controls target approximately 48 dp, use labels or visible text, preserve focus outlines, and never communicate state by color alone. Charts have text alternatives. Responsive type uses `clamp()` and flexible cards rather than fixed desktop dimensions. Reduced motion follows the OS and can also be enabled in Appearance. High-contrast text tokens are defined independently for light, dark, AMOLED, and Aurora surfaces.

The emulator suite covers the narrow 360 px presentation. Physical-device font scaling, orientation, touch drag, ripple feel, and assistive-technology acceptance remain external gates.

## Performance

No framework, animation library, polling timer, dashboard MutationObserver, or external theme resource was added. Theme changes update root attributes synchronously. Rendering and theme-switch durations are exposed through `VitalisFinalUX.snapshot().metrics`; the API 35 fixture asserts that measurements exist and writes them with the screenshot evidence. Dashboard metric updates currently rerender the visible widget grid, which is acceptable for twelve small dependency-free widgets but remains the main optimization opportunity if live update frequency grows.

## Automated evidence

`scripts/test-final-ux-regressions.cjs` covers all six themes, all accents, stable unique IDs, sanitation, show/hide, reorder, long-press/drag contracts, presets, restore defaults, data-state semantics, nested-action propagation, date/settings integration, responsive/accessibility contracts, injection order, and security-sensitive absence of API-key DOM methods.

`FinalUxInstrumentationTest` uses deterministic non-personal data to exercise the score hero, metric cards, detail round trips, one-shot `+250 ml`, date preservation, theme and accent persistence across recreation, order persistence, hide/restore, state rendering, responsive presentation, and screenshot capture. Existing Run 1–6 suites remain in the same workflow.

## Screenshot evidence

The API 35 emulator produces: Classic, Ocean, Dark, AMOLED, and Aurora dashboards; Score, Activity, Sleep, and Nutrition details; Coach card; Health Connect status; Customize Dashboard; Appearance Settings; empty state; error state; and small-screen view. CI pulls these non-personal fixture images from the target app and retains them with instrumentation reports.

## Security and privacy preservation

The Final UX layer has no API-key input or secret API. Settings opens the native privacy/data control through the existing origin-aware bridge. It does not alter exact trusted-origin rules, iframe blocking, SSL cancellation, release debugging, encrypted key storage, or import/export behavior. The CI continues to run Run 6 security/privacy tests and the release smoke.

## Remaining issues

- Physical Android device validation is not available in this run.
- Production signing still depends on repository secrets; absent credentials produce an explicitly test-signed release in CI.
- Legal privacy-document review and final release-candidate regression remain external gates.
- Detail histories are intentionally lightweight; insufficient data produces no chart rather than interpolation.

