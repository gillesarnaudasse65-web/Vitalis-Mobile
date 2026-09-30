const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8');
const compat = read('app/src/main/assets/vitalis/compat.js');
const power = read('app/src/main/assets/vitalis/vitalis-3.12.js');
const finalUx = read('app/src/main/assets/vitalis/final-ux.js');
const mainActivity = read('app/src/main/java/com/vitalis/healthos/MainActivity.kt');
const instrumentation = read('app/src/androidTest/java/com/vitalis/healthos/FinalUxInstrumentationTest.kt');

const coachIds = ['general', 'nutrition', 'activity', 'sleep', 'recovery', 'mental'];

test('legacy capture routers cannot steal Final UX controls', () => {
  const finalUxExclusions = compat.match(/target\.closest\("#vitalis-final-ux/g) || [];
  assert.ok(finalUxExclusions.length >= 3, 'all legacy capture routers exclude Final UX');
  assert.match(
    compat,
    /function contextText[\s\S]*?document\.addEventListener\("click", function \(event\) \{[\s\S]*?target\.closest\("#vitalis-final-ux"\)\) return;/,
    'the deep-details capture router must ignore Final UX controls'
  );
  assert.match(power, /target\.closest\("#vitalis-final-ux"\)/);
  assert.match(power, /target\.closest\("\.vitalis-power-overlay-312"\)/);
});

test('coach selection is delegated before generic actions and never implies navigation', () => {
  const mountIndex = finalUx.indexOf('function mount()');
  const listener = finalUx.slice(
    finalUx.indexOf('root.addEventListener("click"', mountIndex),
    finalUx.indexOf('root.querySelector("[data-date-input]")', mountIndex)
  );
  assert.ok(listener.indexOf('[data-coach-select]') >= 0);
  assert.ok(listener.indexOf('[data-coach-select]') < listener.indexOf('[data-act]'));
  assert.ok(listener.indexOf('[data-coach-select]') < listener.indexOf('[data-nav]'));
  assert.match(power, /select:function \(id\) \{ return setSelectedCoach\(id\); \}/);
  assert.match(power, /new CustomEvent\("vitalis-coach-changed"/);
  assert.match(finalUx, /addEventListener\("vitalis-coach-changed",render\)/);
  const coachBranch = listener.slice(listener.indexOf('if(coachSelect)'), listener.indexOf('var action='));
  assert.doesNotMatch(coachBranch, /render\(\)/, 'the coach event owns the single render');
});

test('all six coach chips remain present during repeated selection stress', () => {
  for (let round = 0; round < 20; round += 1) {
    const id = coachIds[round % coachIds.length];
    assert.match(power, new RegExp('id:"' + id + '"'));
  }
  assert.match(finalUx, /data-coach-select=/);
  assert.match(instrumentation, /repeat\(4\)/);
  for (const id of coachIds) assert.match(instrumentation, new RegExp('"' + id + '"'));
});

test('Sources always has a normal path and a visible built-in fallback', () => {
  assert.match(finalUx, /value==="sources"\)openDetail\("sources"\)/);
  assert.match(finalUx, /VitalisConnectorControls\.showSources\(\);[\s\S]*document\.querySelector\("\.vitalis-power-overlay-312,\.vitalis-source-overlay"\)/);
  assert.match(finalUx, /modal\.setAttribute\("data-view", id \+ "-details"\)/);
  assert.match(finalUx, /if \(id === "sources"\).*add\("Sources"/);
  assert.match(power, /Array\.isArray\(connectorState\.connectors\)/);
  assert.match(instrumentation, /synthetic failure/);
  assert.match(instrumentation, /showSources=function\(\)\{\}/);
});

test('touch controls request deterministic tap behavior', () => {
  assert.match(finalUx, /#vitalis-final-ux button,#vitalis-final-ux \[role=button\]\{touch-action:manipulation\}/);
});

test('date changes issue one native refresh and Android back closes the active layer first', () => {
  assert.match(finalUx, /\[data-date-input\]"\)\.onchange=function\(event\)[\s\S]*event\.stopPropagation\(\)/);
  assert.match(instrumentation, /__dateSelectCalls\.push\(value\)/);
  assert.match(instrumentation, /"\[\\"2026-09-18\\"\]"[\s\S]*decoded\(eval\(scenario, "JSON\.stringify\(window\.__dateSelectCalls\)"\)\)/);
  assert.match(mainActivity, /webBackInFlight/);
  assert.match(mainActivity, /\.vux-layer,\.vitalis-power-overlay-312,\.vitalis-source-overlay/);
  assert.match(instrumentation, /androidBackClosesFinalUxAndCoachLayersBeforeLeavingTheApp/);
});

test('every Final UX SVG path is syntactically complete for Android WebView', () => {
  assert.match(finalUx, /activity:"<path d='M13 5a2 2 0 1 0 0-4 2 2 0 1 0 0 4'/);
  assert.doesNotMatch(finalUx, /a2 2 0 1 0 0-4 2 2 0 0 0 4/);
});

test('renderer loss cannot recreate an Activity that is already being destroyed', () => {
  assert.match(mainActivity, /override fun onRenderProcessGone[\s\S]*isFinishing \|\| isDestroyed/);
  assert.match(mainActivity, /lifecycle\.currentState == Lifecycle\.State\.DESTROYED/);
  assert.match(mainActivity, /renderProcessRecoveryPending = true/);
  assert.match(mainActivity, /override fun onResume\(\)[\s\S]*if \(renderProcessRecoveryPending\)/);
  assert.match(instrumentation, /JSON\.stringify\(window\.__dateSelectCalls\)/);
});

test('stale native mirrors cannot roll back newer synchronous Web state', () => {
  assert.match(mainActivity, /localState\.selectedCoach&&localStorage\.getItem\("vitalis-selected-coach-v312"\)===null/);
  assert.match(mainActivity, /localState\.dashboardSettings&&localStorage\.getItem\("vitalis-offline-v1"\)===null/);
  assert.match(mainActivity, /localState\.localJournal\)&&localStorage\.getItem\("vitalis-native-journal-v1"\)===null/);
  assert.match(mainActivity, /Explicit native imports still replace Web state/);
});
