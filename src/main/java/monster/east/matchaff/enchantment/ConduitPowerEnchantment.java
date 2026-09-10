package monster.east.matchaff.enchantment;

import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.List;

final class ConduitPowerEnchantment {
	private static final net.minecraft.resources.Identifier CONDUIT_POWER = EnchantmentUtil.id("conduit_power");

	private ConduitPowerEnchantment() {
	}

	static void tick(ServerPlayer player, Registry<Enchantment> enchantments, List<ItemStack> extraHeadItems) {
		if (EnchantmentUtil.maxHeadLevel(player, extraHeadItems, enchantments, CONDUIT_POWER) == 0) {
			return;
		}
		if (player.isInWater()) {
			player.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 20, 0, true, false));
		}
		player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 20, 0, true, false));
	}
}
