package monster.east.matchaff.mechanic;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

final class VillageMechanics {
	private static final Identifier OPENED_DOOR_ADVANCEMENT = id("mechanics/opened_oak_door");
	private static final AttachmentType<Integer> JUKEBOX_CLEANUP = AttachmentRegistry.create(
			id("village_jukebox_cleanup"), builder -> builder.persistent(Codec.INT));
	private static final Set<Mannequin> HAUNTED_MANNEQUINS =
			Collections.newSetFromMap(new IdentityHashMap<>());
	private static final Set<Marker> JUKEBOX_MARKERS =
			Collections.newSetFromMap(new IdentityHashMap<>());

	private VillageMechanics() {
	}

	static void init() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			HAUNTED_MANNEQUINS.clear();
			JUKEBOX_MARKERS.clear();
		});
		ServerEntityEvents.ENTITY_LOAD.register(VillageMechanics::trackVillageEntity);
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			HAUNTED_MANNEQUINS.remove(entity);
			JUKEBOX_MARKERS.remove(entity);
		});
	}

	static void handleDoorAdvancement(ServerPlayer player) {
		if (WorldMechanics.advancementDone(player, OPENED_DOOR_ADVANCEMENT)) {
			WorldMechanics.revoke(player, OPENED_DOOR_ADVANCEMENT);
			openVillageDoor(player);
		}
	}

	private static boolean tickPlayer(ServerPlayer player) {
		MechanicAdvancementTriggers.run(player, MechanicAdvancementTriggers.Stage.VILLAGE);
		var level = player.level();
		if (!inVillage(level, player.blockPosition())) {
			return false;
		}
		player.connection.send(new ClientboundStopSoundPacket(null, SoundSource.MUSIC));
		int step = eerieStep(level.getServer().getTickCount());
		if (step != 0) {
			var ground = level.getBlockState(BlockPos.containing(player.getX(), player.getY() - 0.5, player.getZ()));
			eerieCue(player, ground, step);
		}
		return true;
	}

	static void tick(MinecraftServer server) {
		Set<Mannequin> activeMannequins = Collections.newSetFromMap(new IdentityHashMap<>());
		Set<Marker> activeJukeboxes = Collections.newSetFromMap(new IdentityHashMap<>());
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!tickPlayer(player)) continue;
			HAUNTED_MANNEQUINS.stream()
					.filter(entity -> entity.level() == player.level() && !entity.isRemoved())
					.min(Comparator.comparingDouble(player::distanceToSqr)).ifPresent(activeMannequins::add);
			JUKEBOX_MARKERS.stream()
					.filter(entity -> entity.level() == player.level() && !entity.isRemoved())
					.min(Comparator.comparingDouble(player::distanceToSqr)).ifPresent(activeJukeboxes::add);
		}
		for (Mannequin mannequin : activeMannequins) {
			if (mannequin.isRemoved()) continue;
			ServerLevel level = (ServerLevel) mannequin.level();
			Marker marker = JUKEBOX_MARKERS.stream()
					.filter(entity -> entity.level() == level && !entity.isRemoved())
					.min(Comparator.comparingDouble(mannequin::distanceToSqr)).orElse(null);
			if (level.players().stream().anyMatch(player -> player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL
					&& (player.distanceToSqr(mannequin) <= 25 || marker != null && player.distanceToSqr(marker) <= 4))) {
				mannequin.discard();
				continue;
			}
			level.players().stream().min(Comparator.comparingDouble(mannequin::distanceToSqr))
					.ifPresent(player -> mannequin.lookAt(EntityAnchorArgument.Anchor.EYES, player.getEyePosition()));
		}
		// Process each marker once, regardless of how many players selected it.
		for (Marker marker : List.copyOf(JUKEBOX_MARKERS)) {
			if (marker.isRemoved()) continue;
			ServerLevel level = (ServerLevel) marker.level();
			BlockPos center = marker.blockPosition();
			if (!level.hasChunksAt(center.offset(-1, 0, -1), center.offset(1, 0, 1))) continue;
			int remaining = marker.getAttachedOrElse(JUKEBOX_CLEANUP, -1);
			if (!marker.hasAttached(JUKEBOX_CLEANUP)) {
				boolean hasRedstone = false;
				for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, 0, -1), center.offset(1, 0, 1))) {
					hasRedstone |= level.getBlockState(pos).is(Blocks.REDSTONE_BLOCK);
				}
				// Legacy playback removed the redstone even if its mannequin or pending task was lost.
				remaining = initialJukeboxCountdown(hasRedstone, marker.entityTags().contains("music_played"));
				marker.setAttached(JUKEBOX_CLEANUP, remaining);
				if (remaining >= 0) {
					marker.addTag("music_played");
					continue;
				}
			}
			if (remaining >= 0) {
				remaining = nextJukeboxCountdown(remaining);
				marker.setAttached(JUKEBOX_CLEANUP, remaining);
				if (remaining == 0) {
					for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, 0, -1), center.offset(1, 0, 1))) {
						if (level.getBlockState(pos).is(Blocks.HOPPER)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
					}
					marker.discard();
				}
				continue;
			}
			if (!activeJukeboxes.contains(marker) || level.players().stream().noneMatch(player ->
					player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL && player.distanceToSqr(marker) <= 30 * 30)) {
				continue;
			}
			for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, 0, -1), center.offset(1, 0, 1))) {
				if (level.getBlockState(pos).is(Blocks.REDSTONE_BLOCK)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
			}
			marker.addTag("music_played");
			marker.setAttached(JUKEBOX_CLEANUP, 2);
		}
	}

	static int initialJukeboxCountdown(boolean hasRedstone, boolean played) {
		return played || !hasRedstone ? 2 : -1;
	}

	static int nextJukeboxCountdown(int remaining) {
		return Math.max(0, remaining - 1);
	}

	private static void trackVillageEntity(Entity entity, ServerLevel level) {
		if (entity instanceof Mannequin mannequin && entity.entityTags().contains("haunted")) {
			HAUNTED_MANNEQUINS.add(mannequin);
		} else if (entity instanceof Marker marker && entity.entityTags().contains("jukebox")) {
			JUKEBOX_MARKERS.add(marker);
		}
	}

	private static void eerieCue(ServerPlayer player, BlockState ground, int step) {
		Vec3 pos = player.position();
		if (ground.is(Blocks.COARSE_DIRT) && step <= 4) {
			playAt(player, pos.x + step - 3, pos.y + 4, pos.z, SoundEvents.WOOD_STEP, 0.5F);
		}
		if (ground.is(Blocks.OAK_PLANKS)) {
			if (step == 1 || step == 100) {
				Vec3 left = WorldMechanics.localOffset(player, 5, 0, 0);
				playAt(player, left.x, left.y, left.z, SoundEvents.GRASS_BREAK, 1.0F);
			}
			if (step == 1) {
				playAt(player, pos.x, pos.y, pos.z, SoundEvents.AMBIENT_CAVE.value(), 1.0F, SoundSource.AMBIENT);
			}
		}
		if (ground.is(Blocks.GRAVEL)) {
			SoundEvent sound = gravelCueSound(step);
			if (sound != null) {
				Vec3 below = WorldMechanics.localOffset(player, 0, -3, 0);
				playAt(player, below.x, below.y, below.z, sound, 1.0F);
			}
		}
		if (ground.is(Blocks.SUSPICIOUS_GRAVEL) && step == 1) {
			Vec3 behind = WorldMechanics.localOffset(player, 0, 0, -3);
			playAt(player, behind.x, behind.y, behind.z, SoundEvents.WOODEN_DOOR_OPEN, 1.0F);
		}
		if (ground.is(Blocks.GRASS_BLOCK)) {
			if (step == 1) {
				playAt(player, pos.x, pos.y - 4, pos.z, SoundEvents.GRASS_BREAK, 0.5F);
			}
			if (step >= 2 && step <= 4) {
				playAt(player, pos.x + (5 - step), pos.y - 4, pos.z, SoundEvents.STONE_PLACE, 0.5F);
			}
			if (step == 4) {
				playAt(player, pos.x, pos.y - 4, pos.z, SoundEvents.STONE_PLACE, 0.5F);
			}
		}
	}

	private static void openVillageDoor(ServerPlayer player) {
		if (player.gameMode.getGameModeForPlayer() != GameType.SURVIVAL) {
			return;
		}
		HAUNTED_MANNEQUINS.stream()
				.filter(mannequin -> mannequin.level() == player.level() && !mannequin.isRemoved()
						&& mannequin.distanceToSqr(player) <= 11 * 11)
				.min(Comparator.comparingDouble(mannequin -> mannequin.distanceToSqr(player)))
				.ifPresent(Mannequin::discard);
	}

	static int eerieStep(int serverTick) {
		// Upstream restarts the shared eerie stopwatch after 150 seconds.
		return switch (Math.floorMod(serverTick, 3000)) {
			case 20 -> 1;
			case 25 -> 2;
			case 30 -> 3;
			case 35 -> 4;
			case 2000 -> 100;
			default -> 0;
		};
	}

	static SoundEvent gravelCueSound(int step) {
		return switch (step) {
			case 1 -> SoundEvents.GRAVEL_BREAK;
			case 100 -> SoundEvents.STONE_PLACE;
			default -> null;
		};
	}

	private static void playAt(ServerPlayer player, double x, double y, double z, SoundEvent sound, float volume) {
		playAt(player, x, y, z, sound, volume, SoundSource.PLAYERS);
	}

	private static void playAt(ServerPlayer player, double x, double y, double z, SoundEvent sound, float volume, SoundSource source) {
		player.connection.send(new ClientboundSoundPacket(Holder.direct(sound), source,
				x, y, z, volume, 1.0F, player.getRandom().nextLong()));
	}

	private static boolean inVillage(ServerLevel level, BlockPos pos) {
		return level.structureManager().getStructureWithPieceAt(pos, StructureTags.VILLAGE).isValid();
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("matcha", path);
	}
}
