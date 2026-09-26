'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('app/src/main/assets/vitalis/compat.js', 'utf8');

function snippet(start, end, from = 0) {
  const a = source.indexOf(start, from);
  assert.ok(a >= 0, start);
  const b = source.indexOf(end, a);
  assert.ok(b > a, end);
  return source.slice(a, b);
}

function runCaptureClick(label, inCoachOverlay) {
  const events = [];
  const context = {
    window: {VitalisDeepDetails: {open: id => events.push('deep:' + id)}, VitalisDate: {refresh: () => events.push('date:2026-09-18')}},
    nativeBridge: {refreshHealthData: () => events.push('date:today')},
    normalize: s => s.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, ''),
    document: {addEventListener: (_name, listener) => { context.listener = listener; }}
  };
  const code = snippet('  function actionFor(label) {', '  window.VitalisNativeActions = {');
  vm.runInNewContext(code, context);
  const target = {
    innerText: label, textContent: label,
    getAttribute: () => '',
    closest: selector => selector === 'button,a,[role=\'button\']' ||
      (inCoachOverlay && selector === '.vitalis-power-overlay-312') ? target : null
  };
  context.listener({target, preventDefault: () => events.push('prevent'),
    stopImmediatePropagation: () => events.push('stop')});
  return events;
}

test('Nia and Sékou coach cards reach their own handler without deep-detail interception', () => {
  for (const label of ['Nia Coach sommeil', 'Sékou Coach récupération']) {
    assert.deepEqual(runCaptureClick(label, true), [], label);
  }
});

test('the legacy refresh capture path sends the selected historical date once', () => {
  assert.deepEqual(runCaptureClick('Actualiser les données', false), ['prevent', 'stop', 'date:2026-09-18']);
});
