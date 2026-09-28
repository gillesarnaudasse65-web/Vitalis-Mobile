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
  assert.match(workflow, /VITALIS_SIGNING_BACKUP_PASSPHRASE/);
  assert.match(workflow, /VITALIS_SIGNING_VAULT_TOKEN/);
  assert.match(workflow, /restore-vitalis-signing\.sh/);
  assert.match(workflow, /rm -rf "\$RUNNER_TEMP\/vitalis-signing"/);
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
  assert.match(identity, /Vitalis-Signing-Vault/);
  assert.match(identity, /ACTIVE — BACKUP_CONFIRMED/);
  assert.doesNotMatch(identity, /BEGIN (?:RSA )?PRIVATE KEY/);
});

test('GitHub-only signing initialization is manual, protected, encrypted, and rerun-safe', () => {
  const workflow = read('.github/workflows/initialize-production-signing.yml');
  const inputs = workflow.split('    inputs:')[1].split('\npermissions:')[0];
  assert.match(workflow, /^name: Initialize Vitalis Production Signing/m);
  assert.match(workflow, /workflow_dispatch:/);
  assert.doesNotMatch(workflow, /^\s{2}(?:push|pull_request|schedule):/m);
  assert.match(workflow, /environment: production-signing-init/);
  assert.match(workflow, /confirm_first_identity != true/);
  assert.match(workflow, /VITALIS_SIGNING_BACKUP_PASSPHRASE/);
  assert.match(workflow, /VITALIS_SIGNING_VAULT_TOKEN/);
  assert.match(workflow, /PRODUCTION SIGNING ALREADY INITIALIZED/);
  assert.match(workflow, /keytool -genkeypair/);
  assert.match(workflow, /go run \. encrypt/);
  assert.match(workflow, /go run \. decrypt/);
  assert.match(workflow, /jarsigner -verify/);
  assert.match(workflow, /Vitalis-production-signing-encrypted-recovery/);
  assert.match(workflow, /retention-days: 7/);
  assert.match(workflow, /if: always\(\)/);
  assert.doesNotMatch(workflow, /VITALIS_KEYSTORE_BASE64/);
  assert.doesNotMatch(inputs, /password/i);
});

test('backup activation confirmation uses booleans only and regenerates no key', () => {
  const workflow = read('.github/workflows/confirm-production-signing-backup.yml');
  const inputs = workflow.split('    inputs:')[1].split('\npermissions:')[0];
  for (const input of [
    'backup_a_confirmed', 'backup_b_confirmed',
    'recovery_passphrase_stored', 'confirm_activation_review'
  ]) {
    assert.match(inputs, new RegExp(input));
  }
  assert.doesNotMatch(inputs, /^\s+(?:passphrase|password|token|secret):/im);
  assert.match(workflow, /BACKUP_CONFIRMED/);
  assert.match(workflow, /go run \. decrypt/);
  assert.doesNotMatch(workflow, /keytool -genkeypair/);
});

test('age recovery helper uses the standard pinned authenticated encryption library', () => {
  assert.match(read('scripts/vitalis-age/go.mod'), /filippo\.io\/age v1\.3\.2/);
  const helper = read('scripts/vitalis-age/main.go');
  assert.match(helper, /age\.NewScryptRecipient/);
  assert.match(helper, /age\.NewScryptIdentity/);
  assert.match(helper, /VITALIS_SIGNING_BACKUP_PASSPHRASE/);
  assert.doesNotMatch(helper, /fmt\.Println\(passphrase\)/);
});

test('production and controlled v20 jobs enforce documented certificate continuity', () => {
  const workflow = read('.github/workflows/build-apk.yml');
  assert.match(workflow, /build_upgrade_baseline:/);
  assert.match(workflow, /production_upgrade_baseline:/);
  assert.match(workflow, /188a31e2ff34ef102cdcfa861f8de69972bee88e/);
  assert.match(workflow, /Vitalis-upgrade-test-baseline-v20-production-signed/);
  assert.match(workflow, /UPGRADE TEST BASELINE NOT CURRENT DISTRIBUTION BUILD/);
  assert.match(workflow, /test "\$BASELINE_SHA256" = "\$EXPECTED_SHA256"/);
  assert.match(workflow, /test "\$AAB_SHA256" = "\$EXPECTED_SHA256"/);
});

test('phone recovery guide requires two independent copies and no local shell', () => {
  const guide = read('docs/VITALIS_PHONE_SIGNING_RECOVERY_GUIDE.md');
  assert.match(guide, /No PC, terminal, ADB, Android Studio/);
  assert.match(guide, /Backup A/);
  assert.match(guide, /Backup B/);
  assert.match(guide, /Google Drive/);
  assert.match(guide, /AgePony/);
  assert.match(guide, /Do not paste it into ChatGPT/);
});

test('physical acceptance checklist provides a no-shell in-place upgrade matrix', () => {
  const checklist = read('docs/VITALIS_PHYSICAL_ACCEPTANCE_CHECKLIST.md');
  assert.match(checklist, /No terminal, ADB, Android Studio, PowerShell, Command Prompt, or Bash is required/);
  assert.match(checklist, /without uninstalling/);
  for (const area of [
    'Install', 'Upgrade', 'Themes', 'Widgets', 'Drag and drop', 'Health Connect',
    'Scanner', 'Camera', 'Photo picker', 'Microphone', 'Text-to-speech', 'Offline',
    'Export and import', 'Privacy', 'API key settings', 'Restart and persistence'
  ]) {
    assert.match(checklist, new RegExp(`## \\d+\\. ${area}`));
  }
  assert.match(checklist, /PHYSICAL ACCEPTANCE NOT TESTED/);
});
