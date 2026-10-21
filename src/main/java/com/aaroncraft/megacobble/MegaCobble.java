package com.aaroncraft.megacobble;

import com.aaroncraft.megacobble.battle.BattleDamageMonitor;
import com.aaroncraft.megacobble.command.MegaCobbleCommands;
import com.aaroncraft.megacobble.config.MegaCobbleConfig;
import com.aaroncraft.megacobble.item.MegaStones;
import com.aaroncraft.megacobble.item.ZCrystals;
import com.aaroncraft.megacobble.mega.MegaEvolution;
import com.aaroncraft.megacobble.mega.MegaShowdownInjector;
import com.aaroncraft.megacobble.mega.MegaStoneHeldItemManager;
import com.aaroncraft.megacobble.mega.RayquazaMegaHeldItemManager;
import com.aaroncraft.megacobble.net.RequestWorldMegaPayload;
import com.aaroncraft.megacobble.zmove.ZCrystalHeldItemManager;
import com.aaroncraft.megacobble.zmove.ZMoves;
import com.aaroncraft.megacobble.skin.GlobalSkins;
import com.aaroncraft.megacobble.variant.MegaVariants;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.pokemon.helditem.HeldItemProvider;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.UUID;

/**
 * Mega Cobble - a Cobblemon add-on that adds Mega Evolution.
 *
 * <p>Common (server + client) entrypoint. Mega Evolution runs through Cobblemon's native, Showdown-
 * driven mega: we expose our Mega Stones to the sim, bridge our Key Stone item to Cobblemon's
 * key-item gate, mirror the form change visually when a Pokémon mega evolves, and revert it when the
 * battle ends.</p>
 */
public class MegaCobble implements ModInitializer {
	public static final String MOD_ID = "megacobble";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("[Mega Cobble] Initializing Mega Evolution for Cobblemon.");

		MegaCobbleConfig.load();
		MegaVariants.load();
		MegaStones.load();
		ZCrystals.load();
		GlobalSkins.init();
		MegaShowdownInjector.load();

		// Networking + commands for out-of-battle ("world") Mega Evolution. The payload codec must be
		// registered on both sides; the receiver and command tree are server-authoritative.
		PayloadTypeRegistry.playC2S().register(RequestWorldMegaPayload.TYPE, RequestWorldMegaPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(RequestWorldMegaPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			UUID entityId = payload.entityId();
			player.getServer().execute(() -> handleWorldMegaRequest(player, entityId));
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			MegaCobbleCommands.register(dispatcher));

		// Expose our Mega Stones and Z-Crystals to the bundled Showdown sim (Cobblemon's default
		// manager only handles cobblemon-namespace items). Higher priority than Cobblemon's LOWEST
		// default; the two managers coexist since a Pokémon holds one or the other, never both.
		HeldItemProvider.register(new MegaStoneHeldItemManager(), Priority.NORMAL);
		HeldItemProvider.register(new ZCrystalHeldItemManager(), Priority.NORMAL);
		// Stoneless Mega Rayquaza: feed the sim a virtual "Rayquazite" for a Dragon-Ascent Rayquaza so
		// Showdown's (ungated) item-based mega offers Mega Rayquaza — no stone the player ever holds.
		HeldItemProvider.register(new RayquazaMegaHeldItemManager(), Priority.NORMAL);

		// At battle start: inject custom-mega data into the sim (megas not in Cobblemon's bundled
		// sim), bridge each player's Key Stone item to Cobblemon's key-item gate so the native mega
		// button is only enabled when they brought a Key Stone, and revert any out-of-battle ("world")
		// megas so the real in-battle Showdown mega applies to the base form.
		CobblemonEvents.BATTLE_STARTED_PRE.subscribe(event -> {
			for (var actor : event.getBattle().getActors()) {
				for (var member : actor.getPokemonList()) {
					MegaEvolution.recoverOrphanedMega(member.getOriginalPokemon());
					MegaEvolution.recoverOrphanedMega(member.getEffectedPokemon());
				}
			}
			MegaShowdownInjector.injectAll();
			MegaShowdownInjector.fixMegaAbilities();
			event.getBattle().getPlayers().forEach(MegaEvolution::syncKeyStone);
			// Bridge the Z-Ring item to Cobblemon's cobblemon:z_ring key-item gate, so the native Z
			// button appears only when a player brought a Z-Ring. Z-Moves themselves are pure Showdown.
			event.getBattle().getPlayers().forEach(ZMoves::syncZRing);
			MegaEvolution.revertWorldMegasForBattle(event.getBattle().getPlayers());
		});

		// When Showdown mega evolves a Pokémon, mirror the form change on the Minecraft side (and
		// remember which battle it happened in, so we can revert even on a custom/forced battle end).
		CobblemonEvents.MEGA_EVOLUTION.subscribe(event ->
			MegaEvolution.applyMega(event.getPokemon().getEffectedPokemon(), event.getBattle()));

		// 1.8.2 reports every battle ending, including captures, forfeits and forced stops.
		CobblemonEvents.BATTLE_ENDED.subscribe(event -> MegaEvolution.revertBattle(event.getBattle()));
		// Keep the earlier victory/flee hooks for immediate cleanup, and revert on fainting.
		CobblemonEvents.BATTLE_VICTORY.subscribe(event -> MegaEvolution.revertBattle(event.getBattle()));
		CobblemonEvents.BATTLE_FLED.subscribe(event -> MegaEvolution.revertBattle(event.getBattle()));
		CobblemonEvents.BATTLE_FAINTED.subscribe(event -> MegaEvolution.revert(event.getKilled().getEffectedPokemon()));

		// Party storage may be saved/unloaded before end-of-tick battle cleanup on disconnect.
		// Recover from the Pokemon's durable snapshot on login and when a boxed Pokemon is sent out.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			MegaEvolution.recoverPlayerMegas(handler.player));
		CobblemonEvents.POKEMON_SENT_PRE.subscribe(event -> MegaEvolution.recoverOrphanedMega(event.getPokemon()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> MegaEvolution.clearSessionState());

		// Safety net for addon battles that mark themselves ended without posting BATTLE_ENDED.
		// Cheap: a no-op unless something is currently mega-evolved in battle.
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			MegaEvolution.revertEndedBattleMegas();
			// Per-player battle-damage readout (opt in with /megacobble damage on). No-op if nobody opted in.
			BattleDamageMonitor.tick(server);
		});
	}

	/**
	 * Handles a client's interaction-wheel "Mega Evolve" request: resolves the targeted Pokémon
	 * entity, verifies the requester owns it, toggles its world mega, and reports the result on the
	 * action bar. Runs on the server thread.
	 */
	private static void handleWorldMegaRequest(ServerPlayer player, UUID entityId) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		// The wheel-only gate. Enforced here rather than client-side: the client reads its own local
		// config file, which on a dedicated server has nothing to do with the server's.
		if (!MegaCobbleConfig.get().allowRideInMega) {
			player.displayClientMessage(Component.translatable("megacobble.feedback.disabled"), true);
			return;
		}
		Entity entity = level.getEntity(entityId);
		if (!(entity instanceof PokemonEntity pokemonEntity)) {
			return;
		}
		Pokemon pokemon = pokemonEntity.getPokemon();
		if (!player.getUUID().equals(pokemon.getOwnerUUID())) {
			return;
		}
		MegaEvolution.WorldMegaResult result = MegaEvolution.toggleWorldMega(player, pokemon);
		player.displayClientMessage(
			Component.translatable("megacobble.feedback." + result.name().toLowerCase(Locale.ROOT)), true);
	}
}
