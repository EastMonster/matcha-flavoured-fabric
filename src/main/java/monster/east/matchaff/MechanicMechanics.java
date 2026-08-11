package monster.east.matchaff;

import monster.east.matchaff.mixin.VillagerAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
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
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
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
	private static final Identifier SUMMONED_WITHER = id("mechanics/summoned_wither");

	private static final TagKey<EntityType<?>> VILLAGER_FRIENDS = TagKey.create(
			Registries.ENTITY_TYPE, id("villager_friends")
	);
	private static final TagKey<EntityType<?>> WARDING_STONE_TARGETS = TagKey.create(
			Registries.ENTITY_TYPE, id("warding_stone_targets")
	);
	private static final ResourceKey<Structure> TRIAL_CHAMBERS = ResourceKey.create(
			Registries.STRUCTURE, Identifier.fromNamespaceAndPath("minecraft", "trial_chambers")
	);

	private record WeatherTask(int triggerTick, boolean rain, UUID player, Identifier advancement) {
	}

	private record BusterTask(int triggerTick, ResourceKey<Level> level, UUID tnt) {
	}

	private record WitherTask(int triggerTick, ResourceKey<Level> level, UUID wither) {
	}

	private record StarCleanupTask(int triggerTick, ResourceKey<Level> level, Vec3 position) {
	}

	private record BeaconTask(ResourceKey<Level> level, BlockPos pos, int startTick, UUID trader) {
	}

	private static final List<WeatherTask> PENDING_WEATHER = new ArrayList<>();
	private static final List<BusterTask> PENDING_BUSTER = new ArrayList<>();
	private static final List<WitherTask> PENDING_WITHERS = new ArrayList<>();
	private static final List<StarCleanupTask> PENDING_STAR_CLEANUP = new ArrayList<>();
	private static final Map<UUID, BeaconTask> BEACONS = new HashMap<>();
	private static final Map<UUID, Integer> LAST_WATER_BUCKET_USE = new HashMap<>();
	private static final Map<UUID, Integer> LAST_CAKE_SLICES = new HashMap<>();
	private static final Set<ArmorStand> WARDING_STONES =
			Collections.newSetFromMap(new IdentityHashMap<>());
	private static final Set<Villager> VILLAGERS =
			Collections.newSetFromMap(new IdentityHashMap<>());

	private MechanicMechanics() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STOPPING.register(MechanicMechanics::cleanupBeacons);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PENDING_WEATHER.clear();
			PENDING_BUSTER.clear();
			PENDING_WITHERS.clear();
			PENDING_STAR_CLEANUP.clear();
			BEACONS.clear();
			LAST_WATER_BUCKET_USE.clear();
			LAST_CAKE_SLICES.clear();
			WARDING_STONES.clear();
			VILLAGERS.clear();
		});
		UseBlockCallback.EVENT.register(MechanicMechanics::useMechanicItem);
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
				checkSummonedWither(player, tick);
				checkNetherWater(player);
				checkCake(player);
				stackWaterBottles(player);
			}
			processWeatherTasks(server, tick);
			processWitherTasks(server, tick);
			processStarCleanup(server, tick);
			tickBeacons(server, tick);
			tickWardingStones();
		});
	}

	private static InteractionResult useMechanicItem(
			net.minecraft.world.entity.player.Player player, Level level,
			net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit
	) {
		ItemStack stack = player.getItemInHand(hand);
		boolean amnestic = isMatchaItem(stack, "amnestic");
		boolean beacon = isMatchaItem(stack, "beacon_kindling");
		if (!amnestic && !beacon) {
			return InteractionResult.PASS;
		}
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		BlockPos clicked = hit.getBlockPos();
		BlockPos target = level.getBlockState(clicked).getCollisionShape(level, clicked).isEmpty()
				? clicked : clicked.relative(hit.getDirection());
		if (amnestic) {
			useAmnestic(serverPlayer, (ServerLevel) level, target);
		} else {
			placeBeacon(serverPlayer, (ServerLevel) level, target);
		}
		if (!serverPlayer.isCreative()) {
			stack.shrink(1);
		}
		return InteractionResult.SUCCESS_SERVER;
	}

	private static boolean isMatchaItem(ItemStack stack, String path) {
		return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())
				.equals(Identifier.fromNamespaceAndPath("matcha-flavoured", path));
	}

	private static void useAmnestic(ServerPlayer player, ServerLevel level, BlockPos pos) {
		VILLAGERS.stream()
				.filter(villager -> villager.level() == level && !villager.isRemoved())
				.min(Comparator.comparingDouble(villager -> villager.distanceToSqr(Vec3.atCenterOf(pos))))
				.ifPresent(villager -> {
					villager.setVillagerData(villager.getVillagerData()
							.withProfession(level.registryAccess(), VillagerProfession.NONE).withLevel(1));
					villager.setVillagerXp(0);
					((VillagerAccessor) villager).matcha$setLastRestockGameTime(0);
				});
	}

	private static void placeBeacon(ServerPlayer player, ServerLevel level, BlockPos pos) {
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
		BEACONS.put(player.getUUID(), new BeaconTask(level.dimension(), pos.immutable(),
				level.getServer().getTickCount(), null));
		level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
				"A Wandering Trader has spotted your beacon, they will arrive in 10 minutes")
				.withStyle(ChatFormatting.GRAY), false);
	}

	private static void checkEstus(ServerPlayer player) {
		if (!advancementDone(player, ESTUS)) {
			return;
		}
		if (!player.isCreative()) {
			boolean easy = WorldMechanics.cachedDifficulty(player.level().getServer()).getId() <= 1;
			for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
				ItemStack stack = player.getInventory().getItem(slot);
				if (!stack.is(Items.BLAZE_POWDER)) {
					continue;
				}
				stack.shrink(1);
				player.addEffect(new MobEffectInstance(
						MobEffects.REGENERATION, easy ? 80 : 40, 4, true, true));
				player.addEffect(new MobEffectInstance(
						MobEffects.RESISTANCE, easy ? 200 : 100, 0, true, true));
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
		WorldMechanics.raiseDifficultyAfterDragon(server);
		scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly("gamerule"), objective).set(1);
	}

	private static void checkSummonedWither(ServerPlayer player, int tick) {
		if (!advancementDone(player, SUMMONED_WITHER)) {
			return;
		}
		if (player.level().dimension() == Level.END) {
			ServerLevel level = player.level();
			level.getEntitiesOfClass(WitherBoss.class, player.getBoundingBox().inflate(64.0), Entity::isAlive)
					.stream().min(Comparator.comparingDouble(wither -> wither.distanceToSqr(player)))
					.ifPresent(wither -> PENDING_WITHERS.add(
							new WitherTask(tick + 23, level.dimension(), wither.getUUID())));
		}
		revoke(player, SUMMONED_WITHER);
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

	private static void checkNetherWater(ServerPlayer player) {
		int current = player.getStats().getValue(Stats.ITEM_USED.get(Items.WATER_BUCKET));
		Integer previous = LAST_WATER_BUCKET_USE.put(player.getUUID(), current);
		if (previous == null || current <= previous || player.level().dimension() != Level.NETHER
				|| player.getY() < 0 || player.getY() > 127) {
			return;
		}
		ServerLevel level = player.level();
		BlockPos center = player.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-20, -20, -20), center.offset(20, 20, 20))) {
			if (level.getBlockState(pos).is(Blocks.WATER)) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
			}
		}
	}

	private static void checkCake(ServerPlayer player) {
		int current = player.getStats().getValue(Stats.CUSTOM, Stats.EAT_CAKE_SLICE);
		Integer previous = LAST_CAKE_SLICES.put(player.getUUID(), current);
		if (previous == null || current <= previous) {
			return;
		}
		player.addEffect(new MobEffectInstance(MobEffects.INSTANT_HEALTH, 20, 0, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.HEALTH_BOOST, 3600, 1, false, false));
	}

	private static void tickBeacons(MinecraftServer server, int tick) {
		for (var entry : new HashMap<>(BEACONS).entrySet()) {
			UUID owner = entry.getKey();
			BeaconTask task = entry.getValue();
			ServerLevel level = server.getLevel(task.level());
			if (level == null || !level.hasChunkAt(task.pos())) {
				BEACONS.put(owner, new BeaconTask(
						task.level(), task.pos(), task.startTick() + 1, task.trader()));
				continue;
			}
			var state = level.getBlockState(task.pos());
			if (!state.is(Blocks.CAMPFIRE) || !state.getValue(CampfireBlock.LIT)) {
				endBeacon(server, owner, task, true, false);
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
				endBeacon(server, owner, task, false, true);
			}
		}
	}

	private static void endBeacon(
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
	}

	private static void cleanupBeacons(MinecraftServer server) {
		for (var entry : new HashMap<>(BEACONS).entrySet()) {
			endBeacon(server, entry.getKey(), entry.getValue(), false, false);
		}
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
							MobEffects.REGENERATION, 60, 0, false, false));
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
						new ItemStack(Items.BLAZE_POWDER)));
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

			// Aura: slow and damage the dedicated 1.10 target set (including pillagers).
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, stoneX, stoneY + 0.5, stoneZ,
					1, 0.5, 0.5, 0.5, 0);
			for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, stone.getBoundingBox().inflate(26.0),
					u -> u.getType().builtInRegistryHolder().is(WARDING_STONE_TARGETS)
							&& u.distanceToSqr(stone) <= 26.0 * 26.0)) {
				target.addEffect(new MobEffectInstance(
						MobEffects.SLOWNESS, 40, 2, false, false));
			}

			if (level.getServer().getTickCount() % 10 == 0) {
				LivingEntity generalTarget = nearestWardingStoneTarget(level, stone, 24.0, target -> true);
				if (generalTarget != null && nearestWardingStoneTarget(level, generalTarget, 20.0,
						target -> target.getType() == EntityTypes.WITHER) == null) {
					level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
							generalTarget.getX(), generalTarget.getY() + 2.0, generalTarget.getZ(),
							1, 0.25, 0.25, 0.25, 0.025);
					LivingEntity damageTarget = nearestWardingStoneTarget(level, generalTarget, 14.0, target -> true);
					if (damageTarget != null) {
						damageTarget.hurt(level.damageSources().fellOutOfWorld(), 7.0F);
					}
				}

				LivingEntity witherTarget = nearestWardingStoneTarget(level, stone, 24.0,
						target -> target.getType() == EntityTypes.WITHER);
				if (witherTarget != null) {
					level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
							witherTarget.getX(), witherTarget.getY() + 2.5, witherTarget.getZ(),
							2, 1.0, 1.0, 1.0, 0.5);
					LivingEntity damageTarget = nearestWardingStoneTarget(level, witherTarget, 14.0, target -> true);
					if (damageTarget != null) {
						damageTarget.hurt(level.damageSources().fellOutOfWorld(), 2.0F);
					}
				}
			}
		}
	}

	private static LivingEntity nearestWardingStoneTarget(
			ServerLevel level, LivingEntity center, double radius, Predicate<LivingEntity> predicate
	) {
		double radiusSquared = radius * radius;
		return level.getEntitiesOfClass(LivingEntity.class, center.getBoundingBox().inflate(radius), target ->
					target.getType().builtInRegistryHolder().is(WARDING_STONE_TARGETS)
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
