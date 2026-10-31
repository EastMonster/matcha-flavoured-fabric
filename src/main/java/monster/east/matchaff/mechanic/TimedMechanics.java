package monster.east.matchaff.mechanic;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class TimedMechanics {
	private static final Logger LOGGER = LoggerFactory.getLogger("matcha");

	private record WeatherTask(int triggerTick, boolean rain, UUID player, Identifier advancement) {
	}

	private record WitherTask(int triggerTick, ResourceKey<Level> level, UUID wither) {
	}

	private static final List<WeatherTask> PENDING_WEATHER = new ArrayList<>();
	private static final String BUSTER_TAG = "matcha:bedrock_buster";
	private static final TagKey<Block> BEDROCK_BUSTABLE = TagKey.create(
			Registries.BLOCK, Identifier.fromNamespaceAndPath("matcha", "bedrock_bustable"));
	private static final Set<PrimedTnt> BEDROCK_BUSTERS = Collections.newSetFromMap(new IdentityHashMap<>());
	private static final List<WitherTask> PENDING_WITHERS = new ArrayList<>();

	private TimedMechanics() {
	}

	static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof PrimedTnt tnt && tnt.entityTags().contains(BUSTER_TAG)) {
				BEDROCK_BUSTERS.add(tnt);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> BEDROCK_BUSTERS.remove(entity));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PENDING_WEATHER.clear();
			BEDROCK_BUSTERS.clear();
			PENDING_WITHERS.clear();
		});
	}

	static void startTick(MinecraftServer server) {
		// Remove bedrock before the entity tick consumes the last fuse tick.
		if (server.tickRateManager().runsNormally()) {
			processBedrockBusters();
		}
	}

	static void tick(MinecraftServer server, int tick) {
		processWeatherTasks(server, tick);
		processWitherTasks(server, tick);
	}

	static void scheduleWeather(ServerPlayer player, boolean rain, Identifier advancement) {
		boolean alreadyScheduled = PENDING_WEATHER.stream().anyMatch(task ->
				task.player().equals(player.getUUID()) && task.advancement().equals(advancement));
		if (!alreadyScheduled) {
			PENDING_WEATHER.add(new WeatherTask(
					player.level().getServer().getTickCount() + 60,
					rain, player.getUUID(), advancement));
		}
		// An existing task also consumes repeated triggers so none survive logout.
		WorldMechanics.revoke(player, advancement);
	}

	static void scheduleWither(ServerPlayer player, int tick) {
		ServerLevel level = player.level();
		level.getEntitiesOfClass(WitherBoss.class, player.getBoundingBox().inflate(64.0),
				wither -> wither.isAlive() && wither.getInvulnerableTicks() > 0)
				.stream().min(Comparator.comparingDouble(wither -> wither.distanceToSqr(player)))
				.ifPresent(wither -> {
					BlockPos pos = wither.blockPosition();
					// Use generated terrain height
					boolean onOverworldSurface = level.dimension() == Level.OVERWORLD
							&& pos.getY() > 62
							&& pos.getY() == level.getChunkSource().getGenerator().getBaseHeight(
									pos.getX(), pos.getZ(), Heightmap.Types.WORLD_SURFACE_WG,
									level, level.getChunkSource().randomState());
					if (!onOverworldSurface && PENDING_WITHERS.stream().noneMatch(task -> task.wither().equals(wither.getUUID()))) {
						for (ServerPlayer nearby : level.players()) {
							if (nearby.distanceToSqr(wither) <= 100.0 * 100.0) {
								nearby.sendSystemMessage(Component.translatable("error.matcha.wither_spawn_invalid")
										.withStyle(ChatFormatting.RED));
							}
						}
						PENDING_WITHERS.add(new WitherTask(tick + 23, level.dimension(), wither.getUUID()));
					}
				});
	}

	private static void processWitherTasks(MinecraftServer server, int tick) {
		PENDING_WITHERS.removeIf(task -> {
			if (task.triggerTick() > tick) {
				return false;
			}
			ServerLevel level = server.getLevel(task.level());
			if (level != null && level.getEntity(task.wither()) instanceof WitherBoss wither && wither.isAlive()) {
				Vec3 position = wither.position();
				wither.discard();
				level.sendParticles(new DustColorTransitionOptions(0xFF0000, 0x000000, 2.0F),
						position.x, position.y, position.z, 50, 1.0, 1.0, 1.0, 1.0);
				level.playSound(null, position.x, position.y, position.z, SoundEvents.WITHER_HURT,
						SoundSource.HOSTILE, 2.0F, 1.0F);
				level.addFreshEntity(new ItemEntity(level, position.x, position.y, position.z,
						new ItemStack(Items.WITHER_SKELETON_SKULL, 3)));
			}
			return true;
		});
	}

	private static void processWeatherTasks(MinecraftServer server, int tick) {
		PENDING_WEATHER.removeIf(task -> {
			if (task.triggerTick() > tick) {
				return false;
			}
			int duration = (task.rain() ? ServerLevel.RAIN_DURATION : ServerLevel.RAIN_DELAY)
					.sample(server.overworld().getRandom());
			server.setWeatherParameters(task.rain() ? 0 : duration, task.rain() ? duration : 0, task.rain(), false);
			return true;
		});
	}

	static boolean shouldBreakBedrock(int fuse) {
		return fuse >= 0 && fuse <= 1;
	}

	private static void processBedrockBusters() {
		for (PrimedTnt tnt : new ArrayList<>(BEDROCK_BUSTERS)) {
			if (tnt.isRemoved() || !tnt.entityTags().contains(BUSTER_TAG)) {
				BEDROCK_BUSTERS.remove(tnt);
				continue;
			}
			if (!(tnt.level() instanceof ServerLevel level)
					|| !level.isPositionEntityTicking(tnt.blockPosition())
					|| !shouldBreakBedrock(tnt.getFuse())) {
				continue;
			}
			tnt.removeTag(BUSTER_TAG);
			BEDROCK_BUSTERS.remove(tnt);
			BlockPos pos = tnt.blockPosition();
			int removed = 0;
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					for (int dy = -3; dy <= 3; dy++) {
						BlockPos target = pos.offset(dx, dy, dz);
						if (level.getBlockState(target).is(BEDROCK_BUSTABLE)) {
							level.setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());
							removed++;
						}
					}
				}
			}
			level.sendParticles(ParticleTypes.END_ROD, pos.getX(), pos.getY(), pos.getZ(),
					50, 0.5, 0.5, 0.5, 0.1);
			if (removed > 0) {
				LOGGER.info("Bedrock buster removed {} blocks", removed);
			}
		}
	}
}
