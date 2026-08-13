package monster.east.matchaff.mechanic;

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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.Comparator;
import java.util.List;
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
	private static final Identifier CLEANSE_MALEFFECT = id("cleanse_armor_maleffect");
	private static final Identifier SHAKUDO_REGEN = id("shakudo_regen");
	private static final Identifier SHAKUDO_ELYTRA = id("shakudo_elytra");
	private static final Identifier CONDUIT_POWER = id("conduit_power");
	private static final Identifier FIRE_PROOF = id("fire_proof");
	private static final Identifier HASTE = id("haste");
	private static final Identifier REGENERATION = id("regeneration");
	private static final Identifier ZEPHYR = id("zephyr");
	private static final Identifier ANEMOS = id("anemos");
	private static final Identifier SLAUGHTER = id("slaughter");

	private static final TagKey<net.minecraft.world.entity.EntityType<?>> WARDING_TARGETS = TagKey.create(
			Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("main", "warding_targets")
	);
	private static final TagKey<net.minecraft.world.entity.EntityType<?>> WARDING_TARGETS_SLOWED = TagKey.create(
			Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("main", "warding_targets_slowed")
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

		// Hand-held warding runs every tick, matching the enchantment tick effect.
		int heldWarding = heldWardingLevel(player, enchantments);
		if (heldWarding >= 0) {
			wardingAura(player, heldWarding);
		}

		// Armour warding (apotropaic): 1-2 pieces every second, 3-4 every half second.
		int apotropaic = countArmor(player, enchantments, WARDING_ARMOUR);
		if (apotropaic > 0) {
			int interval = apotropaic <= 2 ? 20 : 10;
			if (elapsed(player, interval)) {
				int level = apotropaic == 1 ? 1 : apotropaic == 4 ? 3 : 2;
				wardingAura(player, level);
			}
			if (apotropaic == 4 && elapsed(player, 600)) {
				player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 600, 1, false, false));
			}
		}

		// Treat armour plus a Divinity main-hand item as the four-piece set instead of the upstream score-5 gap.
		int divinity = Math.min(4, countArmor(player, enchantments, DIVINITY) + maxLevel(mainHand, enchantments, DIVINITY));
		if (divinity >= 1 && divinity <= 4) {
			int interval = divinity == 4 ? 400 : 600;
			if (elapsed(player, interval)) {
				int amplifier = divinity == 4 ? 4 : divinity - 1;
				player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, interval, amplifier, false, false));
			}
		}

		// Bloodrage: below 5 hearts, wielded axe grants resistance and strength.
		if (maxLevel(mainHand, enchantments, BLOODRAGE) > 0 && player.getHealth() <= 10.0F) {
			player.sendOverlayMessage(Component.translatable("matcha.message.bloodrage").withStyle(ChatFormatting.RED));
			player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20, 1, true, true));
			player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 20, 0, true, true));
			var level = player.level();
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
		if (countArmor(player, enchantments, CLEANSE_MALEFFECT) > 0) {
			player.removeEffect(MobEffects.POISON);
			player.removeEffect(MobEffects.WITHER);
			player.removeEffect(MobEffects.MINING_FATIGUE);
			player.removeEffect(MobEffects.WEAKNESS);
			player.removeEffect(MobEffects.BLINDNESS);
			player.removeEffect(MobEffects.DARKNESS);
			player.removeEffect(MobEffects.INFESTED);
			player.removeEffect(MobEffects.WEAVING);
			player.removeEffect(MobEffects.NAUSEA);
			player.removeEffect(MobEffects.OOZING);
			player.removeEffect(MobEffects.SLOWNESS);
		}

		// Shakudo Elytra intentionally scores +1 as chest equipment and another +4 as Elytra.
		int shakudoRegen = countArmor(player, enchantments, SHAKUDO_REGEN);
		ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
		if (maxLevel(chest, enchantments, SHAKUDO_REGEN) > 0
				&& SHAKUDO_ELYTRA.equals(BuiltInRegistries.ITEM.getKey(chest.getItem()))) {
			shakudoRegen += 4;
		}
		if (shakudoRegen >= 1 && shakudoRegen <= 8) {
			int interval = switch (shakudoRegen) {
				case 1 -> 600;
				case 2 -> 520;
				case 3 -> 440;
				case 4 -> 360;
				case 5 -> 400;
				case 6 -> 320;
				case 7 -> 240;
				default -> 160;
			};
			if (elapsed(player, interval)) {
				player.addEffect(new MobEffectInstance(
						MobEffects.REGENERATION, 60, shakudoRegen >= 5 ? 1 : 0, false, false));
			}
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
			var level = player.level();
			for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class,
					player.getBoundingBox().inflate(3.0),
					entity -> entity.is(LIVESTOCK)
							&& entity.distanceToSqr(player) <= 3.0 * 3.0)) {
				nearby.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 9, true, false));
			}
		}
	}

	private static void zephyr(ServerPlayer player) {
		int ticks = player.getAttachedOrElse(ZEPHYR_TICKS, 0);
		var level = player.level();
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
		var serverLevel = player.level();
		int queryRadius = level == 3 ? 24 : level == 2 ? 14 : 8;
		List<LivingEntity> targets = wardingTargets(serverLevel, player, queryRadius);
		if (level == 3) {
			for (LivingEntity target : targets) {
				if (!target.is(WARDING_TARGETS)
						|| wearingCopperArmor(target)) {
					continue;
				}
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 1, false, false));
				if (target.getType() != EntityTypes.WITHER) {
					wardingParticle(serverLevel, target, ParticleTypes.SOUL_FIRE_FLAME);
				}
			}
			LivingEntity damaged = nearestTarget(targets, player, 12, WARDING_TARGETS,
					entity -> entity.getType() != EntityTypes.WITHER && !wearingCopperArmor(entity));
			if (damaged != null) {
				damaged.hurtServer(serverLevel, serverLevel.damageSources().fellOutOfWorld(), 2.0F);
			}

			for (LivingEntity target : targets) {
				if (target.distanceToSqr(player) <= 12 * 12
						&& target.is(WARDING_TARGETS)
						&& target.getType() != EntityTypes.WITHER && wearingCopperArmor(target)) {
					wardingParticle(serverLevel, target, ParticleTypes.SOUL_FIRE_FLAME);
				}
			}
			LivingEntity copperDamaged = nearestTarget(targets, player, 8, WARDING_TARGETS,
					entity -> entity.getType() != EntityTypes.WITHER && wearingCopperArmor(entity));
			if (copperDamaged != null) {
				copperDamaged.hurtServer(serverLevel, serverLevel.damageSources().fellOutOfWorld(), 1.0F);
			}
			LivingEntity copperSlowed = nearestTarget(targets, player, 12, WARDING_TARGETS,
					EnchantmentMechanics::wearingCopperArmor);
			if (copperSlowed != null) {
				copperSlowed.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 0, false, false));
			}
			witherWarding(serverLevel, targets, player, 16, 2.0F);
			return;
		}

		int slowRadius = level == 2 ? 14 : 8;
		int damageRadius = switch (level) {
			case 0 -> 3;
			case 1 -> 6;
			default -> 8;
		};
		int slowAmplifier = level == 1 ? 1 : 0;
		LivingEntity slowed = nearestTarget(targets, player, slowRadius, WARDING_TARGETS_SLOWED,
				entity -> !wearingCopperArmor(entity));
		if (slowed != null) {
			slowed.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, slowAmplifier, false, false));
		}
		LivingEntity damaged = nearestTarget(targets, player, damageRadius, WARDING_TARGETS,
				entity -> entity.getType() != EntityTypes.WITHER && !wearingCopperArmor(entity));
		if (damaged != null) {
			damaged.hurtServer(serverLevel, serverLevel.damageSources().fellOutOfWorld(), 1.0F);
		}
		LivingEntity particleTarget = nearestTarget(targets, player, slowRadius, WARDING_TARGETS,
				entity -> entity.getType() != EntityTypes.WITHER && !wearingCopperArmor(entity));
		if (particleTarget != null) {
			wardingParticle(serverLevel, particleTarget, ParticleTypes.SOUL_FIRE_FLAME);
		}
		LivingEntity copperTarget = nearestTarget(targets, player, slowRadius, WARDING_TARGETS,
				entity -> entity.getType() != EntityTypes.WITHER && wearingCopperArmor(entity));
		if (copperTarget != null) {
			wardingParticle(serverLevel, copperTarget, ParticleTypes.ELECTRIC_SPARK);
		}
		if (level > 0) {
			witherWarding(serverLevel, targets, player, level == 1 ? 8 : 12, 1.0F);
		}
	}

	private static LivingEntity nearestTarget(
			List<LivingEntity> targets, LivingEntity center, double radius,
			TagKey<net.minecraft.world.entity.EntityType<?>> tag, Predicate<LivingEntity> predicate
	) {
		double radiusSquared = radius * radius;
		return targets.stream()
				.filter(entity -> entity.is(tag)
							&& predicate.test(entity)
							&& entity.distanceToSqr(center) <= radiusSquared)
				.min(Comparator.comparingDouble(entity -> entity.distanceToSqr(center)))
				.orElse(null);
	}

	private static List<LivingEntity> wardingTargets(ServerLevel level, LivingEntity center, double radius) {
		double radiusSquared = radius * radius;
		return level.getEntitiesOfClass(LivingEntity.class, center.getBoundingBox().inflate(radius), entity ->
				(entity.is(WARDING_TARGETS)
						|| entity.is(WARDING_TARGETS_SLOWED))
						&& entity.distanceToSqr(center) <= radiusSquared);
	}

	private static boolean wearingCopperArmor(LivingEntity entity) {
		return entity.getItemBySlot(EquipmentSlot.HEAD).is(Items.COPPER_HELMET)
				|| entity.getItemBySlot(EquipmentSlot.CHEST).is(Items.COPPER_CHESTPLATE)
				|| entity.getItemBySlot(EquipmentSlot.LEGS).is(Items.COPPER_LEGGINGS)
				|| entity.getItemBySlot(EquipmentSlot.FEET).is(Items.COPPER_BOOTS);
	}

	private static void wardingParticle(
			ServerLevel level, LivingEntity target, net.minecraft.core.particles.SimpleParticleType particle
	) {
		level.sendParticles(particle, target.getX(), target.getY() + 1.5, target.getZ(),
				1, particle == ParticleTypes.ELECTRIC_SPARK ? 0.25 : 0.1, 0.3,
				particle == ParticleTypes.ELECTRIC_SPARK ? 0.25 : 0.1, 0.02);
	}

	private static void witherWarding(
			ServerLevel level, List<LivingEntity> targets, LivingEntity center, double radius, float damage
	) {
		LivingEntity wither = nearestTarget(targets, center, radius, WARDING_TARGETS,
				entity -> entity.getType() == EntityTypes.WITHER);
		if (wither != null) {
			wither.hurtServer(level, level.damageSources().fellOutOfWorld(), damage);
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
					wither.getX(), wither.getY() + 2.5, wither.getZ(), 1, 0.5, 0.5, 0.5, 0.02);
		}
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
		var level = serverPlayer.level();
		var charge = EntityTypes.WIND_CHARGE.create(level, EntitySpawnReason.EVENT);
		var eye = serverPlayer.getEyePosition().add(serverPlayer.getLookAngle().scale(0.75));
		charge.setPos(eye.x, eye.y, eye.z);
		charge.setDeltaMovement(serverPlayer.getLookAngle().scale(2.5));
		charge.setOwner(serverPlayer);
		level.addFreshEntity(charge);
		serverPlayer.setAttached(ANEMOS_READY_TICK, currentTick + 10);
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
