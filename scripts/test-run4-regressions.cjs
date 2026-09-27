const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = relative => {
  try { return fs.readFileSync(path.join(root, relative), 'utf8'); }
  catch (_) { return ''; }
};

const activity = read('app/src/main/java/com/vitalis/healthos/MainActivity.kt');
const nutrition = read('app/src/main/java/com/vitalis/healthos/NutritionReliability.kt');
const processor = read('app/src/main/java/com/vitalis/healthos/SafeNutritionImageProcessor.kt');
const compat = read('app/src/main/assets/vitalis/compat.js');
const power = read('app/src/main/assets/vitalis/vitalis-3.12.js');

test('scan sessions capture one stable id and the selected date before image selection', () => {
  assert.match(nutrition, /data class NutritionScanSession/);
  assert.match(nutrition, /val scanId: String/);
  assert.match(nutrition, /val selectedDate: LocalDate/);
  assert.match(activity, /beginNutritionScan/);
  assert.match(power, /capturedSelectedDate/);
});

test('native image pipeline bounds bytes, dimensions and pixels before decode', () => {
  assert.match(nutrition, /MAX_SOURCE_BYTES/);
  assert.match(nutrition, /MAX_SOURCE_WIDTH/);
  assert.match(nutrition, /MAX_SOURCE_HEIGHT/);
  assert.match(nutrition, /MAX_SOURCE_PIXELS/);
  assert.match(processor, /inJustDecodeBounds/);
  assert.match(processor, /ImageDecoder/);
  assert.doesNotMatch(compat, /reader\.readAsDataURL\(file\)/);
  assert.doesNotMatch(compat, /image\.onerror\s*=\s*function\s*\(\)\s*\{\s*complete\(reader\.result\)/);
});

test('nutrition estimates are strictly validated instead of coercing malformed values to zero', () => {
  assert.match(nutrition, /object NutritionEstimateParser/);
  assert.match(nutrition, /NutritionValidationResult/);
  assert.match(nutrition, /NUTRIENT_LIMITS/);
  assert.doesNotMatch(power, /Math\.max\(0, Number\(parsed\[key\]\) \|\| 0\)/);
  assert.match(activity, /parseForReview/);
});

test('meal persistence is stable-id upsert with edit delete and no rolling data loss', () => {
  assert.match(nutrition, /class NutritionMealStore/);
  assert.match(nutrition, /fun upsert/);
  assert.match(nutrition, /fun delete/);
  assert.match(nutrition, /fun deleteAll/);
  assert.doesNotMatch(activity, /scanner-\$\{System\.currentTimeMillis\(\)\}/);
  assert.doesNotMatch(activity, /existing\.length\(\) - MAX_MANUAL_MEALS/);
});

test('local nutrition export is explicit and excludes credentials and images', () => {
  assert.match(nutrition, /object NutritionExportCodec/);
  assert.match(activity, /exportLocalNutrition/);
  assert.match(activity, /ActivityResultContracts\.CreateDocument/);
  assert.match(nutrition, /nutrientUnits/);
  assert.doesNotMatch(nutrition, /Authorization|api[_-]?key|imageDataUrl/i);
});

test('newer scan ownership and consent invalidation block stale analysis responses', () => {
  assert.match(nutrition, /class NutritionScanCoordinator/);
  assert.match(nutrition, /fun acceptAnalysis/);
  assert.match(activity, /activeNutritionAnalysisJob\?\.cancel/);
  assert.match(activity, /isCurrentNutritionScan/);
  assert.match(activity, /hasAiHealthConsent/);
});
