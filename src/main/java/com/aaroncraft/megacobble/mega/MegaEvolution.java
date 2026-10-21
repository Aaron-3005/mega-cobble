package com.aaroncraft.megacobble.mega;

import com.aaroncraft.megacobble.MegaCobble;
import com.aaroncraft.megacobble.config.MegaCobbleConfig;
import com.aaroncraft.megacobble.item.MegaItems;
import com.aaroncraft.megacobble.item.MegaStones;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.abilities.Ability;
import com.cobblemon.mod.common.api.abilities.PotentialAbility;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.net.messages.client.battle.BattleSwitchPokemonPacket;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mega Evolution glue around Cobblemon's native (Showdown-driven) mega.
 *
 * <p>Showdown computes the real in-battle mega (stats, typing, ability) for the classic mega-capable
 * species. Our job is two-fold:</p>
 * <ul>
 *   <li>{@link #syncKeyStone} — bridge our Key Stone item to Cobblemon's key-item gate so the native
 *       mega button appears only when the player brought a Key Stone.</li>
 *   <li>{@link #applyMega} — when Showdown mega evolves a Pokémon, mirror the form on the Minecraft
 *       side (model / name), and {@link #revert} it when the battle ends.</li>
 * </ul>
 */
public final class MegaEvolution {

    private MegaEvolution() {}

    /** Cobblemon's abstract key-item flag that {@code ShowdownActionRequest.sanitize()} checks for mega. */
    private static final ResourceLocation KEY_STONE_KEY_ITEM =
        ResourceLocation.fromNamespaceAndPath("cobblemon", "key_stone");

    /** Pre-mega state kept so a Pokémon can be reverted at battle end / faint / flee. */
    private static final Map<UUID, Original> MEGA_STATES = new ConcurrentHashMap<>();

    /** Pre-mega state for out-of-battle ("world") megas; kept separate from the in-battle states. */
    private static final Map<UUID, Original> WORLD_MEGA_STATES = new ConcurrentHashMap<>();

    /**
     * In-battle megas tracked with the battle they happened in, so they can be reverted when that
     * battle ends by ANY path — including custom/forced/drawn battles that post no win/flee event.
     */
    private static final Map<UUID, BattleMega> BATTLE_MEGAS = new ConcurrentHashMap<>();

    private record Original(Set<String> forcedAspects, MutableComponent nickname, Ability ability) {}

    private static final String SAVED_MEGA_STATE = "megacobble:original";

    /** Save before changing the Pokemon: logout can serialize its party before battle cleanup. */
    private static Original rememberOriginal(Pokemon pokemon, String mode) {
        Original existing = readOriginal(pokemon, mode);
        if (existing != null) return existing;
        Original original = new Original(new HashSet<>(pokemon.getForcedAspects()),
            pokemon.getNickname(), pokemon.getAbility());
        CompoundTag saved = new CompoundTag();
        saved.putString("mode", mode);
        ListTag aspects = new ListTag();
        original.forcedAspects().forEach(aspect -> aspects.add(StringTag.valueOf(aspect)));
        saved.put("aspects", aspects);
        saved.put("ability", Ability.getCODEC().encodeStart(NbtOps.INSTANCE, original.ability()).getOrThrow());
        if (original.nickname() != null) {
            saved.putString("nickname", Component.Serializer.toJson(original.nickname(),
                Cobblemon.INSTANCE.getImplementation().server().registryAccess()));
        }
        pokemon.getPersistentData().put(SAVED_MEGA_STATE, saved);
        return original;
    }

    private static Original readOriginal(Pokemon pokemon, String mode) {
        CompoundTag saved = pokemon.getPersistentData().getCompound(SAVED_MEGA_STATE);
        if (!mode.equals(saved.getString("mode"))) return null;
        Set<String> aspects = new HashSet<>();
        for (Tag aspect : saved.getList("aspects", Tag.TAG_STRING)) aspects.add(aspect.getAsString());
        Ability ability = Ability.getCODEC().parse(NbtOps.INSTANCE, saved.get("ability")).getOrThrow();
        MutableComponent nickname = saved.contains("nickname", Tag.TAG_STRING)
            ? Component.Serializer.fromJson(saved.getString("nickname"),
                Cobblemon.INSTANCE.getImplementation().server().registryAccess()) : null;
        return new Original(aspects, nickname, ability);
    }

    private static void restoreOriginal(Pokemon pokemon, Original original) {
        pokemon.setForcedAspects(original.forcedAspects());
        pokemon.setAbility$common(original.ability());
        pokemon.setNickname(original.nickname());
        pokemon.getPersistentData().remove(SAVED_MEGA_STATE);
    }

    /** Restores saved temporary forms after a reload, before send-out or a new battle. */
    public static void recoverOrphanedMega(Pokemon pokemon) {
        if (MEGA_STATES.containsKey(pokemon.getUuid()) || WORLD_MEGA_STATES.containsKey(pokemon.getUuid())) return;
        if (pokemon.getPersistentData().contains(SAVED_MEGA_STATE, Tag.TAG_COMPOUND)) {
            Original original = readOriginal(pokemon, "battle");
            if (original == null) original = readOriginal(pokemon, "world");
            if (original != null) {
                restoreOriginal(pokemon, original);
                MegaCobble.LOGGER.info("[Mega Cobble] Recovered saved Mega state for {}.", pokemon.getUuid());
            }
            return;
        }
        // Older versions saved no original state. Only migrate our forced Mega + generated name,
        // leaving intentionally configured forms/custom nicknames belonging to other mods alone.
        FormData form = pokemon.getForm();
        String generated = buildMegaName(pokemon.getSpecies().getName(), form.getName());
        String nickname = pokemon.getNickname() == null ? "" : pokemon.getNickname().getString();
        if (!form.getName().toLowerCase(Locale.ROOT).startsWith("mega")
            || !(nickname.equals(generated) || nickname.equals(generated.substring(0, Math.min(12, generated.length()))))) {
            return;
        }
        Set<String> aspects = new HashSet<>(pokemon.getForcedAspects());
        if (!aspects.removeIf(aspect -> aspect.equals("mega") || aspect.startsWith("mega-"))) return;
        Ability oldAbility = pokemon.getAbility();
        pokemon.setForcedAspects(aspects);
        // Some old saves still have their base ability. Keep it if it belongs to the restored form.
        for (PotentialAbility potential : pokemon.getForm().getAbilities()) {
            if (potential.getTemplate() != null && potential.getTemplate().equals(oldAbility.getTemplate())) {
                pokemon.setAbility$common(oldAbility);
                break;
            }
        }
        // Forced Mega-only abilities bypass automatic form updates, so choose a valid base slot.
        boolean valid = false;
        for (PotentialAbility potential : pokemon.getForm().getAbilities()) {
            if (pokemon.getAbility().getTemplate().equals(potential.getTemplate())) valid = true;
        }
        if (!valid) pokemon.rollAbility();
        pokemon.setNickname(null);
        MegaCobble.LOGGER.info("[Mega Cobble] Recovered legacy Mega state for {}; original nickname/slot was not saved.",
            pokemon.getUuid());
    }

    /** Reconnects start outside the old battle, including reconnects in the same server process. */
    public static void recoverPlayerMegas(ServerPlayer player) {
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
            revert(pokemon);
            revertWorldMega(pokemon);
            recoverOrphanedMega(pokemon);
        }
    }

    /** Never carry object references across integrated-server worlds. Durable snapshots survive. */
    public static void clearSessionState() {
        MEGA_STATES.clear();
        WORLD_MEGA_STATES.clear();
        BATTLE_MEGAS.clear();
    }

    private record BattleMega(Pokemon pokemon, PokemonBattle battle) {}

    /** Outcome of a world (out-of-battle) Mega Evolution request, for command / packet feedback. */
    public enum WorldMegaResult {
        APPLIED, REVERTED, ALREADY_MEGA, NOT_MEGA, DISABLED, NO_KEY_STONE, NO_MEGA_STONE,
        WRONG_MEGA_STONE, NO_MEGA_FORM
    }

    /**
     * Bridges our Key Stone item to Cobblemon's native mega gate. If the player is carrying a Key
     * Stone in their inventory, grant the {@code cobblemon:key_stone} key item (so {@code sanitize()}
     * lets the native mega gimmick through); otherwise remove it. Called at battle start.
     */
    public static void syncKeyStone(ServerPlayer player) {
        boolean hasKeyStone = player.getInventory().hasAnyMatching(MegaItems::isKeyStone);
        Set<ResourceLocation> keyItems =
            Cobblemon.INSTANCE.getPlayerDataManager().getGenericData(player).getKeyItems();
        if (hasKeyStone) {
            keyItems.add(KEY_STONE_KEY_ITEM);
            MegaCobble.LOGGER.info("[Mega Cobble] {} brought a Key Stone -> mega enabled this battle.",
                player.getGameProfile().getName());
        } else {
            keyItems.remove(KEY_STONE_KEY_ITEM);
        }
    }

    /**
     * Mirrors a Showdown mega evolution onto the Minecraft side: forces the mega aspect (which drives
     * the rendered model and re-selects the mega form) and renames the Pokémon. Battle stats/typing/
     * ability are Showdown's job; this is the visual + data form. The pre-mega state is recorded for
     * {@link #revert}.
     */
    public static void applyMega(Pokemon target, PokemonBattle battle) {
        recoverOrphanedMega(target);
        MegaStones.MegaStone stone = MegaStones.byCustomData(target.heldItem());
        // Stoneless megas (Rayquaza via Dragon Ascent) hold no Mega Stone, so resolve their form from
        // the species' first mega form instead — otherwise the Showdown-driven mega would apply the
        // stats but never switch the Minecraft-side model, aspect, or name.
        FormData megaForm = stone != null
            ? findFormByName(target, stone.form())
            : (knowsStonelessMegaMove(target) ? firstMegaForm(target) : null);
        if (megaForm == null) {
            return;
        }
        MEGA_STATES.computeIfAbsent(target.getUuid(), uuid -> rememberOriginal(target, "battle"));
        BATTLE_MEGAS.put(target.getUuid(), new BattleMega(target, battle));

        Set<String> forced = new HashSet<>(target.getForcedAspects());
        forced.addAll(megaForm.getAspects());
        target.setForcedAspects(forced);
        target.setForm(megaForm);
        applyMegaAbility(target, megaForm);
        target.setNickname(Component.literal(buildMegaName(target.getSpecies().getName(), megaForm.getName())));
        refreshBattleTeam(target, battle);
        refreshBattleName(target, battle);

        MegaCobble.LOGGER.info("[Mega Cobble] In-battle mega: {} -> {} with ability {} ({}).",
            target.getSpecies().getName(), megaForm.getName(), target.getAbility().getName(), target.getUuid());
    }

    /** Forced base abilities bypass Cobblemon's automatic form update; explicitly use the Mega slot. */
    private static void applyMegaAbility(Pokemon pokemon, FormData megaForm) {
        for (PotentialAbility potential : megaForm.getAbilities()) {
            if (potential.getTemplate() != null) {
                pokemon.updateAbility(potential.getTemplate().create(false, potential.getPriority()));
                return;
            }
        }
    }

    /**
     * The battle summary uses a separate team snapshot, not the party object or active HUD tile.
     * Refresh that snapshot for its owner as well as the ordinary Pokemon update packets.
     */
    private static void refreshBattleTeam(Pokemon pokemon, PokemonBattle battle) {
        for (BattleActor actor : battle.getActors()) {
            for (BattlePokemon member : actor.getPokemonList()) {
                if (member.getEffectedPokemon().getUuid().equals(pokemon.getUuid())) {
                    member.sendUpdate();
                    return;
                }
            }
        }
    }

    /**
     * Pushes the mega name to the in-battle HUD. Cobblemon captures a battler's name label when it's
     * sent out and never refreshes it mid-battle — its own forme changes (Aegislash, etc.) behave the
     * same way, so renaming the Pokémon alone updates the overworld nameplate but not the battle HUD.
     * Re-send the active slot the way Cobblemon's own switch does: an ally-perspective and an
     * opponent-perspective {@link BattleSwitchPokemonPacket} routed by {@code sendSidedUpdate}. The
     * client handler only slides the info tile (carrying the new name + current HP) — it does not
     * recall or re-send-out the model. No-op if the Pokémon isn't an active battler.
     */
    private static void refreshBattleName(Pokemon pokemon, PokemonBattle battle) {
        for (ActiveBattlePokemon active : battle.getActivePokemon()) {
            BattlePokemon battlePokemon = active.getBattlePokemon();
            if (battlePokemon == null
                || !battlePokemon.getEffectedPokemon().getUuid().equals(pokemon.getUuid())) {
                continue;
            }
            String pnx = active.getPNX();
            BattlePokemon illusion = active.getIllusion();
            BattleSwitchPokemonPacket ally = new BattleSwitchPokemonPacket(pnx, battlePokemon, true, illusion);
            BattleSwitchPokemonPacket enemy = new BattleSwitchPokemonPacket(pnx, battlePokemon, false, illusion);
            battle.sendSidedUpdate(active.getActor(), ally, enemy, false);
            return;
        }
    }

    /**
     * Reverts a single Pokémon back to its pre-mega state, if it was mega evolved. Restoring the
     * original forced aspects (dropping "mega") makes Cobblemon re-select the standard form, which
     * syncs to the client automatically.
     */
    public static void revert(Pokemon pokemon) {
        BattleMega battleMega = BATTLE_MEGAS.remove(pokemon.getUuid());
        Original original = MEGA_STATES.remove(pokemon.getUuid());
        if (original == null) original = readOriginal(pokemon, "battle");
        if (original == null) {
            return;
        }
        restoreOriginal(pokemon, original);
        if (battleMega != null && !battleMega.battle().getEnded()) {
            refreshBattleTeam(pokemon, battleMega.battle());
        }
        MegaCobble.LOGGER.info("[Mega Cobble] Reverted {} ({}) to normal form.",
            pokemon.getSpecies().getName(), pokemon.getUuid());
    }

    /** Reverts every mega-evolved Pokémon participating in the given battle. */
    public static void revertBattle(PokemonBattle battle) {
        for (BattleActor actor : battle.getActors()) {
            for (BattlePokemon battlePokemon : actor.getPokemonList()) {
                revert(battlePokemon.getEffectedPokemon());
            }
        }
    }

    /**
     * Safety net for addon battles that bypass the normal {@code BATTLE_ENDED} event. Once
     * a tracked in-battle mega's battle is over (ended, or removed from the registry), revert it.
     * Called every server tick; a no-op unless something is currently mega-evolved in battle.
     */
    public static void revertEndedBattleMegas() {
        if (BATTLE_MEGAS.isEmpty()) {
            return;
        }
        for (BattleMega battleMega : new ArrayList<>(BATTLE_MEGAS.values())) {
            PokemonBattle battle = battleMega.battle();
            boolean over = battle.getEnded()
                || BattleRegistry.INSTANCE.getBattle(battle.getBattleId()) == null;
            if (over) {
                revert(battleMega.pokemon());
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Out-of-battle ("world") Mega Evolution — exploration / riding. Purely the Minecraft-side form
    // + visual (no Showdown), gated like a battle mega (Key Stone + held Mega Stone) and reverted
    // when a battle starts so the real in-battle Showdown mega can take over cleanly.
    // ---------------------------------------------------------------------------------------------

    /** @return true if the Pokémon is currently world-mega'd (mega applied outside of battle). */
    public static boolean isWorldMega(Pokemon pokemon) {
        return WORLD_MEGA_STATES.containsKey(pokemon.getUuid());
    }

    /** Toggles world mega: reverts if already world-mega'd, otherwise tries to apply it. */
    public static WorldMegaResult toggleWorldMega(ServerPlayer player, Pokemon target) {
        if (isWorldMega(target)) {
            revertWorldMega(target);
            return WorldMegaResult.REVERTED;
        }
        return applyWorldMega(player, target);
    }

    /**
     * Applies an out-of-battle Mega Evolution to the target, honouring the config gate (feature
     * enabled, Key Stone carried, Mega Stone held). The mega form/aspect is forced on the Minecraft
     * side — Cobblemon syncs it to the client and persists it — without touching the battle sim.
     */
    public static WorldMegaResult applyWorldMega(ServerPlayer player, Pokemon target) {
        recoverOrphanedMega(target);
        MegaCobbleConfig cfg = MegaCobbleConfig.get();
        if (!cfg.worldMegaEnabled) {
            return WorldMegaResult.DISABLED;
        }
        if (isWorldMega(target)) {
            return WorldMegaResult.ALREADY_MEGA;
        }
        if (cfg.requireKeyStone && !playerHasKeyStone(player)) {
            return WorldMegaResult.NO_KEY_STONE;
        }
        MegaStones.MegaStone stone = MegaStones.byCustomData(target.heldItem());
        // A stone only unlocks *its own* species' mega. Nearly every stone declares form "Mega", so
        // without this the form lookup below would happily match any mega-capable species — letting
        // e.g. an Absolite mega-evolve an Abomasnow.
        if (stone != null && !stoneMatches(stone, target)) {
            if (cfg.requireMegaStone) {
                return WorldMegaResult.WRONG_MEGA_STONE;
            }
            stone = null; // stone requirement off: ignore the mismatched stone rather than block.
        }
        // Stoneless megas (e.g. Rayquaza) have a Mega form but no Mega Stone — they're unlocked by a
        // MOVE instead (Rayquaza -> Dragon Ascent), exactly as in-battle. The Mega Stone requirement
        // can't apply to them, so accept them when the Pokémon knows the unlock move.
        boolean stonelessMega = stone == null && knowsStonelessMegaMove(target);
        if (cfg.requireMegaStone && stone == null && !stonelessMega) {
            return WorldMegaResult.NO_MEGA_STONE;
        }
        FormData megaForm = stone != null ? findFormByName(target, stone.form()) : firstMegaForm(target);
        if (megaForm == null) {
            return WorldMegaResult.NO_MEGA_FORM;
        }
        inheritRidingFromSpecies(megaForm);

        WORLD_MEGA_STATES.put(target.getUuid(), rememberOriginal(target, "world"));

        Set<String> forced = new HashSet<>(target.getForcedAspects());
        forced.addAll(megaForm.getAspects());
        target.setForcedAspects(forced);
        target.setForm(megaForm);
        applyMegaAbility(target, megaForm);
        target.setNickname(Component.literal(buildMegaName(target.getSpecies().getName(), megaForm.getName())));

        MegaCobble.LOGGER.info("[Mega Cobble] World mega: {} -> {} ({}).",
            target.getSpecies().getName(), megaForm.getName(), target.getUuid());
        return WorldMegaResult.APPLIED;
    }

    /** Reverts a world-mega'd Pokémon to its pre-mega state. @return true if it was world-mega'd. */
    public static boolean revertWorldMega(Pokemon pokemon) {
        Original original = WORLD_MEGA_STATES.remove(pokemon.getUuid());
        if (original == null) original = readOriginal(pokemon, "world");
        if (original == null) {
            return false;
        }
        restoreOriginal(pokemon, original);
        MegaCobble.LOGGER.info("[Mega Cobble] Reverted world mega {} ({}).",
            pokemon.getSpecies().getName(), pokemon.getUuid());
        return true;
    }

    /**
     * Reverts world megas on every battling player's party at battle start (if enabled), so the
     * in-battle Showdown mega path applies to the base form rather than an already-mega'd one.
     */
    public static void revertWorldMegasForBattle(Iterable<ServerPlayer> players) {
        if (!MegaCobbleConfig.get().revertOnBattleStart) {
            return;
        }
        for (ServerPlayer player : players) {
            for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
                revertWorldMega(pokemon);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Named visual variants — the lever behind /megacobble variant. Forced aspects sync to the client
    // and persist; resolvers map aspect combinations to model / texture / poser (independently, so a
    // texture aspect can layer over a model aspect).
    // ---------------------------------------------------------------------------------------------

    /** Adds or removes all of a named variant's aspects at once. @return true if the set changed. */
    public static boolean setVariant(Pokemon pokemon, List<String> aspects, boolean present) {
        Set<String> forced = new HashSet<>(pokemon.getForcedAspects());
        boolean changed = present ? forced.addAll(aspects) : forced.removeAll(aspects);
        if (changed) {
            pokemon.setForcedAspects(forced);
        }
        return changed;
    }

    /**
     * Clears every forced aspect, releasing the Pokémon back to its default/calculated look (e.g. the
     * substitute doll for a mega, or whatever an installed datapack's feature decides). Use this to
     * undo a command-applied skin so a datapack can drive it again.
     */
    public static void clearForcedAspects(Pokemon pokemon) {
        if (!pokemon.getForcedAspects().isEmpty()) {
            pokemon.setForcedAspects(new HashSet<>());
        }
    }

    /** @return true if the player is carrying a Key Stone in their inventory. */
    public static boolean playerHasKeyStone(ServerPlayer player) {
        return player.getInventory().hasAnyMatching(MegaItems::isKeyStone);
    }

    /**
     * @return true if this Mega Stone actually unlocks this Pokémon's mega — i.e. it is that
     * species' stone, and (for stones that name one, e.g. Floettite -> Eternal Flower Floette) the
     * Pokémon has the required form aspect. Shared by the server gate and the client wheel hint so
     * both agree on what counts as the "right" stone.
     */
    public static boolean stoneMatches(MegaStones.MegaStone stone, Pokemon pokemon) {
        if (stone == null) {
            return false;
        }
        String species = pokemon.getSpecies().getResourceIdentifier().getPath();
        if (!stone.species().equalsIgnoreCase(species)) {
            return false;
        }
        String requiredAspect = stone.requiredAspect();
        return requiredAspect == null || pokemon.getAspects().contains(requiredAspect);
    }

    /** @return true if the species has any form whose name starts with "Mega". */
    public static boolean hasMegaForm(Pokemon pokemon) {
        return firstMegaForm(pokemon) != null;
    }

    /**
     * Stoneless megas: species that Mega Evolve with no Mega Stone, unlocked by a specific MOVE
     * instead. Maps the species (resource path) to the required move's normalised id. Rayquaza is the
     * canonical case (Dragon Ascent). In battle the bundled Showdown enforces this move; out of battle
     * we enforce it here so a Rayquaza that doesn't know Dragon Ascent still can't world-mega.
     */
    private static final Map<String, String> STONELESS_MEGA_MOVES = Map.of("rayquaza", "dragonascent");

    /**
     * Stoneless megas -> the VIRTUAL Showdown mega-stone id the mod feeds the sim (never the player).
     * Mega Rayquaza is unlocked by Dragon Ascent, not a Mega Stone, and Cobblemon's Gen-9 sim doesn't
     * reliably honor the move-based mega gate — but the sim's ITEM-based canMegaEvo has no gate. So the
     * mod tells the sim a Dragon-Ascent Rayquaza "holds" this item, offering Mega Rayquaza with no stone
     * the player ever holds or obtains.
     */
    private static final Map<String, String> STONELESS_MEGA_SIM_ITEM = Map.of("rayquaza", "rayquazite");

    /**
     * @return the virtual Showdown mega-stone id to feed the sim for a stoneless mega that currently
     * knows its unlock move (e.g. "rayquazite" for a Dragon-Ascent Rayquaza), or null otherwise.
     */
    public static String stonelessMegaSimItem(Pokemon pokemon) {
        if (!knowsStonelessMegaMove(pokemon)) {
            return null;
        }
        return STONELESS_MEGA_SIM_ITEM.get(pokemon.getSpecies().getResourceIdentifier().getPath());
    }

    /**
     * @return true if this Pokémon is a stoneless mega (e.g. Rayquaza) AND currently knows the move
     * that unlocks its mega. Used to let such megas skip the held-Mega-Stone requirement out of battle.
     */
    public static boolean knowsStonelessMegaMove(Pokemon pokemon) {
        String species = pokemon.getSpecies().getResourceIdentifier().getPath();
        String requiredMove = STONELESS_MEGA_MOVES.get(species);
        if (requiredMove == null) {
            return false;
        }
        for (Move move : pokemon.getMoveSet().getMoves()) {
            if (move != null && normalizeMoveId(move.getName()).equals(requiredMove)) {
                return true;
            }
        }
        return false;
    }

    /** Normalise a move name/id to the sim's id form: lowercase, letters/digits only ("Dragon Ascent" -> "dragonascent"). */
    private static String normalizeMoveId(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** Mega forms already checked for the riding-seats patch (per-form, once). */
    private static final Set<FormData> RIDING_PATCHED = ConcurrentHashMap.newKeySet();

    /**
     * Cobblemon ships several Mega forms with an empty riding seat list, so they can't be ridden even
     * when the base species can. Drop the form's riding override (its {@code _riding}) so it inherits
     * the base species' riding — including its seats — letting players ride the Pokémon in mega form.
     * No-op if the form is already rideable, or if the base species itself isn't rideable.
     */
    private static void inheritRidingFromSpecies(FormData megaForm) {
        if (!RIDING_PATCHED.add(megaForm)) {
            return;
        }
        try {
            if (!megaForm.getRiding().getSeats().isEmpty()) {
                return;
            }
            Field ridingField = FormData.class.getDeclaredField("_riding");
            ridingField.setAccessible(true);
            ridingField.set(megaForm, null);
        } catch (Exception e) {
            MegaCobble.LOGGER.warn("[Mega Cobble] Could not make {} rideable in its mega form.",
                megaForm.getName(), e);
        }
    }

    /** @return the species' first form whose name starts with "Mega" (case-insensitive), or null. */
    private static FormData firstMegaForm(Pokemon pokemon) {
        for (FormData form : pokemon.getSpecies().getForms()) {
            if (form.getName().toLowerCase(Locale.ROOT).startsWith("mega")) {
                return form;
            }
        }
        return null;
    }

    /** Finds the form with the given name (case-insensitive), e.g. "Mega", "Mega-X". */
    public static FormData findFormByName(Pokemon pokemon, String formName) {
        for (FormData form : pokemon.getSpecies().getForms()) {
            if (form.getName().equalsIgnoreCase(formName)) {
                return form;
            }
        }
        return null;
    }

    /**
     * Builds the transformed display name using the species' default (correctly-cased) name:
     * {@code Mega-<Species>[-X|-Y|-Z]}. "Mega" -> "Mega-Venusaur"; "Mega-X" -> "Mega-Charizard-X".
     */
    public static String buildMegaName(String speciesName, String formName) {
        String suffix = "";
        int dash = formName.indexOf('-');
        if (dash >= 0 && dash + 1 < formName.length()) {
            suffix = "-" + formName.substring(dash + 1).toUpperCase(Locale.ROOT); // "-X", "-Y", "-Z"
        }
        return "Mega-" + speciesName + suffix;
    }
}
