package monster.east.matchaff.enchantment;

import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.List;

final class
RegenerationEnchantment {
	private static final net.minecraft.resources.Identifier REGENERATION = EnchantmentUtil.id("regeneration");

	private RegenerationEnchantment() {
	}

	static void tick(ServerPlayer player, Registry<Enchantment> enchantments, List<ItemStack> extraHeadItems) {
		if (EnchantmentUtil.maxHeadLevel(player, extraHeadItems, enchantments, REGENERATION) > 0
				&& !player.hasEffect(MobEffects.REGENERATION)) {
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, false));
		}
	}
}
