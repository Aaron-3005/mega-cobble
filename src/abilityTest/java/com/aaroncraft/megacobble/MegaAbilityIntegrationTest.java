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
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
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
                }
                Files.writeString(Path.of("ability-tests-passed.txt"), "6 battle + 6 world ability lifecycles passed\n");
                MegaCobble.LOGGER.info("[Mega ability tests] PASS: 6 battle + 6 world ability lifecycles");
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
                } finally {
                    buffer.release();
                }
            }
        }
    }
}
