package monster.east.matchaff.mechanic;

import monster.east.matchaff.mixin.VillagerAccessor;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
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

/**
 * The remaining datapack "mechanic" behaviours: estus, clay-statue weather,
 * bedrock buster, asylum-seeker application, happy ghast horn, dragon reward
 * and stackable water bottles.
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
	private static final AttachmentType<Integer> WATER_BOTTLE_INVENTORY_VERSION = AttachmentRegistry.create(
			id("water_bottle_inventory_version")
	);

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
	private static final Map<UUID, Integer> LAST_WATER_BUCKET_USE = new HashMap<>();
	private static final Map<UUID, Integer> LAST_CAKE_SLICES = new HashMap<>();
	private static final Set<Villager> VILLAGERS =
			Collections.newSetFromMap(new IdentityHashMap<>());

	private MechanicMechanics() {
	}

	public static void init() {
		BeaconKindlingMechanics.init();
		WardingStoneMechanics.init();
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PENDING_WEATHER.clear();
			PENDING_BUSTER.clear();
			PENDING_WITHERS.clear();
			PENDING_STAR_CLEANUP.clear();
			LAST_WATER_BUCKET_USE.clear();
			LAST_CAKE_SLICES.clear();
			VILLAGERS.clear();
		});
		UseBlockCallback.EVENT.register(MechanicMechanics::useMechanicItem);
		ServerEntityEvents.ENTITY_LOAD.register(MechanicMechanics::trackVillager);
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
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
			BeaconKindlingMechanics.tick(server, tick);
			WardingStoneMechanics.tick();
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
			BeaconKindlingMechanics.place(serverPlayer, (ServerLevel) level, target);
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

	private static void checkEstus(ServerPlayer player) {
		if (!advancementDone(player, ESTUS)) {
			return;
		}
		if (!player.isCreative()) {
			boolean easy = WorldMechanics.cachedDifficulty(player.level().getServer()).getId() <= 1;
			boolean consumed;
			do {
				consumed = false;
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
					var level = player.level();
					level.sendParticles(new DustParticleOptions(0xFFAA17, 1.0F),
							player.getX(), player.getY() + 1.5, player.getZ(), 8, 0.25, 0.25, 0.25, 0.1);
					consumed = true;
					break;
				}
			} while (consumed);
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
		var level = player.level();
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
		var level = player.level();
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
		var level = player.level();
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

	private static void stackWaterBottles(ServerPlayer player) {
		var inventory = player.getInventory();
		int inventoryVersion = inventory.getTimesChanged();
		if (player.getAttachedOrElse(WATER_BOTTLE_INVENTORY_VERSION, -1) == inventoryVersion) {
			return;
		}
		player.setAttached(WATER_BOTTLE_INVENTORY_VERSION, inventoryVersion);
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.is(Items.POTION)) {
				continue;
			}
			PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
			if (contents.is(Potions.WATER) && stack.getOrDefault(DataComponents.MAX_STACK_SIZE, 1) != 1) {
				// Keep the original component identity; ItemInstanceMixin supplies the
				// water bottle's effective max stack size.
				stack.set(DataComponents.MAX_STACK_SIZE, 1);
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

	private static void trackVillager(Entity entity, ServerLevel level) {
		if (entity instanceof Villager villager) {
			VILLAGERS.add(villager);
		}
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
