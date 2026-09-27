const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const root = path.resolve(__dirname, '..');
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8');

test('RC version is 3.15.0-rc1 with monotonically increased code 21', () => {
  const gradle = read('app/build.gradle.kts');
  assert.match(gradle, /versionName = "3\.15\.0-rc1"/);
  assert.match(gradle, /versionCode = 21/);
});

test('application identity remains unchanged', () => {
  assert.match(read('app/build.gradle.kts'), /applicationId = "com\.vitalis\.healthos"/);
});

test('release signing remains secret-driven and fails closed by default', () => {
  const gradle = read('app/build.gradle.kts');
  assert.match(gradle, /VITALIS_KEYSTORE_PATH/);
  assert.match(gradle, /VITALIS_KEYSTORE_PASSWORD/);
  assert.match(gradle, /VITALIS_KEY_ALIAS/);
  assert.match(gradle, /VITALIS_KEY_PASSWORD/);
  assert.match(gradle, /else -> null/);
});

test('test-signed RC output is explicitly prohibited from production distribution', () => {
  const workflow = read('.github/workflows/build-apk.yml');
  assert.match(workflow, /Vitalis-3\.15\.0-rc1-qa-test-signed/);
  assert.match(workflow, /NOT FOR PRODUCTION DISTRIBUTION/);
});

test('RC release workflow verifies signatures and emits SHA-256 checksums', () => {
  const workflow = read('.github/workflows/build-apk.yml');
  assert.match(workflow, /APKSIGNER_PATH/);
  assert.match(workflow, /verify --verbose --print-certs/);
  assert.match(workflow, /jarsigner -verify -verbose -certs/);
  assert.match(workflow, /sha256sum/);
});

test('production release is manual protected fail-closed and removes temporary key material', () => {
  const workflow = read('.github/workflows/build-apk.yml');
  const production = workflow.split('  production_release:')[1].split('\n  emulator:')[0];
  assert.match(workflow, /production_release:/);
  assert.match(workflow, /environment: production/);
  assert.match(workflow, /test -n "\$VITALIS_KEYSTORE_BASE64"/);
  assert.match(workflow, /test -n "\$VITALIS_KEYSTORE_PASSWORD"/);
  assert.match(workflow, /test -n "\$VITALIS_KEY_ALIAS"/);
  assert.match(workflow, /test -n "\$VITALIS_KEY_PASSWORD"/);
  assert.match(workflow, /rm -f "\$RUNNER_TEMP\/vitalis-production\.jks"/);
  assert.doesNotMatch(production, /VITALIS_USE_DEBUG_RELEASE_SIGNING/);
});

test('privacy policy remains a draft requiring legal review', () => {
  assert.match(read('docs/PRIVACY_POLICY_DRAFT.md'), /DRAFT[\s\S]*REQUIRES LEGAL REVIEW/i);
});

test('production signing identity documentation contains no secret value and requires recovery controls', () => {
  const identity = read('docs/VITALIS_SIGNING_IDENTITY.md');
  assert.match(identity, /FIRST STABLE VITALIS PRODUCTION IDENTITY/);
  assert.match(identity, /vitalis-production/);
  assert.match(identity, /RSA 4096/);
  assert.match(identity, /BACKUP_BLOCKED/);
  assert.match(identity, /GitHub Secrets are not a user-downloadable backup vault/);
  assert.match(identity, /VITALIS_KEYSTORE_BASE64/);
  assert.doesNotMatch(identity, /BEGIN (?:RSA )?PRIVATE KEY/);
});

test('GitHub-only signing initialization is manual, protected, fail-closed, and artifact-safe', () => {
  const workflow = read('.github/workflows/initialize-production-signing.yml');
  const inputs = workflow.split('    inputs:')[1].split('\npermissions:')[0];
  assert.match(workflow, /^name: Initialize Vitalis Production Signing/m);
  assert.match(workflow, /workflow_dispatch:/);
  assert.doesNotMatch(workflow, /^\s{2}(?:push|pull_request|schedule):/m);
  assert.match(workflow, /environment: production-signing-init/);
  assert.match(workflow, /confirm_first_identity != true/);
  assert.match(workflow, /VITALIS_INIT_KEYSTORE_PASSWORD/);
  assert.match(workflow, /VITALIS_INIT_KEY_PASSWORD/);
  assert.match(workflow, /PRODUCTION SIGNING ALREADY INITIALIZED/);
  assert.match(workflow, /INITIALIZATION BLOCKED — SECURE SECRET TRANSFER CHANNEL REQUIRED/);
  assert.match(workflow, /if: always\(\)/);
  assert.doesNotMatch(workflow, /actions\/upload-artifact/);
  assert.doesNotMatch(workflow, /keytool -genkeypair/);
  assert.doesNotMatch(inputs, /password/i);
});

test('physical acceptance checklist provides a no-shell in-place upgrade matrix', () => {
  const checklist = read('docs/VITALIS_PHYSICAL_ACCEPTANCE_CHECKLIST.md');
  assert.match(checklist, /No terminal, ADB, Android Studio, PowerShell, Command Prompt, or Bash is required/);
  assert.match(checklist, /without uninstalling/);
  for (const area of [
    'Install', 'Upgrade', 'Themes', 'Widgets', 'Drag and drop', 'Health Connect',
    'Scanner', 'Camera', 'Microphone', 'Text-to-speech', 'Offline',
    'Export and import', 'Privacy', 'API key settings', 'Restart and persistence'
  ]) {
    assert.match(checklist, new RegExp(`## \\d+\\. ${area}`));
  }
  assert.match(checklist, /PHYSICAL ACCEPTANCE NOT TESTED/);
});
