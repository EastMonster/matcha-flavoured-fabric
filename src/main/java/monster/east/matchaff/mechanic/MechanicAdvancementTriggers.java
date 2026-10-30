package monster.east.matchaff.mechanic;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Completion flags consumed by the existing mechanic tick phases. */
public final class MechanicAdvancementTriggers {
	enum Stage {
		GAMEPLAY("mechanics/estus_obtained", "mechanics/cheerful_clay_statue",
				"mechanics/mournful_clay_statue", "mechanics/happy_ghast_horn",
				"mechanics/summoned_wither", "mechanics/enter_fortress", "mechanics/place_water_in_nether"),
		WORLD_SULFUR("particle/on_sulfur"),
		WORLD_ELYTRA("item_contingencies/elytra"),
		VILLAGE("mechanics/opened_oak_door"),
		ABBEY("mechanics/hit_copper_eye");

		final List<Identifier> order;

		Stage(String... paths) {
			order = Arrays.stream(paths).map(MechanicAdvancementTriggers::id).toList();
		}
	}

	private static final Set<Identifier> WATCHED = Arrays.stream(Stage.values())
			.flatMap(stage -> stage.order.stream()).collect(Collectors.toUnmodifiableSet());
	private static final Map<UUID, Set<Identifier>> PENDING = new HashMap<>();

	private MechanicAdvancementTriggers() {
	}

	static void init() {
		ServerPlayerEvents.JOIN.register(MechanicAdvancementTriggers::restore);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PENDING.remove(handler.player.getUUID()));
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
			if (success) server.getPlayerList().getPlayers().forEach(MechanicAdvancementTriggers::restore);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING.clear());
	}

	public static void completed(ServerPlayer player, Identifier advancement) {
		mark(player.getUUID(), advancement);
	}

	static void mark(UUID player, Identifier advancement) {
		if (WATCHED.contains(advancement)) {
			PENDING.computeIfAbsent(player, ignored -> new HashSet<>()).add(advancement);
		}
	}

	// Process only queued actions, in their former order. Re-triggers of an already passed
	// action stay queued for the next tick, while later actions may still run this tick.
	static void run(ServerPlayer player, Stage stage) {
		int cursor = -1;
		Identifier action;
		while ((action = next(player.getUUID(), stage, cursor)) != null) {
			cursor = stage.order.indexOf(action);
			switch (stage) {
				case GAMEPLAY -> GameplayMechanics.handleAdvancement(player, action);
				case WORLD_SULFUR, WORLD_ELYTRA -> WorldMechanics.handleAdvancement(player, action);
				case VILLAGE -> VillageMechanics.handleDoorAdvancement(player);
				case ABBEY -> AbbeyMechanics.checkCopperEye(player);
			}
		}
	}

	static void runAbbey(MinecraftServer server) {
		if (PENDING.isEmpty()) return;
		var players = server.getPlayerList().getPlayers();
		var queued = new ArrayList<>(PENDING.entrySet().stream()
				.filter(entry -> entry.getValue().contains(id("mechanics/hit_copper_eye")))
				.map(Map.Entry::getKey).toList());
		queued.sort(Comparator.comparingInt(uuid -> players.indexOf(server.getPlayerList().getPlayer(uuid))));
		for (UUID uuid : queued) {
			ServerPlayer player = server.getPlayerList().getPlayer(uuid);
			if (player != null) run(player, Stage.ABBEY);
		}
	}

	static Identifier next(UUID player, Stage stage, int cursor) {
		Set<Identifier> pending = PENDING.get(player);
		if (pending == null) return null;
		Identifier next = pending.stream()
				.filter(id -> stage.order.indexOf(id) > cursor)
				.min(Comparator.comparingInt(stage.order::indexOf)).orElse(null);
		if (next != null) clear(player, next);
		return next;
	}

	static void clear(UUID player, Identifier advancement) {
		Set<Identifier> pending = PENDING.get(player);
		if (pending != null) {
			pending.remove(advancement);
			if (pending.isEmpty()) PENDING.remove(player);
		}
	}

	private static void restore(ServerPlayer player) {
		PENDING.remove(player.getUUID());
		// Saved completed flags must still run after login or a datapack reload.
		for (Identifier id : WATCHED) {
			var advancement = player.level().getServer().getAdvancements().get(id);
			if (advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone()) {
				completed(player, id);
			}
		}
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("matcha", path);
	}
}
