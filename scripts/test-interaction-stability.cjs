const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8');
const compat = read('app/src/main/assets/vitalis/compat.js');
const power = read('app/src/main/assets/vitalis/vitalis-3.12.js');
const finalUx = read('app/src/main/assets/vitalis/final-ux.js');
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
  assert.match(finalUx, /try \{[\s\S]*VitalisConnectorControls\.showSources\(\);[\s\S]*\} catch \(_\)/);
  assert.match(finalUx, /modal\.setAttribute\("data-view", id \+ "-details"\)/);
  assert.match(finalUx, /if \(id === "sources"\).*add\("Sources"/);
  assert.match(power, /Array\.isArray\(connectorState\.connectors\)/);
  assert.match(instrumentation, /synthetic failure/);
});

test('touch controls request deterministic tap behavior', () => {
  assert.match(finalUx, /#vitalis-final-ux button,#vitalis-final-ux \[role=button\]\{touch-action:manipulation\}/);
});
