package monster.east.matchaff.mechanic;

import net.minecraft.ChatFormatting;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.Repairable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * World-level mechanics for the slow day cycle, boat/sulphur particles,
 * glass-bottle crafting bonus, infinite anvil repairs and global gamerules.
 */
public final class WorldMechanics {
	private static final String DIFFICULTY_OBJECTIVE = "difficulty_score";
	private static final String CURRENT_DIFFICULTY = "current_world_settings_difficulty";
	private static final String VERSION_OBJECTIVE = "matcha_version";
	private static final String REWARD_VERSION_OBJECTIVE = "matcha_reward_version";
	private static final String GAMERULES_OBJECTIVE = "matcha_gamerules_version";
	private static final String GAMERULES_HOLDER = "current";
	private static final int RECIPE_UNLOCK_VERSION = 1_12_01_004;
	private static final int REWARD_UPSTREAM_VERSION = 1_12_01;
	private static final String VERSION_LABEL = "1.12.1";
	private static final Identifier GLASS_BOTTLE_ADVANCEMENT = id("glass_bottle_from_crafting");
	private static final Identifier TWO_GLASS_BOTTLES_ADVANCEMENT = id("two_glass_bottles_from_crafting");
	private static final Identifier ELYTRA_ADVANCEMENT = id("item_contingencies/elytra");

	private static final Map<UUID, Integer> LAST_BOATING_DISTANCE = new HashMap<>();

	private WorldMechanics() {
	}

	public static void init() {
		MobMechanics.init();
		VillageMechanics.init();
		DivineItemMechanics.init();
		ServerLifecycleEvents.SERVER_STARTED.register(WorldMechanics::onServerStarted);
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
			if (success) {
				cacheDifficulty(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LAST_BOATING_DISTANCE.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(WorldMechanics::tick);
		ServerPlayerEvents.JOIN.register(WorldMechanics::welcome);
	}

	private static void onServerStarted(MinecraftServer server) {
		applyGameRulesIfNeeded(server);
		if (server.getScoreboard().getObjective("gamerule_safe_surface") == null) {
			server.getScoreboard().addObjective("gamerule_safe_surface", ObjectiveCriteria.DUMMY,
					Component.literal("gamerule_safe_surface"), ObjectiveCriteria.RenderType.INTEGER, true,
					StyledFormat.NO_STYLE);
		}
		if (server.getScoreboard().getObjective(VERSION_OBJECTIVE) == null) {
			server.getScoreboard().addObjective(VERSION_OBJECTIVE, ObjectiveCriteria.DUMMY,
					Component.literal(VERSION_OBJECTIVE), ObjectiveCriteria.RenderType.INTEGER, true,
					StyledFormat.NO_STYLE);
		}
		if (server.getScoreboard().getObjective(REWARD_VERSION_OBJECTIVE) == null) {
			server.getScoreboard().addObjective(REWARD_VERSION_OBJECTIVE, ObjectiveCriteria.DUMMY,
					Component.literal(REWARD_VERSION_OBJECTIVE), ObjectiveCriteria.RenderType.INTEGER, true,
					StyledFormat.NO_STYLE);
		}
		cacheDifficulty(server);
	}

	private static void applyGameRulesIfNeeded(MinecraftServer server) {
		var scoreboard = server.getScoreboard();
		if (scoreboard.getObjective(GAMERULES_OBJECTIVE) == null) {
			scoreboard.addObjective(GAMERULES_OBJECTIVE, ObjectiveCriteria.DUMMY,
					Component.literal(GAMERULES_OBJECTIVE), ObjectiveCriteria.RenderType.INTEGER, true,
					StyledFormat.NO_STYLE);
		}
		var objective = scoreboard.getObjective(GAMERULES_OBJECTIVE);
		var applied = scoreboard.getPlayerScoreInfo(ScoreHolder.forNameOnly(GAMERULES_HOLDER), objective);
		if (applied != null && applied.value() == RECIPE_UNLOCK_VERSION) {
			return;
		}
		var rules = server.getGameRules();
		rules.set(GameRules.NATURAL_HEALTH_REGENERATION, false, server);
		rules.set(GameRules.ADVANCE_TIME, false, server);
		rules.set(GameRules.SPAWN_PHANTOMS, false, server);
		rules.set(GameRules.KEEP_INVENTORY, true, server);
		rules.set(GameRules.BLOCK_EXPLOSION_DROP_DECAY, false, server);
		rules.set(GameRules.MOB_EXPLOSION_DROP_DECAY, false, server);
		rules.set(GameRules.ENDER_PEARLS_VANISH_ON_DEATH, false, server);
		rules.set(GameRules.MAX_BLOCK_MODIFICATIONS, 200000, server);
		rules.set(GameRules.COMMAND_BLOCK_OUTPUT, false, server);
		scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(GAMERULES_HOLDER), objective).set(RECIPE_UNLOCK_VERSION);
	}

	private static void cacheDifficulty(MinecraftServer server) {
		var scoreboard = server.getScoreboard();
		var objective = scoreboard.getObjective(DIFFICULTY_OBJECTIVE);
		if (objective == null) {
			objective = scoreboard.addObjective(DIFFICULTY_OBJECTIVE, ObjectiveCriteria.DUMMY,
					Component.literal(DIFFICULTY_OBJECTIVE), ObjectiveCriteria.RenderType.INTEGER, true,
					StyledFormat.NO_STYLE);
		}
		scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(CURRENT_DIFFICULTY), objective)
				.set(server.getWorldData().getDifficulty().getId());
	}

	public static Difficulty cachedDifficulty(MinecraftServer server) {
		var objective = server.getScoreboard().getObjective(DIFFICULTY_OBJECTIVE);
		if (objective == null) {
			return server.getWorldData().getDifficulty();
		}
		int id = server.getScoreboard()
				.getOrCreatePlayerScore(ScoreHolder.forNameOnly(CURRENT_DIFFICULTY), objective).get();
		Difficulty[] values = Difficulty.values();
		return values[Math.floorMod(id, values.length)];
	}

	/** World progress raises the difficulty to hard once the heart floor hits 10. */
	public static void raiseDifficulty(MinecraftServer server) {
		if (cachedDifficulty(server) == Difficulty.HARD) {
			return;
		}
		server.getPlayerList().broadcastSystemMessage(
				Component.translatable("matcha.message.difficulty.is_now")
						.append(Component.literal(" "))
						.append(Component.translatable(difficultyNameKey(Difficulty.HARD)).withStyle(
								ChatFormatting.RED, ChatFormatting.BOLD))
						.append("\n")
						.append(Component.translatable("matcha.message.difficulty.disclaimer")
								.withStyle(ChatFormatting.GRAY)), false);
		server.setDifficulty(Difficulty.HARD, true);
		cacheDifficulty(server);
	}

	private static void tick(MinecraftServer server) {
		if (!server.tickRateManager().runsNormally()) {
			return;
		}
		int tick = server.getTickCount();
		if (tick % 3 == 0) {
			var clock = server.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).getOrThrow(WorldClocks.OVERWORLD);
			server.clockManager().addTicks(clock, 1);
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			VillageMechanics.tickPlayer(player);
			boatParticles(player);
			sulfurousHellstone(player);
			glassBottleReward(player);
			twoGlassBottleReward(player);
			updateElytra(player);
		}
		DivineItemMechanics.tick(tick);
		VillageMechanics.tick(server, tick);
	}

	private static void welcome(ServerPlayer player) {
		migrateRecipeUnlocks(player);
		player.sendSystemMessage(Component.translatable("matcha.message.welcome")
				.append(Component.literal(VERSION_LABEL))
				.withStyle(style -> style.withColor(TextColor.fromRgb(0x65E082))));
		player.sendSystemMessage(Component.translatable("matcha.message.welcome.desc")
				.withStyle(style -> style.withColor(TextColor.fromRgb(0x8FB398))));
		difficultyWelcome(player);
	}

	/**
	 * One-time per-player recipe unlock migration (datapack decision 15.3,
	 * option 1): on first join with a newer recipe version, revoke the hidden
	 * {@code main:recipe_unlocks/*} advancement criteria so the updated reward
	 * lists re-trigger when the player obtains the matching materials again.
	 * Recipe-book IDs themselves are intentionally not migrated; vanilla drops
	 * old IDs that no longer exist, and item acquisition can unlock them again.
	 * The per-player scoreboard only marks the advancement refresh as done.
	 */
	private static void migrateRecipeUnlocks(ServerPlayer player) {
		var server = player.level().getServer();
		var objective = server.getScoreboard().getObjective(VERSION_OBJECTIVE);
		var rewardObjective = server.getScoreboard().getObjective(REWARD_VERSION_OBJECTIVE);
		if (objective == null || rewardObjective == null) {
			return;
		}
		boolean updated = false;
		int playerVersion = server.getScoreboard().getOrCreatePlayerScore(player, objective).get();
		if (playerVersion < RECIPE_UNLOCK_VERSION) {
			for (AdvancementHolder advancement : server.getAdvancements().getAllAdvancements()) {
				Identifier id = advancement.id();
				if (id.getNamespace().equals("main") && id.getPath().startsWith("recipe_unlocks/")) {
					revoke(player, id);
				}
			}
			server.getScoreboard().getOrCreatePlayerScore(player, objective).set(RECIPE_UNLOCK_VERSION);
			updated = true;
		}
		int rewardVersion = server.getScoreboard().getOrCreatePlayerScore(player, rewardObjective).get();
		if (rewardVersion < REWARD_UPSTREAM_VERSION) {
			for (AdvancementHolder advancement : server.getAdvancements().getAllAdvancements()) {
				Identifier id = advancement.id();
				if (id.getNamespace().equals("main") && isRewardAdvancementToRevoke(id)) {
					revoke(player, id);
				}
			}
			server.getScoreboard().getOrCreatePlayerScore(player, rewardObjective).set(REWARD_UPSTREAM_VERSION);
			updated = true;
		}
		if (!updated) {
			return;
		}
		player.sendSystemMessage(Component.literal("[!]: ").withStyle(ChatFormatting.GREEN)
				.append(Component.translatable("matcha.message.player_updated").withStyle(ChatFormatting.GRAY)));
	}

	private static boolean isRewardAdvancementToRevoke(Identifier id) {
		// Mirror the upstream `main:setup/revoke_all_recipe_unlock_advancements`
		// whitelist exactly. The function revokes trade_everything even though
		// its comment only spares `catch_everything` ("fish everything") and
		// `steal_all_prayers`, so keep it revoked to match upstream behaviour.
		return REWARD_ADVANCEMENTS.contains(id.getPath());
	}

	private static final Set<String> REWARD_ADVANCEMENTS = Set.of(
			"end/elytra",
			"end/smith_bronze_elytra",
			"end/smith_shakudo_elytra",
			"hell/craft_blessing",
			"hell/craft_hell_bound_book",
			"hell/obtain_benzene",
			"hell/obtain_stabilised_estus",
			"hell/obtain_warding_stone",
			"tutorial/cook_pumpkin_curry",
			"tutorial/cook_secret_food",
			"tutorial/cook_secret_meal",
			"tutorial/enter_nether",
			"tutorial/find_stronghold",
			"tutorial/light_beacon",
			"tutorial/obtain_adamant",
			"tutorial/obtain_adamant_tool",
			"tutorial/obtain_amber",
			"tutorial/obtain_bronze_tool",
			"tutorial/obtain_copper",
			"tutorial/obtain_diamond",
			"tutorial/obtain_divine_fragment",
			"tutorial/obtain_electrum",
			"tutorial/obtain_electrum_tool",
			"tutorial/obtain_fetish",
			"tutorial/obtain_green_curry",
			"tutorial/obtain_heart",
			"tutorial/obtain_iron_ingot",
			"tutorial/obtain_japanese_curry",
			"tutorial/obtain_mattock",
			"tutorial/obtain_nazar",
			"tutorial/obtain_opal",
			"tutorial/obtain_palatinate_tool",
			"tutorial/obtain_paneer_mahkani",
			"tutorial/obtain_ramen",
			"tutorial/obtain_ruby",
			"tutorial/obtain_silver",
			"tutorial/obtain_steel_tool",
			"tutorial/obtain_topaz",
			"tutorial/preserve_everything",
			"tutorial/root",
			"tutorial/smith_silver_sword",
			"tutorial/smith_warding_shield",
			"tutorial/trade_everything",
			"tutorial/trade_fish",
			"tutorial/trade_for_asylum_seeker",
			"tutorial/upgrade_mattock");

	private static void difficultyWelcome(ServerPlayer player) {
		Difficulty difficulty = cachedDifficulty(player.level().getServer());
		if (difficulty == Difficulty.PEACEFUL) {
			return;
		}
		ChatFormatting color = difficulty == Difficulty.EASY ? ChatFormatting.GREEN
				: difficulty == Difficulty.NORMAL ? ChatFormatting.GOLD : ChatFormatting.RED;
		String icon = difficulty == Difficulty.EASY ? "[⛏]" : difficulty == Difficulty.NORMAL ? "[☠]" : "[☠☠☠]";
		player.sendSystemMessage(Component.literal(icon).withStyle(color)
				.append(Component.literal(" "))
				.append(Component.translatable("matcha.message.difficulty.is").withStyle(ChatFormatting.GRAY))
				.append(Component.literal(" "))
				.append(Component.translatable(difficultyNameKey(difficulty)).withStyle(color, ChatFormatting.BOLD))
				.append(Component.literal("\n"))
				.append(Component.translatable("matcha.message.difficulty.disclaimer")
						.withStyle(ChatFormatting.DARK_GRAY)));
	}

	private static String difficultyNameKey(Difficulty difficulty) {
		return switch (difficulty) {
			case EASY -> "matcha.message.difficulty.easy";
			case NORMAL -> "matcha.message.difficulty.normal";
			case HARD -> "matcha.message.difficulty.hard";
			default -> "matcha.message.difficulty.normal";
		};
	}

	static Vec3 localOffset(Entity entity, double right, double up, double forward) {
		Vec3 look = entity.getLookAngle();
		Vec3 f = new Vec3(look.x, 0, look.z).normalize();
		Vec3 r = new Vec3(-f.z, 0, f.x);
		return entity.position().add(r.scale(right)).add(0, up, 0).add(f.scale(forward));
	}

	private static void boatParticles(ServerPlayer player) {
		if (!(player.getVehicle() instanceof AbstractBoat boat)) {
			LAST_BOATING_DISTANCE.remove(player.getUUID());
			return;
		}
		var level = player.level();
		BlockPos waterPos = BlockPos.containing(boat.getX(), boat.getY() - 0.1, boat.getZ());
		boolean onWater = level.getBlockState(waterPos).is(Blocks.WATER);
		int currentDistance = player.getStats().getValue(Stats.CUSTOM, Stats.BOAT_ONE_CM);
		Integer previousDistance = LAST_BOATING_DISTANCE.put(player.getUUID(), currentDistance);
		if (previousDistance == null) {
			if (onWater) {
				boatParticle(level, boat, ParticleTypes.SPLASH, 0.75, 0.5, 1.0, 5, 0.1, 0.1, 0.1, 1.0);
				boatParticle(level, boat, ParticleTypes.SPLASH, -0.75, 0.5, 1.0, 5, 0.1, 0.1, 0.1, 1.0);
				boatParticle(level, boat, ParticleTypes.SPLASH, 0.0, 0.5, 1.0, 5, 0.1, 0.1, 0.1, 1.0);
			}
		}

		if (!onWater || previousDistance == null) {
			return;
		}
		int travelled = Math.max(0, currentDistance - previousDistance);
		if (travelled >= 12) {
			boatParticle(level, boat, ParticleTypes.SPLASH, 0.75, 0.5, 1.0, 3, 0.1, 0.1, 0.1, 1.0);
			boatParticle(level, boat, ParticleTypes.SPLASH, -0.75, 0.5, 1.0, 3, 0.1, 0.1, 0.1, 1.0);
			boatParticle(level, boat, ParticleTypes.SPLASH, 0.0, 0.5, -1.1, 10, 0.25, 0.1, 0.25, 1.0);
			boatParticle(level, boat, ParticleTypes.SULFUR_BUBBLES, -0.75, 0.5, -1.0, 3, 0.1, 0.1, 0.1, 0.0);
			boatParticle(level, boat, ParticleTypes.SULFUR_BUBBLES, 0.75, 0.5, -1.0, 3, 0.1, 0.1, 0.1, 0.0);
		}
		if (travelled >= 1) {
			boatParticle(level, boat, ParticleTypes.SPLASH, 0.75, 0.1, 1.0, 1, 0.1, 0.1, 0.1, 0.1);
			boatParticle(level, boat, ParticleTypes.SPLASH, -0.75, 0.1, 1.0, 1, 0.1, 0.1, 0.1, 0.1);
		}
	}

	private static void boatParticle(
			ServerLevel level, AbstractBoat boat, net.minecraft.core.particles.ParticleOptions particle,
			double side, double up, double forward, int count,
			double spreadX, double spreadY, double spreadZ, double speed
	) {
		Vec3 position = localOffset(boat, side, up, forward);
		level.sendParticles(particle, position.x, position.y, position.z,
				count, spreadX, spreadY, spreadZ, speed);
	}

	private static void sulfurousHellstone(ServerPlayer player) {
		if (player.tickCount % 3 != 0) {
			return;
		}
		var level = player.level();
		if (level.getBlockState(player.blockPosition().below()).is(Blocks.NETHER_QUARTZ_ORE)) {
			level.sendParticles(ParticleTypes.NOXIOUS_GAS,
					player.getX(), player.getY() + 0.1, player.getZ(), 1, 0.5, 0, 0.5, 0);
		}
	}

	public static void glassBottleReward(ServerPlayer player) {
		glassBottleReward(player, GLASS_BOTTLE_ADVANCEMENT, 1);
	}

	public static void twoGlassBottleReward(ServerPlayer player) {
		glassBottleReward(player, TWO_GLASS_BOTTLES_ADVANCEMENT, 2);
	}

	private static void glassBottleReward(ServerPlayer player, Identifier advancementId, int count) {
		if (advancementDone(player, advancementId)) {
			ItemStack reward = new ItemStack(Items.GLASS_BOTTLE, count);
			if (!player.addItem(reward)) {
				var dropped = player.drop(reward, false);
				if (dropped != null) {
					dropped.setNoPickUpDelay();
					dropped.setTarget(player.getUUID());
				}
			}
			revoke(player, advancementId);
		}
	}

	private static void updateElytra(ServerPlayer player) {
		if (!advancementDone(player, ELYTRA_ADVANCEMENT)) {
			return;
		}
		ItemStack elytra = player.getItemBySlot(EquipmentSlot.CHEST);
		if (elytra.is(Items.ELYTRA)
				&& elytra.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty()
				&& Identifier.withDefaultNamespace("elytra").equals(elytra.get(DataComponents.ITEM_MODEL))
				&& elytra.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().isEmpty()) {
			ItemStack replacement = new ItemStack(Items.ELYTRA);
			replacement.set(DataComponents.REPAIRABLE, new Repairable(HolderSet.direct(
					Items.HONEYCOMB.builtInRegistryHolder(), Items.FEATHER.builtInRegistryHolder())));
			replacement.set(DataComponents.LORE, new ItemLore(java.util.List.of(
					Component.translatable("desc.kleispack.repaired_with")
							.withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(false)),
					Component.translatable("item.minecraft.honeycomb")
							.withStyle(style -> style.withColor(ChatFormatting.DARK_GRAY).withItalic(false)),
					Component.translatable("item.minecraft.feather")
							.withStyle(style -> style.withColor(ChatFormatting.DARK_GRAY).withItalic(false))
			)));
			player.setItemSlot(EquipmentSlot.CHEST, replacement);
		}
		revoke(player, ELYTRA_ADVANCEMENT);
	}

	static boolean advancementDone(ServerPlayer player, Identifier advancementId) {
		AdvancementHolder advancement = player.level().getServer().getAdvancements().get(advancementId);
		return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
	}

	static void revoke(ServerPlayer player, Identifier advancementId) {
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
