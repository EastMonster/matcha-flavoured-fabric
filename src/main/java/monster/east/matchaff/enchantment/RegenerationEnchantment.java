package monster.east.matchaff.enchantment;

import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.List;

final class RegenerationEnchantment {
	private static final net.minecraft.resources.Identifier REGENERATION = EnchantmentUtil.id("regeneration");

	private RegenerationEnchantment() {
	}

	static void tick(ServerPlayer player, Registry<Enchantment> enchantments, List<ItemStack> extraHeadItems) {
		if (player.hasEffect(MobEffects.REGENERATION)) {
			return;
		}
		if (EnchantmentUtil.maxLevel(player.getItemBySlot(EquipmentSlot.HEAD), enchantments, REGENERATION) > 0) {
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, false));
			return;
		}
		boolean hasRegenerationEarring = extraHeadItems.stream()
				.anyMatch(stack -> EnchantmentUtil.maxLevel(stack, enchantments, REGENERATION) > 0);
		if (!hasRegenerationEarring) {
			return;
		}
		if (player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, false));
			return;
		}
		// A 60-tick regeneration I pulse heals once; cap accessories only while a helmet is worn.
		if (EnchantmentUtil.elapsed(player, 60)) {
			float amount = earringHealAmount(player.getHealth(), player.getMaxHealth());
			if (amount > 0) {
				player.heal(amount);
			}
		}
	}

	static float earringHealAmount(float health, float maxHealth) {
		return Math.max(0.0F, Math.min(1.0F, Math.min(maxHealth / 2.0F, 20.0F) - health));
	}
}
