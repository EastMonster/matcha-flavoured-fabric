package monster.east.matchaff.mechanic;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

final class TimedMechanics {
	private static final Logger LOGGER = LoggerFactory.getLogger("matcha");

	private record WeatherTask(int triggerTick, boolean rain, UUID player, Identifier advancement) {
	}

	private record BusterTask(int triggerTick, ResourceKey<Level> level, UUID tnt) {
	}

	private record WitherTask(int triggerTick, ResourceKey<Level> level, UUID wither) {
	}

	private record StarCleanupTask(int triggerTick, ResourceKey<Level> level, Vec3 position) {
	}

	private static final List<WeatherTask> PENDING_WEATHER = new ArrayList<>();
	private static final List<BusterTask> PENDING_BUSTER = new ArrayList<>();
	private static final List<WitherTask> PENDING_WITHERS = new ArrayList<>();
	private static final List<StarCleanupTask> PENDING_STAR_CLEANUP = new ArrayList<>();

	private TimedMechanics() {
	}

	static void init() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PENDING_WEATHER.clear();
			PENDING_BUSTER.clear();
			PENDING_WITHERS.clear();
			PENDING_STAR_CLEANUP.clear();
		});
	}

	static void startTick(MinecraftServer server, int tick) {
		// The datapack's scheduled function runs before entities tick, so the
		// glowing TNT is still alive (fuse 1) when bedrock is removed. Running
		// this at END_SERVER_TICK left the TNT exploded and never found it.
		if (server.tickRateManager().runsNormally()) {
			processBusterTasks(server, tick);
		}
	}

	static void tick(MinecraftServer server, int tick) {
		processWeatherTasks(server, tick);
		processWitherTasks(server, tick);
		processStarCleanup(server, tick);
	}

	static void scheduleWeather(ServerPlayer player, boolean rain, Identifier advancement) {
		boolean alreadyScheduled = PENDING_WEATHER.stream().anyMatch(task ->
				task.player().equals(player.getUUID()) && task.advancement().equals(advancement));
		if (!alreadyScheduled) {
			PENDING_WEATHER.add(new WeatherTask(
					player.level().getServer().getTickCount() + 60,
					rain, player.getUUID(), advancement));
		}
	}

	static void scheduleBuster(ServerPlayer player, PrimedTnt tnt) {
		var level = player.level();
		PENDING_BUSTER.add(new BusterTask(
				level.getServer().getTickCount() + 79, level.dimension(), tnt.getUUID()));
	}

	static void scheduleWither(ServerPlayer player, int tick) {
		if (player.level().dimension() != Level.END) {
			return;
		}
		ServerLevel level = player.level();
		level.getEntitiesOfClass(WitherBoss.class, player.getBoundingBox().inflate(64.0), Entity::isAlive)
				.stream().min(Comparator.comparingDouble(wither -> wither.distanceToSqr(player)))
				.ifPresent(wither -> PENDING_WITHERS.add(
						new WitherTask(tick + 23, level.dimension(), wither.getUUID())));
	}

	private static void processWitherTasks(MinecraftServer server, int tick) {
		PENDING_WITHERS.removeIf(task -> {
			if (task.triggerTick() > tick) {
				return false;
			}
			ServerLevel level = server.getLevel(task.level());
			if (level != null && level.getEntity(task.wither()) instanceof WitherBoss wither && wither.isAlive()) {
				Vec3 position = wither.position();
				wither.kill(level);
				PENDING_STAR_CLEANUP.add(new StarCleanupTask(tick + 1, task.level(), position));
			}
			return true;
		});
	}

	private static void processStarCleanup(MinecraftServer server, int tick) {
		PENDING_STAR_CLEANUP.removeIf(task -> {
			if (task.triggerTick() > tick) {
				return false;
			}
			ServerLevel level = server.getLevel(task.level());
			if (level != null) {
				Vec3 pos = task.position();
				AABB area = new AABB(pos.x - 8, pos.y - 8, pos.z - 8,
						pos.x + 8, pos.y + 8, pos.z + 8);
				for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area,
						entity -> entity.getItem().is(Items.NETHER_STAR))) {
					item.discard();
				}
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
			ServerPlayer player = server.getPlayerList().getPlayer(task.player());
			if (player != null) {
				WorldMechanics.revoke(player, task.advancement());
			}
			return true;
		});
	}

	private static void processBusterTasks(MinecraftServer server, int tick) {
		PENDING_BUSTER.removeIf(task -> {
			if (task.triggerTick() > tick) {
				return false;
			}
			ServerLevel level = server.getLevel(task.level());
			if (level != null) {
				int removed = 0;
				Entity entity = level.getEntity(task.tnt());
				if (entity instanceof PrimedTnt tnt && tnt.hasGlowingTag()) {
					BlockPos pos = tnt.blockPosition();
					for (int dx = -1; dx <= 1; dx++) {
						for (int dz = -1; dz <= 1; dz++) {
							for (int dy = -3; dy <= 3; dy++) {
								BlockPos target = pos.offset(dx, dy, dz);
								if (level.getBlockState(target).is(Blocks.BEDROCK)) {
									level.setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());
									removed++;
								}
							}
						}
					}
					level.sendParticles(ParticleTypes.END_ROD, pos.getX(), pos.getY(), pos.getZ(),
							50, 0.5, 0.5, 0.5, 0.1);
				}
				if (removed > 0) {
					LOGGER.info("Bedrock buster removed {} bedrock blocks", removed);
				}
			}
			return true;
		});
	}
}
