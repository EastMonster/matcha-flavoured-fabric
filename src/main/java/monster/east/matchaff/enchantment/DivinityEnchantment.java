package monster.east.matchaff.enchantment;

import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.enchantment.Enchantment;

final class DivinityEnchantment {
	private static final net.minecraft.resources.Identifier DIVINITY = EnchantmentUtil.id("divinity");

	private DivinityEnchantment() {
	}

	static void tick(ServerPlayer player, Registry<Enchantment> enchantments) {
		// Armour plus a Divinity main-hand item caps at four instead of the upstream score-5 gap.
		int divinity = Math.min(4, EnchantmentUtil.countArmor(player, enchantments, DIVINITY)
				+ EnchantmentUtil.maxLevel(player.getMainHandItem(), enchantments, DIVINITY));
		if (divinity < 1) {
			return;
		}
		if (EnchantmentUtil.elapsed(player, 10)) {
			player.addEffect(new MobEffectInstance(MobEffects.HEALTH_BOOST, 20, divinity - 1, false, false));
		}
		if (divinity == 4 && EnchantmentUtil.elapsed(player, 600)) {
			player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 620, 0, false, false));
		}
	}
}
