package monster.east.matchaff.mechanic;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class VillageMechanics {
	private static final Identifier EERIE_ADVANCEMENT = id("mechanics/enter_village");
	private static final List<ResourceKey<Structure>> VILLAGES = List.of(
			structure("village_plains"), structure("village_desert"), structure("village_savanna"),
			structure("village_snowy"), structure("village_taiga")
	);
	private static final AttachmentType<Boolean> EERIE = AttachmentRegistry.create(id("eerie"));
	private static final AttachmentType<Integer> EERIE_TIMER = AttachmentRegistry.create(id("eerie_timer"));
	private static final Set<Mannequin> HAUNTED_MANNEQUINS =
			Collections.newSetFromMap(new IdentityHashMap<>());
	private static final Set<Marker> JUKEBOX_MARKERS =
			Collections.newSetFromMap(new IdentityHashMap<>());
	private static final List<HopperCleanupTask> HOPPER_CLEANUPS = new ArrayList<>();

	private record HopperCleanupTask(int triggerTick, ResourceKey<Level> level, UUID marker, BlockPos pos) {
	}

	private VillageMechanics() {
	}

	static void init() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			HAUNTED_MANNEQUINS.clear();
			JUKEBOX_MARKERS.clear();
			HOPPER_CLEANUPS.clear();
		});
		ServerEntityEvents.ENTITY_LOAD.register(VillageMechanics::trackVillageEntity);
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			HAUNTED_MANNEQUINS.remove(entity);
			JUKEBOX_MARKERS.remove(entity);
		});
		UseBlockCallback.EVENT.register(VillageMechanics::openVillageDoor);
	}

	static void tickPlayer(ServerPlayer player) {
		if (WorldMechanics.advancementDone(player, EERIE_ADVANCEMENT)) {
			player.setAttached(EERIE, true);
			WorldMechanics.revoke(player, EERIE_ADVANCEMENT);
		}
		if (!player.getAttachedOrElse(EERIE, false)) {
			return;
		}
		var level = player.level();
		if (!inVillage(level, player.blockPosition())) {
			player.setAttached(EERIE, false);
			player.setAttached(EERIE_TIMER, 0);
			return;
		}
		if (player.tickCount % 20 == 0) {
			player.connection.send(new ClientboundStopSoundPacket(null, SoundSource.MUSIC));
			int seconds = player.getAttachedOrElse(EERIE_TIMER, 0) + 1;
			if (seconds > 150) {
				seconds = 1;
			}
			player.setAttached(EERIE_TIMER, seconds);
			var ground = level.getBlockState(player.blockPosition().below());
			if (seconds <= 4) {
				eerieCue(player, level, ground, seconds);
			}
			if (seconds == 100) {
				eerieCue(player, level, ground, 100);
			}
		}
	}

	static void tick(MinecraftServer server, int tick) {
		for (Mannequin mannequin : List.copyOf(HAUNTED_MANNEQUINS)) {
			if (mannequin.isRemoved() || !(mannequin.level() instanceof ServerLevel level)) {
				HAUNTED_MANNEQUINS.remove(mannequin);
				continue;
			}
			ServerPlayer player = server.getPlayerList().getPlayers().stream()
					.filter(candidate -> candidate.level() == level
							&& candidate.gameMode.getGameModeForPlayer() == GameType.SURVIVAL
							&& candidate.getAttachedOrElse(EERIE, false))
					.min(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(mannequin)))
					.orElse(null);
			if (player == null) {
				continue;
			}
			mannequin.lookAt(EntityAnchorArgument.Anchor.EYES, player.getEyePosition());
			Marker marker = JUKEBOX_MARKERS.stream()
					.filter(candidate -> candidate.level() == level && !candidate.isRemoved())
					.min(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(mannequin)))
					.orElse(null);
			if (!mannequin.entityTags().contains("music_played") && player.distanceToSqr(mannequin) <= 30 * 30) {
				mannequin.addTag("music_played");
				if (marker != null) {
					for (BlockPos pos : BlockPos.betweenClosed(marker.blockPosition().offset(-1, 0, -1),
							marker.blockPosition().offset(1, 0, 1))) {
						if (level.getBlockState(pos).is(Blocks.REDSTONE_BLOCK)) {
							level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
						}
					}
					HOPPER_CLEANUPS.add(new HopperCleanupTask(
							tick + 2, level.dimension(), marker.getUUID(), marker.blockPosition().immutable()));
				}
			}
			if (player.distanceToSqr(mannequin) <= 25
					|| marker != null && player.distanceToSqr(marker) <= 4) {
				mannequin.discard();
			}
		}
		HOPPER_CLEANUPS.removeIf(task -> {
			if (task.triggerTick() > tick) {
				return false;
			}
			ServerLevel level = server.getLevel(task.level());
			if (level != null) {
				for (BlockPos pos : BlockPos.betweenClosed(task.pos().offset(-1, 0, -1),
						task.pos().offset(1, 0, 1))) {
					if (level.getBlockState(pos).is(Blocks.HOPPER)) {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
					}
				}
				Entity marker = level.getEntity(task.marker());
				if (marker != null) {
					marker.discard();
				}
			}
			return true;
		});
	}

	private static void trackVillageEntity(Entity entity, ServerLevel level) {
		if (entity instanceof Mannequin mannequin && entity.entityTags().contains("haunted")) {
			HAUNTED_MANNEQUINS.add(mannequin);
		} else if (entity instanceof Marker marker && entity.entityTags().contains("jukebox")) {
			JUKEBOX_MARKERS.add(marker);
		}
	}

	private static void eerieCue(ServerPlayer player, ServerLevel level, BlockState ground, int step) {
		Vec3 pos = player.position();
		if (ground.is(Blocks.COARSE_DIRT) && step <= 4) {
			playAt(level, pos.x + step - 3, pos.y + 4, pos.z, SoundEvents.WOOD_STEP, 0.5F);
		}
		if (ground.is(Blocks.OAK_PLANKS)) {
			if (step == 1 || step == 100) {
				Vec3 right = WorldMechanics.localOffset(player, 5, 0, 0);
				playAt(level, right.x, right.y, right.z, SoundEvents.GRASS_BREAK, 1.0F);
			}
			if (step == 1) {
				playAt(level, pos.x, pos.y, pos.z, SoundEvents.AMBIENT_CAVE.value(), 1.0F);
			}
		}
		if (ground.is(Blocks.GRAVEL)) {
			playAt(level, pos.x, pos.y, pos.z, step == 1 ? SoundEvents.GRAVEL_BREAK : SoundEvents.STONE_PLACE, 1.0F);
		}
		if (ground.is(Blocks.SUSPICIOUS_GRAVEL) && step == 1) {
			Vec3 behind = WorldMechanics.localOffset(player, 0, 0, -3);
			playAt(level, behind.x, behind.y, behind.z, SoundEvents.WOODEN_DOOR_OPEN, 1.0F);
		}
		if (ground.is(Blocks.GRASS_BLOCK) && step <= 4) {
			playAt(level, pos.x, pos.y - 4, pos.z, SoundEvents.GRASS_BREAK, 0.5F);
			if (step > 1) {
				playAt(level, pos.x + (4 - step), pos.y - 4, pos.z, SoundEvents.STONE_PLACE, 0.5F);
			}
		}
	}

	private static InteractionResult openVillageDoor(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		BlockState state = level.getBlockState(hit.getBlockPos());
		if (!(player instanceof ServerPlayer serverPlayer)
				|| serverPlayer.gameMode.getGameModeForPlayer() != GameType.SURVIVAL
				|| !state.is(Blocks.OAK_DOOR) || state.getValue(BlockStateProperties.OPEN)) {
			return InteractionResult.PASS;
		}
		HAUNTED_MANNEQUINS.stream()
				.filter(mannequin -> mannequin.level() == level && !mannequin.isRemoved()
						&& mannequin.distanceToSqr(player) <= 11 * 11)
				.min(Comparator.comparingDouble(mannequin -> mannequin.distanceToSqr(player)))
				.ifPresent(Mannequin::discard);
		return InteractionResult.PASS;
	}

	private static void playAt(ServerLevel level, double x, double y, double z, net.minecraft.sounds.SoundEvent sound, float volume) {
		level.playSound(null, x, y, z, sound, SoundSource.PLAYERS, volume, 1.0F);
	}

	private static boolean inVillage(ServerLevel level, BlockPos pos) {
		var lookup = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		var holders = VILLAGES.stream().map(lookup::getOrThrow).toList();
		return level.structureManager().getStructureWithPieceAt(pos, HolderSet.direct(holders)).isValid();
	}

	private static ResourceKey<Structure> structure(String path) {
		return ResourceKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath("minecraft", path));
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("main", path);
	}
}
