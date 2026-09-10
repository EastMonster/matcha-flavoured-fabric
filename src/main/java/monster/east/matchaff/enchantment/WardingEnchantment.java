package monster.east.matchaff.enchantment;

import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.Comparator;
import java.util.List;

final class WardingEnchantment {
	private static final Identifier ELECTRUM_ARMOUR = EnchantmentUtil.id("electrum_armour");
	private static final Identifier[] TIERS = {
			EnchantmentUtil.id("warding_1"), EnchantmentUtil.id("warding_2"),
			EnchantmentUtil.id("warding_3"), EnchantmentUtil.id("warding_4")
	};
	private static final TagKey<EntityType<?>> TARGETS = TagKey.create(
			Registries.ENTITY_TYPE, EnchantmentUtil.id("warding_targets")
	);
	private static final TagKey<EntityType<?>> DAMAGE_TARGETS = TagKey.create(
			Registries.ENTITY_TYPE, EnchantmentUtil.id("warding_targets_no_wither")
	);
	private static final TagKey<EntityType<?>> SLOWED_TARGETS = TagKey.create(
			Registries.ENTITY_TYPE, EnchantmentUtil.id("warding_targets_slowed")
	);

	private WardingEnchantment() {
	}

	static void tick(ServerPlayer player, Registry<Enchantment> enchantments) {
		int armour = EnchantmentUtil.countArmor(player, enchantments, ELECTRUM_ARMOUR);
		int level = effectiveLevel(heldScore(player, enchantments), armour);
		if (level > 0 && EnchantmentUtil.elapsed(player, level <= 2 ? 20 : 10)) {
			aura(player, level);
		}
		if (armour == 4 && EnchantmentUtil.elapsed(player, 600)) {
			player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 620, 0, false, false));
		}
	}

	static int effectiveLevel(int heldScore, int armourScore) {
		return Math.max(Math.min(3, heldScore), Math.min(4, armourScore));
	}

	private static int heldScore(ServerPlayer player, Registry<Enchantment> enchantments) {
		int score = 0;
		for (int tier = 0; tier < TIERS.length; tier++) {
			int value = tier + 1;
			score += value * EnchantmentUtil.maxLevel(player.getMainHandItem(), enchantments, TIERS[tier]);
			score += value * EnchantmentUtil.maxLevel(player.getOffhandItem(), enchantments, TIERS[tier]);
		}
		return score;
	}

	private static void aura(ServerPlayer player, int level) {
		ServerLevel serverLevel = player.level();
		int slowRadius = switch (level) {
			case 1 -> 8;
			case 2 -> 10;
			case 3 -> 14;
			default -> 24;
		};
		int damageRadius = switch (level) {
			case 1 -> 3;
			case 2 -> 6;
			case 3 -> 8;
			default -> 12;
		};
		int witherRadius = switch (level) {
			case 1 -> 0;
			case 2 -> 8;
			case 3 -> 12;
			default -> 16;
		};
		List<LivingEntity> targets = targets(serverLevel, player, slowRadius);
		TagKey<EntityType<?>> slowedTag = level == 4 ? TARGETS : SLOWED_TARGETS;
		for (LivingEntity target : targets) {
			if (target.is(slowedTag)
					&& target.distanceToSqr(player) <= slowRadius * slowRadius
					&& (level == 4 || !wearingCopperArmor(target))) {
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, level >= 3 ? 1 : 0, false, false));
				slownessParticles(serverLevel, target);
			}
		}

		LivingEntity damaged = nearestTarget(targets, player, damageRadius, DAMAGE_TARGETS);
		if (damaged != null) {
			boolean wearingCopper = wearingCopperArmor(damaged);
			if (wearingCopper && level < 4) {
				resistanceEffect(serverLevel, player, damaged);
			} else {
				float damage = level == 4 && !wearingCopper ? 2.0F : 1.0F;
				damage(serverLevel, damaged, damage);
			}
		}
		if (witherRadius > 0) {
			LivingEntity wither = nearestTarget(targets, player, witherRadius, TARGETS, EntityTypes.WITHER);
			if (wither != null) {
				damage(serverLevel, wither, level == 4 ? 2.0F : 1.0F);
			}
		}
	}

	private static LivingEntity nearestTarget(
			List<LivingEntity> targets, LivingEntity center, double radius, TagKey<EntityType<?>> tag
	) {
		return nearestTarget(targets, center, radius, tag, null);
	}

	private static LivingEntity nearestTarget(
			List<LivingEntity> targets, LivingEntity center, double radius,
			TagKey<EntityType<?>> tag, EntityType<?> type
	) {
		double radiusSquared = radius * radius;
		return targets.stream()
				.filter(entity -> entity.is(tag)
						&& (type == null || entity.getType() == type)
						&& entity.distanceToSqr(center) <= radiusSquared)
				.min(Comparator.comparingDouble(entity -> entity.distanceToSqr(center)))
				.orElse(null);
	}

	private static List<LivingEntity> targets(ServerLevel level, LivingEntity center, double radius) {
		double radiusSquared = radius * radius;
		return level.getEntitiesOfClass(LivingEntity.class, center.getBoundingBox().inflate(radius), entity ->
				(entity.is(TARGETS) || entity.is(SLOWED_TARGETS))
						&& entity.distanceToSqr(center) <= radiusSquared);
	}

	private static boolean wearingCopperArmor(LivingEntity entity) {
		return entity.getItemBySlot(EquipmentSlot.HEAD).is(Items.COPPER_HELMET)
				|| entity.getItemBySlot(EquipmentSlot.CHEST).is(Items.COPPER_CHESTPLATE)
				|| entity.getItemBySlot(EquipmentSlot.LEGS).is(Items.COPPER_LEGGINGS)
				|| entity.getItemBySlot(EquipmentSlot.FEET).is(Items.COPPER_BOOTS);
	}

	private static void slownessParticles(ServerLevel level, LivingEntity target) {
		level.sendParticles(ParticleTypes.SCULK_SOUL, target.getX(), target.getY() + 0.1, target.getZ(),
				5, 0.25, 0.0, 0.25, 0.01);
	}

	private static void resistanceEffect(ServerLevel level, LivingEntity center, LivingEntity target) {
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getEyeY() + 0.25, target.getZ(),
				10, 0.35, 0.25, 0.35, 0.02);
		level.playSound(null, center.getX(), center.getY(), center.getZ(),
				SoundEvents.TRIDENT_RETURN, SoundSource.HOSTILE, 1.0F, 2.0F);
	}

	private static void damage(ServerLevel level, LivingEntity target, float amount) {
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, target.getX(), target.getEyeY(), target.getZ(),
				amount == 2.0F ? 5 : 10, 0.1, 0.2, 0.1, 0.05);
		target.hurtServer(level, level.damageSources().fellOutOfWorld(), amount);
	}
}
