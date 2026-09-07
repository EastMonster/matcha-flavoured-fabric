package monster.east.matchaff.mechanic;

import net.minecraft.ChatFormatting;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;

/**
 * Matcha player mechanics, replacing the datapack's scoreboard/function tick
 * loop:
 *   * hunger bar is meaningless: food >= 10 drains (hunger 256), food <= 6 is
 *     force-saturated so sprinting always works; natural regen stays off
 *   * crystal hearts: auto-consumed from the inventory up to 30 hearts.
 *     Death costs two points and can push a player below 10 hearts; the
 *     world-wide minimum starts at 20 and drops by 2 each time the first
 *     player reaches an "age" milestone. Easy keeps the 20-point floor.
 *   * sleeping fast-forwards 12 hours instead of skipping the night
 *   * icy water in frozen biomes applies blindness, slowness and freeze damage
 *     (freezing_protection III on the chest blocks it)
 *   * xp is wiped every tick; anvils are free (AnvilMenuMixin)
 */
public final class PlayerMechanics {
	private static final int MAX_HEARTS = 60;
	private static final int ABSOLUTE_MINIMUM_HEARTS = 6;
	private static final TagKey<Biome> FROZEN_BIOME = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("minecraft", "is_frozen"));
	private static final Identifier FREEZING_PROTECTION = Identifier.fromNamespaceAndPath("matcha", "freezing_protection");
	private static final Identifier HEART_CONTAINER_OBTAINED = Identifier.fromNamespaceAndPath(
			"main", "mechanics/heart_container_obtained"
	);
	private static final String CURRENT_MINIMUM = "current_minimum_hearts";
	private static final String[] AGE_HOLDERS = {
			"copper_age", "iron_age", "diamond_age", "nether_age",
			"electrum_age", "netherite_age", "end_age"
	};
	private static final String[] AGE_ADVANCEMENTS = {
			"main:tutorial/obtain_copper", "main:tutorial/obtain_iron_ingot",
			"main:tutorial/obtain_diamond", "main:tutorial/enter_nether",
			"main:tutorial/obtain_electrum", "main:tutorial/obtain_adamant",
			"main:tutorial/find_stronghold"
	};
	private static final AttachmentType<Integer> HEART_INVENTORY_VERSION = AttachmentRegistry.create(
			Identifier.fromNamespaceAndPath("matcha-flavoured", "heart_inventory_version")
	);

	private static final String HEARTS_OBJECTIVE = "Hearts";

	private PlayerMechanics() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			var scoreboard = server.getScoreboard();
			if (scoreboard.getObjective(HEARTS_OBJECTIVE) == null) {
				scoreboard.addObjective(HEARTS_OBJECTIVE, ObjectiveCriteria.DUMMY,
						Component.literal("Hearts"), ObjectiveCriteria.RenderType.INTEGER, true,
						StyledFormat.NO_STYLE);
			}
			var objective = scoreboard.getObjective(HEARTS_OBJECTIVE);
			if (objective != null) {
				ensureWorldScore(scoreboard, objective, CURRENT_MINIMUM, 20);
				for (String age : AGE_HOLDERS) {
					ensureWorldScore(scoreboard, objective, age, 0);
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!server.tickRateManager().runsNormally()) {
				return;
			}
			manageSleep(server);
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tick(player);
			}
		});
		ServerPlayerEvents.JOIN.register(player -> ensureWorldScore(
				player.level().getServer().getScoreboard(),
				player.level().getServer().getScoreboard().getObjective(HEARTS_OBJECTIVE),
				player.getScoreboardName(), 20));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				int hearts = getHearts(player);
				int minimum = minimumHearts(player);
				hearts = Math.max(minimum, hearts - 2);
				setHearts(player, hearts);
				applyMaxHealth(player, hearts);
			}
		});
	}

	private static void tick(ServerPlayer player) {
		manageHunger(player);
		manageHearts(player);
		manageExperience(player);
		manageFreezingWater(player);
		manageAgeMilestones(player);
	}

	/** World progress lowers the heart floor once, per age, when not on Easy. */
	private static void manageAgeMilestones(ServerPlayer player) {
		if (player.tickCount % 20 != 0
				|| WorldMechanics.cachedDifficulty(player.level().getServer()) == Difficulty.EASY
				|| WorldMechanics.cachedDifficulty(player.level().getServer()) == Difficulty.PEACEFUL) {
			return;
		}
		var server = player.level().getServer();
		var objective = server.getScoreboard().getObjective(HEARTS_OBJECTIVE);
		if (objective == null) {
			return;
		}
		for (int i = 0; i < AGE_ADVANCEMENTS.length; i++) {
			if (worldScore(server.getScoreboard(), objective, AGE_HOLDERS[i]) != 0) {
				continue;
			}
			if (!WorldMechanics.advancementDone(player, Identifier.parse(AGE_ADVANCEMENTS[i]))) {
				continue;
			}
			int floor = worldScore(server.getScoreboard(), objective, CURRENT_MINIMUM);
			if (floor <= ABSOLUTE_MINIMUM_HEARTS) {
				forceWorldScore(server.getScoreboard(), objective, AGE_HOLDERS[i], 1);
				break;
			}
			floor -= 2;
			forceWorldScore(server.getScoreboard(), objective, CURRENT_MINIMUM, floor);
			forceWorldScore(server.getScoreboard(), objective, AGE_HOLDERS[i], 1);
			announceMinimum(server, floor);
			if (floor == 10 && WorldMechanics.cachedDifficulty(server) != Difficulty.HARD) {
				WorldMechanics.raiseDifficulty(server);
			}
			break;
		}
	}

	private static void announceMinimum(MinecraftServer server, int floor) {
		int index = (20 - floor) / 2;
		server.getPlayerList().broadcastSystemMessage(
				Component.translatable("log.kleispack.god_grows_angry_" + index)
						.withStyle(style -> style.withColor(ChatFormatting.RED)), false);
		server.getPlayerList().broadcastSystemMessage(
				Component.translatable("log.kleispack.minimum_heart_decreased", floor / 2)
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
		int hearts = getHearts(player);
		applyMaxHealth(player, hearts);
		// Creative refills items, so an auto-consume loop would never end there
		// (the datapack's own comment warned about this for estus).
		if (player.isCreative() || hearts >= MAX_HEARTS) {
			return;
		}
		var inventory = player.getInventory();
		int inventoryVersion = inventory.getTimesChanged();
		AdvancementHolder obtained = player.level().getServer().getAdvancements().get(HEART_CONTAINER_OBTAINED);
		boolean newlyObtained = obtained != null && player.getAdvancements().getOrStartProgress(obtained).isDone();
		if (!newlyObtained && player.getAttachedOrElse(HEART_INVENTORY_VERSION, -1) == inventoryVersion) {
			return;
		}
		player.setAttached(HEART_INVENTORY_VERSION, inventoryVersion);
		Item heartContainer = heartContainerItem();
		boolean consumed = false;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty() || !stack.is(heartContainer)) {
				continue;
			}
			while (!stack.isEmpty() && hearts < MAX_HEARTS) {
				stack.shrink(1);
				inventory.setChanged();
				if (obtained != null) {
					for (String criterion : obtained.value().criteria().keySet()) {
						player.getAdvancements().revoke(obtained, criterion);
					}
				}
				hearts = Math.min(MAX_HEARTS, hearts + 2);
				setHearts(player, hearts);
				player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 10, false, false));
				consumed = true;
			}
		}
		if (consumed) {
			applyMaxHealth(player, hearts);
			// One sound per pickup batch instead of one per heart container,
			// matching the upstream Hashiru optimisation.
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.5F, 0.0F);
		}
	}

	/**
	 * Looked up lazily: the item registry only has matcha:crystal_heart after
	 * the registrar ran, so a static field would resolve to air and match every
	 * empty inventory slot.
	 */
	private static Item heartContainerItem() {
		return BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("matcha", "crystal_heart"));
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

	// sleeping fast-forwards ~12 hours with a single +120/tick once enough
	// overworld (non-spectator) players are sleeping; the threshold respects
	// the players_sleeping_percentage gamerule like vanilla. Other-dimension
	// players don't count.
	private static void manageSleep(MinecraftServer server) {
		int active = 0;
		int sleeping = 0;
		boolean anyInWindow = false;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.level().dimension() != Level.OVERWORLD
					|| player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
				continue;
			}
			active++;
			if (player.isSleeping()) {
				sleeping++;
				if (player.getSleepTimer() > 0 && player.getSleepTimer() < 100) {
					anyInWindow = true;
				}
			}
		}
		int percentage = server.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
		int needed = Math.max(1, (int) Math.ceil(active * percentage / 100.0));
		if (sleeping < needed || !anyInWindow) {
			return;
		}
		if (server.getGameRules().get(GameRules.ADVANCE_WEATHER)) {
			server.setWeatherParameters(ServerLevel.RAIN_DELAY.sample(server.overworld().getRandom()), 0, false, false);
		}
		var clock = server.registryAccess()
				.lookupOrThrow(Registries.WORLD_CLOCK)
				.getOrThrow(WorldClocks.OVERWORLD);
		server.clockManager().addTicks(clock, 120);
	}

	private static int minimumHearts(ServerPlayer player) {
		Difficulty difficulty = WorldMechanics.cachedDifficulty(player.level().getServer());
		if (difficulty == Difficulty.EASY || difficulty == Difficulty.PEACEFUL) {
			return 20;
		}
		var objective = player.level().getServer().getScoreboard().getObjective(HEARTS_OBJECTIVE);
		if (objective == null) {
			return 20;
		}
		return Math.clamp(worldScore(player.level().getServer().getScoreboard(), objective, CURRENT_MINIMUM),
				ABSOLUTE_MINIMUM_HEARTS, MAX_HEARTS);
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

	private static int worldScore(Scoreboard scoreboard, Objective objective, String holder) {
		var info = scoreboard.getPlayerScoreInfo(ScoreHolder.forNameOnly(holder), objective);
		return info == null ? 0 : info.value();
	}

	private static void ensureWorldScore(Scoreboard scoreboard, Objective objective, String holder, int value) {
		if (objective != null && scoreboard.getPlayerScoreInfo(ScoreHolder.forNameOnly(holder), objective) == null) {
			scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(holder), objective).set(value);
		}
	}

	private static void forceWorldScore(Scoreboard scoreboard, Objective objective, String holder, int value) {
		if (objective != null) {
			scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(holder), objective).set(value);
		}
	}
}
