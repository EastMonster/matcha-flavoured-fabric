package monster.east.matchaff.mechanic;

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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
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
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/**
 * Matcha player mechanics, replacing the datapack's scoreboard/function tick
 * loop:
 *   * hunger bar is meaningless: food >= 10 drains (hunger 256), food <= 6 is
 *     force-saturated so sprinting always works; natural regen stays off
 *   * crystal hearts: auto-consumed from the inventory up to 30 hearts; dying
 *     costs one heart while above 10. Stored in the per-world "Hearts"
 *     scoreboard (like the datapack) so a new world starts at 10 hearts.
 *   * sleeping fast-forwards 12 hours instead of skipping the night
 *   * icy water in frozen biomes applies blindness, slowness and freeze damage
 *     (freezing_protection III on the chest blocks it)
 *   * xp is wiped every tick; anvils are free (AnvilMenuMixin)
 */
public final class PlayerMechanics {
	private static final int MAX_HEARTS = 60;
	private static final TagKey<Biome> FROZEN_BIOME = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("minecraft", "is_frozen"));
	private static final Identifier FREEZING_PROTECTION = Identifier.fromNamespaceAndPath("matcha-flavoured", "freezing_protection");
	private static final Identifier HEART_CONTAINER_OBTAINED = Identifier.fromNamespaceAndPath(
			"main", "mechanics/heart_container_obtained"
	);
	private static final AttachmentType<Integer> HEART_INVENTORY_VERSION = AttachmentRegistry.create(
			Identifier.fromNamespaceAndPath("matcha-flavoured", "heart_inventory_version")
	);

	private static final String HEARTS_OBJECTIVE = "Hearts";

	private PlayerMechanics() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (server.getScoreboard().getObjective(HEARTS_OBJECTIVE) == null) {
				server.getScoreboard().addObjective(HEARTS_OBJECTIVE, ObjectiveCriteria.DUMMY,
						Component.literal("Hearts"), ObjectiveCriteria.RenderType.INTEGER, true,
						StyledFormat.NO_STYLE);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tick(player);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				int hearts = getHearts(player);
				if (hearts > 20) {
					hearts = Math.max(20, hearts - 2);
					setHearts(player, hearts);
					applyMaxHealth(player, hearts);
				}
			}
		});
	}

	private static void tick(ServerPlayer player) {
		manageHunger(player);
		manageHearts(player);
		manageExperience(player);
		manageFreezingWater(player);
		manageSleep(player);
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
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty() || !stack.is(heartContainer)) {
				continue;
			}
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
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.5F, 1.0F);
			applyMaxHealth(player, hearts);
			return;
		}
	}

	/**
	 * Looked up lazily: the item registry only has matcha:heart_container after
	 * the registrar ran, so a static field would resolve to air and match every
	 * empty inventory slot.
	 */
	private static Item heartContainerItem() {
		return BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("matcha-flavoured", "heart_container"));
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

	private static void manageSleep(ServerPlayer player) {
		if (player.isSleeping() && player.getSleepTimer() > 0 && player.getSleepTimer() < 100) {
			var level = player.level();
			level.getServer().setWeatherParameters(6000, 0, false, false);
			var clock = level.getServer().registryAccess()
					.lookupOrThrow(Registries.WORLD_CLOCK)
					.getOrThrow(WorldClocks.OVERWORLD);
			level.getServer().clockManager().addTicks(clock, 120);
		}
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
		return Math.clamp(hearts, 20, MAX_HEARTS);
	}

	private static void setHearts(ServerPlayer player, int hearts) {
		var scoreboard = player.level().getServer().getScoreboard();
		var objective = scoreboard.getObjective(HEARTS_OBJECTIVE);
		if (objective != null) {
			scoreboard.getOrCreatePlayerScore(player, objective).set(Math.clamp(hearts, 20, MAX_HEARTS));
		}
	}
}
