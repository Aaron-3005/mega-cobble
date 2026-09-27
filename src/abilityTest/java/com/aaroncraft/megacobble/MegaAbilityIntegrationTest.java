package com.aaroncraft.megacobble;

import com.aaroncraft.megacobble.config.MegaCobbleConfig;
import com.aaroncraft.megacobble.item.MegaItems;
import com.aaroncraft.megacobble.item.MegaStones;
import com.aaroncraft.megacobble.mega.MegaEvolution;
import com.cobblemon.mod.common.api.abilities.Ability;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.net.NetworkPacket;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.net.messages.client.battle.BattleUpdateTeamPokemonPacket;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.FormData;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Runs in an isolated, headless Fabric server with the actual species additions loaded. */
public final class MegaAbilityIntegrationTest implements ModInitializer {
    private static RegistryAccess registries;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                registries = server.registryAccess();
                for (boolean forced : List.of(false, true)) {
                    checkLifecycle("garchomp ability=sandveil", "garchompite_z", "levitate", forced);
                    checkLifecycle("garchomp ability=roughskin", "garchompite_z", "levitate", forced);
                    checkLifecycle("garchomp ability=roughskin", "garchompite", "sandforce", forced);
                    for (boolean world : List.of(false, true)) {
                        checkReload("garchomp ability=sandveil", forced, world);
                        checkReload("garchomp ability=roughskin", forced, world);
                    }
                }
                for (String ability : List.of("sandveil", "levitate")) {
                    for (boolean forced : List.of(false, true)) checkLegacyRecovery(ability, forced);
                }
                checkFullRoster();
                Files.writeString(Path.of("ability-tests-passed.txt"), "24 focused checks + all 93 Mega form lifecycles passed\n");
                MegaCobble.LOGGER.info("[Mega ability tests] PASS: 24 focused checks + all 93 Mega form lifecycles");
            } catch (Exception | AssertionError failure) {
                MegaCobble.LOGGER.error("[Mega ability tests] FAIL", failure);
            } finally {
                server.halt(false);
            }
        });
    }

    private static void checkLifecycle(String properties, String stone, String megaAbility, boolean forced) {
        Pokemon pokemon = PokemonProperties.Companion.parse(properties).create();
        pokemon.getAbility().setForced$common(forced);
        Ability original = pokemon.getAbility();
        var originalForm = pokemon.getForm();
        var originalAspects = pokemon.getForcedAspects();
        pokemon.setNickname(Component.literal("Test partner"));
        pokemon.swapHeldItem(MegaItems.createStone(MegaStones.byStoneId(stone)), false, false);

        RecordingActor owner = new RecordingActor(pokemon);
        RecordingActor opponent = new RecordingActor(PokemonProperties.Companion.parse("magikarp").create());
        PokemonBattle battle = new PokemonBattle(BattleFormat.Companion.getGEN_9_SINGLES(),
            new BattleSide(owner), new BattleSide(opponent));

        MegaEvolution.applyMega(pokemon, battle);
        check(pokemon.getAbility().getName().equals(megaAbility), "Mega ability did not change");
        check(owner.abilities.equals(List.of(megaAbility)), "Owner's battle summary was not refreshed");
        check(opponent.abilities.isEmpty(), "Private team data was sent to the opponent");
        // Duplicate events must not overwrite the pre-Mega hidden/forced slot.
        MegaEvolution.applyMega(pokemon, battle);
        MegaEvolution.revert(pokemon);
        check(pokemon.getAbility() == original, "Original ability/slot was not restored");
        check(pokemon.getForm() == originalForm, "Original form was not restored");
        check(pokemon.getForcedAspects().equals(originalAspects), "Original aspects were not restored");
        check(pokemon.getNickname().getString().equals("Test partner"), "Nickname was not restored");
        check(owner.abilities.getLast().equals(original.getName()), "Reverted summary was not refreshed");

        MegaCobbleConfig config = MegaCobbleConfig.get();
        boolean requiredKey = config.requireKeyStone;
        config.requireKeyStone = false;
        try {
            check(MegaEvolution.applyWorldMega(null, pokemon) == MegaEvolution.WorldMegaResult.APPLIED,
                "World Mega did not apply");
            check(pokemon.getAbility().getName().equals(megaAbility), "World Mega ability did not change");
            check(MegaEvolution.revertWorldMega(pokemon), "World Mega did not revert");
            check(pokemon.getAbility() == original, "World Mega lost the base ability/slot");
            check(pokemon.getForm() == originalForm, "World Mega lost the base form");
        } finally {
            config.requireKeyStone = requiredKey;
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void checkFullRoster() {
        check(MegaStones.all().size() == 92, "Unexpected Mega Stone count");
        for (MegaStones.MegaStone stone : MegaStones.all()) {
            Pokemon pokemon = PokemonProperties.Companion.parse(stone.species()).create();
            if (stone.requiredAspect() != null) pokemon.setForcedAspects(java.util.Set.of(stone.requiredAspect()));
            pokemon.swapHeldItem(MegaItems.createStone(stone), false, false);
            check(MegaEvolution.stoneMatches(stone, pokemon), "Stone rejected its required base form: " + stone.stoneId());
            checkRosterForm(pokemon, stone.form());
        }
        Pokemon rayquaza = PokemonProperties.Companion.parse("rayquaza moves=dragonascent").create();
        check(MegaEvolution.knowsStonelessMegaMove(rayquaza), "Rayquaza test did not learn Dragon Ascent");
        checkRosterForm(rayquaza, "Mega");
        for (String gender : List.of("male", "female")) {
            Pokemon meowstic = PokemonProperties.Companion.parse("meowstic gender=" + gender).create();
            meowstic.swapHeldItem(MegaItems.createStone(MegaStones.byStoneId("meowsticite")), false, false);
            checkRosterForm(meowstic, "Mega");
        }
    }

    private static void checkRosterForm(Pokemon pokemon, String formName) {
        String label = pokemon.getSpecies().getName() + " " + formName;
        try {
            FormData mega = MegaEvolution.findFormByName(pokemon, formName);
            check(mega != null, "Missing form");
            String expectedAbility = mega.getAbilities().iterator().next().getTemplate().getName();
            FormData base = pokemon.getForm();
            var originalGender = pokemon.getGender();
            Ability original = pokemon.getAbility();
            var aspects = new java.util.HashSet<>(pokemon.getForcedAspects());
            pokemon.setNickname(Component.literal("Partner"));
            // Check the previously broken forced-ability path on every classic and custom form.
            original.setForced$common(true);
            RecordingActor owner = new RecordingActor(pokemon);
            PokemonBattle battle = new PokemonBattle(BattleFormat.Companion.getGEN_9_SINGLES(),
                new BattleSide(owner), new BattleSide());
            MegaEvolution.applyMega(pokemon, battle);
            check(pokemon.getForm() == mega, "Wrong Mega form selected");
            check(pokemon.getGender() == originalGender, "Battle Mega changed gender");
            check(pokemon.getAbility().getName().equals(expectedAbility), "Wrong Mega ability");
            check(owner.abilities.equals(List.of(expectedAbility)), "Battle summary did not receive Mega ability");
            CompoundTag battleSave = pokemon.saveToNBT(registries, new CompoundTag());
            MegaEvolution.revert(pokemon);
            checkRestored(pokemon, base, original, aspects);
            check(pokemon.getGender() == originalGender, "Reversion changed gender");
            checkReloadedRoster(battleSave, base, original, aspects);

            boolean keyRequired = MegaCobbleConfig.get().requireKeyStone;
            MegaCobbleConfig.get().requireKeyStone = false;
            try {
                check(MegaEvolution.applyWorldMega(null, pokemon) == MegaEvolution.WorldMegaResult.APPLIED,
                    "World Mega rejected correct species/form/item");
                check(pokemon.getForm() == mega && pokemon.getAbility().getName().equals(expectedAbility),
                    "World Mega disagrees with battle Mega");
                check(pokemon.getGender() == originalGender, "World Mega changed gender");
                CompoundTag worldSave = pokemon.saveToNBT(registries, new CompoundTag());
                MegaEvolution.revertWorldMega(pokemon);
                checkRestored(pokemon, base, original, aspects);
                checkReloadedRoster(worldSave, base, original, aspects);
            } finally {
                MegaCobbleConfig.get().requireKeyStone = keyRequired;
            }
        } catch (AssertionError | RuntimeException failure) {
            throw new AssertionError(label + ": " + failure.getMessage(), failure);
        }
    }

    private static void checkRestored(Pokemon pokemon, FormData base, Ability ability, java.util.Set<String> aspects) {
        check(pokemon.getForm() == base, "Reversion lost the base form: " + base.getName() + " -> " + pokemon.getForm().getName());
        check(pokemon.getAbility().getName().equals(ability.getName())
            && pokemon.getAbility().getForced() == ability.getForced()
            && pokemon.getAbility().getPriority() == ability.getPriority()
            && pokemon.getAbility().getIndex() == ability.getIndex(), "Reversion lost the original ability slot");
        check(pokemon.getForcedAspects().equals(aspects), "Reversion lost required base aspects");
        check(pokemon.getNickname().getString().equals("Partner"), "Reversion lost the nickname");
    }

    private static void checkReloadedRoster(CompoundTag saved, FormData base, Ability ability, java.util.Set<String> aspects) {
        Pokemon loaded = new Pokemon().loadFromNBT(registries, saved);
        MegaEvolution.recoverOrphanedMega(loaded);
        checkRestored(loaded, base, ability, aspects);
    }

    private static void checkReload(String properties, boolean forced, boolean world) {
        Pokemon pokemon = PokemonProperties.Companion.parse(properties).create();
        pokemon.getAbility().setForced$common(forced);
        Ability original = pokemon.getAbility();
        var originalForm = pokemon.getForm().getName();
        pokemon.setForcedAspects(java.util.Set.of("test-skin"));
        MutableComponent nickname = Component.literal("Partner").withStyle(ChatFormatting.GOLD);
        pokemon.setNickname(nickname);
        pokemon.swapHeldItem(MegaItems.createStone(MegaStones.byStoneId("garchompite_z")), false, false);
        boolean requiredKey = MegaCobbleConfig.get().requireKeyStone;
        MegaCobbleConfig.get().requireKeyStone = false;
        try {
            if (world) {
                MegaEvolution.applyWorldMega(null, pokemon);
            } else {
                MegaEvolution.applyMega(pokemon, new PokemonBattle(BattleFormat.Companion.getGEN_9_SINGLES(),
                    new BattleSide(new RecordingActor(pokemon)), new BattleSide()));
            }
            MegaEvolution.recoverOrphanedMega(pokemon);
            check(pokemon.getForm().getName().equals("Mega-Z"), "Recovery reverted a live Mega");
            // Reproduce logout/save before cleanup, followed by a fresh JVM with no in-memory state.
            CompoundTag saved = pokemon.saveToNBT(registries, new CompoundTag());
            MegaEvolution.clearSessionState();
            Pokemon reloaded = new Pokemon().loadFromNBT(registries, saved);
            MegaEvolution.recoverOrphanedMega(reloaded);
            check(reloaded.getForm().getName().equals(originalForm), "Reload retained the Mega form");
            check(reloaded.getForcedAspects().equals(java.util.Set.of("test-skin")), "Reload lost a skin aspect");
            check(nickname.equals(reloaded.getNickname()), "Reload lost the original styled nickname");
            check(reloaded.getAbility().getName().equals(original.getName()), "Reload lost the original ability");
            check(reloaded.getAbility().getForced() == forced, "Reload changed forced ability status");
            check(reloaded.getAbility().getPriority() == original.getPriority()
                && reloaded.getAbility().getIndex() == original.getIndex(), "Reload lost the hidden ability slot");
            check(!reloaded.getPersistentData().contains("megacobble:original"), "Recovery left its marker behind");
        } finally {
            MegaCobbleConfig.get().requireKeyStone = requiredKey;
            MegaEvolution.clearSessionState();
        }
    }

    private static void checkLegacyRecovery(String ability, boolean forced) {
        Pokemon pokemon = PokemonProperties.Companion.parse("garchomp ability=" + ability).create();
        pokemon.getAbility().setForced$common(forced);
        Ability original = pokemon.getAbility();
        pokemon.setForcedAspects(java.util.Set.of("mega-z", "test-skin"));
        pokemon.setAbility$common(original);
        pokemon.setNickname(Component.literal("Mega-Garchom"));
        MegaEvolution.recoverOrphanedMega(pokemon);
        check(!pokemon.getForm().getName().startsWith("Mega"), "Legacy save stayed Mega");
        check(List.of("sandveil", "roughskin").contains(pokemon.getAbility().getName()), "Legacy ability stayed Mega-only");
        if (ability.equals("sandveil")) {
            check(pokemon.getAbility() == original, "Legacy base ability was discarded");
        }
        check(pokemon.getNickname() == null, "Legacy generated nickname stayed Mega");
        check(pokemon.getForcedAspects().equals(java.util.Set.of("test-skin")), "Legacy recovery lost a skin");
    }

    private static final class RecordingActor extends BattleActor {
        private final List<String> abilities = new ArrayList<>();

        RecordingActor(Pokemon pokemon) {
            super(UUID.randomUUID(), List.of(new BattlePokemon(pokemon, pokemon, new ArrayList<>(), new ArrayList<>())));
            getPokemonList().getFirst().setActor(this);
        }

        @Override public ActorType getType() { return ActorType.PLAYER; }
        @Override public MutableComponent getName() { return Component.literal("Test trainer"); }
        @Override public MutableComponent nameOwned(String name) { return Component.literal(name); }
        @Override public void sendUpdate(NetworkPacket<?> packet) {
            if (packet instanceof BattleUpdateTeamPokemonPacket update) {
                // Check what survives transmission, not just the server-side packet reference.
                RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
                try {
                    update.encode(buffer);
                    Pokemon received = BattleUpdateTeamPokemonPacket.Companion.decode(buffer).getPokemon();
                    abilities.add(received.getAbility().getName());
                    check(received.getForm().getName().equals(update.getPokemon().getForm().getName()),
                        "Battle summary packet lost the form");
                    check(received.getForm().getBaseStats().equals(update.getPokemon().getForm().getBaseStats()),
                        "Battle summary packet lost the form's stats");
                    check(received.getForm().getPrimaryType().equals(update.getPokemon().getForm().getPrimaryType())
                        && java.util.Objects.equals(received.getForm().getSecondaryType(),
                            update.getPokemon().getForm().getSecondaryType()), "Battle summary packet lost typing");
                } finally {
                    buffer.release();
                }
            }
        }
    }
}
