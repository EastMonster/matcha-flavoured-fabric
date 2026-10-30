package monster.east.matchaff.mechanic;

import monster.east.matchaff.mixin.VillagerAccessor;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.scores.ScoreHolder;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;

/**
 * Player-triggered and inventory mechanics; delayed world tasks live in
 * {@link TimedMechanics}.
 */
public final class GameplayMechanics {
	private static final Identifier ESTUS = id("mechanics/estus_obtained");
	private static final Identifier MOURNFUL_STATUE = id("mechanics/mournful_clay_statue");
	private static final Identifier HAPPY_GHAST_HORN = id("mechanics/happy_ghast_horn");
	private static final Identifier KILL_DRAGON = id("end/kill_dragon");
	private static final Identifier SUMMONED_WITHER = id("mechanics/summoned_wither");
	private static final Identifier ENTER_FORTRESS = id("mechanics/enter_fortress");
	private static final Identifier PLACE_WATER_IN_NETHER = id("mechanics/place_water_in_nether");
	private static final AttachmentType<Integer> WATER_BOTTLE_INVENTORY_VERSION = AttachmentRegistry.create(
			id("water_bottle_inventory_version")
	);

	private static final Set<Villager> VILLAGERS =
			Collections.newSetFromMap(new IdentityHashMap<>());
	private static final Set<UUID> AMNESTIC_VILLAGERS = new HashSet<>();
	// Upstream favorite_food/food_trades/food_0..41, in random selection order.
	private static final String[] FAVORITE_FOODS = {
			"apple_empanada", "bokguk", "brachio_chicken_nugget", "brownie",
			"bruschetta", "bubbling_mead", "bunuelo", "carrot_cupcake",
			"cheese_pizza", "chocolate_chip_cookie", "crimson_stroganoff", "fish_and_chips",
			"french_toast", "gimmari", "glow_berry_crumble", "gnocchi",
			"honied_french_toast", "ice_cream", "kontomire_stew", "latke",
			"lumpia", "mead", "meat_pizza", "melon_sorbet",
			"mushroom_pizza", "oatmeal", "pad_thai", "ptero_chicken_nugget",
			"puerquito", "pumpkin_empanada", "red_mushroom_stroganoff", "stego_chicken_nugget",
			"stroganoff", "sweet_berry_danish", "tricero_chicken_nugget", "warped_pizza",
			"green_curry", "honey_ginger_tea", "japanese_curry", "paneer_makhani",
			"pumpkin_curry", "ramen"
	};

	private GameplayMechanics() {
	}

	public static void init() {
		BeaconKindlingMechanics.init();
		WardingStoneMechanics.init();
		TimedMechanics.init();
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			VILLAGERS.clear();
			AMNESTIC_VILLAGERS.clear();
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (player.isSpectator() || !(entity instanceof Villager villager)
					|| !isMatchaItem(stack, "amnestic")) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer && level instanceof ServerLevel serverLevel) {
				useAmnestic(villager, serverLevel);
				if (!player.isCreative()) {
					stack.shrink(1);
				}
			}
			return InteractionResult.SUCCESS;
		});
		ServerEntityEvents.ENTITY_LOAD.register(GameplayMechanics::trackVillager);
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			VILLAGERS.remove(entity);
		});
		ServerTickEvents.START_SERVER_TICK.register(server -> {
			TimedMechanics.startTick(server);
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!server.tickRateManager().runsNormally()) {
				return;
			}
			int tick = server.getTickCount();
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				MechanicAdvancementTriggers.run(player, MechanicAdvancementTriggers.Stage.GAMEPLAY);
				stackWaterBottles(player);
			}
			refreshAmnesticVillagers();
			// First check after 3 seconds, then every 30 seconds, as in upstream.
			if (tick >= 60 && (tick - 60) % 600 == 0) {
				assignFavoriteFoods();
			}
			TimedMechanics.tick(server, tick);
			BeaconKindlingMechanics.tick(server, tick);
			WardingStoneMechanics.tick();
		});
	}

	private static boolean isMatchaItem(ItemStack stack, String path) {
		return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())
				.equals(Identifier.fromNamespaceAndPath("matcha", path));
	}

	private static void useAmnestic(Villager villager, ServerLevel level) {
		AMNESTIC_VILLAGERS.add(villager.getUUID());
		villager.removeTag("foodChecked");
		villager.setVillagerXp(0);
		villager.releasePoi(MemoryModuleType.JOB_SITE);
		villager.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
		villager.setVillagerData(villager.getVillagerData()
				.withProfession(level.registryAccess(), VillagerProfession.NONE).withLevel(1));
		villager.refreshBrain(level);
		((VillagerAccessor) villager).matcha$setLastRestockGameTime(0);
		level.playSound(null, villager.getX(), villager.getY(), villager.getZ(),
				net.minecraft.sounds.SoundEvents.VILLAGER_AMBIENT, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 0.65F);
	}

	/** Mirrors recipe take * and advancement revoke from minecraft:recipes/root. */
	public static void onAmnesticConsumed(ServerPlayer player, ItemStack stack) {
		if (!isMatchaItem(stack, "amnestic")) {
			return;
		}
		var server = player.level().getServer();
		player.resetRecipes(server.getRecipeManager().getRecipes());
		var manager = server.getAdvancements();
		Identifier root = Identifier.withDefaultNamespace("recipes/root");
		for (var advancement : manager.getAllAdvancements()) {
			Identifier ancestor = advancement.id();
			while (ancestor != null && !ancestor.equals(root)) {
				var holder = manager.get(ancestor);
				ancestor = holder == null ? null : holder.value().parent().orElse(null);
			}
			if (root.equals(ancestor)) {
				WorldMechanics.revoke(player, advancement.id());
			}
		}
	}

	private static void assignFavoriteFoods() {
		for (Villager villager : VILLAGERS) {
			// Defer reordering until the trade menu is closed; the next scan will retry.
			if (villager.isRemoved() || villager.isTrading() || villager.entityTags().contains("foodChecked")
					|| villager.getVillagerData().profession().is(VillagerProfession.NONE)
					|| villager.getVillagerData().profession().is(VillagerProfession.FISHERMAN)
					|| villager.getVillagerXp() == 0) {
				continue;
			}
			String food = FAVORITE_FOODS[villager.getRandom().nextInt(FAVORITE_FOODS.length)];
			villager.getOffers().add(0, new MerchantOffer(
					new ItemCost(food.equals("chocolate_chip_cookie") ? Items.COOKIE : BuiltInRegistries.ITEM.getValue(id(food)),
							favoriteFoodCount(food)),
					new ItemStack(Items.EMERALD), 1, 1, 0.0F));
			villager.addTag("foodChecked");
		}
	}

	public static boolean isFavoriteFoodOffer(MerchantOffer offer) {
		ItemStack food = offer.getBaseCostA();
		ItemStack result = offer.getResult();
		if (!offer.getCostB().isEmpty()
				|| !result.is(Items.EMERALD) || result.getCount() != 1 || offer.getMaxUses() != 1) {
			return false;
		}
		Identifier foodId = BuiltInRegistries.ITEM.getKey(food.getItem());
		for (String favorite : FAVORITE_FOODS) {
			if (foodId.equals(favorite.equals("chocolate_chip_cookie")
					? BuiltInRegistries.ITEM.getKey(Items.COOKIE) : id(favorite))) {
				// Existing villagers retain their old one-item offers.
				return food.getCount() == 1 || food.getCount() == favoriteFoodCount(favorite);
			}
		}
		return false;
	}

	static int favoriteFoodCount(String food) {
		return switch (food) {
			case "brachio_chicken_nugget", "ptero_chicken_nugget", "stego_chicken_nugget",
					"tricero_chicken_nugget" -> 8;
			case "puerquito" -> 4;
			case "honey_ginger_tea" -> 3;
			default -> 1;
		};
	}

	private static void refreshAmnesticVillagers() {
		if (AMNESTIC_VILLAGERS.isEmpty()) {
			return;
		}
		// Vanilla only appends the next tier when a trade grants XP; rebuild all tiers here.
		for (Villager villager : VILLAGERS) {
			if (villager.isRemoved()
					|| !AMNESTIC_VILLAGERS.contains(villager.getUUID())
					|| villager.getVillagerData().profession().is(VillagerProfession.NONE)
					|| !(villager.level() instanceof ServerLevel level)) {
				continue;
			}
			int targetLevel = 1;
			while (VillagerData.canLevelUp(targetLevel)
					&& villager.getVillagerXp() >= VillagerData.getMaxXpPerLevel(targetLevel)) {
				targetLevel++;
			}
			villager.setVillagerData(villager.getVillagerData().withLevel(1));
			villager.getOffers();
			VillagerAccessor accessor = (VillagerAccessor) villager;
			for (int levelNumber = 2; levelNumber <= targetLevel; levelNumber++) {
				villager.setVillagerData(villager.getVillagerData().withLevel(levelNumber));
				accessor.matcha$updateTrades(level);
			}
			accessor.matcha$setLastRestockGameTime(0);
			AMNESTIC_VILLAGERS.remove(villager.getUUID());
		}
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
					if (!stack.is(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("matcha", "raw_estus")))) {
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

	static void handleAdvancement(ServerPlayer player, Identifier advancement) {
		switch (advancement.getPath()) {
			case "mechanics/estus_obtained" -> checkEstus(player);
			case "mechanics/cheerful_clay_statue", "mechanics/mournful_clay_statue" -> {
				if (WorldMechanics.advancementDone(player, advancement)) {
					TimedMechanics.scheduleWeather(player, advancement.equals(MOURNFUL_STATUE), advancement);
				}
			}
			case "mechanics/happy_ghast_horn" -> checkHappyGhast(player);
			case "mechanics/summoned_wither" -> checkSummonedWither(player, player.level().getServer().getTickCount());
			case "mechanics/enter_fortress" -> checkWitherSkeletons(player);
			case "mechanics/place_water_in_nether" -> checkNetherWater(player);
			default -> throw new IllegalArgumentException("Unexpected mechanic advancement: " + advancement);
		}
	}

	public static void onAsylumSeekerSpawn(ServerLevel level, ItemStack stack, Entity entity) {
		if (!(entity instanceof Villager villager) || !isMatchaItem(stack, "asylum_seeker")
				|| level.getEntity(villager.getUUID()) != villager) {
			return;
		}
		level.sendParticles(ParticleTypes.POOF,
				villager.getX(), villager.getY() + 0.5, villager.getZ(), 40, 0.25, 1.0, 0.25, 0.05);
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

	/** Runs at the same completion transition as vanilla advancement rewards. */
	public static void rewardDragonAdvancement(ServerPlayer player, Identifier advancement) {
		if (!KILL_DRAGON.equals(advancement)) return;
		MinecraftServer server = player.level().getServer();
		var end = server.getLevel(Level.END);
		if (end != null) {
			ItemEntity reward = new ItemEntity(end, 0, 100, 0, new ItemStack(Items.NETHER_STAR, 1));
			reward.setUnlimitedLifetime();
			end.addFreshEntity(reward);
		}
		server.getPlayerList().broadcastSystemMessage(
				Component.translatable("message.matcha.evil_banished").withStyle(ChatFormatting.GRAY), false);
		var scoreboard = server.getScoreboard();
		var safeSurface = scoreboard.getObjective("gamerule_safe_surface");
		if (safeSurface != null) {
			scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly("gamerule"), safeSurface).set(1);
		}
	}

	private static void checkSummonedWither(ServerPlayer player, int tick) {
		if (!WorldMechanics.advancementDone(player, SUMMONED_WITHER)) {
			return;
		}
		TimedMechanics.scheduleWither(player, tick);
		WorldMechanics.revoke(player, SUMMONED_WITHER);
	}

	private static void checkWitherSkeletons(ServerPlayer player) {
		if (!WorldMechanics.advancementDone(player, ENTER_FORTRESS)) {
			return;
		}
		ServerLevel level = player.level();
		for (WitherSkeleton skeleton : level.getEntitiesOfClass(WitherSkeleton.class,
				player.getBoundingBox().inflate(5.0), skeleton ->
						skeleton.isAlive() && skeleton.distanceToSqr(player) <= 25.0)) {
			BlockPos block = BlockPos.containing(skeleton.getEyePosition().add(skeleton.getLookAngle()));
			level.destroyBlock(block, true);
			level.destroyBlock(block.below(), true);
		}
		WorldMechanics.revoke(player, ENTER_FORTRESS);
	}

	private static void checkNetherWater(ServerPlayer player) {
		if (!WorldMechanics.advancementDone(player, PLACE_WATER_IN_NETHER)) {
			return;
		}
		WorldMechanics.revoke(player, PLACE_WATER_IN_NETHER);
		ServerLevel level = player.level();
		if (level.dimension() != Level.NETHER) {
			return;
		}
		BlockPos center = player.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-20, -20, -20), center.offset(20, 20, 20))) {
			if (level.getBlockState(pos).is(Blocks.WATER)) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
			}
		}
		player.connection.send(new ClientboundSoundPacket(Holder.direct(SoundEvents.FIRE_EXTINGUISH),
				SoundSource.BLOCKS, player.getX(), player.getY(), player.getZ(), 0.5F, 2.0F,
				player.getRandom().nextLong()));
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
