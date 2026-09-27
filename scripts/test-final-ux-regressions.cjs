const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');

const core = require('../app/src/main/assets/vitalis/final-ux-core.js');
const ux = fs.readFileSync('app/src/main/assets/vitalis/final-ux.js', 'utf8');
const activity = fs.readFileSync('app/src/main/java/com/vitalis/healthos/MainActivity.kt', 'utf8');
const compat = fs.readFileSync('app/src/main/assets/vitalis/compat.js', 'utf8');

test('Final UX does not mount over stabilized Run 2 to Run 6 fixtures', () => {
  assert.match(ux, /run\(\?:2\|3\|4\|5\|6\)\[\^\/\]\*fixture/);
});

test('six named themes and six accents remain stable', () => {
  assert.deepEqual(core.themes, ['classic', 'ocean', 'dark', 'amoled', 'aurora', 'system']);
  assert.deepEqual(core.accents, ['green', 'ocean', 'aqua', 'indigo', 'violet', 'coral']);
});

test('widget ids are unique and cover every required destination', () => {
  const ids = core.widgets.map(item => item.id);
  assert.equal(new Set(ids).size, ids.length);
  for (const id of ['score','activity','heart','sleep','hydration','nutrition','recovery','mental','body','coach','quick','sources']) {
    assert.ok(ids.includes(id), id);
  }
});

test('settings sanitation preserves valid choices and repairs corrupt layout', () => {
  const value = core.sanitize({theme:'amoled', accent:'violet', order:['heart','heart','invalid'], hidden:['sleep','invalid']});
  assert.equal(value.theme, 'amoled');
  assert.equal(value.accent, 'violet');
  assert.equal(value.order[0], 'heart');
  assert.equal(new Set(value.order).size, core.widgets.length);
  assert.deepEqual(value.hidden, ['sleep']);
});

test('hide show reorder and restore defaults are deterministic', () => {
  let value = core.toggle(core.defaults(), 'sleep', false);
  assert.ok(!core.visible(value).includes('sleep'));
  value = core.toggle(value, 'sleep', true);
  assert.ok(core.visible(value).includes('sleep'));
  value = core.move(value, 'sleep', -2);
  assert.equal(value.order.indexOf('sleep'), 1);
  assert.deepEqual(core.defaults().order, core.widgets.map(item => item.id));
});

test('dashboard presets change layout only and keep complete stable order metadata', () => {
  const fitness = core.applyPreset(core.defaults(), 'fitness');
  assert.deepEqual(core.visible(fitness).slice(0, 6), ['score','activity','heart','recovery','hydration','quick']);
  assert.equal(fitness.order.length, core.widgets.length);
  assert.equal(fitness.preset, 'fitness');
});

test('data state distinguishes real zero no data error permission and unsupported', () => {
  assert.deepEqual(core.metric(0, 'DATA'), {status:'DATA', value:0});
  assert.deepEqual(core.metric(0, 'NO_DATA'), {status:'NO_DATA', value:null});
  assert.equal(core.metric(null, 'ERROR').status, 'ERROR');
  assert.equal(core.metric(null, 'NOT_AUTHORIZED').status, 'PERMISSION_REQUIRED');
  assert.equal(core.metric(null, 'UNSUPPORTED').status, 'UNSUPPORTED');
});

test('full-card navigation excludes nested actions and quick hydration is one-shot', () => {
  assert.match(ux, /event\.target\.closest\("button,input,select,\.vux-drag-handle"\)/);
  assert.match(ux, /event\.preventDefault\(\);event\.stopPropagation\(\);doAction/);
  assert.match(ux, /addWaterAmount\(amount, date\)/);
  assert.match(compat, /function addWaterAmount\(litres, selectedDate\)/);
  assert.match(compat, /function removeJournalEntry\(entryId\)/);
  for (const action of ['water','scan-meal','nutrition-manager','coach','zuri','measure','sync']) {
    assert.match(ux, new RegExp('action === "' + action + '"'), action);
  }
});

test('detail views date selection appearance and customization share one settings model', () => {
  assert.match(ux, /function openDetail\(id\)/);
  assert.match(ux, /window\.VitalisDate\.select\(this\.value\)/);
  assert.match(ux, /function openAppearance\(\)/);
  assert.match(ux, /function openCustomize\(\)/);
  assert.match(ux, /offline\.finalUx = settings/);
  assert.match(ux, /setTimeout\(function \(\) \{\s*window\.dispatchEvent\(new CustomEvent\("vitalis-local-state-changed"\)\)/);
  assert.match(ux, /ondragstart/);
  assert.match(ux, /onpointerdown/);
  assert.match(ux, /setTimeout\(function \(\) \{[\s\S]*active = true; dragged/, 'long press enables touch reordering');
  assert.match(ux, /Core\.reorder\(settings, source, target\.getAttribute\("data-sort"\)\)/);
  assert.match(ux, /sheet\.scrollBy\(0, -24\)/, 'touch drag supports edge scrolling');
});

test('responsive accessibility reduced motion and touch targets are explicit', () => {
  assert.match(ux, /min-height:48px/);
  assert.match(ux, /@media\(max-width:370px\)/);
  assert.match(ux, /@media\(min-width:1000px\)/);
  assert.match(ux, /prefers-reduced-motion:reduce/);
  assert.match(ux, /aria-label='Ouvrir le détail/);
});

test('Final UX injection follows all stabilized security layers', () => {
  const selected = activity.indexOf('vitalis/selected-date.js');
  const compatIndex = activity.indexOf('vitalis/compat.js');
  const run6 = activity.indexOf('vitalis/vitalis-3.12.js');
  const finalUx = activity.indexOf('vitalis/final-ux.js');
  assert.ok(selected < compatIndex && compatIndex < run6 && run6 < finalUx);
  assert.match(activity, /if \(!stabilizedFixture\) productionLayers\.addAll/);
  assert.match(activity, /OriginBridgePolicy\.allowedOriginRules\(\)/);
  assert.match(activity, /WebViewFeature\.WEB_MESSAGE_LISTENER/);
  assert.doesNotMatch(ux, /saveOpenAiKey|readOpenAiKey|clearOpenAiKey/);
});
