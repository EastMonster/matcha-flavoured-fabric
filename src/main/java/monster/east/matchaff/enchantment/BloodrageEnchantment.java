package monster.east.matchaff.enchantment;

import net.minecraft.core.Registry;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.enchantment.Enchantment;

final class BloodrageEnchantment {
	private static final net.minecraft.resources.Identifier BLOODRAGE = EnchantmentUtil.id("bloodrage");

	private BloodrageEnchantment() {
	}

	static void tick(ServerPlayer player, Registry<Enchantment> enchantments) {
		if (EnchantmentUtil.maxLevel(player.getMainHandItem(), enchantments, BLOODRAGE) == 0
				|| player.getHealth() > 10.0F) {
			return;
		}
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20, 1, true, true));
		player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 20, 0, true, true));
		var level = player.level();
		level.sendParticles(new DustParticleOptions(0xFF0000, 1.0F),
				player.getX(), player.getY() + 1.5, player.getZ(), 1, 0.25, 0.25, 0.25, 0.1);
	}
}
