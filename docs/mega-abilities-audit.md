# Mega ability audit — 2026-09-27

Compared all 45 custom Mega primary-ability assignments and all 48 classic Mega
forms (including stoneless Rayquaza) with current Pokémon Showdown data. Four
custom assignments needed changes; all classic assignments matched. This is an
ability audit, not a new verification of the original stats, types or visual forms.

## Assignment changes

### Stat follow-up — 2026-10-21

Version 0.2.2 corrects Mega Starmie's base Attack from 140 to 100, retaining Huge Power.
The previous entry combined the Legends: Z-A stat line (660 total) with the Champions ability.
The Champions stat line is **60 / 100 / 105 / 130 / 105 / 120**, totaling **620**.
Sources: [Champions Pokédex](https://www.serebii.net/pokedex-champions/starmie/),
[Legends: Z-A Pokédex](https://www.serebii.net/pokedex-sv/starmie/), and
[pinned Showdown data](https://github.com/smogon/pokemon-showdown/blob/651b6fa8542bb75078fd66337ab150effe623d30/data/pokedex.ts#L2747).
The six provisional ability assignments below remain unchanged.

### Ability corrections — 2026-09-27

| Mega | Previous placeholder | Current ability |
| --- | --- | --- |
| Garchomp Z | Sand Veil | Levitate |
| Lucario Z | Steadfast | Aura Guard |
| Golisopod | Emergency Exit | Tough Claws |
| Absol Z | Pressure | Sharpness |

The official Pokédex confirms [Lucario Z's Aura Guard](https://www.pokemon.com/us/pokedex/0448),
[Golisopod's Tough Claws](https://www.pokemon.com/uk/pokedex/golisopod), and
[Absol Z's Sharpness](https://www.pokemon.com/uk/pokedex/absol).
The [Showdown species data](https://github.com/smogon/pokemon-showdown/blob/master/data/pokedex.ts)
also assigns these abilities and Levitate to Garchomp Z. Baxcalibur retains Thermal
Exchange; its old blanket placeholder label was outdated.

Six entries remain **provisional**: Darkrai (Bad Dreams), Heatran (Flash Fire),
Magearna (Soul-Heart), Zeraora (Volt Absorb), Tatsugiri (Commander), and Zygarde
(Aura Break). They match upstream primary-ability data, but a fallback matching
the base form is not sufficient evidence of a released Champions assignment.
Do not silently substitute a speculative ability. Tatsugiri's upstream entries
also retain a hidden Storm Drain slot; that is not evidence to change our Mega's
single primary ability. Meowstic's male/female Mega entries both have Trace.

## Mechanics corrected

- **Piercing Drill:** protection now reduces ordinary contact-move damage to a
  quarter, per the [official Excadrill entry](https://www.pokemon.com/uk/pokedex/excadrill).
  Apply the reduction per target, after normal damage modifiers, only when a
  protection condition actually blocks the move. Keep contact penalties and other
  hit effects. Do not turn off the move's protection flag globally. Max Guard
  still blocks it; noncontact moves do not pierce. Fixed-damage and OHKO attacks
  keep the simulator's separate damage path.
- **Mega Sol:** move weather checks now see sun. This includes Solar Beam/Blade,
  Weather Ball, Growth, Synthesis/Moonlight/Morning Sun, Thunder/Hurricane, and
  the Hydro Steam exception. Weather damage is applied once, at the weather
  modifier stage, replacing real rain/sun rather than stacking with it. Attacks
  ignore the sand/snow defensive bonuses and weather evasion. Incoming attacks,
  the actual weather, and other Pokémon's sun abilities are unaffected. See the
  [official announcement](https://www.pokemon.com/uk/pokemon-news/pokemon-champions-is-now-available-on-nintendo-switch-and-nintendo-switch-2),
  [Showdown's regression tests](https://github.com/smogon/pokemon-showdown/blob/master/test/sim/abilities/megasol.js),
  and [first-hand mechanics research](https://www.smogon.com/forums/threads/champions-battle-mechanics-research.3780372/page-8).
  Electro Shot continues to read actual rain, following current upstream logic.
  Sun does not automatically thaw a frozen user; the old README's "thaw" item
  was not a valid missing feature.
- **Eelevate:** implements airborne state, including Spikes/Toxic Spikes/Sticky
  Web, Arena Trap and grounded terrain interactions. Gravity, Iron Ball, Ingrain,
  Smack Down, Thousand Arrows, Mold Breaker and ability suppression still work.
  The older Smack Down condition needed a separate compatibility fix. Attack KOs
  still raise the highest stat. Stealth Rock still deals damage.
- **Dragonize:** now excludes the other special type-selection moves as well as
  Weather Ball, damaging Z moves and Terastallized Tera Blast. Ordinary eligible
  Normal moves still convert and receive the 1.2× boost.
- **Fire Mane:** uses the Attack/Special Attack modifier stage, matching current
  Showdown, instead of changing base power. This matters for integer rounding and
  combinations with other modifiers.
- **Aura Guard:** newly registered for Mega Lucario Z, halving contact damage
  (including special contact attacks). Its Mold Breaker flag follows upstream;
  upstream currently marks that interaction as needing game verification.
- **Spicy Spray:** existing behavior already matched upstream. Added coverage for
  ranged hits, natural burn immunity and Substitute instead of changing it.

Implementation references: [Showdown abilities](https://github.com/smogon/pokemon-showdown/blob/master/data/abilities.ts),
[Pokémon grounding/weather](https://github.com/smogon/pokemon-showdown/blob/master/sim/pokemon.ts),
[damage calculation](https://github.com/smogon/pokemon-showdown/blob/master/sim/battle-actions.ts),
and [protection conditions](https://github.com/smogon/pokemon-showdown/blob/master/data/moves.ts).
These are current upstream references, not a claim that Cobblemon 1.7.3 already
contains those implementations. Unreleased-game and legacy-mechanic interactions
follow the simulator where direct game evidence is unavailable.

## Source and runtime layout

Edit `tools/abilities/<id>.js` and the names/descriptions in
`tools/gen_custom_megas.py`, then run the generator. It writes both the Cobblemon
datapack registrations and the sim injection definitions. Do not edit generated
copies independently.

`tools/abilities/compatibility.js` supplies battle-local adapters for the older
simulator. Start/Skill Swap and pre-hazard SwitchIn hooks install them once on
the battle and its Pokémon. Ability checks remain dynamic, so suppression and
ability replacement restore ordinary behavior. No global prototypes, shared Dex
records, Cobblemon JARs, or extracted runtime files are modified.

Cobblemon's `GraalShowdownService.sendRegistryData` strips newlines before eval.
The generator omits whole-line JS comments, and the regression runner reproduces
this flattening to catch transport-specific syntax failures.

## Verification

The Minecraft bridge explicitly applies the Mega form's ability and refreshes Cobblemon's separate
battle-team snapshot. This fixes the summary showing the base ability (for example Sand Veil on
Mega Garchomp Z). Both battle and world Mega reversion preserve the original ability and slot.
The original state also saves in Pokémon persistent data, since party storage can save/unload before
battle cleanup during logout. Login, send-out and battle entry recover interrupted transformations.
Legacy saves with the mod's generated name and forced Mega aspect are repaired, although an original
nickname or ability slot absent from those old saves cannot be reconstructed exactly.
With Java 21, `.\gradlew.bat -PmegaAbilityTests runAbilityTestServer` runs 24 checks in an
isolated headless server: 12 lifecycles, 8 save/reloads and 4 legacy repairs, including packet
encode/decode and normal, hidden and forced abilities.
It shuts down automatically; its test mod is excluded from the release JAR and ordinary client runs.

### Comparison with previously implemented Megas

All **48 classic** forms and **45 custom** forms pass the same Minecraft lifecycle checks:

- Correct form and primary ability in battle and out of battle, including forced base abilities.
- Battle-summary packets preserve the ability, form stats and typing after encode/decode.
- Reversion preserves the original ability slot, nickname and required base-form aspects.
- Saving either kind of Mega and recovering the reloaded Pokémon restores its original state.
- The roster includes Charizard/Mewtwo X/Y, the Z forms, Eternal Flower Floette, Complete Zygarde
  and stoneless Rayquaza with Dragon Ascent.

The 28 simulator tests also exercise all 93 actual Mega transformations. Classic forms retain
the bundled simulator's stats, types and abilities; custom forms use their configured data.
No additional production discrepancy was found in this comparison. These checks establish
implementation consistency, not external confirmation of the six provisional abilities or a new
official-source audit of every custom stat/type assignment. Placeholder models remain unchanged.

```powershell
py -3 tools/gen_custom_megas.py
py -3 tools/test_mega_abilities.py
# Or supply another copy of the same Cobblemon version:
py -3 tools/test_mega_abilities.py path/to/Cobblemon-fabric-1.8.1+1.21.1.jar
```

Requires Python 3.9+ and Node.js. The Python runner extracts a fresh, unmodified
simulator from the supplied JAR into a temporary build directory, runs the tests,
and removes only that temporary directory. The JS suite can also accept an
already extracted simulator directory as its argument.

The suite covers generated data consistency, all seven custom abilities,
weather/grounding/suppression, protection and doubles targeting, Skill Swap,
and all 93 actual simulator Mega transformations. It runs on Node.js using the
Cobblemon simulator, not through the Minecraft client. Manual server/client
battle checks remain useful, especially with other mods that change the sim.
