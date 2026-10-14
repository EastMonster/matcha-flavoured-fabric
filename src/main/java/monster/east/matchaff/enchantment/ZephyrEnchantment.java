package monster.east.matchaff.enchantment;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;

final class ZephyrEnchantment {
	private static final net.minecraft.resources.Identifier ZEPHYR = EnchantmentUtil.id("zephyr");
	private static final AttachmentType<Integer> TICKS = AttachmentRegistry.create(
			net.minecraft.resources.Identifier.fromNamespaceAndPath("matcha-flavoured", "zephyr_ticks")
	);

	private ZephyrEnchantment() {
	}

	static void tick(ServerPlayer player, Registry<Enchantment> enchantments) {
		int zephyrLevel = EnchantmentUtil.maxLevel(player.getItemBySlot(EquipmentSlot.FEET), enchantments, ZEPHYR);
		if (zephyrLevel == 0) {
			player.setAttached(TICKS, 0);
			return;
		}
		int ticks = player.getAttachedOrElse(TICKS, 0);
		var level = player.level();
		if (player.isCrouching()) {
			ticks = zephyrLevel >= 3 ? Math.min(ticks + 1, 46) : 0;
			player.setAttached(TICKS, ticks);
			player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 5, 6, true, false));
			if (zephyrLevel >= 2) {
				player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 5, 1, true, false));
			}
			level.sendParticles(ParticleTypes.DUST_PLUME,
					player.getX(), player.getY(), player.getZ(), 1, 0.3, 0, 0.3, 0.1);
			if (ticks == 45) {
				level.sendParticles(ParticleTypes.DUST_PLUME,
						player.getX(), player.getY(), player.getZ(), 50, 0.5, 0.1, 0.5, 0.1);
				level.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.75F, 1.0F);
			}
		} else {
			if (canLaunch(zephyrLevel, ticks, player.onGround())) {
				level.sendParticles(ParticleTypes.GUST,
						player.getX(), player.getY() + 0.1, player.getZ(), 1, 0.1, 0, 0.1, 0);
				level.sendParticles(ParticleTypes.POOF,
						player.getX(), player.getY(), player.getZ(), 50, 0.1, 0.1, 0.1, 0.5);
				player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 20, 12));
				level.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.5F, 1.0F);
			}
			player.setAttached(TICKS, 0);
		}
	}

	static boolean canLaunch(int level, int ticks, boolean onGround) {
		return level >= 3 && ticks >= 45 && !onGround;
	}
}
