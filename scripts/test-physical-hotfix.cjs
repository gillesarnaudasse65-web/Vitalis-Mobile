const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8');
const activity = read('app/src/main/java/com/vitalis/healthos/MainActivity.kt');
const voice = read('app/src/main/java/com/vitalis/healthos/VoiceReliability.kt');
const hotfix = read('app/src/main/java/com/vitalis/healthos/PhysicalHotfixReliability.kt');
const ui = read('app/src/main/assets/vitalis/vitalis-3.12.js');

test('voice fallback uses Activity Result and the existing session owner', () => {
  assert.match(activity, /voiceFallbackLauncher\s*=\s*registerForActivityResult/);
  assert.match(activity, /RecognizerIntent\.ACTION_RECOGNIZE_SPEECH/);
  assert.match(activity, /recognitionSessionCoordinator\.consumeFinal\(sessionId, finalText\)/);
  assert.match(voice, /RecognitionState\.FALLBACK/);
});

test('voice presents all required physical states and permanent denial action', () => {
  for (const label of [
    'Permission required', 'Starting microphone', 'Listening', 'Processing',
    'No speech detected', 'Recognition service unavailable',
    'Network recognition error', 'Microphone busy', 'Permission denied',
    'Recognition failed', 'Ready'
  ]) assert.match(voice, new RegExp(label));
  assert.match(ui, /Open App Settings/);
  assert.match(activity, /Settings\.ACTION_APPLICATION_DETAILS_SETTINGS/);
});

test('nutrition readiness comes from trusted native key and consent state', () => {
  assert.match(activity, /secureSecretStore\.status\(AiKeyKind\.HEALTH\)\.configured/);
  assert.match(activity, /vitalis-nutrition-analysis-readiness/);
  assert.match(hotfix, /AI_KEY_REQUIRED[\s\S]*AI_CONSENT_REQUIRED[\s\S]*READY_FOR_ANALYSIS/);
  assert.match(ui, /Photo prête\. Configurez la clé IA pour lancer l’analyse\./);
});

test('key and consent return continue the same nutrition scan', () => {
  assert.match(activity, /onResume\(\)[\s\S]*dispatchNutritionReadiness/);
  assert.match(ui, /data-nutrition-key/);
  assert.match(ui, /Autoriser et analyser/);
  assert.match(ui, /scan\.analyzeWhenReady/);
  assert.doesNotMatch(ui, /data-nutrition-key[\s\S]{0,500}startNutritionScan/);
});

test('nutrition API failures retain precise safe classifications', () => {
  for (const code of [
    'api_auth_error', 'api_quota_error', 'network_error', 'timeout',
    'service_unavailable', 'invalid_response', 'analysis_error'
  ]) assert.match(hotfix + activity, new RegExp(code));
  assert.match(ui, /Ré-analyser/);
});

test('hotfix preserves secret and image logging boundaries', () => {
  assert.doesNotMatch(activity, /Log\.[dviwe]\s*\(/);
  assert.doesNotMatch(activity, /println\s*\(.*(?:apiKey|imageDataUrl)/i);
  assert.match(activity, /setRequestProperty\("Authorization", "Bearer \$apiKey"\)/);
  assert.doesNotMatch(ui, /localStorage\.setItem\([^,]*(?:api[_-]?key|photo|image)/i);
});
