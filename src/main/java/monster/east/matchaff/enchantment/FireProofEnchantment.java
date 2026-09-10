package monster.east.matchaff.enchantment;

import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.List;

final class FireProofEnchantment {
	private static final net.minecraft.resources.Identifier FIRE_PROOF = EnchantmentUtil.id("fire_proof");

	private FireProofEnchantment() {
	}

	static void tick(ServerPlayer player, Registry<Enchantment> enchantments, List<ItemStack> extraHeadItems) {
		if (EnchantmentUtil.maxHeadLevel(player, extraHeadItems, enchantments, FIRE_PROOF) > 0) {
			player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20, 0, true, false));
		}
	}
}
