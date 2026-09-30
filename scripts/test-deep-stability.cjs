const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8');
const activity = read('app/src/main/java/com/vitalis/healthos/MainActivity.kt');
const nutrition = read('app/src/main/java/com/vitalis/healthos/ActiveNutritionImageCache.kt');
const connectors = read('app/src/main/java/com/vitalis/healthos/ConnectorCatalog.kt');
const manifest = read('app/src/main/AndroidManifest.xml');
const power = read('app/src/main/assets/vitalis/vitalis-3.12.js');
const finalUx = read('app/src/main/assets/vitalis/final-ux.js');
const local = read('app/src/main/assets/vitalis/index.html');
const workflow = read('.github/workflows/build-apk.yml');
const core = require('../app/src/main/assets/vitalis/final-ux-core.js');

const coaches = [
  ['general', 'Kofi', 'kofi.webp'],
  ['nutrition', 'Ama', 'ama.webp'],
  ['activity', 'Ayo', 'ayo.webp'],
  ['sleep', 'Nia', 'nia.webp'],
  ['recovery', 'Sékou', 'sekou.webp'],
  ['mental', 'Zuri', 'zuri.webp']
];

test('all six coach portraits are valid bundled WebP assets with exact case', () => {
  for (const [, , file] of coaches) {
    const bytes = fs.readFileSync(path.join(root, 'app/src/main/assets/vitalis/coaches', file));
    assert.equal(bytes.subarray(0, 4).toString('ascii'), 'RIFF', file);
    assert.equal(bytes.subarray(8, 12).toString('ascii'), 'WEBP', file);
    assert.ok(bytes.length > 1_000, file);
  }
});

test('coach images use Android bundled assets first and deterministic avatars on failure', () => {
  const base = 'https://appassets.androidplatform.net/assets/vitalis/coaches/';
  assert.match(power, new RegExp('var ASSET_BASE = "' + base.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '"'));
  assert.match(finalUx, new RegExp('var base = "' + base.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '"'));
  assert.match(power, /function coachAvatarDataUrl/);
  assert.match(power, /data-vitalis-avatar-fallback/);
  assert.match(power, /VitalisImageReliability/);
  assert.doesNotMatch(finalUx, /location\.origin \+ "\/__vitalis\/coaches\//);
});

test('native local state hydrates before coach and dashboard JavaScript initialize', () => {
  assert.match(activity, /val scripts = mutableListOf\(buildNativeProxyBootstrap\(\)\)[\s\S]*scripts\.addAll\(loadedLayers\)/);
  assert.match(activity, /__vitalisHydratedNativeLocalState=true/);
  assert.match(activity, /localState\.selectedCoach[\s\S]*vitalis-selected-coach-v312/);
  assert.match(activity, /localState\.dashboardSettings[\s\S]*vitalis-offline-v1/);
  assert.match(activity, /preferences\.containsKey\(VitalisLocalDataStore\.LOCAL_JOURNAL_KEY\)/);
  assert.match(activity, /if\(Array\.isArray\(localState\.localJournal\)\)/);
});

test('active nutrition image survives recreation through a private expiring cache', () => {
  assert.match(nutrition, /Duration\.ofHours\(24\)/);
  assert.match(nutrition, /\.normalized\.jpg/);
  assert.match(activity, /activeNutritionImageCache\.save\(scanId, image\.dataUrl\)/);
  assert.match(activity, /activeNutritionImageCache\.load\(scanId\)/);
  assert.match(activity, /ACTIVE_SCAN_ASSET_PATH/);
  assert.match(activity, /draftResult/);
  assert.match(activity, /normalizedImageMetadata/);
});

test('temporary nutrition photos are removed on cancel save and local-data deletion', () => {
  const deletes = activity.match(/activeNutritionImageCache\.delete\(/g) || [];
  assert.ok(deletes.length >= 3, 'cancel, supersede and save delete paths');
  assert.match(activity, /activeNutritionImageCache\.clear\(\)/);
  assert.match(activity, /NutritionScanStatus\.SAVED,[\s\S]*NutritionScanStatus\.CANCELLED[\s\S]*remove\(PENDING_NUTRITION_SCAN_KEY\)/);
});

test('legacy object URLs are revoked on replacement error and close', () => {
  assert.match(local, /URL\.createObjectURL/);
  const revokes = local.match(/URL\.revokeObjectURL/g) || [];
  assert.ok(revokes.length >= 3);
  assert.match(local, /Aperçu indisponible/);
});

test('startup readiness requires every critical layer and has bounded native recovery', () => {
  for (const marker of [
    '__vitalisSelectedDateRun2', '__vitalisNativeCompatibility',
    '__vitalisConnectorVoiceControls', '__vitalisPowerLayer312', '__vitalisFinalUx'
  ]) assert.match(activity, new RegExp(marker.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + " === 'ready'"));
  assert.match(activity, /FINAL_UX_RECOVERY_ATTEMPTS = 3/);
  assert.match(activity, /loadOfflineFallback\(\)/);
  assert.match(activity, /onRenderProcessGone/);
});

test('every connector package is visible to Android package queries', () => {
  const catalogPackages = [...connectors.matchAll(/"([a-z][a-z0-9_]*(?:\.[A-Za-z0-9_]+)+)"/g)]
    .map(match => match[1])
    .filter(value => !value.includes('Developer account'));
  for (const packageName of new Set(catalogPackages)) {
    assert.match(manifest, new RegExp('<package android:name="' + packageName.replaceAll('.', '\\.') + '"'));
  }
});

test('connector and Health Connect states are evidence based and partial-safe', () => {
  assert.match(connectors, /HEALTH_CONNECT_PARTIAL_PERMISSION/);
  assert.match(connectors, /providerRecordsDetected -> ConnectorRuntimeState\.HEALTH_CONNECT_DATA_AVAILABLE/);
  assert.match(activity, /providerRecordsDetected = detected != null/);
  assert.match(activity, /it != applicationContext\.packageName/);
  assert.match(activity, /HealthConnectUiStateResolver\.resolve/);
  assert.doesNotMatch(power, />Connected</);
});

test('widget settings withstand repeated reorder hide show and theme operations', () => {
  let state = core.defaults();
  const ids = core.widgets.map(item => item.id);
  for (let index = 0; index < 20; index += 1) {
    const id = ids[index % ids.length];
    state = core.move(state, id, index % 2 ? -1 : 1);
    state = core.toggle(state, id, index % 3 !== 0);
    state.theme = core.themes[index % core.themes.length];
    state = core.sanitize(state);
    assert.equal(new Set(state.order).size, ids.length);
    assert.ok(state.hidden.every(item => ids.includes(item)));
  }
});

test('deep stability QA artifact remains explicitly test signed', () => {
  assert.match(workflow, /Vitalis-3\.15\.0-rc1-deep-stability-qa/);
  assert.match(workflow, /Signing label: test-signed/);
  assert.match(workflow, /NOT FOR PRODUCTION DISTRIBUTION/);
});
