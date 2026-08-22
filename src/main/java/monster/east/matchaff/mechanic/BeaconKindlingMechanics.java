package monster.east.matchaff.mechanic;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class BeaconKindlingMechanics {
	private record BeaconTask(ResourceKey<Level> level, BlockPos pos, int startTick, UUID trader) {
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
				PersistedBeacon.CODEC.listOf().fieldOf("beacons").forGetter(data -> data.beacons)
			).apply(i, BeaconSavedData::new));
		private static final SavedDataType<BeaconSavedData> TYPE = new SavedDataType<>(
				Identifier.fromNamespaceAndPath("matcha-flavoured", "beacons"),
				BeaconSavedData::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE
		);

		private List<PersistedBeacon> beacons;

		private BeaconSavedData() {
			this(List.of());
		}

		private BeaconSavedData(List<PersistedBeacon> beacons) {
			this.beacons = new ArrayList<>(beacons);
		}

		private List<PersistedBeacon> beacons() {
			return beacons;
		}

		private void replace(List<PersistedBeacon> beacons) {
			if (!this.beacons.equals(beacons)) {
				this.beacons = new ArrayList<>(beacons);
				setDirty();
			}
		}
	}

	private static final HashMap<UUID, BeaconTask> BEACONS = new HashMap<>();
	private static boolean DATA_LOADED;

	private BeaconKindlingMechanics() {
	}

	static void init() {
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> save(server, server.getTickCount()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			BEACONS.clear();
			DATA_LOADED = false;
		});
	}

	static void place(ServerPlayer player, ServerLevel level, BlockPos pos) {
		int tick = level.getServer().getTickCount();
		load(level.getServer(), tick);
		level.setBlock(pos, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.SIGNAL_FIRE, true), 3);
		level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5,
				30, 0.1, 0.1, 0.1, 0.07);
		level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
		level.playSound(null, pos, SoundEvents.WITHER_SPAWN, SoundSource.BLOCKS, 0.5F, 1.0F);
		if (BEACONS.containsKey(player.getUUID())) {
			player.sendSystemMessage(Component.literal(
					"You have already summoned a Wandering Trader, please wait patiently while they travel")
					.withStyle(ChatFormatting.GRAY));
			return;
		}
		BEACONS.put(player.getUUID(), new BeaconTask(level.dimension(), pos.immutable(), tick, null));
		save(level.getServer(), tick);
		level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
				"A Wandering Trader has spotted your beacon, they will arrive in 10 minutes")
				.withStyle(ChatFormatting.GRAY), false);
	}

	static void tick(MinecraftServer server, int tick) {
		load(server, tick);
		for (var entry : new HashMap<>(BEACONS).entrySet()) {
			UUID owner = entry.getKey();
			BeaconTask task = entry.getValue();
			ServerLevel level = server.getLevel(task.level());
			if (level == null || !level.isLoaded(task.pos())) {
				BEACONS.put(owner, new BeaconTask(
						task.level(), task.pos(), task.startTick() + 1, task.trader()));
				continue;
			}
			var state = level.getBlockState(task.pos());
			if (!state.is(Blocks.CAMPFIRE) || !state.getValue(CampfireBlock.LIT)) {
				end(server, owner, task, true, false);
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
					trader.setInvulnerable(true);
					trader.addTag("summoned_by_beacon");
					level.addFreshEntity(trader);
					BEACONS.put(owner, new BeaconTask(task.level(), task.pos(), task.startTick(), trader.getUUID()));
					server.getPlayerList().broadcastSystemMessage(Component.literal(
							"The Wandering Trader has arrived, they will depart in 5 minutes")
							.withStyle(ChatFormatting.GRAY), false);
				}
			} else if (task.trader() != null && elapsed >= 18000) {
				end(server, owner, task, false, true);
			}
		}
		save(server, tick);
	}

	private static void end(
			MinecraftServer server, UUID owner, BeaconTask task, boolean early, boolean announce
	) {
		ServerLevel level = server.getLevel(task.level());
		if (level != null) {
			if (task.trader() != null && level.getEntity(task.trader()) != null) {
				Entity trader = level.getEntity(task.trader());
				level.sendParticles(ParticleTypes.POOF, trader.getX(), trader.getY() + 0.5, trader.getZ(),
						50, 0.2, 1.0, 0.2, 0);
				trader.discard();
			}
			var state = level.getBlockState(task.pos());
			if (state.is(Blocks.CAMPFIRE)) {
				level.setBlock(task.pos(), state.setValue(CampfireBlock.LIT, false), 3);
			}
			if (!early) {
				level.sendParticles(ParticleTypes.LARGE_SMOKE,
						task.pos().getX() + 0.5, task.pos().getY() + 0.5, task.pos().getZ() + 0.5,
						10, 0.1, 0.1, 0.1, 0.1);
			}
		}
		BEACONS.remove(owner);
		if (early) {
			ServerPlayer player = server.getPlayerList().getPlayer(owner);
			if (player != null) {
				player.sendSystemMessage(Component.literal("The Wandering Trader has lost sight of your beacon...")
						.withStyle(ChatFormatting.GRAY));
			}
		} else if (announce) {
			server.getPlayerList().broadcastSystemMessage(
					Component.literal("The Wandering Trader has left").withStyle(ChatFormatting.GRAY), false);
		}
		save(server, server.getTickCount());
	}

	private static BeaconSavedData data(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(BeaconSavedData.TYPE);
	}

	private static void load(MinecraftServer server, int tick) {
		if (DATA_LOADED) {
			return;
		}
		for (PersistedBeacon saved : data(server).beacons()) {
			int limit = saved.trader().isPresent() ? 18000 : 12000;
			int remaining = Math.max(0, Math.min(limit, saved.remainingTicks()));
			BEACONS.put(saved.owner(), new BeaconTask(
					saved.level(), saved.pos(), tick - (limit - remaining), saved.trader().orElse(null)
			));
		}
		DATA_LOADED = true;
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
			saved.add(new PersistedBeacon(entry.getKey(), task.level(), task.pos(),
					Math.max(0, limit - elapsed), Optional.ofNullable(task.trader())));
		}
		data(server).replace(saved);
	}
}
