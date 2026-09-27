const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = (relative) => {
  const file = path.join(root, relative);
  return fs.existsSync(file) ? fs.readFileSync(file, 'utf8') : '';
};

const main = read('app/src/main/java/com/vitalis/healthos/MainActivity.kt');
const voice = read('app/src/main/java/com/vitalis/healthos/VoiceReliability.kt');
const connectors = read('app/src/main/java/com/vitalis/healthos/ConnectorCatalog.kt');
const compat = read('app/src/main/assets/vitalis/compat.js');
const modern = read('app/src/main/assets/vitalis/vitalis-3.12.js');

test('voice uses explicit recognition and TTS state machines', () => {
  assert.match(voice, /enum class RecognitionState/);
  assert.match(voice, /REQUESTING_PERMISSION[\s\S]*PROCESSING_FINAL[\s\S]*UNAVAILABLE/);
  assert.match(voice, /enum class TtsState/);
  assert.match(voice, /TTS_UNINITIALIZED[\s\S]*TTS_SPEAKING[\s\S]*TTS_ERROR/);
});

test('recognition sessions bound retries and consume one final result', () => {
  assert.match(voice, /MAX_TRANSIENT_RETRIES\s*=\s*1/);
  assert.match(voice, /finalResultConsumed/);
  assert.match(voice, /sessionId/);
  assert.match(voice, /fun consumeFinal/);
  assert.doesNotMatch(main, /onResults\([\s\S]{0,180}restartMicrophone/);
});

test('background destroy and explicit close stop voice resources', () => {
  assert.match(main, /override fun onStop\(\)[\s\S]{0,350}stopMicrophone/);
  assert.match(main, /override fun onStop\(\)[\s\S]{0,500}stopSpeaking/);
  assert.match(main, /override fun onDestroy\(\)[\s\S]{0,600}speechRecognizer\?\.destroy/);
  assert.match(compat, /function stopOverlayVoice/);
  assert.match(modern, /function stopOverlayVoice/);
});

test('partial voice input is display-only and final input is deduplicated', () => {
  assert.match(main, /VoiceResultKind\.PARTIAL/);
  assert.match(main, /VoiceResultKind\.FINAL/);
  assert.match(compat, /detail\.kind\s*!==\s*["']FINAL["']/);
  assert.match(modern, /detail\.kind\s*!==\s*["']FINAL["']/);
});

test('connector catalogue separates capability runtime and direct support', () => {
  assert.match(connectors, /enum class ConnectorCapability/);
  assert.match(connectors, /enum class ConnectorRuntimeState/);
  assert.match(connectors, /directIntegrationImplemented/);
  assert.match(connectors, /UNSUPPORTED_PLATFORM/);
  assert.match(connectors, /DIRECT_OAUTH/);
  assert.match(connectors, /APP_SETUP_ONLY/);
});

test('connector status is resolved from evidence rather than installation alone', () => {
  assert.match(connectors, /object ConnectorStateResolver/);
  assert.match(connectors, /HEALTH_CONNECT_DATA_AVAILABLE/);
  assert.match(connectors, /HEALTH_CONNECT_AVAILABLE_NO_DATA/);
  assert.match(connectors, /HEALTH_CONNECT_PERMISSION_REQUIRED/);
  assert.match(main, /ConnectorStateResolver\.resolve/);
  assert.doesNotMatch(main, /installed\s*!=\s*null\s*->\s*["']connected["']/);
});

test('unsupported and absent direct integrations are explicit in the UI contract', () => {
  assert.match(connectors, /apple_health[\s\S]{0,300}UNSUPPORTED_PLATFORM/);
  assert.match(main, /directIntegrationImplemented/);
  assert.match(main, /direct_connection_not_implemented/);
  assert.match(compat + modern, /Direct connection not implemented|Connexion directe non implémentée/);
});
