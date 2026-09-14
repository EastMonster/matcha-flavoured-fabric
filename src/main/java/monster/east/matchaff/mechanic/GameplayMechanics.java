package monster.east.matchaff.mechanic;

import monster.east.matchaff.mixin.VillagerAccessor;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
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
import net.minecraft.world.phys.Vec3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Player-triggered and inventory mechanics; delayed world tasks live in
 * {@link TimedMechanics}.
 */
public final class GameplayMechanics {
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

	private static final Map<UUID, Integer> LAST_WATER_BUCKET_USE = new HashMap<>();
	private static final Map<UUID, Integer> LAST_CAKE_SLICES = new HashMap<>();
	private static final Set<Villager> VILLAGERS =
			Collections.newSetFromMap(new IdentityHashMap<>());

	private GameplayMechanics() {
	}

	public static void init() {
		BeaconKindlingMechanics.init();
		WardingStoneMechanics.init();
		TimedMechanics.init();
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LAST_WATER_BUCKET_USE.clear();
			LAST_CAKE_SLICES.clear();
			VILLAGERS.clear();
		});
		UseBlockCallback.EVENT.register(GameplayMechanics::useMechanicItem);
		ServerEntityEvents.ENTITY_LOAD.register(GameplayMechanics::trackVillager);
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			VILLAGERS.remove(entity);
		});
		ServerTickEvents.START_SERVER_TICK.register(server -> {
			TimedMechanics.startTick(server, server.getTickCount());
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!server.tickRateManager().runsNormally()) {
				return;
			}
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
			TimedMechanics.tick(server, tick);
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
		} else if (!BeaconKindlingMechanics.place(serverPlayer, (ServerLevel) level, hand, hit, stack)) {
			return InteractionResult.FAIL;
		}
		if (!serverPlayer.isCreative()) {
			stack.shrink(1);
		}
		return InteractionResult.SUCCESS_SERVER;
	}

	private static boolean isMatchaItem(ItemStack stack, String path) {
		return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())
				.equals(Identifier.fromNamespaceAndPath("matcha", path));
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
		if (!WorldMechanics.advancementDone(player, ESTUS)) {
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
		WorldMechanics.revoke(player, ESTUS);
	}

	private static void checkStatueWeather(ServerPlayer player) {
		if (WorldMechanics.advancementDone(player, CHEERFUL_STATUE)) {
			TimedMechanics.scheduleWeather(player, false, CHEERFUL_STATUE);
		}
		if (WorldMechanics.advancementDone(player, MOURNFUL_STATUE)) {
			TimedMechanics.scheduleWeather(player, true, MOURNFUL_STATUE);
		}
	}

	private static void checkBedrockBuster(ServerPlayer player) {
		if (!WorldMechanics.advancementDone(player, BEDROCK_BUSTER)) {
			return;
		}
		var level = player.level();
		level.getEntitiesOfClass(PrimedTnt.class, player.getBoundingBox().inflate(16.0), PrimedTnt::hasGlowingTag)
				.stream()
				.min(Comparator.comparingDouble(tnt -> tnt.distanceToSqr(player)))
				.ifPresentOrElse(
						tnt -> TimedMechanics.scheduleBuster(player, tnt),
						() -> LOGGER.warn("Bedrock buster triggered without a nearby glowing TNT for {}", player.getName().getString())
				);
		WorldMechanics.revoke(player, BEDROCK_BUSTER);
	}

	private static void checkApplication(ServerPlayer player) {
		if (!WorldMechanics.advancementDone(player, APPLICATION)) {
			return;
		}
		var level = player.level();
		var villager = VILLAGERS.stream()
				.filter(v -> v.level() == level)
				.min(Comparator.comparingDouble(v -> v.distanceToSqr(player)));
		villager.ifPresent(v -> level.sendParticles(ParticleTypes.POOF,
				v.getX(), v.getY() + 0.5, v.getZ(), 40, 0.25, 1.0, 0.25, 0.05));
		WorldMechanics.revoke(player, APPLICATION);
	}

	private static void checkHappyGhast(ServerPlayer player) {
		if (!WorldMechanics.advancementDone(player, HAPPY_GHAST_HORN)) {
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
		WorldMechanics.revoke(player, HAPPY_GHAST_HORN);
	}

	private static void checkDragonReward(MinecraftServer server, ServerPlayer player) {
		if (!WorldMechanics.advancementDone(player, KILL_DRAGON)) {
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
				Component.translatable("message.matcha.evil_banished").withStyle(ChatFormatting.GRAY), false);
		scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly("gamerule"), objective).set(1);
	}

	private static void checkSummonedWither(ServerPlayer player, int tick) {
		if (!WorldMechanics.advancementDone(player, SUMMONED_WITHER)) {
			return;
		}
		TimedMechanics.scheduleWither(player, tick);
		WorldMechanics.revoke(player, SUMMONED_WITHER);
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

	private static void trackVillager(Entity entity, ServerLevel level) {
		if (entity instanceof Villager villager) {
			VILLAGERS.add(villager);
		}
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("matcha", path);
	}
}
