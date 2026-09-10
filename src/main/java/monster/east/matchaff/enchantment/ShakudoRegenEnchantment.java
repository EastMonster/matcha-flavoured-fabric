package monster.east.matchaff.enchantment;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

final class ShakudoRegenEnchantment {
	private static final Identifier SHAKUDO_REGEN = EnchantmentUtil.id("shakudo_regen");
	private static final Identifier SHAKUDO_ELYTRA = EnchantmentUtil.id("shakudo_elytra");

	private ShakudoRegenEnchantment() {
	}

	static void tick(ServerPlayer player, Registry<Enchantment> enchantments) {
		int score = EnchantmentUtil.countArmor(player, enchantments, SHAKUDO_REGEN);
		ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
		if (EnchantmentUtil.maxLevel(chest, enchantments, SHAKUDO_REGEN) > 0
				&& SHAKUDO_ELYTRA.equals(BuiltInRegistries.ITEM.getKey(chest.getItem()))) {
			score += 4;
		}
		if (score < 1 || score > 8) {
			return;
		}
		int interval = switch (score) {
			case 1 -> 600;
			case 2 -> 520;
			case 3 -> 440;
			case 4 -> 360;
			case 5 -> 400;
			case 6 -> 320;
			case 7 -> 240;
			default -> 160;
		};
		if (EnchantmentUtil.elapsed(player, interval)) {
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, score >= 5 ? 1 : 0, false, false));
		}
	}
}
