package monster.east.matchaff.mechanic;

import net.minecraft.commands.arguments.coordinates.LocalCoordinates;
import net.minecraft.ChatFormatting;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
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
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.Repairable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * World-level mechanics for timeline-driven day/night, boat/sulphur particles,
 * glass-bottle crafting bonus, infinite anvil repairs and global gamerules.
 */
public final class WorldMechanics {
	private static final String DIFFICULTY_OBJECTIVE = "difficulty_score";
	private static final String CURRENT_DIFFICULTY = "current_world_settings_difficulty";
	private static final String VERSION_OBJECTIVE = "matcha_version";
	private static final String REWARD_VERSION_OBJECTIVE = "matcha_reward_version";
	private static final String GAMERULES_OBJECTIVE = "matcha_gamerules_version";
	private static final String GAMERULES_HOLDER = "current";
	private static final String INITIAL_DIFFICULTY_NOTICE = "initial_difficulty_notice";
	private static final int RECIPE_UNLOCK_VERSION = 1_13_00_001;
	private static final int GAMERULES_VERSION = 2;
	private static final int REWARD_UPSTREAM_VERSION = 1_12_01;
	private static final String VERSION_LABEL = "1.13.0";
	private static final Identifier ELYTRA_ADVANCEMENT = id("item_contingencies/elytra");
	private static final Identifier ON_SULFUR_ADVANCEMENT = id("particle/on_sulfur");
	private static final Repairable ELYTRA_REPAIRABLE = new Repairable(HolderSet.direct(
			Items.HONEYCOMB.builtInRegistryHolder(), Items.FEATHER.builtInRegistryHolder()));

	private static final Map<UUID, Integer> LAST_BOATING_DISTANCE = new HashMap<>();

	private WorldMechanics() {
	}

	public static void init() {
		MechanicAdvancementTriggers.init();
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
		// The version marker does not record the current gamerule value; always
		// restore this rule before allowing the version check to short-circuit.
		var rules = server.getGameRules();
		rules.set(GameRules.ADVANCE_TIME, true, server);
		// Pre-1.12.1 Mod worlds already have this objective, but no gamerule version marker.
		boolean initialized = applied != null || scoreboard.getObjective(VERSION_OBJECTIVE) != null;
		if (shouldLimitInitialDifficulty(initialized, server.getWorldData().isHardcore(),
				server.getWorldData().getDifficulty())) {
			server.setDifficulty(Difficulty.NORMAL, true);
			scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(INITIAL_DIFFICULTY_NOTICE), objective).set(1);
		}
		if (applied != null && applied.value() >= GAMERULES_VERSION) {
			return;
		}
		if (applied != null) {
			scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(GAMERULES_HOLDER), objective)
					.set(GAMERULES_VERSION);
			return;
		}
		rules.set(GameRules.NATURAL_HEALTH_REGENERATION, false, server);
		rules.set(GameRules.SPAWN_PHANTOMS, false, server);
		rules.set(GameRules.KEEP_INVENTORY, true, server);
		rules.set(GameRules.BLOCK_EXPLOSION_DROP_DECAY, false, server);
		rules.set(GameRules.MOB_EXPLOSION_DROP_DECAY, false, server);
		rules.set(GameRules.ENDER_PEARLS_VANISH_ON_DEATH, false, server);
		rules.set(GameRules.MAX_BLOCK_MODIFICATIONS, 200000, server);
		rules.set(GameRules.COMMAND_BLOCK_OUTPUT, false, server);
		scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(GAMERULES_HOLDER), objective).set(GAMERULES_VERSION);
	}

	static boolean shouldLimitInitialDifficulty(boolean initialized, boolean hardcore, Difficulty difficulty) {
		return !initialized && !hardcore && difficulty == Difficulty.HARD;
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
				Component.translatable("message.matcha.difficulty.is_now")
						.append(Component.literal(" "))
						.append(Component.translatable(difficultyNameKey(Difficulty.HARD)).withStyle(
								ChatFormatting.RED, ChatFormatting.BOLD))
						.append("\n")
						.append(Component.translatable("message.matcha.difficulty.disclaimer")
								.withStyle(ChatFormatting.GRAY)), false);
		server.setDifficulty(Difficulty.HARD, true);
		cacheDifficulty(server);
	}

	private static void tick(MinecraftServer server) {
		if (!server.tickRateManager().runsNormally()) {
			return;
		}
		int tick = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			boatParticles(player);
			MechanicAdvancementTriggers.run(player, MechanicAdvancementTriggers.Stage.WORLD_SULFUR);
			MechanicAdvancementTriggers.run(player, MechanicAdvancementTriggers.Stage.WORLD_ELYTRA);
		}
		DivineItemMechanics.tick(tick);
		VillageMechanics.tick(server);
	}

	private static void welcome(ServerPlayer player) {
		migrateRecipeUnlocks(player);
		player.sendSystemMessage(Component.translatable("message.matcha.welcome", VERSION_LABEL)
				.withStyle(style -> style.withColor(TextColor.fromRgb(0x65E082))));
		player.sendSystemMessage(Component.translatable("message.matcha.welcome.desc")
				.withStyle(style -> style.withColor(TextColor.fromRgb(0x8FB398))));
		difficultyWelcome(player);
		var scoreboard = player.level().getServer().getScoreboard();
		var objective = scoreboard.getObjective(GAMERULES_OBJECTIVE);
		if (objective != null) {
			var notice = scoreboard.getPlayerScoreInfo(ScoreHolder.forNameOnly(INITIAL_DIFFICULTY_NOTICE), objective);
			if (notice != null && notice.value() == 1) {
				player.connection.send(new ClientboundSetSubtitleTextPacket(
						Component.translatable("error.matcha.hard_too_early.subtitle").withStyle(ChatFormatting.RED)));
				player.connection.send(new ClientboundSetTitleTextPacket(
						Component.translatable("error.matcha.hard_too_early").withStyle(ChatFormatting.RED)));
				player.sendSystemMessage(Component.translatable("error.matcha.hard_too_early.desc")
						.withStyle(ChatFormatting.GRAY));
				scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(INITIAL_DIFFICULTY_NOTICE), objective).set(0);
			}
		}
	}

	/**
	 * One-time per-player recipe unlock migration (datapack decision 15.3,
	 * option 1): on first join with a newer recipe version, revoke the hidden
	 * {@code matcha:recipe_unlocks/*} advancement criteria so the updated reward
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
				if (id.getNamespace().equals("matcha") && id.getPath().startsWith("recipe_unlocks/")) {
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
				if (id.getNamespace().equals("matcha") && isRewardAdvancementToRevoke(id)) {
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
				.append(Component.translatable("message.matcha.player_updated").withStyle(ChatFormatting.GRAY)));
	}

	private static boolean isRewardAdvancementToRevoke(Identifier id) {
		// Mirror the upstream `matcha:setup/revoke_all_recipe_unlock_advancements`
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
				.append(Component.translatable("message.matcha.difficulty.is").withStyle(ChatFormatting.GRAY))
				.append(Component.literal(" "))
				.append(Component.translatable(difficultyNameKey(difficulty)).withStyle(color, ChatFormatting.BOLD))
				.append(Component.literal("\n"))
				.append(Component.translatable("message.matcha.difficulty.disclaimer")
						.withStyle(ChatFormatting.DARK_GRAY)));
	}

	private static String difficultyNameKey(Difficulty difficulty) {
		return switch (difficulty) {
			case EASY -> "message.matcha.difficulty.easy";
			case NORMAL -> "message.matcha.difficulty.normal";
			case HARD -> "message.matcha.difficulty.hard";
			default -> "message.matcha.difficulty.normal";
		};
	}

	static Vec3 localOffset(Entity entity, double left, double up, double forward) {
		return new LocalCoordinates(left, up, forward).apply(entity.position(), entity.getRotationVector());
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

	static void handleAdvancement(ServerPlayer player, Identifier advancement) {
		if (advancement.equals(ON_SULFUR_ADVANCEMENT)) {
			sulfurousHellstone(player);
		} else if (advancement.equals(ELYTRA_ADVANCEMENT)) {
			updateElytra(player);
		}
	}

	private static void sulfurousHellstone(ServerPlayer player) {
		if (!advancementDone(player, ON_SULFUR_ADVANCEMENT)) {
			return;
		}
		revoke(player, ON_SULFUR_ADVANCEMENT);
		var level = player.level();
		level.sendParticles(ParticleTypes.NOXIOUS_GAS,
				player.getX(), player.getY() + 0.1, player.getZ(), 3, 0.5, 0, 0.5, 0);
	}

	private static void updateElytra(ServerPlayer player) {
		if (!advancementDone(player, ELYTRA_ADVANCEMENT)) {
			return;
		}
		ItemStack elytra = player.getItemBySlot(EquipmentSlot.CHEST);
		if (elytra.is(Items.ELYTRA)
				&& elytra.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty()
				&& Identifier.withDefaultNamespace("elytra").equals(elytra.get(DataComponents.ITEM_MODEL))
				&& !ELYTRA_REPAIRABLE.equals(elytra.get(DataComponents.REPAIRABLE))) {
			elytra.set(DataComponents.REPAIRABLE, ELYTRA_REPAIRABLE);
			player.setItemSlot(EquipmentSlot.CHEST, elytra);
		}
		revoke(player, ELYTRA_ADVANCEMENT);
	}

	static boolean advancementDone(ServerPlayer player, Identifier advancementId) {
		AdvancementHolder advancement = player.level().getServer().getAdvancements().get(advancementId);
		boolean done = advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
		return done;
	}

	static void revoke(ServerPlayer player, Identifier advancementId) {
		MechanicAdvancementTriggers.clear(player.getUUID(), advancementId);
		AdvancementHolder advancement = player.level().getServer().getAdvancements().get(advancementId);
		if (advancement != null) {
			for (String criterion : advancement.value().criteria().keySet()) {
				player.getAdvancements().revoke(advancement, criterion);
			}
		}
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("matcha", path);
	}

}
