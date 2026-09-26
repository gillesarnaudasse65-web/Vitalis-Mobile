'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '..');
const js = fs.readFileSync(path.join(root, 'app/src/main/assets/vitalis/vitalis-3.12.js'), 'utf8');
const native = fs.readFileSync(path.join(root, 'app/src/main/java/com/vitalis/healthos/MainActivity.kt'), 'utf8');
const begin = js.indexOf('  var coaches = [');
const end = js.indexOf('\n  function norm', begin);
assert.ok(begin !== -1 && end > begin, 'coach registry must remain available to characterize');
const context = vm.createContext({});
vm.runInContext(js.slice(begin, end), context);
const coaches = Array.from(context.coaches);

test('the six existing coach names and stable ids remain unique', () => {
  assert.deepEqual(coaches.map(({name, id}) => ({name, id})), [
    {name:'Kofi',id:'general'}, {name:'Ama',id:'nutrition'},
    {name:'Ayo',id:'activity'}, {name:'Nia',id:'sleep'},
    {name:'Sékou',id:'recovery'}, {name:'Zuri',id:'mental'}
  ]);
  assert.equal(new Set(coaches.map(c => c.id)).size, 6);
  assert.ok(coaches.every(c => c.name.trim() && c.id.trim() && c.role.trim() && c.prompt.trim()));
});

test('portraits and Android coach instruction dispatch exist for every role', () => {
  const mapping = native.slice(native.indexOf('private fun coachInstructions('), native.indexOf('private fun writeEncryptedSecret('));
  for (const coach of coaches) {
    assert.ok(fs.existsSync(path.join(root, 'app/src/main/assets/vitalis/coaches', coach.image)), coach.name);
    if (coach.id !== 'general') assert.ok(mapping.includes('"' + coach.id + '" ->'), coach.id);
    else assert.ok(mapping.includes('else -> "Tu es Kofi'), 'Kofi fallback');
  }
});
