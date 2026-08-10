package monster.east.matchaff;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.scores.ScoreHolder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The remaining datapack "mechanic" behaviours: estus, clay-statue weather,
 * bedrock buster, asylum-seeker application, happy ghast horn, dragon reward,
 * the warding stone entity system and stackable water bottles.
 */
public final class MechanicMechanics {
	private static final Logger LOGGER = LoggerFactory.getLogger("matcha");
	private static final Identifier ESTUS = id("mechanics/estus_obtained");
	private static final Identifier CHEERFUL_STATUE = id("mechanics/cheerful_clay_statue");
	private static final Identifier MOURNFUL_STATUE = id("mechanics/mournful_clay_statue");
	private static final Identifier BEDROCK_BUSTER = id("mechanics/bedrock_buster");
	private static final Identifier APPLICATION = id("mechanics/application");
	private static final Identifier HAPPY_GHAST_HORN = id("mechanics/happy_ghast_horn");
	private static final Identifier KILL_DRAGON = id("end/kill_dragon");

	private static final TagKey<EntityType<?>> VILLAGER_FRIENDS = TagKey.create(
			Registries.ENTITY_TYPE, id("villager_friends")
	);
	private static final ResourceKey<Structure> TRIAL_CHAMBERS = ResourceKey.create(
			Registries.STRUCTURE, Identifier.fromNamespaceAndPath("minecraft", "trial_chambers")
	);

	private record WeatherTask(int triggerTick, boolean rain, UUID player, Identifier advancement) {
	}

	private record BusterTask(int triggerTick, ResourceKey<Level> level, UUID tnt) {
	}

	private static final List<WeatherTask> PENDING_WEATHER = new ArrayList<>();
	private static final List<BusterTask> PENDING_BUSTER = new ArrayList<>();
	private static final Set<ArmorStand> WARDING_STONES =
			Collections.newSetFromMap(new IdentityHashMap<>());
	private static final Set<Villager> VILLAGERS =
			Collections.newSetFromMap(new IdentityHashMap<>());

	private MechanicMechanics() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PENDING_WEATHER.clear();
			PENDING_BUSTER.clear();
			WARDING_STONES.clear();
			VILLAGERS.clear();
		});
		ServerEntityEvents.ENTITY_LOAD.register(MechanicMechanics::trackEntity);
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			WARDING_STONES.remove(entity);
			VILLAGERS.remove(entity);
		});
		// The datapack's scheduled function runs before entities tick, so the
		// glowing TNT is still alive (fuse 1) when bedrock is removed. Running
		// this at END_SERVER_TICK left the TNT exploded and never found it.
		ServerTickEvents.START_SERVER_TICK.register(server -> processBusterTasks(server, server.getTickCount()));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int tick = server.getTickCount();
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				checkEstus(player);
				checkStatueWeather(player);
				checkBedrockBuster(player);
				checkApplication(player);
				checkHappyGhast(player);
				checkDragonReward(server, player);
				stackWaterBottles(player);
			}
			processWeatherTasks(server, tick);
			tickWardingStones();
		});
	}

	private static void checkEstus(ServerPlayer player) {
		if (!advancementDone(player, ESTUS)) {
			return;
		}
		if (!player.isCreative()) {
			for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
				ItemStack stack = player.getInventory().getItem(slot);
				if (!stack.is(Items.BLAZE_POWDER)) {
					continue;
				}
				stack.shrink(1);
				player.addEffect(new MobEffectInstance(
						MobEffects.REGENERATION, 40, 4, true, true));
				player.addEffect(new MobEffectInstance(
						MobEffects.RESISTANCE, 100, 0, true, true));
				ItemStack reward = new ItemStack(Items.GLOWSTONE_DUST, 1);
				if (!player.addItem(reward)) {
					var dropped = player.drop(reward, false);
					if (dropped != null) {
						dropped.setNoPickUpDelay();
						dropped.setTarget(player.getUUID());
					}
				}
				var level = (ServerLevel) player.level();
				level.sendParticles(new DustParticleOptions(0xFFAA17, 1.0F),
						player.getX(), player.getY() + 1.5, player.getZ(), 8, 0.25, 0.25, 0.25, 0.1);
				break;
			}
		}
		revoke(player, ESTUS);
	}

	private static void checkStatueWeather(ServerPlayer player) {
		if (advancementDone(player, CHEERFUL_STATUE)) {
			scheduleWeather(player, false, CHEERFUL_STATUE);
		}
		if (advancementDone(player, MOURNFUL_STATUE)) {
			scheduleWeather(player, true, MOURNFUL_STATUE);
		}
	}

	private static void scheduleWeather(ServerPlayer player, boolean rain, Identifier advancement) {
		boolean alreadyScheduled = PENDING_WEATHER.stream().anyMatch(task ->
				task.player().equals(player.getUUID()) && task.advancement().equals(advancement));
		if (!alreadyScheduled) {
			PENDING_WEATHER.add(new WeatherTask(
					player.level().getServer().getTickCount() + 60,
					rain, player.getUUID(), advancement));
		}
	}

	private static void checkBedrockBuster(ServerPlayer player) {
		if (!advancementDone(player, BEDROCK_BUSTER)) {
			return;
		}
		var level = (ServerLevel) player.level();
		level.getEntitiesOfClass(PrimedTnt.class, player.getBoundingBox().inflate(16.0), PrimedTnt::hasGlowingTag)
				.stream()
				.min(Comparator.comparingDouble(tnt -> tnt.distanceToSqr(player)))
				.ifPresentOrElse(
						tnt -> PENDING_BUSTER.add(new BusterTask(
								level.getServer().getTickCount() + 79, level.dimension(), tnt.getUUID())),
						() -> LOGGER.warn("Bedrock buster triggered without a nearby glowing TNT for {}", player.getName().getString())
				);
		revoke(player, BEDROCK_BUSTER);
	}

	private static void checkApplication(ServerPlayer player) {
		if (!advancementDone(player, APPLICATION)) {
			return;
		}
		var level = (ServerLevel) player.level();
		var villager = VILLAGERS.stream()
				.filter(v -> v.level() == level)
				.min(Comparator.comparingDouble(v -> v.distanceToSqr(player)));
		villager.ifPresent(v -> level.sendParticles(ParticleTypes.POOF,
				v.getX(), v.getY() + 0.5, v.getZ(), 40, 0.25, 1.0, 0.25, 0.05));
		revoke(player, APPLICATION);
	}

	private static void checkHappyGhast(ServerPlayer player) {
		if (!advancementDone(player, HAPPY_GHAST_HORN)) {
			return;
		}
		var level = (ServerLevel) player.level();
		var ghast = level.getEntitiesOfClass(HappyGhast.class, player.getBoundingBox().inflate(80.0),
				g -> g.distanceToSqr(player) <= 80.0 * 80.0)
				.stream().min(Comparator.comparingDouble(g -> g.distanceToSqr(player)));
		ghast.ifPresent(g -> {
			var target = player.position().add(player.getLookAngle().scale(3.0));
			g.teleportTo(target.x, target.y, target.z);
		});
		revoke(player, HAPPY_GHAST_HORN);
	}

	private static void checkDragonReward(MinecraftServer server, ServerPlayer player) {
		if (!advancementDone(player, KILL_DRAGON)) {
			return;
		}
		var scoreboard = server.getScoreboard();
		var objective = scoreboard.getObjective("gamerule_safe_surface");
		if (objective == null
				|| scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly("gamerule"), objective).get() >= 1) {
			return; // already rewarded
		}
		var end = server.getLevel(Level.END);
		if (end != null) {
			ItemEntity reward = new ItemEntity(end, 0, 100, 0, new ItemStack(Items.NETHER_STAR, 1));
			reward.setUnlimitedLifetime();
			end.addFreshEntity(reward);
		}
		server.getPlayerList().broadcastSystemMessage(
				Component.translatable("matcha.message.evil_banished").withStyle(ChatFormatting.GRAY), false);
		scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly("gamerule"), objective).set(1);
	}

	private static void stackWaterBottles(ServerPlayer player) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (!stack.is(Items.POTION)) {
				continue;
			}
			PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
			if (contents.is(Potions.WATER) && stack.getOrDefault(DataComponents.MAX_STACK_SIZE, 0) != 64) {
				stack.set(DataComponents.MAX_STACK_SIZE, 64);
			}
		}
	}

	private static void processWeatherTasks(MinecraftServer server, int tick) {
		PENDING_WEATHER.removeIf(task -> {
			if (task.triggerTick() > tick) {
				return false;
			}
			server.setWeatherParameters(task.rain() ? 0 : 6000, task.rain() ? 6000 : 0, task.rain(), false);
			ServerPlayer player = server.getPlayerList().getPlayer(task.player());
			if (player != null) {
				revoke(player, task.advancement());
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

	private static void trackEntity(Entity entity, ServerLevel level) {
		if (entity instanceof ArmorStand stone && stone.entityTags().contains("WardingStone")) {
			WARDING_STONES.add(stone);
		}
		if (entity instanceof Villager villager) {
			VILLAGERS.add(villager);
		}
	}

	private static void tickWardingStones() {
		WARDING_STONES.removeIf(stone ->
				stone.isRemoved() || !stone.entityTags().contains("WardingStone"));
		for (ArmorStand stone : List.copyOf(WARDING_STONES)) {
			if (!(stone.level() instanceof ServerLevel level)) {
				continue;
			}
			BlockPos pos = stone.blockPosition();
			double stoneX = stone.getX();
			double stoneY = stone.getY();
			double stoneZ = stone.getZ();
			boolean setup = stone.entityTags().contains("WardingStoneSetup");

			// Heal friendly villagers nearby.
			var friends = level.getEntitiesOfClass(LivingEntity.class, stone.getBoundingBox().inflate(16.0),
					f -> f.getType().builtInRegistryHolder().is(VILLAGER_FRIENDS)
							&& f.distanceToSqr(stone) <= 16.0 * 16.0);
			boolean someoneRegenerating = friends.stream()
					.anyMatch(f -> f.hasEffect(MobEffects.REGENERATION));
			if (!someoneRegenerating) {
				for (LivingEntity friend : friends) {
					friend.addEffect(new MobEffectInstance(
							MobEffects.REGENERATION, 60, 0, true, true));
				}
			}

			boolean anchored = level.getBlockState(pos).is(Blocks.LODESTONE);
			if (!setup) {
				if (!anchored) {
					level.setBlockAndUpdate(pos, Blocks.LODESTONE.defaultBlockState());
				}
				level.playSound(null, pos, SoundEvents.WITHER_SPAWN, SoundSource.BLOCKS, 0.25F, 1.0F);
				level.sendParticles(ParticleTypes.SCULK_SOUL, stoneX, stoneY + 0.5, stoneZ,
						10, 0.25, 0.1, 0.25, 0.05);
				level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, stoneX, stoneY + 0.5, stoneZ,
						10, 0.5, 0.1, 0.5, 0.1);
				stone.addTag("WardingStoneSetup");
				setup = true;
			}

			// Anchor destroyed: refund blaze powder and remove the stone.
			if (setup && !level.getBlockState(pos).is(Blocks.LODESTONE)) {
				for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, stone.getBoundingBox().inflate(3.0))) {
					if (item.getItem().is(Items.LODESTONE)) {
						item.discard();
					}
				}
				level.sendParticles(ParticleTypes.LARGE_SMOKE, stoneX, stoneY + 0.25, stoneZ,
						20, 0.25, 0.5, 0.25, 0.01);
				level.addFreshEntity(new ItemEntity(level, stoneX, stoneY, stoneZ,
						new ItemStack(Items.BLAZE_POWDER, 7)));
				stone.discard();
				continue;
			}

			// Placed inside a trial chamber: forbidden, destroy it.
			var structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(TRIAL_CHAMBERS);
			if (level.structureManager().getStructureWithPieceAt(pos, HolderSet.direct(structure)).isValid()) {
				level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
				var tnt = EntityTypes.TNT.create(level, EntitySpawnReason.EVENT);
				tnt.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
				tnt.setFuse(0);
				level.addFreshEntity(tnt);
				level.sendParticles(ParticleTypes.SCULK_SOUL, stoneX, stoneY, stoneZ,
						100, 0.1, 0.1, 0.1, 0.5);
				stone.discard();
				continue;
			}

			// Aura: slow and burn undead.
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, stoneX, stoneY + 0.5, stoneZ,
					1, 0.5, 0.5, 0.5, 0);
			for (LivingEntity undead : level.getEntitiesOfClass(LivingEntity.class, stone.getBoundingBox().inflate(26.0),
					u -> u.getType().builtInRegistryHolder().is(net.minecraft.tags.EntityTypeTags.UNDEAD)
							&& u.distanceToSqr(stone) <= 26.0 * 26.0)) {
				undead.addEffect(new MobEffectInstance(
						MobEffects.SLOWNESS, 40, 1, true, false));
			}

			if (level.getServer().getTickCount() % 10 == 0) {
				LivingEntity generalTarget = nearestUndead(level, stone, 24.0, target -> true);
				if (generalTarget != null && nearestUndead(level, generalTarget, 20.0,
						target -> target.getType() == EntityTypes.WITHER) == null) {
					level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
							generalTarget.getX(), generalTarget.getY() + 2.0, generalTarget.getZ(),
							1, 0.25, 0.25, 0.25, 0.025);
					LivingEntity damageTarget = nearestUndead(level, generalTarget, 14.0, target -> true);
					if (damageTarget != null) {
						damageTarget.hurt(level.damageSources().fellOutOfWorld(), 7.0F);
					}
				}

				LivingEntity witherTarget = nearestUndead(level, stone, 24.0,
						target -> target.getType() == EntityTypes.WITHER);
				if (witherTarget != null) {
					level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
							witherTarget.getX(), witherTarget.getY() + 2.5, witherTarget.getZ(),
							2, 1.0, 1.0, 1.0, 0.5);
					LivingEntity damageTarget = nearestUndead(level, witherTarget, 14.0, target -> true);
					if (damageTarget != null) {
						damageTarget.hurt(level.damageSources().fellOutOfWorld(), 2.0F);
					}
				}
			}
		}
	}

	private static LivingEntity nearestUndead(
			ServerLevel level, LivingEntity center, double radius, Predicate<LivingEntity> predicate
	) {
		double radiusSquared = radius * radius;
		return level.getEntitiesOfClass(LivingEntity.class, center.getBoundingBox().inflate(radius), target ->
					target.getType().builtInRegistryHolder().is(net.minecraft.tags.EntityTypeTags.UNDEAD)
							&& predicate.test(target)
							&& target.distanceToSqr(center) <= radiusSquared)
				.stream()
				.min(Comparator.comparingDouble(target -> target.distanceToSqr(center)))
				.orElse(null);
	}

	private static boolean advancementDone(ServerPlayer player, Identifier advancementId) {
		AdvancementHolder advancement = player.level().getServer().getAdvancements().get(advancementId);
		return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
	}

	private static void revoke(ServerPlayer player, Identifier advancementId) {
		AdvancementHolder advancement = player.level().getServer().getAdvancements().get(advancementId);
		if (advancement != null) {
			for (String criterion : advancement.value().criteria().keySet()) {
				player.getAdvancements().revoke(advancement, criterion);
			}
		}
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("main", path);
	}
}
