package monster.east.matchaff;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.Comparator;
import java.util.function.Predicate;

/**
 * Behaviour for the datapack's custom enchantments, replacing the
 * run_function effects that were wired into the enchantment definitions.
 *
 * <p>Pure data-driven enchantments (reach, sanguine, riposte, traversal,
 * freezing_protection, slaughter's damage bonus) stay in JSON and are not
 * touched here.</p>
 */
public final class EnchantmentMechanics {
	private static final Identifier DIVINITY = id("divinity");
	private static final Identifier WARDING_ARMOUR = id("warding_armour");
	private static final Identifier WARDING0 = id("warding0");
	private static final Identifier WARDING1 = id("warding1");
	private static final Identifier WARDING2 = id("warding2");
	private static final Identifier WARDING3 = id("warding3");
	private static final Identifier BLOODRAGE = id("bloodrage");
	private static final Identifier CLEANSE_HEAD = id("cleanse_armor_head");
	private static final Identifier CLEANSE_CHEST = id("cleanse_armor_chest");
	private static final Identifier CLEANSE_LEGS = id("cleanse_armor_legs");
	private static final Identifier CLEANSE_FEET = id("cleanse_armor_feet");
	private static final Identifier CONDUIT_POWER = id("conduit_power");
	private static final Identifier FIRE_PROOF = id("fire_proof");
	private static final Identifier HASTE = id("haste");
	private static final Identifier REGENERATION = id("regeneration");
	private static final Identifier ZEPHYR = id("zephyr");
	private static final Identifier ANEMOS = id("anemos");
	private static final Identifier SLAUGHTER = id("slaughter");

	private static final TagKey<net.minecraft.world.entity.EntityType<?>> UNDEAD = TagKey.create(
			Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("minecraft", "undead")
	);
	private static final TagKey<net.minecraft.world.entity.EntityType<?>> LIVESTOCK = TagKey.create(
			Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("main", "livestock")
	);

	private static final AttachmentType<Integer> ZEPHYR_TICKS = AttachmentRegistry.create(
			Identifier.fromNamespaceAndPath("matcha-flavoured", "zephyr_ticks")
	);
	private static final AttachmentType<Integer> ANEMOS_READY_TICK = AttachmentRegistry.create(
			Identifier.fromNamespaceAndPath("matcha-flavoured", "anemos_ready_tick")
	);

	private EnchantmentMechanics() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tick(player);
			}
		});
		AttackEntityCallback.EVENT.register((player, level, hand, target, hitResult) -> {
			maybeAnemos(player);
			return InteractionResult.PASS;
		});
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			maybeAnemos(player);
			return InteractionResult.PASS;
		});
	}

	private static void tick(ServerPlayer player) {
		Registry<Enchantment> enchantments = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		ItemStack mainHand = player.getMainHandItem();
		ItemStack offHand = player.getOffhandItem();

		// Hand-held warding (damages undead every tick).
		int heldWarding = heldWardingLevel(player, enchantments);
		if (heldWarding >= 0) {
			wardingAura(player, heldWarding);
		}

		// Armour warding (apotropaic), throttled like the old stopwatches.
		int apotropaic = countArmor(player, enchantments, WARDING_ARMOUR);
		if (apotropaic > 0) {
			int interval = apotropaic % 2 == 1 ? 10 : 20;
			if (elapsed(player, interval)) {
				int level = apotropaic == 1 ? 1 : apotropaic == 4 ? 3 : 2;
				wardingAura(player, level);
			}
		}

		// Divinity absorption on a 15s/30s cycle.
		int divinity = countArmor(player, enchantments, DIVINITY) + maxLevel(mainHand, enchantments, DIVINITY);
		if (divinity > 0) {
			int interval = divinity == 5 ? 300 : 600;
			if (elapsed(player, interval)) {
				int amplifier = divinity - 1;
				int duration = divinity == 5 ? 300 : 600;
				player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, duration, amplifier, true, true));
			}
		}

		// Bloodrage: below 5 hearts, wielded axe grants resistance and strength.
		if (maxLevel(mainHand, enchantments, BLOODRAGE) > 0 && player.getHealth() <= 10.0F) {
			player.sendOverlayMessage(Component.translatable("matcha.message.bloodrage").withStyle(ChatFormatting.RED));
			player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20, 1, true, true));
			player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 20, 0, true, true));
			var level = (ServerLevel) player.level();
			level.sendParticles(new DustParticleOptions(0xFF0000, 1.0F),
					player.getX(), player.getY() + 1.5, player.getZ(), 1, 0.25, 0.25, 0.25, 0.1);
		}

		// Cleanse: each armour piece purges its debuff while equipped.
		if (maxLevel(player.getItemBySlot(EquipmentSlot.HEAD), enchantments, CLEANSE_HEAD) > 0) {
			player.removeEffect(MobEffects.BLINDNESS);
			player.removeEffect(MobEffects.DARKNESS);
		}
		if (maxLevel(player.getItemBySlot(EquipmentSlot.CHEST), enchantments, CLEANSE_CHEST) > 0) {
			player.removeEffect(MobEffects.POISON);
			player.removeEffect(MobEffects.WITHER);
		}
		if (maxLevel(player.getItemBySlot(EquipmentSlot.LEGS), enchantments, CLEANSE_LEGS) > 0) {
			player.removeEffect(MobEffects.SLOWNESS);
		}
		if (maxLevel(player.getItemBySlot(EquipmentSlot.FEET), enchantments, CLEANSE_FEET) > 0) {
			player.removeEffect(MobEffects.LEVITATION);
		}

		// Head-slot buffs.
		ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
		if (maxLevel(head, enchantments, CONDUIT_POWER) > 0) {
			player.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 20, 0, true, false));
			player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 20, 0, true, false));
		}
		if (maxLevel(head, enchantments, FIRE_PROOF) > 0) {
			player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20, 0, true, false));
		}
		if (maxLevel(head, enchantments, HASTE) > 0) {
			player.addEffect(new MobEffectInstance(MobEffects.HASTE, 20, 1, true, false));
		}
		if (maxLevel(head, enchantments, REGENERATION) > 0 && !player.hasEffect(MobEffects.REGENERATION)) {
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, false));
		}

		// Zephyr boots: sneak to charge, release to launch.
		if (maxLevel(player.getItemBySlot(EquipmentSlot.FEET), enchantments, ZEPHYR) > 0) {
			zephyr(player);
		}

		// Slaughter: slow nearby livestock.
		if (maxLevel(mainHand, enchantments, SLAUGHTER) > 0) {
			var level = (ServerLevel) player.level();
			for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class,
					player.getBoundingBox().inflate(3.0),
					entity -> entity.getType().builtInRegistryHolder().is(LIVESTOCK))) {
				nearby.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 9, true, false));
			}
		}
	}

	private static void zephyr(ServerPlayer player) {
		int ticks = player.getAttachedOrElse(ZEPHYR_TICKS, 0);
		var level = (ServerLevel) player.level();
		if (player.isCrouching()) {
			// Every charging tick: jump boost 6 + slow falling 1 (0.25s) and a dust plume.
			ticks++;
			player.setAttached(ZEPHYR_TICKS, ticks);
			player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 5, 6, true, false));
			player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 5, 1, true, false));
			level.sendParticles(ParticleTypes.DUST_PLUME,
					player.getX(), player.getY(), player.getZ(), 1, 0.3, 0, 0.3, 0.1);
			if (ticks == 45) {
				level.sendParticles(ParticleTypes.DUST_PLUME,
						player.getX(), player.getY(), player.getZ(), 50, 0.5, 0.1, 0.5, 0.1);
				level.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.75F, 1.0F);
			}
		} else if (!player.onGround() && player.hasEffect(MobEffects.SLOW_FALLING)) {
			// zephyr_execute: airborne release while slow falling is still active.
			if (ticks > 0) {
				level.sendParticles(ParticleTypes.GUST,
						player.getX(), player.getY() + 0.1, player.getZ(), 1, 0.1, 0, 0.1, 0);
			}
			if (ticks >= 45) {
				level.sendParticles(ParticleTypes.POOF,
						player.getX(), player.getY(), player.getZ(), 50, 0.1, 0.1, 0.1, 0.5);
				player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 20, 12, true, false));
			}
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.HOSTILE, 2.0F, 1.0F);
			player.setAttached(ZEPHYR_TICKS, 0);
		} else if (!player.onGround()) {
			// zephyr_failure: airborne release without slow falling, charge is lost.
			player.setAttached(ZEPHYR_TICKS, 0);
		}
	}

	private static void wardingAura(ServerPlayer player, int level) {
		int slowRadius = switch (level) {
			case 0, 1 -> 8;
			case 2 -> 12;
			default -> 24;
		};
		int damageRadius = switch (level) {
			case 0 -> 3;
			case 1 -> 8;
			case 2 -> 12;
			default -> 24;
		};
		int witherRadius = switch (level) {
			case 0 -> 0;
			case 1 -> 8;
			default -> 12;
		};
		int particleRadius = level == 3 ? 12 : damageRadius;
		float damage = switch (level) {
			case 0, 1 -> 1.0F;
			case 2 -> 3.0F;
			default -> 19.0F;
		};
		float witherDamage = switch (level) {
			case 0 -> 0;
			case 1, 2 -> 1.0F;
			default -> 2.0F;
		};
		int slowAmplifier = switch (level) {
			case 0, 1 -> 1;
			case 2 -> 2;
			default -> 3;
		};

		var serverLevel = (ServerLevel) player.level();
		LivingEntity slowed = nearestUndead(serverLevel, player, slowRadius, entity -> true);
		if (slowed != null) {
			slowed.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, slowAmplifier, true, false));
		}

		LivingEntity damaged = nearestUndead(serverLevel, player, damageRadius,
				entity -> entity.getType() != EntityTypes.WITHER);
		if (damaged != null) {
			damaged.hurt(serverLevel.damageSources().fellOutOfWorld(), damage);
		}
		LivingEntity particleTarget = nearestUndead(serverLevel, player, particleRadius,
				entity -> entity.getType() != EntityTypes.WITHER);
		if (particleTarget != null) {
			serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
					particleTarget.getX(), particleTarget.getY() + 1.5, particleTarget.getZ(),
					1, 0.1, 0.3, 0.1, 0.02);
		}

		if (witherRadius > 0) {
			LivingEntity wither = nearestUndead(serverLevel, player, witherRadius,
					entity -> entity.getType() == EntityTypes.WITHER);
			if (wither != null) {
				wither.hurt(serverLevel.damageSources().fellOutOfWorld(), witherDamage);
				serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
						wither.getX(), wither.getY() + 2.5, wither.getZ(), 1, 0.5, 0.5, 0.5, 0.02);
			}
		}
	}

	private static LivingEntity nearestUndead(
			ServerLevel level, LivingEntity center, double radius, Predicate<LivingEntity> predicate
	) {
		double radiusSquared = radius * radius;
		return level.getEntitiesOfClass(LivingEntity.class, center.getBoundingBox().inflate(radius), entity ->
					entity.getType().builtInRegistryHolder().is(UNDEAD)
							&& predicate.test(entity)
							&& entity.distanceToSqr(center) <= radiusSquared)
				.stream()
				.min(Comparator.comparingDouble(entity -> entity.distanceToSqr(center)))
				.orElse(null);
	}

	/** Returns the warding tier (0-3) held in main/off hand, or -1 when absent. */
	private static int heldWardingLevel(ServerPlayer player, Registry<Enchantment> enchantments) {
		Identifier[] tiers = {WARDING0, WARDING1, WARDING2, WARDING3};
		int found = -1;
		for (int tier = 0; tier < tiers.length; tier++) {
			if (maxLevel(player.getMainHandItem(), player.getOffhandItem(), enchantments, tiers[tier]) > 0) {
				found = tier;
			}
		}
		return found;
	}

	private static void maybeAnemos(Player player) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		Registry<Enchantment> enchantments = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		if (maxLevel(player.getMainHandItem(), enchantments, ANEMOS) == 0) {
			return;
		}
		int currentTick = serverPlayer.level().getServer().getTickCount();
		if (currentTick < serverPlayer.getAttachedOrElse(ANEMOS_READY_TICK, 0)) {
			return;
		}
		var level = (ServerLevel) serverPlayer.level();
		var charge = EntityTypes.WIND_CHARGE.create(level, EntitySpawnReason.EVENT);
		var eye = serverPlayer.getEyePosition().add(serverPlayer.getLookAngle().scale(0.75));
		charge.setPos(eye.x, eye.y, eye.z);
		charge.setDeltaMovement(serverPlayer.getLookAngle().scale(2.5));
		level.addFreshEntity(charge);
		serverPlayer.setAttached(ANEMOS_READY_TICK, currentTick + 20);
	}

	private static int countArmor(ServerPlayer player, Registry<Enchantment> enchantments, Identifier enchantment) {
		int count = 0;
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			count += maxLevel(player.getItemBySlot(slot), enchantments, enchantment);
		}
		return count;
	}

	private static int maxLevel(ItemStack stack, Registry<Enchantment> enchantments, Identifier... ids) {
		int best = 0;
		for (Identifier id : ids) {
			Holder.Reference<Enchantment> holder = enchantments.get(id).orElse(null);
			if (holder != null && !stack.isEmpty()) {
				best = Math.max(best, stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(holder));
			}
		}
		return best;
	}

	private static int maxLevel(ItemStack first, ItemStack second, Registry<Enchantment> enchantments, Identifier... ids) {
		return Math.max(maxLevel(first, enchantments, ids), maxLevel(second, enchantments, ids));
	}

	private static boolean elapsed(ServerPlayer player, int interval) {
		return player.level().getServer().getTickCount() % interval == 0;
	}

	private static Identifier id(String name) {
		return Identifier.fromNamespaceAndPath("matcha-flavoured", name);
	}
}
