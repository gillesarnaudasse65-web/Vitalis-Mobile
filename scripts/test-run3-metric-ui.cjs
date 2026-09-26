'use strict';

const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');

const compat = fs.readFileSync('app/src/main/assets/vitalis/compat.js', 'utf8');
const fallback = fs.readFileSync('app/src/main/assets/vitalis/index.html', 'utf8');
const coaches = fs.readFileSync('app/src/main/assets/vitalis/vitalis-3.12.js', 'utf8');

test('classic and fallback metrics distinguish no-data authorization unsupported and errors', () => {
  for (const source of [compat, fallback]) {
    assert.match(source, /NO_DATA/);
    assert.match(source, /NOT_AUTHORIZED/);
    assert.match(source, /UNSUPPORTED/);
    assert.match(source, /Aucune donnée/);
    assert.match(source, /Non autorisé/);
  }
});

test('local coach answers consume structured metric states rather than missing-as-zero', () => {
  assert.match(coaches, /data\.metrics/);
  assert.match(coaches, /metricValue\("steps"/);
  assert.match(coaches, /metricValue\("sleepMinutes"/);
  assert.match(coaches, /metricValue\("hydrationLitres"/);
});
