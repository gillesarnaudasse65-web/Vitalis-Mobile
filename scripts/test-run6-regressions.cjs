const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');

const activity = fs.readFileSync('app/src/main/java/com/vitalis/healthos/MainActivity.kt', 'utf8');
const security = fs.readFileSync('app/src/main/java/com/vitalis/healthos/Run6SecurityPrivacy.kt', 'utf8');
const manifest = fs.readFileSync('app/src/main/AndroidManifest.xml', 'utf8');
const gradle = fs.readFileSync('app/build.gradle.kts', 'utf8');
const workflow = fs.readFileSync('.github/workflows/build-apk.yml', 'utf8');
const classic = fs.readFileSync('app/src/main/assets/vitalis/vitalis-3.12.js', 'utf8');
const compat = fs.readFileSync('app/src/main/assets/vitalis/compat.js', 'utf8');

test('privileged bridge uses exact-origin WebMessageListener and rejects subframes', () => {
  assert.match(activity, /WebViewCompat\.addWebMessageListener/);
  assert.match(activity, /OriginBridgePolicy\.allowedOriginRules\(\)/);
  assert.match(activity, /isMainFrame/);
  assert.doesNotMatch(activity, /addJavascriptInterface\(VitalisAndroidBridge/);
  assert.match(security, /VITALIS_ORIGIN/);
  assert.match(security, /APPASSETS_ORIGIN/);
});

test('remote DOM cannot save clear or retrieve plaintext API keys', () => {
  assert.doesNotMatch(activity, /@JavascriptInterface[\s\S]{0,120}saveOpenAiKey/);
  assert.doesNotMatch(activity, /@JavascriptInterface[\s\S]{0,120}saveDeveloperAiKey/);
  assert.doesNotMatch(classic, /type=["']password["']/);
  assert.doesNotMatch(compat, /type=["']password["']/);
  assert.match(classic, /openKeySettings/);
  assert.match(compat, /openKeySettings/);
  assert.match(manifest, /\.KeySettingsActivity/);
  assert.match(manifest, /\.PrivacyDataActivity/);
});

test('native key surface is screenshot protected and never prepopulates secrets', () => {
  const keyUi = fs.readFileSync('app/src/main/java/com/vitalis/healthos/KeySettingsActivity.kt', 'utf8');
  assert.match(keyUi, /FLAG_SECURE/);
  assert.match(keyUi, /TYPE_TEXT_VARIATION_PASSWORD/);
  assert.match(keyUi, /importantForAutofill/);
  assert.doesNotMatch(keyUi, /setText\([^)]*read/);
  assert.match(keyUi, /(?:editableText|text)\?\.clear/);
});

test('global export import delete and consent invalidation are native controlled', () => {
  const privacy = fs.readFileSync('app/src/main/java/com/vitalis/healthos/PrivacyDataActivity.kt', 'utf8');
  const store = fs.readFileSync('app/src/main/java/com/vitalis/healthos/VitalisLocalDataStore.kt', 'utf8');
  assert.match(privacy, /CreateDocument/);
  assert.match(privacy, /OpenDocument/);
  assert.match(privacy, /MERGE/);
  assert.match(privacy, /REPLACE/);
  assert.match(privacy, /Health Connect permissions are managed separately|autorisations Health Connect sont gérées séparément/);
  assert.match(store, /VitalisExportCodec/);
  assert.match(store, /deleteVitalisLocalData/);
  assert.match(activity, /AiConsentCoordinator/);
});

test('release build and CI produce honest APK AAB mapping checksums and reports', () => {
  assert.match(gradle, /isMinifyEnabled = true/);
  assert.match(gradle, /VITALIS_KEYSTORE_PATH/);
  assert.match(gradle, /VITALIS_KEYSTORE_PASSWORD/);
  assert.match(gradle, /VITALIS_KEY_ALIAS/);
  assert.match(gradle, /VITALIS_KEY_PASSWORD/);
  assert.match(workflow, /assembleRelease/);
  assert.match(workflow, /bundleRelease/);
  assert.match(workflow, /sha256sum/);
  assert.match(workflow, /mapping\.txt/);
  assert.match(workflow, /retention-days: 90/);
});

test('release security remains strict in manifest and runtime', () => {
  assert.match(manifest, /android:allowBackup="false"/);
  assert.match(manifest, /android:usesCleartextTraffic="false"/);
  assert.match(manifest, /androidx\.core\.content\.FileProvider[\s\S]*android:exported="false"/);
  assert.match(activity, /ReleaseSecurityPolicy\.webViewDebuggingEnabled\(BuildConfig\.DEBUG\)/);
  assert.match(activity, /handler\.cancel\(\)/);
});

test('production sources contain no verbose logging or secret-bearing diagnostics', () => {
  const kotlinSources = fs.readdirSync('app/src/main/java/com/vitalis/healthos')
    .filter(name => name.endsWith('.kt'))
    .map(name => fs.readFileSync(`app/src/main/java/com/vitalis/healthos/${name}`, 'utf8'))
    .join('\n');
  assert.doesNotMatch(kotlinSources, /\bLog\.(?:d|v)\s*\(/);
  assert.doesNotMatch(kotlinSources, /\bprintln\s*\(/);
  assert.doesNotMatch(classic + compat, /console\.log\s*\(/);
  assert.match(activity, /setRequestProperty\("Authorization", "Bearer \$apiKey"\)/);
  assert.doesNotMatch(activity, /(?:Log\.|println\()[\s\S]{0,120}(?:apiKey|Authorization|healthContext)/);
});
