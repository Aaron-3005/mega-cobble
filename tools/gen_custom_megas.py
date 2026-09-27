"""
Generates the Legends Z-A / custom megas through the proven custom-mega pipeline:
  - data/cobblemon/species_additions/<sp>_mega.json   (the mega FORM on the Cobblemon side)
  - custom_mega_showdown.json                          (sim item defs + new-ability JS)
  - the substitute resolver (placeholder visual; real skins come from an external pack)
  - appends the stones to megastones.json + the ability lang (gen_megastones.py runs first)

Stones are vanilla items identified by custom_data, so no item textures/models are generated.
Run AFTER gen_megastones.py (which produces the classic 47). Reads za_megas.json for stats/types/abilities.
"""
import os, json
from pathlib import Path

ROOT = str(Path(__file__).resolve().parents[1] / "src/main/resources")

# Species we ship a mega for (complete data in za_megas.json) -> canonical Mega Stone name.
# A species with multiple mega formes maps to a {form-name: stone-name} dict (e.g. Raichu X/Y).
STONE_NAMES = {
 'greninja':'Greninjite','meganium':'Meganiumite','feraligatr':'Feraligite','dragonite':'Dragoninite',
 'excadrill':'Excadrite','chandelure':'Chandelurite','chesnaught':'Chesnaughtite','delphox':'Delphoxite',
 'clefable':'Clefablite','victreebel':'Victreebelite','starmie':'Starminite','skarmory':'Skarmorite',
 'froslass':'Froslassite','emboar':'Emboarite','golurk':'Golurkite','floette':'Floettite',
 'meowstic':'Meowsticite','hawlucha':'Hawluchanite','drampa':'Drampanite',
 # Mega Dimension DLC megas now in Pokemon Champions
 'barbaracle':'Barbaraclite','chimecho':'Chimechite','crabominable':'Crabominite','dragalge':'Dragalgite',
 'eelektross':'Eelektrite','falinks':'Falinksite','glimmora':'Glimmorite','malamar':'Malamarite',
 'pyroar':'Pyroarite','scolipede':'Scolipite','scovillain':'Scovillite','scrafty':'Scraftite',
 'staraptor':'Staraptite',
 'raichu':{'Mega-X':'Raichunite X','Mega-Y':'Raichunite Y'},
 # DLC megas; see docs/mega-abilities-audit.md for confirmed and provisional abilities.
 # Garchomp/Lucario/Absol get a NEW "Z" mega distinct from their classic ORAS mega.
 'darkrai':'Darkraite','garchomp':'Garchompite Z','lucario':'Lucarionite Z','heatran':'Heatranite',
 'magearna':'Magearnite','zeraora':'Zeraorite','baxcalibur':'Baxcalibite','golisopod':'Golisopite',
 'tatsugiri':'Tatsugirite','absol':'Absolite Z',
 # Legendary base-game Z-A mega absent from Champions; megas only from Complete Forme (see below).
 'zygarde':'Zygardite',
}

# Stones restricted to a specific form aspect: the held-item manager only exposes them to the
# sim when the Pokemon has the aspect, so other forms can't mega. (Mega Floette = AZ's Eternal.)
REQUIRED_ASPECT = {
 'floette':'flower-eternal',
 'zygarde':'complete-percent',  # Mega Zygarde forms only from the Complete Forme (100%).
}

# Restricted base forms that Cobblemon already ships a model for: don't hijack them with a
# substitute resolver variation. (Eternal Floette has no model -> not listed -> gets a placeholder.)
REQ_BASE_HAS_MODEL = {'zygarde'}

# Editable ability sources; the same generated object is registered on both sides.
ABILITY_DIR = Path(__file__).resolve().parent / 'abilities'
NEW_ABILITY_ID = {'mega_sol':'megasol', 'piercing_drill':'piercingdrill',
                  'spicy_spray':'spicyspray', 'fire_mane':'firemane', 'aura_guard':'auraguard'}
NEW_ABILITY_DESC = {
 'dragonize': "This Pokemon's eligible Normal-type moves become Dragon-type and gain 20% power.",
 'piercingdrill': "This Pokemon's contact moves hit through protection for one quarter of normal damage.",
 'megasol': "This Pokemon uses its moves as if harsh sunlight were active, without changing the weather.",
 'spicyspray': "When this Pokemon is hit by a damaging move, the attacker is burned.",
 'firemane': "This Pokemon's Fire-type attacks are powered up by 50%.",
 'eelevate': "This Pokemon floats above the ground. Knocking out a Pokemon with an attack raises its highest stat by 1.",
 'auraguard': "Halves the damage this Pokemon takes from contact moves.",
}
ABILITY_NAMES = {'dragonize':'Dragonize', 'piercingdrill':'Piercing Drill', 'megasol':'Mega Sol',
                 'spicyspray':'Spicy Spray', 'firemane':'Fire Mane', 'eelevate':'Eelevate', 'auraguard':'Aura Guard'}
compatibility = (ABILITY_DIR / 'compatibility.js').read_text(encoding='utf-8').strip()

def ability_source(ability):
    source = (ABILITY_DIR / (ability + '.js')).read_text(encoding='utf-8')
    source = source.replace('__INSTALL_COMPAT__', compatibility)
    # GraalShowdownService.sendRegistryData removes newlines before eval. Our
    # source comments occupy whole lines; omit them so they cannot swallow code.
    return '\n'.join(line for line in source.splitlines()
                     if not line.lstrip().startswith('//')).strip()

NEW_ABILITY_JS = {
    ability: (name, ability_source(ability))
    for ability, name in ABILITY_NAMES.items()
}

ZA = json.load(open(os.path.join(ROOT,'za_megas.json'),encoding='utf-8'))['megas']

manifest_path = os.path.join(ROOT,'megastones.json')
lang_path = os.path.join(ROOT,'assets/megacobble/lang/en_us.json')
manifest = json.load(open(manifest_path,encoding='utf-8'))
lang = json.load(open(lang_path,encoding='utf-8'))

# One generated file per species; overwrite it in place on regeneration.
sa_dir = os.path.join(ROOT,'data/cobblemon/species_additions')
os.makedirs(sa_dir,exist_ok=True)

def sub(asp, shiny=False):
    return {"aspects":([asp,"shiny"] if shiny else [asp]),"poser":"cobblemon:substitute",
            "model":"cobblemon:substitute.geo",
            "texture":"cobblemon:textures/pokemon/substitute"+("_shiny" if shiny else "")+".png","layers":[]}

def stone_name_for(sp, form):
    sn=STONE_NAMES[sp]
    return sn[form] if isinstance(sn, dict) else sn

# Group the shippable mega entries by species: a species can have more than one mega forme
# (e.g. Raichu -> Mega-X / Mega-Y), which share one resolver + one species_addition but get
# their own Mega Stone each.
from collections import OrderedDict
by_species=OrderedDict()
for e in ZA:
    sp=e['species']
    if sp not in STONE_NAMES: continue
    if e.get('ability') is None or e.get('baseStats') is None: continue
    by_species.setdefault(sp, []).append(e)

held_items = {}
abilities = {}
count=0; num=9300
for sp, entries in by_species.items():
    species_cap=sp.capitalize()
    req = REQUIRED_ASPECT.get(sp)

    # one substitute resolver per species, covering every mega forme's aspect (+ shiny)
    variations=[]
    for e in entries:
        variations += [sub(e['aspect']), sub(e['aspect'], True)]
    # Also override the restricted base form with a substitute -- but only when Cobblemon has no
    # model for it (e.g. AZ's Eternal Floette). Zygarde's Complete Forme IS modeled, so leave it be.
    if req and sp not in REQ_BASE_HAS_MODEL:
        variations += [sub(req), sub(req, True)]
    json.dump({"species":f"cobblemon:{sp}","order":5,"variations":variations},
              open(f"{ROOT}/assets/cobblemon/bedrock/pokemon/resolvers/megacobble/{sp}.json","w"),indent=2)

    # one species_addition per species, holding all of its mega forms (so X/Y live together)
    forms=[]
    for e in entries:
        ab_id=NEW_ABILITY_ID.get(e['ability'], e['ability'])
        form_obj={"name":e['form'],"baseStats":e['baseStats'],"primaryType":e['type1'],
                  "abilities":[ab_id],"aspects":[e['aspect']],"battleOnly":True,
                  "requiredItem":stone_name_for(sp, e['form'])}
        if e.get('type2'): form_obj["secondaryType"]=e['type2']
        if 'maleRatio' in e: form_obj['maleRatio']=e['maleRatio']
        forms.append(form_obj)
    json.dump({"target":f"cobblemon:{sp}","forms":forms},
              open(f"{sa_dir}/{sp}_mega.json","w"),indent=2)

    # per-forme: stone manifest entry + sim mega-stone item (+ new-ability injection)
    for e in entries:
        ab_id=NEW_ABILITY_ID.get(e['ability'], e['ability'])
        stone_name=stone_name_for(sp, e['form'])
        stone_id=stone_name.lower().replace(' ','_')
        showdown_item=''.join(ch for ch in stone_name.lower() if ch.isalnum())
        mega_species="%s-%s"%(species_cap, e['form'])   # Glimmora-Mega, Raichu-Mega-X, ...
        entry={"stone":stone_id,"name":stone_name,"species":sp,"form":e['form'],"aspect":e['aspect']}
        if req: entry["requiredAspect"]=req
        if not any(r['stone']==stone_id for r in manifest):
            manifest.append(entry)
        held_items[showdown_item]=("{ name: '%s', megaStone: '%s', megaEvolves: '%s', itemUser: ['%s'], num: %d, gen: 8,"
            " onTakeItem(item, source) { if (item.megaEvolves === source.baseSpecies.baseSpecies) return false; return true; } }"
            % (stone_name, mega_species, species_cap, species_cap, num))
        num+=1
        if ab_id in NEW_ABILITY_JS:
            abilities[ab_id]=NEW_ABILITY_JS[ab_id][1]
            lang[f"cobblemon.ability.{ab_id}"]=NEW_ABILITY_JS[ab_id][0]
            lang[f"cobblemon.ability.{ab_id}.desc"]=NEW_ABILITY_DESC[ab_id]
        count+=1

# Mega Rayquaza is stoneless (Dragon Ascent), so it has no Mega Stone in the manifest. But Cobblemon's
# Gen-9 sim only offers a MOVE-based mega when the ruleset carries the "past" tag (which doesn't reliably
# reach the in-process sim), whereas the ITEM-based canMegaEvo branch has no gate. So inject a virtual
# "Rayquazite": the mod hands its Showdown id to the SIM only (never to the player, no held item) for a
# Dragon-Ascent Rayquaza, and the sim then offers Mega Rayquaza. Rayquaza-Mega already exists in the sim.
held_items['rayquazite']=("{ name: 'Rayquazite', megaStone: 'Rayquaza-Mega', megaEvolves: 'Rayquaza',"
    " itemUser: ['Rayquaza'], num: %d, gen: 8,"
    " onTakeItem(item, source) { if (item.megaEvolves === source.baseSpecies.baseSpecies) return false; return true; } }"
    % num)
num+=1

json.dump(manifest, open(manifest_path,"w"),indent=2)
json.dump(lang, open(lang_path,"w",encoding='utf-8'),indent=2,ensure_ascii=False)

# New abilities ship as Cobblemon datapack ability files: this registers them on the Cobblemon side,
# which is REQUIRED for species_additions referencing them to parse (and for our form-data lookup).
ab_dir = os.path.join(ROOT,'data/megacobble/abilities')
os.makedirs(ab_dir,exist_ok=True)
for ab_id, js in abilities.items():
    open(f"{ab_dir}/{ab_id}.js","w").write(js + "\n")

# Cobblemon registers those abilities on its own side but does NOT forward them to the bundled sim,
# and it sends every mega forme to the sim with its abilities blanked to "No Ability". So the SAME
# mega-stone items AND ability defs are also injected straight into the sim at battle start (via
# ShowdownService / MegaShowdownInjector) — without the 'abilities' injection the custom ability is
# registered Cobblemon-side but has no effect in battle.
json.dump({
  "_comment":"Mega-stone item + custom-ability definitions for megas not in Cobblemon's bundled sim (Legends Z-A + custom). Injected at battle start via ShowdownService. Top-level keys MUST be valid sim registry types (ability, bagItem, heldItem, move, species). 'heldItem'/'ability' map a Showdown id to a JS def the sim's receiveData() evaluates. Abilities are ALSO shipped as Cobblemon datapack files under data/megacobble/abilities/ so species_additions referencing them parse.",
  "ability":abilities,
  "heldItem":held_items,
}, open(os.path.join(ROOT,'custom_mega_showdown.json'),"w"),indent=2)

print(f"Generated {count} custom megas. manifest now {len(manifest)} stones.")
print(f"  heldItem injections: {len(held_items)} | abilities (datapack + sim): {sorted(abilities.keys())}")
