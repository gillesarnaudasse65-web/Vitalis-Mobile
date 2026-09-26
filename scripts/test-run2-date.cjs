'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const script = fs.readFileSync('app/src/main/assets/vitalis/selected-date.js', 'utf8');

function harness(saved = '', today = '2026-09-26') {
  const calls = [], events = {}, storage = new Map();
  const input = {value: '', matches: s => s === 'input[type="date"]'};
  const label = {textContent: ''};
  const body = {attributes: {}, setAttribute(k,v) {this.attributes[k] = v;}};
  const bridge = {
    getSelectedHealthDate: () => saved,
    getTodayHealthDate: () => today,
    selectHealthDate: iso => {saved = iso; return true;},
    refreshHealthDataForDate: iso => calls.push(iso)
  };
  const context = {
    window: {VitalisAndroid: bridge},
    document: {body, querySelectorAll: s => s === 'input[type="date"]' ? [input] :
      s === '[data-vitalis-date-label]' ? [label] : [], addEventListener: (name, fn) => {events[name] = fn;}},
    localStorage: {getItem: k => storage.get(k), setItem: (k,v) => storage.set(k,v)}, Date
  };
  function load() {vm.runInNewContext(script, context);}
  load();
  return {context, calls, events, input, label, body, storage, load,
    change: iso => {input.value = iso; events.change({target:input});},
    todayClick: () => events.click({target:{getAttribute:()=>'', textContent:'Aujourd’hui',
      matches:s=>s==='[data-vitalis-today]',closest:()=>({getAttribute:()=>'',textContent:'Aujourd’hui',matches:s=>s==='[data-vitalis-today]'})}})};
}

test('historical selection, one refresh, coach switch, repeated refresh and Today', () => {
  const h = harness('2026-09-26');
  h.change('2026-09-18');
  assert.deepEqual(h.calls, ['2026-09-18']);
  assert.equal(h.label.textContent, '2026-09-18');
  h.context.window.VitalisDate.refresh();
  assert.deepEqual(h.calls, ['2026-09-18','2026-09-18']);
  h.todayClick();
  assert.deepEqual(h.calls, ['2026-09-18','2026-09-18','2026-09-26']);
  assert.equal(h.context.window.VitalisDate.get(), '2026-09-26');
});

test('reload and duplicate initialization preserve selection and one listener', () => {
  const h = harness('2026-09-18');
  assert.equal(h.input.value, '2026-09-18');
  h.load();
  h.context.window.VitalisDate.refresh();
  assert.deepEqual(h.calls, ['2026-09-18']);
  assert.equal(h.body.attributes['data-vitalis-selected-date'], '2026-09-18');
});

test('invalid, leap and boundary dates do not shift with UTC parsing', () => {
  const h = harness('2026-09-26');
  assert.equal(h.context.window.VitalisDate.set('2026-02-30'), false);
  assert.equal(h.context.window.VitalisDate.get(), '2026-09-26');
  for (const date of ['2024-02-29','2025-12-31','2027-01-01']) {
    h.change(date);
    assert.equal(h.calls.at(-1), date);
    assert.equal(h.input.value, date);
  }
});
