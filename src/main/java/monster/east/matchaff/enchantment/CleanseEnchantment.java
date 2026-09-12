package monster.east.matchaff.enchantment;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;

final class CleanseEnchantment {
	private static final Identifier HEAD = EnchantmentUtil.id("cleanse_armor_head");
	private static final Identifier CHEST = EnchantmentUtil.id("cleanse_armor_chest");
	private static final Identifier LEGS = EnchantmentUtil.id("cleanse_armor_legs");
	private static final Identifier FEET = EnchantmentUtil.id("cleanse_armor_feet");
	private static final Identifier MALEFFECT = EnchantmentUtil.id("cleanse_armor_maleffect");

	private CleanseEnchantment() {
	}

	static void tick(ServerPlayer player, Registry<Enchantment> enchantments) {
		if (EnchantmentUtil.maxLevel(player.getItemBySlot(EquipmentSlot.HEAD), enchantments, HEAD) > 0) {
			player.removeEffect(MobEffects.BLINDNESS);
			player.removeEffect(MobEffects.DARKNESS);
		}
		if (EnchantmentUtil.maxLevel(player.getItemBySlot(EquipmentSlot.CHEST), enchantments, CHEST) > 0) {
			player.removeEffect(MobEffects.POISON);
		}
		if (EnchantmentUtil.maxLevel(player.getItemBySlot(EquipmentSlot.LEGS), enchantments, LEGS) > 0) {
			player.removeEffect(MobEffects.SLOWNESS);
		}
		if (EnchantmentUtil.maxLevel(player.getItemBySlot(EquipmentSlot.FEET), enchantments, FEET) > 0) {
			player.removeEffect(MobEffects.LEVITATION);
		}
		if (EnchantmentUtil.countArmor(player, enchantments, MALEFFECT) > 0) {
			player.removeEffect(MobEffects.POISON);
			player.removeEffect(MobEffects.WITHER);
			player.removeEffect(MobEffects.MINING_FATIGUE);
			player.removeEffect(MobEffects.WEAKNESS);
			player.removeEffect(MobEffects.BLINDNESS);
			player.removeEffect(MobEffects.DARKNESS);
			player.removeEffect(MobEffects.INFESTED);
			player.removeEffect(MobEffects.WEAVING);
			player.removeEffect(MobEffects.NAUSEA);
			player.removeEffect(MobEffects.OOZING);
			player.removeEffect(MobEffects.SLOWNESS);
		}
	}
}
