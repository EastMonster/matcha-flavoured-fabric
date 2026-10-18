package monster.east.matchaff.mechanic;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class BeaconKindlingMechanics {
	private static final Identifier LIGHT_BEACON =
			Identifier.fromNamespaceAndPath("matcha", "tutorial/light_beacon");

	private record BeaconTask(UUID owner, ResourceKey<Level> level, BlockPos pos, int startTick, UUID trader) {
	}

	record BeaconStatus(int remainingTicks, boolean traderSummoned) {
	}

	private record Timer(UUID id, int remainingTicks) {
		private static final Codec<Timer> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("id").forGetter(Timer::id),
				Codec.INT.fieldOf("remaining_ticks").forGetter(Timer::remainingTicks)
		).apply(i, Timer::new));
	}

	private record PersistedBeacon(
			UUID owner, ResourceKey<Level> level, BlockPos pos, int remainingTicks, Optional<UUID> trader
	) {
		private static final Codec<PersistedBeacon> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("owner").forGetter(PersistedBeacon::owner),
				Level.RESOURCE_KEY_CODEC.fieldOf("level").forGetter(PersistedBeacon::level),
				BlockPos.CODEC.fieldOf("pos").forGetter(PersistedBeacon::pos),
				Codec.INT.fieldOf("remaining_ticks").forGetter(PersistedBeacon::remainingTicks),
				UUIDUtil.CODEC.optionalFieldOf("trader").forGetter(PersistedBeacon::trader)
			).apply(i, PersistedBeacon::new));
	}

	private static final class BeaconSavedData extends SavedData {
		private static final Codec<BeaconSavedData> CODEC = RecordCodecBuilder.create(i -> i.group(
				PersistedBeacon.CODEC.listOf().fieldOf("beacons").forGetter(data -> data.beacons),
				Timer.CODEC.listOf().optionalFieldOf("cooldowns").forGetter(data -> data.cooldowns),
				Timer.CODEC.listOf().optionalFieldOf("traders").forGetter(data -> data.traders)
			).apply(i, BeaconSavedData::new));
		private static final SavedDataType<BeaconSavedData> TYPE = new SavedDataType<>(
				Identifier.fromNamespaceAndPath("matcha-flavoured", "beacons"),
				BeaconSavedData::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE
		);

		private List<PersistedBeacon> beacons;
		private Optional<List<Timer>> cooldowns;
		private Optional<List<Timer>> traders;

		private BeaconSavedData() {
			this(List.of(), Optional.of(List.of()), Optional.of(List.of()));
		}

		private BeaconSavedData(List<PersistedBeacon> beacons, Optional<List<Timer>> cooldowns,
				Optional<List<Timer>> traders) {
			this.beacons = new ArrayList<>(beacons);
			this.cooldowns = cooldowns;
			this.traders = traders;
		}

		private List<PersistedBeacon> beacons() {
			return beacons;
		}

		private void replace(List<PersistedBeacon> beacons, List<Timer> cooldowns, List<Timer> traders) {
			if (!this.beacons.equals(beacons) || !this.cooldowns.equals(Optional.of(cooldowns))
					|| !this.traders.equals(Optional.of(traders))) {
				this.beacons = new ArrayList<>(beacons);
				this.cooldowns = Optional.of(cooldowns);
				this.traders = Optional.of(traders);
				setDirty();
			}
		}
	}

	private static final HashMap<UUID, BeaconTask> BEACONS = new HashMap<>();
	private static final HashMap<UUID, Integer> COOLDOWNS = new HashMap<>();
	private static final HashMap<UUID, Integer> TRADERS = new HashMap<>();
	private static final HashMap<UUID, WanderingTrader> LOADED_TRADERS = new HashMap<>();
	private static boolean DATA_LOADED;

	private BeaconKindlingMechanics() {
	}

	static void init() {
		ServerEntityEvents.ENTITY_LOAD.register(BeaconKindlingMechanics::trackTrader);
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			LOADED_TRADERS.remove(entity.getUUID());
			if (entity.getRemovalReason() != null && entity.getRemovalReason().shouldDestroy()
					&& TRADERS.remove(entity.getUUID()) != null) {
				save(level.getServer(), level.getServer().getTickCount());
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> save(server, server.getTickCount()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			BEACONS.clear();
			COOLDOWNS.clear();
			TRADERS.clear();
			LOADED_TRADERS.clear();
			DATA_LOADED = false;
		});
	}

	public static InteractionResult useOn(UseOnContext context) {
		var player = context.getPlayer();
		if (player == null) {
			return InteractionResult.PASS;
		}
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		ItemStack stack = context.getItemInHand();
		if (!place(serverPlayer, (ServerLevel) context.getLevel(), context)) {
			return InteractionResult.FAIL;
		}
		stack.consume(1, player);
		return InteractionResult.SUCCESS_SERVER;
	}

	private static boolean place(ServerPlayer player, ServerLevel level, UseOnContext useContext) {
		BlockPlaceContext context = new BlockPlaceContext(useContext);
		if (!context.canPlace()) {
			return false;
		}
		BlockPos pos = context.getClickedPos();
		BlockState state = Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.SIGNAL_FIRE, true);
		if (!state.canSurvive(level, pos)
				|| !level.isUnobstructed(state, pos, CollisionContext.placementContext(player))
				|| !level.setBlock(pos, state, 3)) {
			return false;
		}

		int tick = level.getServer().getTickCount();
		load(level.getServer(), tick);
		AdvancementHolder advancement = level.getServer().getAdvancements().get(LIGHT_BEACON);
		if (advancement != null) {
			player.getAdvancements().award(advancement, "placed_beacon");
		}
		level.setBlock(pos, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.SIGNAL_FIRE, true), 3);
		level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5,
				30, 0.1, 0.1, 0.1, 0.07);
		level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
		level.playSound(null, pos, SoundEvents.WITHER_SPAWN, SoundSource.BLOCKS, 0.5F, 1.0F);
		if (COOLDOWNS.containsKey(player.getUUID())) {
			player.sendSystemMessage(Component.translatable("message.matcha.wandering_trader.beacon_placed")
					.withStyle(ChatFormatting.GRAY));
			return true;
		}
		COOLDOWNS.put(player.getUUID(), 18000);
		BEACONS.put(UUID.randomUUID(), new BeaconTask(player.getUUID(), level.dimension(), pos.immutable(), tick, null));
		save(level.getServer(), tick);
		announce(level, player.getX(), player.getY(), player.getZ(), "initialise");
		return true;
	}

	static void tick(MinecraftServer server, int tick) {
		load(server, tick);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			COOLDOWNS.computeIfPresent(player.getUUID(), (id, remaining) -> remaining > 1 ? remaining - 1 : null);
		}
		for (WanderingTrader trader : List.copyOf(LOADED_TRADERS.values())) {
			Integer remaining = TRADERS.get(trader.getUUID());
			if (remaining == null || trader.isRemoved()) {
				continue;
			}
			if (remaining <= 1) {
				removeTrader(trader.getUUID());
				save(server, tick);
			} else {
				TRADERS.put(trader.getUUID(), remaining - 1);
			}
		}
		for (var entry : new HashMap<>(BEACONS).entrySet()) {
			UUID taskId = entry.getKey();
			BeaconTask task = entry.getValue();
			ServerLevel level = server.getLevel(task.level());
			if (level == null || !level.isLoaded(task.pos())) {
				BEACONS.put(taskId, new BeaconTask(
						task.owner(), task.level(), task.pos(), task.startTick() + 1, task.trader()));
				continue;
			}
			var state = level.getBlockState(task.pos());
			if (!state.is(Blocks.CAMPFIRE) || !state.getValue(CampfireBlock.LIT)) {
				end(server, taskId, task, true);
				continue;
			}
			if (tick % 10 == 0) {
				level.sendParticles(ParticleTypes.TRIAL_SPAWNER_DETECTED_PLAYER,
						task.pos().getX() + 0.5, task.pos().getY() + 0.75, task.pos().getZ() + 0.5,
						2, 0.2, 0.05, 0.2, 0);
			}
			int elapsed = tick - task.startTick();
			if (task.trader() == null && elapsed >= 12000) {
				var trader = EntityTypes.WANDERING_TRADER.create(level, EntitySpawnReason.COMMAND);
				if (trader != null) {
					trader.setPos(task.pos().getX() + 1.5, task.pos().getY(), task.pos().getZ() + 0.5);
					trader.setPermanentlyInvulnerable(true);
					trader.setDespawnDelay(0);
					trader.addTag("summoned_by_beacon");
					TRADERS.put(trader.getUUID(), 6000);
					if (level.addFreshEntity(trader)) {
						BEACONS.put(taskId, new BeaconTask(task.owner(), task.level(), task.pos(),
								task.startTick(), trader.getUUID()));
						announce(level, task.pos().getX(), task.pos().getY(), task.pos().getZ(), "summoned");
						save(server, tick);
					} else {
						TRADERS.remove(trader.getUUID());
					}
				}
			} else if (task.trader() != null && elapsed >= 18000) {
				end(server, taskId, task, false);
			}
		}
	}

	static BeaconStatus status(MinecraftServer server, ResourceKey<Level> level, BlockPos pos) {
		int tick = server.getTickCount();
		load(server, tick);
		return BEACONS.values().stream()
				.filter(task -> task.level().equals(level) && task.pos().equals(pos))
				.map(task -> {
					int remaining = task.trader() == null
							? Math.max(0, 12000 - Math.max(0, tick - task.startTick()))
							: TRADERS.getOrDefault(task.trader(), 0);
					return new BeaconStatus(remaining, task.trader() != null);
				})
				.filter(status -> status.remainingTicks() > 0)
				.min(Comparator.comparingInt(BeaconStatus::remainingTicks))
				.orElse(new BeaconStatus(0, false));
	}

	private static void end(
			MinecraftServer server, UUID taskId, BeaconTask task, boolean early
	) {
		if (early && task.trader() != null) {
			removeTrader(task.trader());
		}
		ServerLevel level = server.getLevel(task.level());
		if (level != null) {
			var state = level.getBlockState(task.pos());
			if (state.is(Blocks.CAMPFIRE)) {
				level.setBlock(task.pos(), state.setValue(CampfireBlock.LIT, false), 3);
			}
			level.sendParticles(ParticleTypes.LARGE_SMOKE,
					task.pos().getX() + 0.5, task.pos().getY() + 0.5, task.pos().getZ() + 0.5,
					10, 0.1, 0.1, 0.1, 0.1);
			if (early) {
				announce(level, task.pos().getX(), task.pos().getY(), task.pos().getZ(), "kill_early");
			}
		}
		BEACONS.remove(taskId);
		save(server, server.getTickCount());
	}

	private static void trackTrader(Entity entity, ServerLevel level) {
		if (!(entity instanceof WanderingTrader trader)
				|| !entity.entityTags().contains("summoned_by_beacon")) {
			return;
		}
		load(level.getServer(), level.getServer().getTickCount());
		if (!TRADERS.containsKey(entity.getUUID())) {
			entity.discard();
		} else {
			// Vanilla pauses despawning while trading; the upstream lifetime does not.
			trader.setDespawnDelay(0);
			LOADED_TRADERS.put(entity.getUUID(), trader);
		}
	}

	private static void removeTrader(UUID id) {
		TRADERS.remove(id);
		WanderingTrader trader = LOADED_TRADERS.remove(id);
		if (trader != null) {
			ServerLevel level = (ServerLevel) trader.level();
			announce(level, trader.getX(), trader.getY(), trader.getZ(), "kill");
			level.sendParticles(ParticleTypes.POOF, trader.getX(), trader.getY() + 0.5, trader.getZ(),
					50, 0.2, 1.0, 0.2, 0);
			trader.discard();
		}
	}

	private static void announce(ServerLevel level, double x, double y, double z, String message) {
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(x, y, z) <= 128 * 128) {
				player.sendSystemMessage(Component.translatable("message.matcha.wandering_trader." + message)
						.withStyle(ChatFormatting.GRAY));
			}
		}
	}

	private static BeaconSavedData data(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(BeaconSavedData.TYPE);
	}

	private static void load(MinecraftServer server, int tick) {
		if (DATA_LOADED) {
			return;
		}
		restore(data(server), tick);
		DATA_LOADED = true;
	}

	private static void restore(BeaconSavedData data, int tick) {
		for (PersistedBeacon saved : data.beacons()) {
			int limit = saved.trader().isPresent() ? 18000 : 12000;
			int remaining = Math.max(0, Math.min(limit, saved.remainingTicks()));
			BEACONS.put(UUID.randomUUID(), new BeaconTask(
					saved.owner(), saved.level(), saved.pos(), tick - (limit - remaining), saved.trader().orElse(null)
			));
			// Old saves tied both timers to the beacon; preserve their remaining time once.
			if (data.cooldowns.isEmpty()) {
				COOLDOWNS.merge(saved.owner(), remaining + (saved.trader().isEmpty() ? 6000 : 0), Math::max);
			}
			if (data.traders.isEmpty()) {
				saved.trader().ifPresent(id -> TRADERS.put(id, Math.min(6000, remaining)));
			}
		}
		data.cooldowns.ifPresent(timers -> timers.forEach(timer -> {
			if (timer.remainingTicks() > 0) COOLDOWNS.put(timer.id(), Math.min(18000, timer.remainingTicks()));
		}));
		data.traders.ifPresent(timers -> timers.forEach(timer ->
				TRADERS.put(timer.id(), Math.clamp(timer.remainingTicks(), 0, 6000))));
	}

	private static void save(MinecraftServer server, int tick) {
		if (!DATA_LOADED) {
			return;
		}
		List<PersistedBeacon> saved = new ArrayList<>(BEACONS.size());
		for (var entry : BEACONS.entrySet()) {
			BeaconTask task = entry.getValue();
			int limit = task.trader() == null ? 12000 : 18000;
			int elapsed = Math.max(0, tick - task.startTick());
			saved.add(new PersistedBeacon(task.owner(), task.level(), task.pos(),
					Math.max(0, limit - elapsed), Optional.ofNullable(task.trader())));
		}
		data(server).replace(saved,
				COOLDOWNS.entrySet().stream().map(entry -> new Timer(entry.getKey(), entry.getValue())).toList(),
				TRADERS.entrySet().stream().map(entry -> new Timer(entry.getKey(), entry.getValue())).toList());
	}
}
