package monster.east.matchaff.enchantment;

import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;

final class AdamantArmourEnchantment {
	private static final Identifier ADAMANT_ARMOUR = EnchantmentUtil.id("adamant_armour");

	private AdamantArmourEnchantment() {
	}

	static void tick(ServerPlayer player, Registry<Enchantment> enchantments) {
		int pieces = EnchantmentUtil.countArmor(player, enchantments, ADAMANT_ARMOUR);
		if (pieces == 0 || !EnchantmentUtil.elapsed(player, 60)) {
			return;
		}

		ServerLevel level = player.level();
		int radius = radius(pieces);
		var targets = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius), target ->
				target.hasEffect(MobEffects.WEAKNESS)
						&& EnchantmentUtil.countArmor(target, enchantments, ADAMANT_ARMOUR) == 0
						&& target.distanceToSqr(player) <= radius * radius);
		if (targets.isEmpty()) {
			return;
		}

		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.APPLY_EFFECT_RAID_OMEN, SoundSource.HOSTILE, 0.4F, 2.0F);
		for (LivingEntity target : targets) {
			level.sendParticles(ParticleTypes.FLAME, target.getX(), target.getEyeY(), target.getZ(),
					pieces == 1 ? 3 : pieces == 4 ? 12 : 8, 0.1, 0.2, 0.1, 0.05);
			target.hurtServer(level, level.damageSources().fellOutOfWorld(), damage(pieces));
			target.removeEffect(MobEffects.WEAKNESS);
		}
	}

	static int radius(int pieces) {
		return switch (pieces) {
			case 1 -> 12;
			case 2 -> 16;
			case 3 -> 20;
			default -> 28;
		};
	}

	static float damage(int pieces) {
		return switch (pieces) {
			case 1 -> 3.0F;
			case 2 -> 6.0F;
			case 3 -> 9.0F;
			default -> 16.0F;
		};
	}
}
