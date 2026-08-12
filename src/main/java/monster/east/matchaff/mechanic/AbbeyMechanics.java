package monster.east.matchaff.mechanic;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * The Abbey "Copper Eye" puzzle, ported from the 27 {@code main:abbey/*}
 * functions. Markers with the copper-eye tags are tracked through entity
 * load/unload events (no global entity scans); every tick they either set up
 * their target/lantern/grate blocks, or die once the supporting block is
 * removed. Hitting any target grants the hidden {@code hit_copper_eye}
 * advancement, whose reward was the old {@code copper_eye_check} function;
 * here the same check runs in Java and revokes the advancement again.
 */
public final class AbbeyMechanics {
	private static final Identifier HIT_COPPER_EYE =
			Identifier.fromNamespaceAndPath("main", "mechanics/hit_copper_eye");

	private static final Set<Marker> COPPER_EYES =
			Collections.newSetFromMap(new IdentityHashMap<>());

	private AbbeyMechanics() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> COPPER_EYES.clear());
		ServerEntityEvents.ENTITY_LOAD.register(AbbeyMechanics::trackMarker);
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Marker marker) {
				COPPER_EYES.remove(marker);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				checkCopperEye(player);
			}
			tickCopperEyes();
		});
	}

	private static void trackMarker(Entity entity, ServerLevel level) {
		if (entity instanceof Marker marker && isCopperEye(marker)) {
			COPPER_EYES.add(marker);
		}
	}

	private static boolean isCopperEye(Marker marker) {
		Set<String> tags = marker.entityTags();
		return tags.contains("copper_eye_door")
				|| tags.contains("copper_eye_lantern")
				|| tags.contains("copper_eye_grate")
				|| tags.contains("copper_eye_grate_vertical")
				|| tags.contains("copper_eye_grate_vertical_retract")
				|| tags.contains("copper_eye_grate_horizontal_retract");
	}

	/**
	 * Datapack {@code main:abbey/copper_eye_check}: fired by the hidden
	 * {@code hit_copper_eye} advancement (any {@code target_hit}), then checks
	 * every set-up marker whose target block is powered (or missing) and runs the
	 * matching action, and finally revokes the advancement for all players.
	 */
	private static void checkCopperEye(ServerPlayer player) {
		if (!hisHitCopperEye(player)) {
			return;
		}
		List<ServerPlayer> hitters = player.level().getServer().getPlayerList().getPlayers().stream()
				.filter(candidate -> candidate.level() == player.level()
						&& hisHitCopperEye(candidate))
				.toList();
		for (Marker marker : List.copyOf(COPPER_EYES)) {
			if (marker.isRemoved() || marker.level() != player.level()
					|| !marker.entityTags().contains("marker_setup")) {
				continue;
			}
			if (!(marker.level() instanceof ServerLevel level)) {
				continue;
			}
			BlockState state = level.getBlockState(marker.blockPosition());
			if (state.is(Blocks.TARGET) && state.getValue(BlockStateProperties.POWER) == 0) {
				continue;
			}
			Set<String> tags = marker.entityTags();
			if (tags.contains("copper_eye_door")) {
				openDoor(marker, level, hitters);
			} else if (tags.contains("copper_eye_lantern")) {
				breakLantern(marker, level, hitters);
			} else if (tags.contains("copper_eye_grate")) {
				pushGrate(marker, level, hitters);
			} else if (tags.contains("copper_eye_grate_vertical")) {
				pushVerticalGrate(marker, level, hitters);
			} else if (tags.contains("copper_eye_grate_vertical_retract")) {
				pullVerticalGrate(marker, level, hitters);
			} else if (tags.contains("copper_eye_grate_horizontal_retract")) {
				pullHorizontalGrate(marker, level, hitters);
			}
		}
		for (ServerPlayer hitter : hitters) {
			revokeCopperEyeHit(hitter);
		}
	}

	/**
	 * Datapack {@code main:abbey/ticking}: markers without setup place their
	 * target/lantern/grate blocks and tag themselves; set-up markers die when
	 * their target (and, for lanterns and retracting grates, the supporting
	 * blocks) disappears.
	 */
	private static void tickCopperEyes() {
		COPPER_EYES.removeIf(marker -> marker.isRemoved() || !isCopperEye(marker));
		for (Marker marker : List.copyOf(COPPER_EYES)) {
			if (!(marker.level() instanceof ServerLevel level)) {
				continue;
			}
			if (!marker.entityTags().contains("marker_setup")) {
				setup(marker, level);
			}
			if (!marker.entityTags().contains("marker_setup")) {
				continue;
			}
			if (!level.getBlockState(marker.blockPosition()).is(Blocks.TARGET)) {
				marker.discard();
				continue;
			}
			Set<String> tags = marker.entityTags();
			if (tags.contains("copper_eye_lantern")
					&& !level.getBlockState(marker.blockPosition().below(2)).is(Blocks.LANTERN)) {
				marker.discard();
				continue;
			}
			if ((tags.contains("copper_eye_grate_vertical_retract")
					|| tags.contains("copper_eye_grate_horizontal_retract"))
					&& !gratesIntact(marker, level)) {
				marker.discard();
			}
		}
	}

	private static void setup(Marker marker, ServerLevel level) {
		BlockPos pos = marker.blockPosition();
		Set<String> tags = marker.entityTags();
		BlockState grate = Blocks.COPPER_GRATE.waxed().weathered().defaultBlockState();
		if (tags.contains("copper_eye_door")) {
			level.setBlock(pos, Blocks.TARGET.defaultBlockState(), 3);
		} else if (tags.contains("copper_eye_lantern")) {
			level.setBlock(pos, Blocks.TARGET.defaultBlockState(), 3);
			level.setBlock(pos.below(), Blocks.IRON_CHAIN.defaultBlockState()
					.setValue(ChainBlock.AXIS, Direction.Axis.Y), 3);
			level.setBlock(pos.below(2), Blocks.LANTERN.defaultBlockState()
					.setValue(LanternBlock.HANGING, true), 3);
		} else if (tags.contains("copper_eye_grate") || tags.contains("copper_eye_grate_vertical")) {
			level.setBlock(pos, Blocks.TARGET.defaultBlockState(), 3);
			setLocal(level, marker, 1, 0, 0, grate);
			setLocal(level, marker, -1, 0, 0, grate);
		} else if (tags.contains("copper_eye_grate_horizontal_retract")) {
			fillLocal(level, marker, -1, 0, 0, 1, 0, -2, grate);
			level.setBlock(pos, Blocks.TARGET.defaultBlockState(), 3);
		} else if (tags.contains("copper_eye_grate_vertical_retract")) {
			level.setBlock(pos, Blocks.TARGET.defaultBlockState(), 3);
			fillLocal(level, marker, -1, 0, -1, 1, 0, -2, grate);
			setLocal(level, marker, 1, 0, 0, grate);
			setLocal(level, marker, -1, 0, 0, grate);
		}
		if (level.getBlockState(pos).is(Blocks.TARGET)) {
			marker.addTag("marker_setup");
		}
	}

	private static boolean gratesIntact(Marker marker, ServerLevel level) {
		int[][] offsets = {{1, 0, -1}, {-1, 0, -1}, {1, 0, -2}, {-1, 0, -2}, {1, 0, 0}, {-1, 0, 0}};
		for (int[] offset : offsets) {
			if (!level.getBlockState(localBlockPos(marker, offset[0], offset[1], offset[2]))
					.is(Blocks.COPPER_GRATE.waxed().weathered())) {
				return false;
			}
		}
		return true;
	}

	private static void openDoor(Marker marker, ServerLevel level, List<ServerPlayer> hitters) {
		playHitSound(level, hitters);
		Vec3 pos = marker.position();
		BlockPos blockPos = marker.blockPosition();
		level.sendParticles(ParticleTypes.TRIAL_SPAWNER_DETECTED_PLAYER,
				pos.x, pos.y + 0.5, pos.z, 20, 0.5, 0.5, 0.5, 0);
		for (BlockPos p : BlockPos.betweenClosed(blockPos.offset(1, -2, 1), blockPos.offset(-1, -3, -1))) {
			if (level.getBlockState(p).is(Blocks.IRON_BARS)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
			}
		}
		for (int side : new int[]{0, 1, -1}) {
			Vec3 plume = localPos(marker, side, -3, 0);
			level.sendParticles(ParticleTypes.DUST_PLUME, plume.x, plume.y, plume.z, 6, 1.0, 0.1, 1.0, 0);
		}
		level.playSound(null, pos.x, pos.y - 3, pos.z,
				SoundEvents.IRON_GOLEM_HURT, SoundSource.BLOCKS, 0.4F, 1.0F);
		marker.discard();
	}

	private static void breakLantern(Marker marker, ServerLevel level, List<ServerPlayer> hitters) {
		playHitSound(level, hitters);
		Vec3 pos = marker.position();
		BlockPos blockPos = marker.blockPosition();
		level.sendParticles(ParticleTypes.TRIAL_SPAWNER_DETECTED_PLAYER,
				pos.x, pos.y + 0.5, pos.z, 20, 0.5, 0.5, 0.5, 0);
		for (BlockPos p : BlockPos.betweenClosed(blockPos.below(2), blockPos)) {
			if (level.getBlockState(p).is(Blocks.LANTERN)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
			}
		}
		for (BlockPos p : BlockPos.betweenClosed(blockPos.below(2), blockPos)) {
			if (level.getBlockState(p).is(Blocks.IRON_CHAIN)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
			}
		}
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.IRON_BLOCK.defaultBlockState()),
				pos.x, pos.y - 1, pos.z, 10, 0.1, 1.0, 0.1, 0.1);
		SmallFireball fireball = new SmallFireball(EntityTypes.SMALL_FIREBALL, level);
		fireball.setPos(pos.x, pos.y - 2, pos.z);
		fireball.setDeltaMovement(0, -0.25, 0);
		level.addFreshEntity(fireball);
		level.playSound(null, pos.x, pos.y - 2, pos.z,
				SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
		marker.discard();
	}

	private static void pushGrate(Marker marker, ServerLevel level, List<ServerPlayer> hitters) {
		playHitSound(level, hitters);
		Vec3 pos = marker.position();
		Vec3 detection = localPos(marker, 0, 0.5, 2);
		level.sendParticles(ParticleTypes.TRIAL_SPAWNER_DETECTED_PLAYER,
				detection.x, detection.y, detection.z, 20, 0.5, 0.5, 0.5, 0);
		fillLocal(level, marker, 1, 0, 0, -1, 0, 2, Blocks.COPPER_GRATE.weathering().unaffected().defaultBlockState());
		setLocal(level, marker, 0, 0, 2, Blocks.TARGET.defaultBlockState());
		Vec3 grate = localPos(marker, 0, 0, 1);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COPPER_GRATE.weathering().unaffected().defaultBlockState()),
				grate.x, grate.y, grate.z, 30, 1.0, 0.5, 1.0, 0.01);
		level.playSound(null, pos.x, pos.y - 3, pos.z,
				SoundEvents.IRON_GOLEM_HURT, SoundSource.BLOCKS, 0.4F, 1.0F);
		marker.discard();
	}

	private static void pullHorizontalGrate(Marker marker, ServerLevel level, List<ServerPlayer> hitters) {
		playHitSound(level, hitters);
		Vec3 pos = marker.position();
		Vec3 detection = localPos(marker, 0, 0.5, -2);
		level.sendParticles(ParticleTypes.TRIAL_SPAWNER_DETECTED_PLAYER,
				detection.x, detection.y, detection.z, 20, 0.5, 0.5, 0.5, 0);
		fillLocal(level, marker, 1, 0, 0, -1, 0, -1, Blocks.AIR.defaultBlockState());
		fillLocal(level, marker, 1, 0, -2, -1, 0, -2, Blocks.COPPER_GRATE.weathering().unaffected().defaultBlockState());
		setLocal(level, marker, 0, 0, -2, Blocks.TARGET.defaultBlockState());
		Vec3 grate = localPos(marker, 0, 0, 1);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COPPER_GRATE.weathering().unaffected().defaultBlockState()),
				grate.x, grate.y, grate.z, 30, 1.0, 0.5, 1.0, 0.01);
		level.playSound(null, pos.x, pos.y - 3, pos.z,
				SoundEvents.IRON_GOLEM_HURT, SoundSource.BLOCKS, 0.4F, 1.0F);
		marker.discard();
	}

	private static void pushVerticalGrate(Marker marker, ServerLevel level, List<ServerPlayer> hitters) {
		playHitSound(level, hitters);
		Vec3 pos = marker.position();
		for (int side : new int[]{0, -1, 1}) {
			teleportAbove(level, marker, side);
		}
		for (int side : new int[]{0, -1, 1}) {
			Vec3 anchor = localPos(marker, side, 0, 0);
			WindCharge charge = new WindCharge(EntityTypes.WIND_CHARGE, level);
			charge.setPos(anchor.x, anchor.y + 3.25, anchor.z);
			charge.setDeltaMovement(0, -1, 0);
			level.addFreshEntity(charge);
		}
		level.sendParticles(ParticleTypes.TRIAL_SPAWNER_DETECTED_PLAYER,
				pos.x, pos.y + 2, pos.z, 20, 0.5, 0.5, 0.5, 0);
		fillLocal(level, marker, 1, 0, 0, -1, 0, 2, Blocks.COPPER_GRATE.weathering().unaffected().defaultBlockState());
		setLocal(level, marker, 0, 0, 2, Blocks.TARGET.defaultBlockState());
		Vec3 grate = localPos(marker, 0, 0, 1);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COPPER_GRATE.weathering().unaffected().defaultBlockState()),
				grate.x, grate.y, grate.z, 30, 1.0, 0.5, 1.0, 0.01);
		level.playSound(null, pos.x, pos.y - 3, pos.z,
				SoundEvents.IRON_GOLEM_HURT, SoundSource.BLOCKS, 0.4F, 1.0F);
		marker.discard();
	}

	private static void pullVerticalGrate(Marker marker, ServerLevel level, List<ServerPlayer> hitters) {
		playHitSound(level, hitters);
		Vec3 pos = marker.position();
		level.sendParticles(ParticleTypes.TRIAL_SPAWNER_DETECTED_PLAYER,
				pos.x, pos.y + 2, pos.z, 20, 0.5, 0.5, 0.5, 0);
		fillLocal(level, marker, 1, 0, 0, -1, 0, -1, Blocks.AIR.defaultBlockState());
		setLocal(level, marker, 0, 0, -2, Blocks.TARGET.defaultBlockState());
		setLocal(level, marker, 1, 0, -2, Blocks.COPPER_GRATE.weathering().unaffected().defaultBlockState());
		setLocal(level, marker, -1, 0, -2, Blocks.COPPER_GRATE.weathering().unaffected().defaultBlockState());
		Vec3 grate = localPos(marker, 0, 0, 1);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COPPER_GRATE.weathering().unaffected().defaultBlockState()),
				grate.x, grate.y, grate.z, 30, 1.0, 0.5, 1.0, 0.01);
		level.playSound(null, pos.x, pos.y - 3, pos.z,
				SoundEvents.IRON_GOLEM_HURT, SoundSource.BLOCKS, 0.4F, 1.0F);
		marker.discard();
	}

	private static void playHitSound(ServerLevel level, List<ServerPlayer> hitters) {
		for (ServerPlayer player : hitters) {
			player.connection.send(new ClientboundSoundPacket(
					BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.ENCHANTMENT_TABLE_USE), SoundSource.BLOCKS,
					player.getX(), player.getY(), player.getZ(), 1.0F, 2.0F, level.getRandom().nextLong()));
		}
	}

	private static void teleportAbove(ServerLevel level, Marker marker, int left) {
		Vec3 anchor = localPos(marker, left, 0, 0);
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class,
				new AABB(anchor, anchor).inflate(1.0), p -> p.distanceToSqr(anchor) <= 1.0)) {
			player.teleportTo(player.getX(), player.getY() + 3, player.getZ());
		}
	}

	/**
	 * Local {@code ^left ^up ^forward} coordinates resolved exactly like the
	 * command source's {@code execute positioned}, using the marker's rotation.
	 */
	private static Vec3 localPos(Marker marker, double left, double up, double forward) {
		Vec3 offset = Vec3.applyLocalCoordinatesToRotation(
				new Vec2(marker.getXRot(), marker.getYRot()), new Vec3(left, up, forward));
		return marker.position().add(offset);
	}

	private static BlockPos localBlockPos(Marker marker, int left, int up, int forward) {
		return BlockPos.containing(localPos(marker, left, up, forward));
	}

	private static void setLocal(ServerLevel level, Marker marker, int left, int up, int forward, BlockState state) {
		level.setBlock(localBlockPos(marker, left, up, forward), state, 3);
	}

	private static void fillLocal(
			ServerLevel level, Marker marker, int l1, int u1, int f1, int l2, int u2, int f2, BlockState state
	) {
		BlockPos a = localBlockPos(marker, l1, u1, f1);
		BlockPos b = localBlockPos(marker, l2, u2, f2);
		for (BlockPos p : BlockPos.betweenClosed(a, b)) {
			level.setBlock(p, state, 3);
		}
	}

	private static boolean hisHitCopperEye(ServerPlayer player) {
		AdvancementHolder advancement = player.level().getServer().getAdvancements().get(AbbeyMechanics.HIT_COPPER_EYE);
		return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
	}

	private static void revokeCopperEyeHit(ServerPlayer player) {
		AdvancementHolder advancement = player.level().getServer().getAdvancements().get(AbbeyMechanics.HIT_COPPER_EYE);
		if (advancement != null) {
			for (String criterion : advancement.value().criteria().keySet()) {
				player.getAdvancements().revoke(advancement, criterion);
			}
		}
	}
}
