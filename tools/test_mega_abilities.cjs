// Run against the UNMODIFIED Showdown shipped in Cobblemon 1.7.3:
// node tools/test_mega_abilities.cjs <extracted-showdown-directory>
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const sim = path.resolve(process.argv[2] || path.join(root, 'run/showdown'));
const { Battle } = require(path.join(sim, 'sim/battle'));
const { Cobblemon } = require(path.join(sim, 'sim/cobblemon/cobblemon'));
const resources = path.join(root, 'src/main/resources');
const injections = JSON.parse(fs.readFileSync(path.join(resources, 'custom_mega_showdown.json')));
for (const [id, js] of Object.entries(injections.ability)) {
  assert.equal(fs.readFileSync(path.join(resources, 'data/megacobble/abilities', `${id}.js`), 'utf8').replaceAll('\r\n', '\n').trim(), js);
  // Mirror GraalShowdownService.sendRegistryData's newline removal before eval.
  Cobblemon.registries.ability.register(Function(`return (${js.replace(/[\r\n]+/g, '')});`)(), id);
}
const battles = [];
function mon(ability = 'noability', moves = ['splash'], extras = {}) {
  return { species: 'Mew', ability, moves, movesInfo: moves.map(() => ({ pp: 40, maxPp: 40 })), ...extras };
}
function battle(p1, p2, extras = {}) {
  const b = new Battle({ formatid: 'gen9customgame', seed: [1, 2, 3, 4], ...extras });
  battles.push(b);
  b.setPlayer('p1', { team: Array.isArray(p1) ? p1 : [p1] });
  b.setPlayer('p2', { team: Array.isArray(p2) ? p2 : [p2] });
  if (b.requestState === 'teampreview') b.makeChoices();
  // Damage and accuracy are deterministic so comparisons catch rounding errors.
  b.randomizer = value => value;
  return b;
}
function attack(ability, move, weather = '', defender = {}, item = '') {
  const b = battle(mon(ability, [move], { item }), mon('noability', ['splash'], defender));
  if (weather) b.field.setWeather(weather, b.p1.active[0]);
  b.makeChoices('move 1', 'move 1');
  return { b, damage: b.p2.active[0].getLastAttackedBy()?.damage || 0 };
}
let passed = 0;
function test(name, fn) {
  try { fn(); passed++; console.log(`PASS ${name}`); }
  catch (e) { console.error(`FAIL ${name}`); throw e; }
  finally { while (battles.length) battles.pop().destroy(); }
}

test('all generated roster abilities match form data and resolve in the sim', () => {
  const roster = JSON.parse(fs.readFileSync(path.join(resources, 'za_megas.json'))).megas;
  const b = battle(mon(), mon());
  for (const entry of roster) {
    const addition = JSON.parse(fs.readFileSync(path.join(resources, 'data/cobblemon/species_additions', `${entry.species}_mega.json`)));
    const id = entry.ability.replaceAll('_', '');
    assert.equal(addition.forms.find(form => form.name === entry.form).abilities[0], id);
    assert.ok(b.dex.abilities.get(id).exists, `${entry.species}: ${id}`);
  }
});

test('Piercing Drill quarters protected damage and leaves unprotected damage intact', () => {
  const normal = attack('noability', 'tackle').damage;
  assert.equal(attack('piercingdrill', 'tackle').damage, normal);
  for (const shield of ['protect', 'detect']) {
    const b = battle(mon('piercingdrill', ['tackle']), mon('noability', [shield]));
    b.makeChoices('move 1', 'move 1');
    assert.equal(b.p2.active[0].maxhp - b.p2.active[0].hp, b.modify(normal, 0.25));
  }
});
test('Piercing Drill does not pierce Max Guard or let noncontact moves through Protect', () => {
  for (const [move, shield] of [['tackle', 'maxguard'], ['earthpower', 'protect']]) {
    const b = battle(mon('piercingdrill', [move]), mon('noability', [shield]));
    b.makeChoices('move 1', 'move 1');
    assert.equal(b.p2.active[0].hp, b.p2.active[0].maxhp);
  }
});
test('Piercing Drill preserves shield contact penalties', () => {
  for (const shield of ['spikyshield', 'banefulbunker', 'burningbulwark', 'kingsshield', 'obstruct', 'silktrap']) {
    const b = battle(mon('piercingdrill', ['tackle']), mon('noability', [shield]));
    b.makeChoices('move 1', 'move 1');
    const source = b.p1.active[0];
    assert.ok(b.p2.active[0].hp < b.p2.active[0].maxhp, shield);
    assert.ok(source.hp < source.maxhp || source.status || Object.values(source.boosts).some(v => v < 0), shield);
  }
});
test('Piercing Drill suppression restores Protect', () => {
  const b = battle(mon('piercingdrill', ['tackle']), mon('noability', ['protect']));
  b.p1.active[0].addVolatile('gastroacid');
  b.makeChoices('move 1', 'move 1');
  assert.equal(b.p2.active[0].hp, b.p2.active[0].maxhp);
});

test('Mega Sol Fire and Water damage equals sunlight in every ordinary weather', () => {
  for (const move of ['flamethrower', 'watergun', 'hydrosteam']) {
    const sunlight = attack('noability', move, 'sunnyday').damage;
    for (const weather of ['', 'sunnyday', 'raindance', 'sandstorm', 'snow']) {
      const { b } = attack('megasol', move, weather);
      // Read move damage rather than end-turn sandstorm damage.
      assert.equal(b.p2.active[0].getLastAttackedBy().damage, sunlight, `${move}/${weather}`);
      assert.equal(b.field.weather, weather);
    }
  }
});
test('Mega Sol Weather Ball, Solar Beam and Solar Blade use sunlight under rain', () => {
  for (const move of ['weatherball', 'solarbeam', 'solarblade']) {
    const expected = attack('noability', move, 'sunnyday').damage;
    assert.equal(attack('megasol', move, 'raindance').damage, expected, move);
  }
});
test('Mega Sol Growth and recovery use sunlight without changing field weather', () => {
  const b = battle(mon('megasol', ['growth', 'synthesis', 'moonlight', 'morningsun']), mon());
  b.field.setWeather('raindance', b.p1.active[0]);
  b.makeChoices('move 1', 'move 1');
  assert.equal(b.p1.active[0].boosts.atk, 2);
  assert.equal(b.p1.active[0].boosts.spa, 2);
  for (let move = 2; move <= 4; move++) {
    b.p1.active[0].hp = 1;
    b.makeChoices(`move ${move}`, 'move 1');
    assert.equal(b.p1.active[0].hp, 1 + Math.round(b.p1.active[0].maxhp * 2 / 3));
  }
});
test('Mega Sol ignores sand/snow defensive bonuses', () => {
  for (const [weather, species, move] of [['sandstorm', 'Regirock', 'watergun'], ['snow', 'Glaceon', 'tackle']]) {
    const expected = attack('megasol', move, '', { species }).damage;
    const { b } = attack('megasol', move, weather, { species });
    assert.equal(b.p2.active[0].getLastAttackedBy().damage, expected);
  }
});
test('Mega Sol does not activate Leaf Guard or alter incoming attacks', () => {
  const b = battle(mon('megasol', ['thunderwave']), mon('leafguard'));
  b.makeChoices('move 1', 'move 1');
  assert.equal(b.p2.active[0].status, 'par');
  assert.equal(attack('noability', 'flamethrower', 'raindance', { ability: 'megasol' }).damage,
    attack('noability', 'flamethrower', 'raindance').damage);
});
test('Mega Sol respects suppression and still works with Cloud Nine', () => {
  const expected = attack('noability', 'flamethrower', 'sunnyday').damage;
  assert.equal(attack('megasol', 'flamethrower', 'raindance', { ability: 'cloudnine' }).damage, expected);
  const b = battle(mon('megasol', ['growth']), mon());
  b.p1.active[0].addVolatile('gastroacid');
  b.makeChoices('move 1', 'move 1');
  assert.equal(b.p1.active[0].boosts.atk, 1);
});

test('Eelevate blocks Ground moves, but Mold Breaker and Thousand Arrows bypass it', () => {
  assert.equal(attack('noability', 'earthquake', '', { ability: 'eelevate' }).damage, 0);
  assert.ok(attack('moldbreaker', 'earthquake', '', { ability: 'eelevate' }).damage > 0);
  assert.ok(attack('noability', 'thousandarrows', '', { ability: 'eelevate' }).damage > 0);
});
test('Eelevate loses airborne immunity under grounding and suppression', () => {
  for (const condition of ['gravity', 'smackdown', 'ingrain', 'ironball', 'gastroacid']) {
    const b = battle(mon('noability', ['earthquake']), mon('eelevate'));
    const target = b.p2.active[0];
    if (condition === 'gravity') b.field.addPseudoWeather(condition, b.p1.active[0]);
    else if (condition === 'ironball') target.setItem(condition);
    else target.addVolatile(condition);
    assert.equal(target.isGrounded(), true, condition);
    b.makeChoices('move 1', 'move 1');
    assert.ok(target.getLastAttackedBy().damage > 0, condition);
  }
});
test('Eelevate avoids grounded hazards on switching in but not Stealth Rock', () => {
  for (const hazard of ['spikes', 'toxicspikes', 'stickyweb', 'stealthrock']) {
    const b = battle([mon(), mon('eelevate')], mon());
    b.p1.addSideCondition(hazard, b.p2.active[0]);
    b.makeChoices('switch 2', 'move 1');
    const p = b.p1.active[0];
    assert.equal(p.status, '');
    assert.equal(p.boosts.spe, 0);
    assert.equal(p.hp === p.maxhp, hazard !== 'stealthrock', hazard);
  }
});
test('Eelevate avoids Arena Trap and grounded terrain; suppression reverses it', () => {
  const b = battle(mon('eelevate'), mon('arenatrap'));
  assert.equal(b.p1.active[0].trapped, false);
  b.field.setTerrain('electricterrain', b.p1.active[0]);
  assert.ok(b.p1.active[0].trySetStatus('slp'));
  b.p1.active[0].clearStatus();
  b.p1.active[0].addVolatile('gastroacid');
  assert.equal(b.p1.active[0].trySetStatus('slp'), false);
});
test('Eelevate boosts the highest stat after a move KO', () => {
  const b = battle(mon('eelevate', ['tackle'], { evs: { atk: 252 } }), [mon(), mon()]);
  b.p2.active[0].hp = 1;
  b.makeChoices('move 1', 'move 1');
  assert.equal(b.p1.active[0].boosts.atk, 1);
});

test('Dragonize converts and boosts Tackle; native Dragon moves get no extra boost', () => {
  const b = attack('dragonize', 'tackle').b;
  assert.equal(b.p1.active[0].lastMove.type, 'Dragon');
  assert.ok(b.p2.active[0].getLastAttackedBy().damage > attack('noability', 'tackle').damage / 1.5);
  assert.equal(attack('dragonize', 'dragonpulse').damage, attack('noability', 'dragonpulse').damage);
});
test('Dragonize leaves special type-selection moves and damaging Z moves alone', () => {
  const b = battle(mon('dragonize'), mon());
  const p = b.p1.active[0];
  for (const id of ['judgment', 'multiattack', 'naturalgift', 'revelationdance', 'technoblast', 'terrainpulse', 'weatherball']) {
    const move = b.dex.getActiveMove(id);
    move.type = 'Normal';
    b.runEvent('ModifyType', p, b.p2.active[0], move, move);
    assert.equal(move.type, 'Normal', id);
  }
  const move = b.dex.getActiveMove('breakneckblitz');
  b.runEvent('ModifyType', p, b.p2.active[0], move, move);
  assert.equal(move.type, 'Normal');
  p.terastallized = 'Normal';
  const tera = b.dex.getActiveMove('terablast');
  b.runEvent('ModifyType', p, b.p2.active[0], tera, tera);
  assert.equal(tera.type, 'Normal');
});
test('Fire Mane modifies Attack and Special Attack only for Fire moves', () => {
  const b = battle(mon('firemane'), mon());
  for (const event of ['ModifyAtk', 'ModifySpA']) {
    assert.equal(b.runEvent(event, b.p1.active[0], b.p2.active[0], b.dex.getActiveMove('ember'), 101), 151);
    assert.equal(b.runEvent(event, b.p1.active[0], b.p2.active[0], b.dex.getActiveMove('tackle'), 101), 101);
  }
});
test('Spicy Spray burns ranged attackers, respects Fire immunity and Substitute', () => {
  assert.equal(attack('noability', 'watergun', '', { ability: 'spicyspray' }).b.p1.active[0].status, 'brn');
  const b = battle(mon('noability', ['watergun'], { species: 'Arcanine' }), mon('spicyspray'));
  b.makeChoices('move 1', 'move 1');
  assert.equal(b.p1.active[0].status, '');
  const sub = battle(mon('noability', ['watergun']), mon('spicyspray'));
  sub.p2.active[0].addVolatile('substitute');
  sub.makeChoices('move 1', 'move 1');
  assert.equal(sub.p1.active[0].status, '');
});
test('Aura Guard halves contact damage, including special contact; leaves noncontact alone', () => {
  for (const move of ['tackle', 'drainingkiss', 'earthquake', 'watergun']) {
    const normal = attack('noability', move).damage;
    const { b, damage } = attack('noability', move, '', { ability: 'auraguard' });
    assert.equal(damage, b.dex.moves.get(move).flags.contact ? b.modify(normal, 0.5) : normal, move);
  }
  assert.equal(attack('moldbreaker', 'tackle', '', { ability: 'auraguard' }).damage, attack('noability', 'tackle').damage);
});
test('compatibility installation is idempotent and does not patch unrelated battles', () => {
  const b = battle(mon('megasol'), mon('eelevate'));
  const handler = b.findEventHandlers;
  const grounded = b.p1.active[0].isGrounded;
  b.dex.abilities.get('megasol').installCompatibility.call(b);
  assert.equal(b.findEventHandlers, handler);
  assert.equal(b.p1.active[0].isGrounded, grounded);
  const ordinary = battle(mon(), mon());
  assert.equal(ordinary.megacobbleAbilities, undefined);
});

test('Mega Sol uses sun accuracy and ignores weather evasion without disabling other abilities', () => {
  for (const [weather, ability] of [['sandstorm', 'sandveil'], ['snow', 'snowcloak']]) {
    const b = battle(mon('megasol', ['thunder', 'hurricane']), mon(ability));
    b.field.setWeather(weather, b.p1.active[0]);
    const source = b.p1.active[0], target = b.p2.active[0];
    b.activePokemon = source;
    for (const id of ['thunder', 'hurricane']) {
      const move = b.dex.getActiveMove(id);
      b.singleEvent('ModifyMove', move, null, source, target, move, move);
      assert.equal(move.accuracy, 50, id);
    }
    assert.equal(b.runEvent('ModifyAccuracy', target, source, b.dex.getActiveMove('tackle'), 100), 100);
    source.addVolatile('gastroacid');
    assert.equal(b.runEvent('ModifyAccuracy', target, source, b.dex.getActiveMove('tackle'), 100), 80);
  }
});
test('Mega Sol retains the upstream Utility Umbrella interaction', () => {
  for (const move of ['flamethrower', 'hydrosteam']) {
    for (const item of ['', 'utilityumbrella']) {
      const defender = { item: 'utilityumbrella' };
      assert.equal(attack('megasol', move, 'raindance', defender, item).damage,
        attack('noability', move, 'sunnyday', defender, item).damage);
    }
  }
});
test('Piercing Drill is per target in doubles and pierces applicable side guards', () => {
  const b = battle(
    [mon('piercingdrill', ['breakingswipe', 'quickattack']), mon()],
    [mon('noability', ['protect', 'wideguard', 'quickguard']), mon()],
    { formatid: 'gen9doublescustomgame' });
  b.makeChoices('move 1, move 1', 'move 1, move 1');
  const [protectedMon, otherMon] = b.p2.active;
  assert.equal(protectedMon.getLastAttackedBy().damage, b.modify(otherMon.getLastAttackedBy().damage, 0.25));
  const normal = otherMon.getLastAttackedBy().damage;
  b.makeChoices('move 1, move 1', 'move 2, move 1');
  for (const target of b.p2.active) assert.equal(target.getLastAttackedBy().damage, b.modify(normal, 0.25));
  b.makeChoices('move 2 1, move 1', 'move 3, move 1');
  assert.ok(b.log.some(line => line.includes('Piercing Drill')));
});
test('compatibility follows Skill Swap and ability suppression dynamically', () => {
  const b = battle(mon('eelevate', ['skillswap']), mon());
  const source = b.p1.active[0], target = b.p2.active[0];
  b.makeChoices('move 1', 'move 1');
  assert.equal(source.isGrounded(), true);
  assert.equal(target.ability, 'eelevate');
  assert.equal(target.isGrounded(), false);
  target.setAbility('neutralizinggas');
  source.setAbility('eelevate');
  assert.equal(source.isGrounded(), true);
  target.setAbility('noability');
  assert.equal(source.isGrounded(), false);
});
test('all 48 classic forms retain their bundled stats, typing and ability through Mega Evolution', () => {
  const dex = require(path.join(sim, 'sim/dex')).Dex;
  const custom = JSON.parse(fs.readFileSync(path.join(resources, 'za_megas.json'))).megas;
  const stones = JSON.parse(fs.readFileSync(path.join(resources, 'megastones.json')))
    .filter(stone => !custom.some(entry => entry.species === stone.species && entry.form === stone.form));
  assert.equal(stones.length, 47);
  Cobblemon.registries.heldItem.register(Function(`return (${injections.heldItem.rayquazite});`)(), 'rayquazite');
  stones.push({ species: 'rayquaza', form: 'Mega', name: 'Rayquazite' });
  for (const stone of stones) {
    const base = dex.species.get(stone.species);
    const expected = dex.species.get(`${base.name}-${stone.form}`);
    assert.ok(expected.exists, `${base.name}-${stone.form}`);
    const moves = stone.species === 'rayquaza' ? ['splash', 'dragonascent'] : ['splash'];
    const b = battle(mon(base.abilities[0], moves, { species: base.name, item: stone.name }), mon());
    b.makeChoices('move 1 mega', 'move 1');
    const mega = b.p1.active[0];
    assert.equal(mega.species.name, expected.name);
    assert.equal(mega.ability, dex.abilities.get(expected.abilities[0]).id, expected.name);
    assert.deepEqual(mega.species.baseStats, expected.baseStats);
    assert.deepEqual(mega.species.types, expected.types);
  }
});

test('all 45 custom forms activate their assigned ability through actual Mega Evolution', () => {
  const dex = require(path.join(sim, 'sim/dex')).Dex;
  for (const [id, js] of Object.entries(injections.heldItem)) {
    Cobblemon.registries.heldItem.register(Function(`return (${js});`)(), id);
  }
  const roster = JSON.parse(fs.readFileSync(path.join(resources, 'za_megas.json'))).megas;
  const stones = JSON.parse(fs.readFileSync(path.join(resources, 'megastones.json')));
  assert.equal(roster.length, 45);
  for (const entry of roster) {
    const species = entry.species;
    const stone = stones.find(s => s.species === species && s.form === entry.form);
    const form = JSON.parse(fs.readFileSync(path.join(resources, 'data/cobblemon/species_additions', `${species}_mega.json`))).forms.find(f => f.name === entry.form);
    const base = dex.species.get(species);
    const name = `${base.name}-${form.name}`;
    // Mirror Cobblemon's species synchronization plus fixMegaAbilities().
    Cobblemon.registries.species.register({
      ...base, name, baseSpecies: base.name, forme: form.name,
      types: [form.primaryType, form.secondaryType].filter(Boolean)
        .map(type => type[0].toUpperCase() + type.slice(1)),
      abilities: { 0: form.abilities[0] }, requiredItem: form.requiredItem, battleOnly: base.name,
      baseStats: { hp: form.baseStats.hp, atk: form.baseStats.attack, def: form.baseStats.defence,
        spa: form.baseStats.special_attack, spd: form.baseStats.special_defence, spe: form.baseStats.speed },
    });
    const b = battle(mon(base.abilities[0], ['splash'], { species, item: stone.name }), mon());
    b.makeChoices('move 1 mega', 'move 1');
    const mega = b.p1.active[0];
    assert.equal(mega.species.name, name);
    assert.equal(mega.ability, entry.ability.replaceAll('_', ''));
    assert.deepEqual(mega.species.types, [form.primaryType, form.secondaryType].filter(Boolean)
      .map(type => type[0].toUpperCase() + type.slice(1)));
    assert.deepEqual(mega.species.baseStats, {
      hp: form.baseStats.hp, atk: form.baseStats.attack, def: form.baseStats.defence,
      spa: form.baseStats.special_attack, spd: form.baseStats.special_defence, spe: form.baseStats.speed,
    });
    if (species === 'eelektross') assert.equal(mega.isGrounded(), false);
    if (['meganium', 'eelektross', 'excadrill'].includes(species)) assert.ok(b.megacobbleAbilities);
  }
});
console.log(`${passed} ability regression tests passed.`);
