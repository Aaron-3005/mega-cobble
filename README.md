# Mega Cobble

[![Build](https://github.com/Aaron-3005/mega-cobble/actions/workflows/build.yml/badge.svg)](https://github.com/Aaron-3005/mega-cobble/actions/workflows/build.yml)

A Fabric add-on that brings **Mega Evolution** to
[Cobblemon](https://cobblemon.com/) — including a data-driven pipeline for adding
**custom megas**.

> Status: working in-battle. All **47 classic Gen 6 / ORAS** megas plus the **full Legends: Z-A +
> Mega Dimension DLC set and base-game Mega Zygarde (45 megas)** run through Cobblemon's bundled Pokémon Showdown sim, so they're
> **real in-battle transformations** (correct stats, typing, and ability), not cosmetic-only. The same
> pipeline can add fully custom megas (see *Adding a custom Mega* below).

## Download

- **[⬇ Latest jar](https://github.com/Aaron-3005/mega-cobble/releases/download/latest/megacobble-latest.jar)**
  — `megacobble-latest.jar`, rebuilt on every push to `main`.
- **[All releases](https://github.com/Aaron-3005/mega-cobble/releases)** — tagged `v*` versions get a
  permanent jar.

Every build also uploads the installable versioned JAR as the **megacobble-jar** artifact on its
Actions run (retained for 30 days). Sources and development JARs are excluded. Use **Actions →
Build → Run workflow** on `main` to rebuild and refresh the rolling latest download manually.
Version tags must match `mod_version` in `gradle.properties`; existing versioned release assets
are preserved when a workflow is rerun. Release downloads remain available after build artifacts expire.

It's a Fabric **add-on**, so drop it in your `mods/` folder alongside its dependencies — it won't
launch without them:

1. [Fabric Loader](https://fabricmc.net/use/installer/)
2. [Fabric API](https://modrinth.com/mod/fabric-api)
3. [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)
4. [Cobblemon](https://modrinth.com/mod/cobblemon) **1.7.3** (Minecraft 1.21.1)
5. this jar

## Features

- **47 classic Mega Stones** (every Gen 6 / ORAS mega, incl. Charizard & Mewtwo X/Y) and the
  **45 Legends: Z-A / Mega Dimension** megas (incl. Raichu X/Y, the Garchomp/Lucario/Absol "Z"
  megas, and base-game **Mega Zygarde** — Complete Forme only), plus a **Key Stone** — **92 stones**
  in all. They're **vanilla items carrying a
  `minecraft:custom_data` tag** (not registered custom items), so they work on a server even for
  clients without the mod; get them with **`/megacobble give <stone> [count]`**. The mod ships **no
  stone textures** — each stone carries a `custom_model_data` (Key Stone = 1, manifest stones = 2…) so
  an external resource pack can re-skin it, exactly like mon skins. Without a pack a stone renders as
  the plain base item.
- **Native, in-battle Mega Evolution** — give a Pokémon its matching Mega Stone as a held
  item and carry a Key Stone; Cobblemon's own mega button appears in the Fight menu, and
  choosing a move + mega runs the real Showdown mega (stats / typing / ability).
- The mega'd Pokémon is renamed `Mega-<Species>` (e.g. `Mega-Charizard-X`) and **reverts**
  automatically at battle end, flee, or faint.
- A **data-driven custom-mega system**: classic megas reuse Cobblemon's bundled sim data;
  Z-A / custom megas add their form, stone, and (if needed) a brand-new ability through a
  generator — no Java changes required.
- All megas currently render the **substitute doll** placeholder. (A custom **Mega Venusaur** 3D
  model + animation was built in Blockbench; it now lives outside the mod in `../megacobble-assets/`
  and isn't shipped while the visual pipeline is reworked.)
- **Z-Moves** — all **35 Z-Crystals** (18 type crystals + 17 signature crystals) plus a **Z-Ring**,
  shipped exactly like the Mega Stones (vanilla items with `custom_data`, server-side, no client mod
  needed). Give a Pokémon its crystal as a held item and carry the Z-Ring; Cobblemon's bundled
  Showdown handles the actual Z-Move mechanics and damage. Get them with **`/megacobble give <crystal>`**
  and **`/megacobble give z_ring`**.
- **In-battle damage readout** — any player can run **`/megacobble damage on`** to see the exact
  damage each hit deals (read from the sim's own battle HP) printed to chat during their battles. A
  built-in testing/QoL tool; off by default, per-player.
- **World (out-of-battle) Mega Evolution** — mega-evolve a Pokémon for overworld exploration via
  a **"Mega Evolve" option on the shift-right-click interaction wheel** (modded clients) or the
  **`/megacobble worldmega`** command (any client, incl. vanilla on a server). You can **ride** the
  Pokémon in its mega form, and it automatically **reverts when a battle starts** so the real
  in-battle Showdown mega takes over. Gated like a battle mega (Key Stone + held Mega Stone) and
  fully configurable.
- **Command-driven, skin-agnostic looks** — the mod only sets a Pokémon's **aspects**; resolvers
  (the mod's substitute doll by default, or any external datapack/pack) decide the visual. Ops apply
  **named looks** (`/megacobble variant`) or **any aspect / datapack skin** (`/megacobble skin`), and
  an external resolver with `order > 5` overrides the default automatically — no client mod required.

## Commands

Everything lives under **`/megacobble`** — see **[COMMANDS.md](COMMANDS.md)** for the full reference
(syntax, permissions, targeting, and examples). In brief:

- **`worldmega`** — out-of-battle Mega Evolution (available to all players, item-gated)
- **`damage [on|off]`** — toggle a per-player, in-battle damage readout (available to all players)
- **`give`** — get a Mega Stone, the Key Stone, a Z-Crystal, or the Z-Ring *(op)*
- **`variant`** — apply/list/reload named looks *(op)*
- **`skin`** — force any aspect, including datapack-defined skins *(op)*
- **`config`** — view/edit the world-mega settings *(op)*

## World Mega Evolution (exploration)

Mega Evolution outside of battle is purely the Minecraft-side form + look (no battle stats) — for
exploring, riding, and screenshots.

- **Trigger it** by shift-right-clicking your sent-out Pokémon and picking **Mega Evolve** on the
  wheel (requires the mod on the client), or with **`/megacobble worldmega [on|off|toggle]`** while
  looking at it (or **`… slot <1-6>`** to target a party slot — works for any client).
- By default it's gated exactly like a battle mega: you must carry a **Key Stone** and the Pokémon
  must hold its **Mega Stone**. It **reverts automatically when a battle starts**.
- **Riding** in mega form works for any species that's normally rideable. (Cobblemon ships Mega forms
  with an empty seat list, so we drop that override at mega time to inherit the base species' riding
  seats.) Setting **`allowRideInMega: false`** refuses the wheel's "Mega Evolve" request, so players
  can't mega-then-ride via the wheel (the command still works).
- The wheel option uses Cobblemon's **Mega button icon** and is **tinted grey** when the client can
  tell the Pokémon isn't holding its matching Mega Stone. It stays **pressable regardless** — the
  server is the only side that knows the real held item and the real config, so it makes the call and
  replies with the reason. (A client-side veto is what made this button silently dead on servers
  before v0.0.9.)
- **Configure** it in `config/megacobble.json` or live with **`/megacobble config <key> <true|false>`**
  (op only). Keys: `worldMegaEnabled`, `requireKeyStone`, `requireMegaStone`, `allowRideInMega`,
  `revertOnBattleStart`.

## Changing a Pokémon's look by command

The mod is a **skin-agnostic framework**: it only ever sets a Pokémon's **aspects** (synced
server→client), and a **resolver** maps an aspect set to a model / texture / animation. Resolvers live
in a resource pack (the mod jar, or any external pack), so visuals reach vanilla clients via a pack —
no client mod needed. All of these commands are **op-gated** (level 2); the default target is the
Pokémon you're looking at, or `slot <1-6>` for a party slot.

**Named looks — a curated catalog:**
```
/megacobble variant list                                 # show the catalog
/megacobble variant apply|remove <variant> [slot <1-6>]  # tab-completed
/megacobble variant reload                               # re-read the catalog after editing it
```
Looks live in the bundled `variants.json` plus an optional editable overlay at
`config/megacobble/variants.json` (same-id entries override) — each is `id`, `label`, `kind`
(`look` = bundled model+texture+animation), `species`, `aspects`. Built-in: `mega`, `mega_x`,
`mega_y`. Scaffold a new look against an existing Cobblemon model **or** custom pack assets with
`tools/gen_look.py`.

**Any skin aspect — one Pokémon or a whole species:**
```
/megacobble skin set <aspect> [slot <1-6>]   # force a skin on one Pokémon
/megacobble skin set <aspect> all            # GLOBAL: every Pokémon of the looked-at species
/megacobble skin clear [slot <1-6>] | all    # reset one Pokémon, or remove the global skin
```
`set … all` applies the skin to **every** Pokémon of a species (loaded, boxed, or caught later) through
a server-side **aspect provider** — persisted to `config/megacobble/global_skins.json` and synced to
clients. Because the mod only sets aspects, an installed datapack/resource-pack resolver with
`order > 5` **overrides the default (substitute doll) skin automatically** — `skin set` applies a
specific aspect, `skin clear` releases it.

## How it works

Everything runs through Cobblemon's existing systems — no edits to Cobblemon's code:

- A custom `HeldItemManager` exposes our Mega Stones — vanilla items tagged with `custom_data` — to
  the Showdown sim, identifying each stone by its tag, so the sim sets `canMegaEvo`.
- At battle start, a bridge grants the `cobblemon:key_stone` key-item while the player
  carries a Key Stone, so Cobblemon's native gate (`sanitize()`) lets the mega button
  through.
- A `MEGA_EVOLUTION` event hook mirrors the form change on the Minecraft side (model /
  name); the Showdown sim handles the actual battle mechanics; revert is hooked to battle
  end / flee / faint.

For megas **not** in Cobblemon's bundled sim (Z-A and custom), three extra data sources fill
the gaps:

- `data/cobblemon/species_additions/<species>_mega.json` — adds the mega **form** (stats /
  types / ability / aspect / `requiredItem`); Cobblemon auto-syncs it to the sim.
- `data/megacobble/abilities/<id>.js` — any **new ability**, registered on the **Cobblemon side**.
  Required for a `species_additions` that references the ability to parse at all (without it the
  datapack fails to load). Cobblemon does **not** forward these to the battle sim, though.
- `custom_mega_showdown.json` — the **mega-stone item** def (`megaStone`/`megaEvolves`) under
  `heldItem`, plus the same **new ability** JS under `ability`. Both are injected into the sim at
  battle start by `MegaShowdownInjector`. Cobblemon won't send our items, doesn't forward datapack
  abilities to the sim, and blanks every mega forme's abilities to "No Ability" when it syncs species
  — so `MegaShowdownInjector` also restores each mega forme's real ability at battle start. Without
  the `abilities` injection a custom ability is registered Cobblemon-side but has no effect in battle.

## Using it in-game

1. Get the items with **`/megacobble give <stone|random> [count] [<targets>]`** — e.g.
   `/megacobble give charizardite_x`, `/megacobble give key_stone`, `/megacobble give random`, or
   `/megacobble give venusaurite 5 @a` to hand them to other players (op only; omit the target for
   yourself). They're `amethyst_shard`s tagged with `custom_data` until the resource pack re-skins them.
2. Give a Pokémon its matching Mega Stone in its **held-item** slot.
3. Keep a **Key Stone** in your inventory and start a battle.
4. On the Fight screen, use the mega button, pick a move, and it Mega Evolves.

## Adding a custom Mega

The pipeline is data-driven; adding a mega (Z-A or fully custom) means editing a couple of
data files and running the generators — no code changes.

1. **Define the mega's data.** Add an entry to `src/main/resources/za_megas.json`:
   ```json
   {
     "species": "lucario", "form": "Mega", "aspect": "mega",
     "type1": "fighting", "type2": "steel", "ability": "adaptability",
     "baseStats": { "hp": 70, "attack": 145, "defence": 88,
                    "special_attack": 140, "special_defence": 70, "speed": 112 }
   }
   ```
   - `species` is the lowercase Cobblemon species id (it must already exist in Cobblemon — a
     brand-new Pokémon would have to be added first).
   - **`form` + `aspect` are where you declare an X / Y / Z variant.** A single mega is
     `"form": "Mega"`, `"aspect": "mega"`. For variants, set them as a pair:

     | Variant | `form`    | `aspect`  | In-game name        |
     | ------- | --------- | --------- | ------------------- |
     | Mega    | `Mega`    | `mega`    | `Mega-<Species>`    |
     | Mega X  | `Mega-X`  | `mega_x`  | `Mega-<Species>-X`  |
     | Mega Y  | `Mega-Y`  | `mega_y`  | `Mega-<Species>-Y`  |
     | Mega Z  | `Mega-Z`  | `mega_z`  | `Mega-<Species>-Z`  |

     Add **one entry per variant** (so an X/Y mega is two entries with the same `species`). The
     `aspect` is just the `form` lowercased with the `-` turned into `_`; it's what the resolver keys
     on. (See Raichu's `Mega-X` / `Mega-Y` entries in `za_megas.json`.)
   - `ability` is a Showdown ability id. Use an existing one (`adaptability`, `protean`, …)
     or a brand-new one you define in step 3.

2. **Give it a Mega Stone.** In `tools/gen_custom_megas.py`, add the species to `STONE_NAMES`
   with the stone's display name:
   ```python
   'lucario': 'Lucaronite',
   ```

3. **(Only if the ability is brand-new)** In `tools/gen_custom_megas.py` add it to:
   - `NEW_ABILITY_ID` — map the `za_megas.json` ability value to its Showdown id,
   - `NEW_ABILITY_JS` — the `(display name, JS effect)` (the sim `eval`s the JS object;
     use Showdown's ability API: `onModifyType`, `onBasePower`, `onDamagingHit`, …),
   - `NEW_ABILITY_DESC` — the summary-screen description.

4. **Generate.** Run both generators (classic first, then the custom batch which appends):
   ```bash
   python tools/gen_megastones.py
   python tools/gen_custom_megas.py
   ```
   This writes the species-addition form, the **manifest entry** (`megastones.json` — the stone is a
   vanilla item + custom_data, so **no item texture/model/lang** is generated), the substitute
   resolver, the sim item injection, and any new ability file + its summary lang. The generators **do
   not** touch the hand-maintained command lang in `en_us.json` (they only add ability keys, in UTF-8).

5. **Build & run.** `./gradlew runClient`. The held-item manager, key-item bridge, sim
   injector, and form mirror all pick it up automatically — give the Pokémon its stone,
   carry a Key Stone, and battle.

> Special forms (e.g. **Mega Floette** only applying to AZ's Eternal-Flower Floette) need
> form-specific handling and aren't covered by the simple flow above.

## Target stack

| Component      | Version           |
| -------------- | ----------------- |
| Minecraft      | 1.21.1            |
| Mod loader     | Fabric            |
| Fabric Loader  | >= 0.17.2         |
| Fabric API     | >= 0.116.6+1.21.1 |
| Cobblemon      | 1.7.3+1.21.1      |
| Java           | 21                |

## Project layout

```
src/main/java/com/aaroncraft/megacobble/
  MegaCobble.java                 common entrypoint: registration + battle event hooks
  client/MegaCobbleClient.java    client entrypoint
  item/MegaItems.java             builds/reads stones as vanilla items + custom_data
  item/MegaStones.java            loads megastones.json; stone lookup by id / custom_data / showdown id
  command/MegaCobbleCommands.java /megacobble give | variant | skin | worldmega | config
  config/MegaCobbleConfig.java    config/megacobble.json (world-mega toggles)
  variant/MegaVariants.java       loads the look catalog (bundled variants.json + config overlay)
  net/RequestWorldMegaPayload.java   client→server world-mega request (wheel button)
  mega/MegaEvolution.java         key-item bridge, in-battle + world mega, skins, revert
  mega/MegaStoneHeldItemManager.java   exposes our stones to the Showdown sim
  mega/MegaShowdownInjector.java  injects custom mega-stone item + ability defs into the sim; restores blanked mega-forme abilities
src/main/resources/
  megastones.json                 manifest: stone -> species / form / aspect
  variants.json                   the bundled look catalog
  za_megas.json                   Legends Z-A / custom mega data (types, abilities, stats)
  custom_mega_showdown.json       mega-stone item + custom-ability defs injected into the sim
  data/cobblemon/species_additions/   the mega forms (Z-A / custom)
  data/megacobble/abilities/      new abilities registered Cobblemon-side (so species_additions parse)
  assets/megacobble/...           lang + the interaction-wheel icon (stone art lives in ../megacobble-assets/)
  assets/cobblemon/bedrock/pokemon/resolvers/megacobble/   substitute-doll resolvers
tools/gen_megastones.py           classic stones: substitute resolvers + megastones.json manifest
tools/gen_custom_megas.py         Z-A / custom megas: species_additions, abilities, sim defs, resolvers
tools/gen_look.py                 scaffold a custom look (resolver + catalog) for an existing species
tools/release.py                  version manager: bump mod_version, commit, and tag a release
```

## Building & running

JDK 21 on your PATH, then:

```bash
.\gradlew.bat build        # build the jar (lands in build/libs/)
.\gradlew.bat runClient    # dev client with the mod + Cobblemon
```

## Releasing

`mod_version` in `gradle.properties` is the single source of truth (jar name, in-mod version, and the
GitHub release). **`tools/release.py`** bumps it, commits, and tags `vX.Y.Z`; pushing the tag triggers
the GitHub Actions **versioned release**. (Pushes to `main` separately refresh the rolling
[`latest`](https://github.com/Aaron-3005/mega-cobble/releases) jar.)

```bash
python tools/release.py                # show current version + recent tags
python tools/release.py patch          # 0.1.0 -> 0.1.1  (bug fixes)
python tools/release.py minor          # 0.1.0 -> 0.2.0  (new features)
python tools/release.py major          # 0.1.0 -> 1.0.0  (breaking / stable)
python tools/release.py 0.3.0          # set an explicit version
python tools/release.py minor --push   # also push commit + tag (kicks off the release)
```

It refuses to run on a dirty tree, so commit your work (and any changelog notes) first.

## Build notes

- Uses **official Mojang mappings** to line up 1:1 with the mappings Cobblemon is developed
  against.
- Uses **Fabric Loom 1.13.6 + Gradle 8.14** and applies the **Kotlin plugin** — not for Kotlin
  sources (there are none) but so Loom remaps Cobblemon's Kotlin `@Metadata` for the dev
  runtime. Without it, Cobblemon's reflection looks up intermediary names that don't exist
  at runtime and crashes on boot.

## Dependencies & the Cobblemon jar

Cobblemon is pulled automatically from its
[Maven repository](https://maven.impactdev.net/) at build time — you do **not** need a local
jar to build. The provided `Cobblemon-fabric-1.7.3+1.21.1.jar` (~135 MB) is **git-ignored**
and kept only as a local reference.

## Limitations / roadmap

- **All megas render the substitute doll** for now — no real per-mega models ship yet. The custom Mega
  Venusaur model/animation was moved out to `../megacobble-assets/` (not shipped) while the visual
  delivery is reworked toward a command-driven, resource-pack-based pipeline. This is the biggest gap.
- **The 10 DLC legendaries** (Darkrai, Garchomp-Z, Lucario-Z, Heatran, Magearna, Zeraora, Baxcalibur,
  Golisopod, Tatsugiri, Absol-Z) temporarily use their **base-form ability** as a placeholder — Pokémon
  Champions hasn't assigned them a mega ability yet. Update the `ability` field in `za_megas.json` and
  re-run `gen_custom_megas.py` when the real ones are revealed (flagged there in `_pending_comment`).
- **Eelevate** (Mega Eelektross) grants Ground-*move* immunity + Beast-Boost-on-KO only. The broader
  "ungrounded" immunities (entry hazards, Arena Trap, terrain) key off the sim's internal
  `isGrounded()`, which is hardcoded to the literal Levitate ability and can't be extended through
  Cobblemon's data-injection API.
- **Mega Sol** covers the Fire ×1.5 / Water ×0.5 damage modifier and the Solar Beam/Blade single-turn
  behaviour; the remaining harsh-sun niceties (Growth +2, Synthesis/Moonlight ⅔ healing, Weather Ball →
  Fire, thaw) aren't implemented.
- **The original 19 Z-A megas' stats/types are unverified** — only their abilities were confirmed. The
  newer 25 use Serebii-verified numbers, so a stat/type pass on the original 19 would make the data set
  consistent.
- **Most megas aren't battle-tested yet** — the multi-form (Raichu X/Y) and "Z" formes, and the new
  abilities beyond Mega Sol/Eelevate, load cleanly but haven't all been exercised in an actual battle.
- **Mega Floette** applies only to AZ's Eternal-Flower Floette (`flower-eternal`) via `requiredAspect`.

## Changelog

Newest first.

- **v0.1.2 — stoneless Mega Rayquaza (in-battle + overworld).** Rayquaza Mega Evolves with **no Mega
  Stone**, the authentic way: it just needs to know **Dragon Ascent** (+ a Key Stone). In battle the
  mod feeds Showdown a virtual "Rayquazite" for a Dragon-Ascent Rayquaza — invisible to the player,
  no held item — so the sim offers the mega (the ungated item path; Gen-9 Showdown otherwise gates the
  move-based mega behind a rule that doesn't reach its in-process sim). Out of battle, `/megacobble
  worldmega` allows it via a move-gated stoneless exception (still refuses other stoneless mons). Model
  falls back to the substitute doll and it's renamed Mega-Rayquaza, like the other model-less megas.
- **v0.1.1 — Z-Moves, Mega Zygarde, and a damage readout.**
  - **Z-Moves**: all 35 Z-Crystals (18 type + 17 signature) and a Z-Ring, shipped like the Mega
    Stones (vanilla items + `custom_data`, server-side). Hold the crystal + carry the Z-Ring; the
    bundled Showdown does the mechanics. `/megacobble give <crystal>` / `give z_ring`.
  - **Mega Zygarde**: the base-game Z-A mega that Champions omitted — Dragon/Ground, BST 778, gated
    to the **Complete Forme** (`Zygardite`). Roster is now 45 Z-A megas / 92 stones.
  - **`/megacobble damage [on|off]`**: a per-player, all-access in-battle damage readout that prints
    each hit's exact damage (from the sim's real HP) to chat. Verified Z-Move/Mega damage is computed
    correctly (e.g. Devastating Drake off Core Enforcer is 140 BP by the sim's spread-move override,
    not 180 — matched to the point in-game).
- **v0.1.0 — first stable release.** Everything below this line is in: the full 44-mega Z-A +
  Champions/DLC roster with 91 Mega Stones, in-battle Showdown megas (correct stats/typing/abilities,
  name synced to the HUD), out-of-battle "world" megas via the interaction wheel and
  `/megacobble worldmega` (with riding), per-mega custom models (Venusaur), the Key Stone gate, and
  the server-side fixes below. No functional change from v0.0.9 — this is the version cut, not a patch.
- **Fix: the interaction-wheel Mega Evolve button did nothing on dedicated servers** — the client
  decided whether the button was usable by reading `Pokemon.heldItem()`, but that is **server-only
  state Cobblemon never syncs to clients**, so on a server it always read empty: the button greyed
  out and the press was swallowed (`if (canMega)`) without sending anything or reporting why. It only
  ever worked in singleplayer, where the client and integrated server share the same `Pokemon` object.
  The client now reads the item it can actually see — the entity's synced `shownItem` — and, more
  importantly, no longer gets a veto: the button always sends and the **server** answers, since it
  alone knows the real held item and the real config (the client reads its *own* `megacobble.json`,
  which on a server is unrelated). The client-side check is now only a grey **tint**. The
  `allowRideInMega` wheel gate moved server-side for the same reason.
- **Fix: any Mega Stone could mega-evolve any Pokémon in the overworld** — `applyWorldMega` checked
  *that* a Mega Stone was held, never *which*. 90 of the 91 stones declare form `"Mega"`, so the form
  lookup matched any mega-capable species: an **Absolite mega-evolved an Abomasnow**. World megas now
  validate the stone's **species**, and its `requiredAspect` (which was never enforced — Floettite now
  correctly requires the Eternal Flower Floette). New feedback: *"That Mega Stone belongs to a
  different Pokémon."* The in-battle path is untouched — Showdown already gates it.
- **Every Z-A + Mega Dimension DLC mega (44 total)** — added the full Pokémon Champions mega roster on
  top of the original 19. The 15 Champions megas (Raichu X/Y, Barbaracle, Chimecho, Crabominable,
  Dragalge, Eelektross, Falinks, Glimmora, Malamar, Pyroar, Scolipede, Scovillain, Scrafty, Staraptor)
  ship with **Serebii-verified types, stats, and abilities** (every ability cross-checked — the original
  19 were already correct; a couple of community tables were wrong). The 10 DLC legendaries (Darkrai,
  Garchomp-Z, Lucario-Z, Heatran, Magearna, Zeraora, Baxcalibur, Golisopod, Tatsugiri, Absol-Z) ship
  too, temporarily **keeping their base-form ability** as a placeholder until Champions assigns the real
  one (documented in `za_megas.json`). Three brand-new abilities implemented as Showdown defs:
  **Spicy Spray** (burn the attacker on hit), **Fire Mane** (Fire ×1.5), **Eelevate** (Ground-move
  immunity, Mold-Breaker-bypassable + Thousand Arrows excepted, plus Beast-Boost-on-KO — the broader
  hazard/terrain immunity isn't replicable without patching Cobblemon's sim, so it's left out). The
  generator now supports **multi-form species** (Raichu X/Y) and form-suffixed mega species
  (`Raichu-Mega-X`, `Garchomp-Mega-Z`, distinct from the classic ORAS megas). 91 mega stones total.
- **Mega Sol now emulates harsh sun properly** — besides Fire ×1.5 / Water ×0.5, **Solar Beam and Solar
  Blade fire in a single turn** (no charge, via `onChargeMove` like Power Herb) and **skip the
  rain/sand/snow power penalty** — matching the in-game "as if harsh sunlight" behaviour.
- **Hotfix: custom-ability injection crashed battle start (and broke Z-A megas)** — the ability defs
  were injected under the registry key `abilities`, but the sim's key is `ability` (singular), so
  `getRegistry("abilities")` returned undefined and `injectAll` threw `Cannot read property 'register'
  of undefined` at every battle start. Worse, that bad key was sent *first*, so the throw aborted the
  whole injection — including the `heldItem` mega-stone defs — which is why custom Z-A megas had quietly
  stopped triggering since v0.0.4 (classic megas use bundled stones, so they were unaffected and hid
  it). Fixed the key, and made `injectAll` inject each registry type independently so one bad type can
  never abort the rest. Verified on a dedicated server: Meganium now mega-evolves in battle with zero
  inject errors.
- **Mega Sol now actually does something** — verified all 19 Z-A / custom mega abilities against the
  Pokémon Champions data (Legends: Z-A itself has no abilities; Champions assigns them). Names and
  typings all already matched; the one gap was **Mega Sol** (Mega Meganium), which shipped as a
  no-op stub. It now makes the user's moves behave as if harsh sunlight is up — Fire-type power ×1.5,
  Water-type power ×0.5 — mirroring the Sunny Day damage modifier. (Dragonize and Piercing Drill were
  already correct; Piercing Drill matches the bundled Unseen Fist.)
- **Hotfix: server failed to load with v0.0.4** — v0.0.4 wrongly removed the
  `data/megacobble/abilities/*.js` files, thinking they were dead. They're not: Cobblemon loads them
  to register the abilities **on its own side**, which a `species_additions` referencing them needs in
  order to parse — without them the datapack failed to load (`Error loading JSON for data:
  cobblemon:excadrill_mega`). Restored the files and updated the generator/docs: the abilities now ship
  in **both** places on purpose — as datapack JS (Cobblemon-side registration) **and** in
  `custom_mega_showdown.json` (sim injection for the actual battle effect).
- **Fix: mega abilities had no effect in battle** — mega-evolving showed the right ability name (e.g.
  Blaziken → Speed Boost) but it never did anything: no speed gain, no Thick Fat, no custom Dragonize.
  Root cause (confirmed by instrumenting the bundled sim): Cobblemon serializes **every** mega forme to
  the battle simulator with its abilities blanked to `"No Ability"`, so the forme change sets the
  ability to nothing — the name you see is only Cobblemon's display-side form data. Fix is in
  `MegaShowdownInjector`: at battle start it reads the sim's species, and for each mega forme
  re-registers it with the correct `abilities[0]` taken from Cobblemon's own form data (the sim
  resolves species from this registry ahead of its cache, so it applies immediately). The three custom
  abilities (Dragonize, Piercing Drill, Mega Sol) are now also injected as real Showdown ability defs
  via `custom_mega_showdown.json`, so even with the right name they no longer resolve to "No Ability".
  Covers all megas, classic and custom.
- **Fix: mega name didn't update on the in-battle HUD** — mega-evolving renames the Pokémon
  (`Mega-Venusaur`) and that already synced to the overworld nameplate and party, but the battle card
  kept showing the base name. Cobblemon captures a battler's name label when it's sent out and never
  refreshes it mid-battle (its own forme changes — Aegislash, etc. — behave the same way), so the
  rename never reached the HUD. The mega now re-sends its active battle slot the way a switch does
  (`PokemonBattle.sendSidedUpdate` with an ally- and opponent-perspective `BattleSwitchPokemonPacket`),
  which slides the info tile back in carrying the mega name and current HP — no model recall.
- **Fix: mega persisted after custom/forced battle ends** — revert was hooked only to `BATTLE_VICTORY`
  / `BATTLE_FLED` / `BATTLE_FAINTED`, but battles that end without a declared winner (custom, forced,
  or drawn — they go through `PokemonBattle.end()`, which posts no event) never reverted, so the mega
  stuck. Now each in-battle mega is tracked with its battle and a per-tick safety net reverts it once
  that battle has ended (or left the registry) by any path; the event hooks stay for instant revert on
  normal ends.
- **Generator cleanup (Track F)** — `gen_megastones.py` / `gen_custom_megas.py` now match the
  custom_data architecture: they stop emitting registered-item textures/models/lang (stones are
  vanilla items skinned by an external pack) and **no longer overwrite the hand-maintained command
  lang** — the classic generator doesn't touch `en_us.json`, and the custom one only adds ability
  keys (now written as UTF-8, fixing an `é`/`—` corruption). So "Adding a custom Mega" is a clean
  one-command flow again. Removed the dead `item.megacobble.*` lang keys.
- **Externalised all visuals (skins + stones)** — the mod is a skin-agnostic framework now. Mon skins
  are driven by aspects → resolvers (substitute doll default), and any datapack/resource-pack resolver
  with `order > 5` overrides them; an op applies a custom skin with **`/megacobble skin set <aspect>`**
  (`clear` resets). Stone textures/models were moved out to `../megacobble-assets/`; stones keep their
  `custom_data` identity + a `custom_model_data` hook for an external pack to re-skin.
- **Parked the Mega Venusaur model** — moved the custom Mega Venusaur model/texture/animation/poser,
  the Blockbench `model-dev/` project, and the base-model `model-workspace/` reference copies out of
  the mod into `../megacobble-assets/`, and reverted Venusaur's resolver to the **substitute doll**.
  The visual pipeline is being reworked toward changing a Pokémon's model/texture by command and
  adding custom mons by command.
- **Server-authoritative items (vanilla + `custom_data`)** — Mega Stones and the Key Stone are no
  longer registered items; they're vanilla `amethyst_shard`s carrying a `minecraft:custom_data` tag
  (`{megacobble:{id:"venusaurite"}}`) plus an `item_name`. The Showdown bridge, key-item gate, world
  mega, and wheel all identify stones by tag instead of item id, so the stones work on a server for
  **clients without the mod** (a resource pack will re-skin them by `custom_model_data` next). Dropped
  the registered items + creative tab; added **`/megacobble give <stone> [count]`** and a
  **named-variant system** (`variants.json` + `/megacobble variant`).
- **World (out-of-battle) Mega Evolution** — mega-evolve for overworld exploration and riding via a
  "Mega Evolve" entry on Cobblemon's shift-right-click interaction wheel (hooked through
  `POKEMON_INTERACTION_GUI_CREATION`, modded clients only) or the new server-authoritative
  `/megacobble worldmega` command (universal, incl. vanilla clients). Mega forms inherit the base
  species' rideability, so you can ride them while mega'd; world megas revert at battle start so the
  Showdown in-battle mega applies cleanly. Gated like a battle mega (Key Stone + held Mega Stone) and
  configurable via `config/megacobble.json` / `/megacobble config` (`worldMegaEnabled`,
  `requireKeyStone`, `requireMegaStone`, `allowRideInMega`, `revertOnBattleStart`).
- **Mega Venusaur 3D model** — the first real per-mega model, replacing the substitute doll.
  Custom-built in Blockbench on top of the base Venusaur rig: 6 big weeping fronds (each on its
  own tree2-side origin, lengthened by tiling the mid-leaf texture) + a 6-frond low drooping
  collar, plus a forehead flower and a back flower (two-layer petals, mirror-built). Foliage
  motion is a **procedural MoLang** `fronds_droop` animation (baseline = posed droop, 60°/segment
  phase lag, amplitude rising to the tip) authored the same way as base Cobblemon idles, layered
  with the base body idle. Wired through a `megacobble` model + poser + animation and a resolver
  that maps Venusaur's `mega` aspect to it.
- **Base-model workspace** — copied the base Cobblemon model, texture, animation, poser, and
  resolver files for every mega-capable species (61 of the 64; **abomasnow / audino / diancie
  are not yet modelled in Cobblemon 1.7.3**) into a git-ignored `model-workspace/`, grouped by
  Pokédex-numbered folder (e.g. `0003_venusaur/`). These are local working references for
  building the real mega models in Blockbench — Cobblemon's own art, not shipped.
- **Shiny support** — mega forms (and the Eternal Floette base) render the **shiny**
  substitute doll for shiny Pokémon, the normal doll otherwise.
- **18 Legends Z-A megas** via a data-driven custom-mega pipeline (`species_additions` +
  Cobblemon datapack abilities + a battle-start sim injector). Adds the new abilities
  Dragonize, Piercing Drill, and Mega Sol (stub). Introduces per-stone `requiredAspect`,
  used to gate **Floettite** to AZ's Eternal-Flower Floette.
- **Native Showdown mega** — Mega Evolution now runs through Cobblemon's bundled Pokémon
  Showdown sim, so the classic megas get **real in-battle stats / typing / ability**. Uses a
  custom `HeldItemManager` (exposes our stones to the sim), a Key Stone → key-item bridge,
  and a `MEGA_EVOLUTION` hook for the visual. Replaced the earlier custom button + packet.
- **All 47 classic Mega Stones** (Gen 6 / ORAS) in a dedicated **Mega Stones** creative tab.
- **First working Mega Evolution (Venusaur)** — Key Stone + Venusaurite, an in-battle
  Mega button, the form transform, the substitute-doll placeholder model, name change, and
  revert on battle end / flee / faint.
- **Initial scaffold** — Fabric Loom project targeting Minecraft 1.21.1, Cobblemon 1.7.3,
  Java 21.

## License

All rights reserved (private).
