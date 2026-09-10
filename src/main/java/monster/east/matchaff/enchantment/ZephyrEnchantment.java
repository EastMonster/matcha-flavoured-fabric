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
		if (EnchantmentUtil.maxLevel(player.getItemBySlot(EquipmentSlot.FEET), enchantments, ZEPHYR) == 0) {
			return;
		}
		int ticks = player.getAttachedOrElse(TICKS, 0);
		var level = player.level();
		if (player.isCrouching()) {
			ticks++;
			player.setAttached(TICKS, ticks);
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
		} else if (ticks > 0 && !player.onGround() && player.hasEffect(MobEffects.SLOW_FALLING)) {
			level.sendParticles(ParticleTypes.GUST,
					player.getX(), player.getY() + 0.1, player.getZ(), 1, 0.1, 0, 0.1, 0);
			if (ticks >= 45) {
				level.sendParticles(ParticleTypes.POOF,
						player.getX(), player.getY(), player.getZ(), 50, 0.1, 0.1, 0.1, 0.5);
				player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 20, 12, true, false));
			}
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.HOSTILE, 0.5F, 1.0F);
			player.setAttached(TICKS, 0);
		} else if (!player.onGround()) {
			player.setAttached(TICKS, 0);
		}
	}
}
