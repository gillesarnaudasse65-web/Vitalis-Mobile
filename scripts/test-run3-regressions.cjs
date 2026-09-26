'use strict';

const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');

const activity = fs.readFileSync(
  'app/src/main/java/com/vitalis/healthos/MainActivity.kt',
  'utf8'
);
const manifest = fs.readFileSync('app/src/main/AndroidManifest.xml', 'utf8');

test('Health Connect reads exhaust page tokens through one production pager', () => {
  assert.match(activity, /AndroidHealthConnectDataSource/);
  assert.match(activity, /readAll\(/);
  assert.doesNotMatch(
    activity,
    /client\.readRecords\(ReadRecordsRequest\([^\n]+\)\)\.records/,
    'single-page reads must not remain in MainActivity'
  );
});

test('health payload exposes explicit state and metric status instead of missing-as-zero', () => {
  assert.match(activity, /HealthConnectStateModel/);
  assert.match(activity, /HealthMetricResult/);
  assert.match(activity, /put\("healthConnectState"/);
  assert.match(activity, /put\("metrics"/);
});

test('newer selected-date requests prevent stale Health Connect responses', () => {
  assert.match(activity, /healthSyncGeneration/);
  assert.match(activity, /isCurrentHealthSync/);
  assert.match(activity, /healthSyncJob\?\.cancel\(\)/);
});

test('unused sensitive Health Connect permissions are removed with their unused readers', () => {
  for (const permission of [
    'READ_TOTAL_CALORIES_BURNED',
    'READ_HEART_RATE_VARIABILITY',
    'READ_RESPIRATORY_RATE',
    'READ_BLOOD_PRESSURE',
    'READ_BODY_TEMPERATURE',
    'READ_BODY_FAT'
  ]) {
    assert.doesNotMatch(manifest, new RegExp(permission));
  }
});
