package monster.east.matchaff.enchantment;

import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.List;

final class HasteEnchantment {
	private static final net.minecraft.resources.Identifier HASTE = EnchantmentUtil.id("haste");

	private HasteEnchantment() {
	}

	static void tick(ServerPlayer player, Registry<Enchantment> enchantments, List<ItemStack> extraHeadItems) {
		if (EnchantmentUtil.maxHeadLevel(player, extraHeadItems, enchantments, HASTE) > 0) {
			player.addEffect(new MobEffectInstance(MobEffects.HASTE, 20, 1, true, false));
		}
	}
}
