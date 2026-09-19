package monster.east.matchaff.mechanic;

import net.minecraft.ChatFormatting;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import monster.east.matchaff.network.SleepFastForwardPayload;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Matcha player mechanics, replacing the datapack's scoreboard/function tick
 * loop:
 *   * hunger bar is meaningless: food >= 10 drains (hunger 256), food <= 6 is
 *     force-saturated so sprinting always works; natural regen stays off
 *   * crystal hearts: actively used to gain hearts below the 30-heart cap,
 *     and provide vanilla death protection while held.
 *     Death costs two points and can push a player below 10 hearts; the
 *     per-player minimum starts at 20 and drops by 2 each time that player
 *     reaches an "age" milestone. Easy tracks the loss but keeps the
 *     effective 20-point floor.
	 *   * sleeping skips 12 hours by day and wakes at morning by night
 *   * icy water in frozen biomes applies blindness, slowness and freeze damage
 *     (freezing_protection III on the chest blocks it)
 *   * xp is wiped every tick; anvils are free (AnvilMenuMixin)
 */
public final class PlayerMechanics {
	private static final int MAX_HEARTS = 60;
	private static final int DEFAULT_MINIMUM_HEARTS = 20;
	private static final int ABSOLUTE_MINIMUM_HEARTS = 6;
	private static final int CRYSTAL_HEART_COOLDOWN_TICKS = 30;
	private static final TagKey<Biome> FROZEN_BIOME = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("minecraft", "is_frozen"));
	private static final Identifier FREEZING_PROTECTION = Identifier.fromNamespaceAndPath("matcha", "freezing_protection");
	private static final String MINIMUM_HEARTS_OBJECTIVE = "minimum_hearts";
	private static final String AGE_PROGRESS_OBJECTIVE = "matcha_heart_ages";
	private static final String HEART_MIGRATION_OBJECTIVE = "matcha_heart_ver";
	private static final int HEART_MIGRATION_VERSION = 1;
	private static final String[] AGE_ADVANCEMENTS = {
			"matcha:tutorial/obtain_copper", "matcha:tutorial/obtain_iron_ingot",
			"matcha:tutorial/obtain_diamond", "matcha:tutorial/enter_nether",
			"matcha:tutorial/obtain_electrum", "matcha:tutorial/obtain_adamant",
			"matcha:tutorial/find_stronghold"
	};
	private static int sleepRate;
	private static final Map<UUID, Integer> SLEEP_REMAINING = new HashMap<>();

	private static final String HEARTS_OBJECTIVE = "Hearts";

	private PlayerMechanics() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			sleepRate = 0;
			SLEEP_REMAINING.clear();
			var scoreboard = server.getScoreboard();
			if (scoreboard.getObjective(HEARTS_OBJECTIVE) == null) {
				scoreboard.addObjective(HEARTS_OBJECTIVE, ObjectiveCriteria.DUMMY,
						Component.literal("Hearts"), ObjectiveCriteria.RenderType.INTEGER, true,
						StyledFormat.NO_STYLE);
			}
			if (scoreboard.getObjective(MINIMUM_HEARTS_OBJECTIVE) == null) {
				scoreboard.addObjective(MINIMUM_HEARTS_OBJECTIVE, ObjectiveCriteria.DUMMY,
						Component.literal(MINIMUM_HEARTS_OBJECTIVE), ObjectiveCriteria.RenderType.INTEGER, true,
						StyledFormat.NO_STYLE);
			}
			if (scoreboard.getObjective(AGE_PROGRESS_OBJECTIVE) == null) {
				scoreboard.addObjective(AGE_PROGRESS_OBJECTIVE, ObjectiveCriteria.DUMMY,
						Component.literal(AGE_PROGRESS_OBJECTIVE), ObjectiveCriteria.RenderType.INTEGER, true,
						StyledFormat.NO_STYLE);
			}
			if (scoreboard.getObjective(HEART_MIGRATION_OBJECTIVE) == null) {
				scoreboard.addObjective(HEART_MIGRATION_OBJECTIVE, ObjectiveCriteria.DUMMY,
						Component.literal(HEART_MIGRATION_OBJECTIVE), ObjectiveCriteria.RenderType.INTEGER, true,
						StyledFormat.NO_STYLE);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SLEEP_REMAINING.clear());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!server.tickRateManager().runsNormally()) {
				setSleepRate(server, 0);
				return;
			}
			manageSleep(server);
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tick(player);
			}
		});
		ServerPlayerEvents.JOIN.register(player -> {
			initializePlayerState(player);
			sendSleepRate(player);
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				loseHeart(player);
			}
		});
	}

	private static void tick(ServerPlayer player) {
		initializePlayerState(player);
		manageSleepDuration(player);
		manageHunger(player);
		manageHearts(player);
		manageExperience(player);
		manageFreezingWater(player);
		manageAgeMilestones(player);
	}

	/** Each player's progress lowers only that player's heart floor once per age. */
	private static void manageAgeMilestones(ServerPlayer player) {
		if (player.tickCount % 20 != 0) {
			return;
		}
		var server = player.level().getServer();
		var scoreboard = server.getScoreboard();
		var minimumObjective = scoreboard.getObjective(MINIMUM_HEARTS_OBJECTIVE);
		var ageObjective = scoreboard.getObjective(AGE_PROGRESS_OBJECTIVE);
		if (minimumObjective == null || ageObjective == null) {
			return;
		}
		Difficulty difficulty = WorldMechanics.cachedDifficulty(server);
		var ageScore = scoreboard.getOrCreatePlayerScore(player, ageObjective);
		int completedAges = ageScore.get();
		for (int i = 0; i < AGE_ADVANCEMENTS.length; i++) {
			int ageMask = 1 << i;
			if ((completedAges & ageMask) != 0
					|| !WorldMechanics.advancementDone(player, Identifier.parse(AGE_ADVANCEMENTS[i]))) {
				continue;
			}
			completedAges |= ageMask;
			ageScore.set(completedAges);
			if (difficulty == Difficulty.PEACEFUL) {
				continue;
			}
			int floor = getStoredMinimumHearts(player);
			if (floor <= ABSOLUTE_MINIMUM_HEARTS) {
				break;
			}
			floor -= 2;
			scoreboard.getOrCreatePlayerScore(player, minimumObjective).set(floor);
			announceMinimum(player, floor, difficulty != Difficulty.EASY);
			if (difficulty == Difficulty.NORMAL && floor == 10) {
				WorldMechanics.raiseDifficulty(server);
			}
			break;
		}
	}

	private static void announceMinimum(ServerPlayer player, int floor, boolean mechanicMessage) {
		int index = (20 - floor) / 2;
		player.sendSystemMessage(
				Component.translatable("message.matcha.heart.minimum.warning." + index)
						.withStyle(style -> style.withColor(ChatFormatting.RED)), false);
		if (!mechanicMessage) {
			return;
		}
		player.sendSystemMessage(
				Component.translatable("message.matcha.heart.minimum.decreased", floor / 2)
						.withStyle(ChatFormatting.GRAY), false);
	}

	private static void manageHunger(Player player) {
		// The hunger bar is meaningless: keep it full so sprinting always works
		// and starvation never happens. Healing comes from eating food directly
		// (see FoodHealMixin) and from food effects.
		var foodData = player.getFoodData();
		if (foodData.getFoodLevel() < 20) {
			foodData.eat(20 - foodData.getFoodLevel(), 1.0F);
		}
	}

	private static void manageHearts(ServerPlayer player) {
		int current = getHearts(player);
		int hearts = Math.max(getMinimumHearts(player), current);
		if (hearts != current) {
			setHearts(player, hearts);
		}
		applyMaxHealth(player, hearts);
	}

	/** Called while a Crystal Heart is actively being consumed. */
	public static void useCrystalHeart(ServerPlayer player, ItemStack stack) {
		if (player.isCreative()) {
			return;
		}
		ItemStack held = player.getItemInHand(player.getUsedItemHand());
		if (stack.isEmpty() || held.isEmpty() || !ItemStack.isSameItem(held, stack)
				|| held.get(DataComponents.DEATH_PROTECTION) == null) {
			return;
		}
		int hearts = getHearts(player);
		if (hearts >= MAX_HEARTS) {
			return;
		}
		if (player.getCooldowns().isOnCooldown(stack)) {
			return;
		}
		ItemStack cooldownStack = stack.copy();
		int countBefore = held.getCount();
		if (!player.hurtServer(player.level(), player.level().damageSources().magic(), 999.0F)) {
			return;
		}
		ItemStack heldAfter = player.getItemInHand(player.getUsedItemHand());
		if (player.isDeadOrDying() || heldAfter.getCount() >= countBefore) {
			return;
		}
		hearts = Math.min(MAX_HEARTS, hearts + 2);
		setHearts(player, hearts);
		applyMaxHealth(player, hearts);
		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 10, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20, 4, false, false));
		player.getCooldowns().addCooldown(cooldownStack, CRYSTAL_HEART_COOLDOWN_TICKS);
	}

	/** Removes one heart container while respecting the current difficulty floor. */
	public static void loseHeart(ServerPlayer player) {
		int hearts = Math.max(getMinimumHearts(player), getHearts(player) - 2);
		setHearts(player, hearts);
		applyMaxHealth(player, hearts);
	}

	private static void manageExperience(ServerPlayer player) {
		if (player.experienceLevel > 0 || player.totalExperience > 0) {
			player.giveExperienceLevels(-player.experienceLevel);
			player.giveExperiencePoints(-player.totalExperience);
		}
	}

	private static void manageFreezingWater(ServerPlayer player) {
		if (player.isCreative()) {
			return;
		}
		var level = player.level();
		var pos = player.blockPosition();
		if (level.getBlockState(pos.above()).is(Blocks.WATER)
				&& level.getBiome(pos).is(FROZEN_BIOME)
				&& !hasFreezingProtection(player)) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 4, true, false));
			player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0, true, false));
			player.hurtServer(level, level.damageSources().freeze(), 1.0f);
		}
	}

	private static boolean hasFreezingProtection(ServerPlayer player) {
		ItemStack chest = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
		if (chest.isEmpty()) {
			return false;
		}
		Registry<Enchantment> enchantments = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		Holder.Reference<Enchantment> holder = enchantments.get(FREEZING_PROTECTION).orElse(null);
		return holder != null
				&& chest.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(holder) >= 3;
	}

	// The gamerule controls how many players must sleep; once that threshold is
	// met, the upstream-normalized sleep rate is always 300 ticks per tick.
	private static void manageSleep(MinecraftServer server) {
		int active = 0;
		int sleeping = 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.level().dimension() != Level.OVERWORLD) {
				continue;
			}
			active++;
			if (player.isSleeping()) {
				sleeping++;
				SLEEP_REMAINING.computeIfAbsent(player.getUUID(), ignored -> calculateSleepDuration(server));
			} else {
				SLEEP_REMAINING.remove(player.getUUID());
			}
		}
		if (active == 0 || sleeping == 0) {
			setSleepRate(server, 0);
			return;
		}
		int percentage = server.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
		int needed = Math.max(1, (int) Math.ceil(active * percentage / 100.0));
		if (sleeping < needed) {
			setSleepRate(server, 0);
			return;
		}
		int rate = 300;
		setSleepRate(server, rate);
		if (server.getGameRules().get(GameRules.ADVANCE_WEATHER)) {
			server.setWeatherParameters(ServerLevel.RAIN_DELAY.sample(server.overworld().getRandom()), 0, false, false);
		}
		var clock = server.registryAccess()
				.lookupOrThrow(Registries.WORLD_CLOCK)
				.getOrThrow(WorldClocks.OVERWORLD);
		server.clockManager().addTicks(clock, rate);
	}

	private static int calculateSleepDuration(MinecraftServer server) {
		long currentTime = Math.floorMod(server.overworld().getOverworldClockTime(), 72_000L);
		if (currentTime >= 36_000L && currentTime <= 69_000L) {
			return (int) (72_000L - currentTime);
		}
		return 36_000;
	}

	private static void manageSleepDuration(ServerPlayer player) {
		UUID uuid = player.getUUID();
		if (!player.isSleeping()) {
			SLEEP_REMAINING.remove(uuid);
			return;
		}
		Integer remaining = SLEEP_REMAINING.get(uuid);
		if (remaining == null || sleepRate <= 0) {
			return;
		}
		remaining -= sleepRate;
		if (remaining <= 0) {
			player.stopSleepInBed(false, true);
			SLEEP_REMAINING.remove(uuid);
		} else {
			SLEEP_REMAINING.put(uuid, remaining);
		}
	}

	private static void setSleepRate(MinecraftServer server, int rate) {
		if (sleepRate == rate) {
			return;
		}
		sleepRate = rate;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			sendSleepRate(player);
		}
	}

	private static void sendSleepRate(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, SleepFastForwardPayload.TYPE)) {
			ServerPlayNetworking.send(player, new SleepFastForwardPayload(sleepRate > 0));
		}
	}

	private static int getMinimumHearts(ServerPlayer player) {
		Difficulty difficulty = WorldMechanics.cachedDifficulty(player.level().getServer());
		if (difficulty == Difficulty.EASY || difficulty == Difficulty.PEACEFUL) {
			return DEFAULT_MINIMUM_HEARTS;
		}
		return getStoredMinimumHearts(player);
	}

	private static int getStoredMinimumHearts(ServerPlayer player) {
		var scoreboard = player.level().getServer().getScoreboard();
		var objective = scoreboard.getObjective(MINIMUM_HEARTS_OBJECTIVE);
		if (objective == null) {
			return DEFAULT_MINIMUM_HEARTS;
		}
		var score = scoreboard.getOrCreatePlayerScore(player, objective);
		int minimum = score.get();
		if (minimum < ABSOLUTE_MINIMUM_HEARTS || minimum > DEFAULT_MINIMUM_HEARTS) {
			minimum = DEFAULT_MINIMUM_HEARTS;
			score.set(minimum);
		}
		return minimum;
	}

	private static void initializePlayerState(ServerPlayer player) {
		var scoreboard = player.level().getServer().getScoreboard();
		var heartsObjective = scoreboard.getObjective(HEARTS_OBJECTIVE);
		if (heartsObjective != null && scoreboard.getPlayerScoreInfo(player, heartsObjective) == null) {
			scoreboard.getOrCreatePlayerScore(player, heartsObjective).set(DEFAULT_MINIMUM_HEARTS);
		}
		var minimumObjective = scoreboard.getObjective(MINIMUM_HEARTS_OBJECTIVE);
		if (minimumObjective != null && scoreboard.getPlayerScoreInfo(player, minimumObjective) == null) {
			scoreboard.getOrCreatePlayerScore(player, minimumObjective).set(DEFAULT_MINIMUM_HEARTS);
		}
		var ageObjective = scoreboard.getObjective(AGE_PROGRESS_OBJECTIVE);
		if (ageObjective != null && scoreboard.getPlayerScoreInfo(player, ageObjective) == null) {
			scoreboard.getOrCreatePlayerScore(player, ageObjective).set(completedAgeMask(player));
		}
		migrateLegacyHeartFloor(player);
	}

	/** Applies completed age milestones once to saves created before per-player floors. */
	private static void migrateLegacyHeartFloor(ServerPlayer player) {
		var scoreboard = player.level().getServer().getScoreboard();
		var minimumObjective = scoreboard.getObjective(MINIMUM_HEARTS_OBJECTIVE);
		var ageObjective = scoreboard.getObjective(AGE_PROGRESS_OBJECTIVE);
		var migrationObjective = scoreboard.getObjective(HEART_MIGRATION_OBJECTIVE);
		if (minimumObjective == null || ageObjective == null || migrationObjective == null) {
			return;
		}
		var migration = scoreboard.getOrCreatePlayerScore(player, migrationObjective);
		if (migration.get() >= HEART_MIGRATION_VERSION) {
			return;
		}
		int completedAges = completedAgeMask(player);
		scoreboard.getOrCreatePlayerScore(player, ageObjective).set(completedAges);
		scoreboard.getOrCreatePlayerScore(player, minimumObjective).set(
				Math.max(ABSOLUTE_MINIMUM_HEARTS, DEFAULT_MINIMUM_HEARTS - Integer.bitCount(completedAges) * 2));
		migration.set(HEART_MIGRATION_VERSION);
	}

	private static int completedAgeMask(ServerPlayer player) {
		int mask = 0;
		for (int i = 0; i < AGE_ADVANCEMENTS.length; i++) {
			if (WorldMechanics.advancementDone(player, Identifier.parse(AGE_ADVANCEMENTS[i]))) {
				mask |= 1 << i;
			}
		}
		return mask;
	}

	private static void applyMaxHealth(Player player, int hearts) {
		AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
		if (attribute != null && (int) attribute.getBaseValue() != hearts) {
			attribute.setBaseValue(hearts);
		}
	}

	/** Hearts live in the per-world scoreboard so a new world starts fresh at 20. */
	private static int getHearts(ServerPlayer player) {
		var scoreboard = player.level().getServer().getScoreboard();
		var objective = scoreboard.getObjective(HEARTS_OBJECTIVE);
		if (objective == null) {
			return 20;
		}
		int hearts = scoreboard.getOrCreatePlayerScore(player, objective).get();
		// A zero score marks a brand-new player who has not died yet.
		return hearts <= 0 ? 20 : Math.clamp(hearts, ABSOLUTE_MINIMUM_HEARTS, MAX_HEARTS);
	}

	private static void setHearts(ServerPlayer player, int hearts) {
		var scoreboard = player.level().getServer().getScoreboard();
		var objective = scoreboard.getObjective(HEARTS_OBJECTIVE);
		if (objective != null) {
			scoreboard.getOrCreatePlayerScore(player, objective).set(
					Math.clamp(hearts, ABSOLUTE_MINIMUM_HEARTS, MAX_HEARTS));
		}
	}

}
