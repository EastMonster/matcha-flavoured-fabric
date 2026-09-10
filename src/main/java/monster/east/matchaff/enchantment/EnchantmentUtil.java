package monster.east.matchaff.enchantment;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.List;

final class EnchantmentUtil {
	private static final EquipmentSlot[] ARMOR_SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	private EnchantmentUtil() {
	}

	static int countArmor(ServerPlayer player, Registry<Enchantment> enchantments, Identifier enchantment) {
		int count = 0;
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			count += maxLevel(player.getItemBySlot(slot), enchantments, enchantment);
		}
		return count;
	}

	static int maxLevel(ItemStack stack, Registry<Enchantment> enchantments, Identifier id) {
		if (stack.isEmpty()) {
			return 0;
		}
		ItemEnchantments itemEnchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		if (itemEnchantments.isEmpty()) {
			return 0;
		}
		Holder.Reference<Enchantment> holder = enchantments.get(id).orElse(null);
		return holder == null ? 0 : itemEnchantments.getLevel(holder);
	}

	static int maxLevel(ItemStack first, ItemStack second, Registry<Enchantment> enchantments, Identifier id) {
		return Math.max(maxLevel(first, enchantments, id), maxLevel(second, enchantments, id));
	}

	static int maxHeadLevel(
			ServerPlayer player, List<ItemStack> extraHeadItems, Registry<Enchantment> enchantments, Identifier id
	) {
		int level = maxLevel(player.getItemBySlot(EquipmentSlot.HEAD), enchantments, id);
		for (ItemStack stack : extraHeadItems) {
			level = Math.max(level, maxLevel(stack, enchantments, id));
		}
		return level;
	}

	static boolean elapsed(ServerPlayer player, int interval) {
		return player.level().getServer().getTickCount() % interval == 0;
	}

	static Identifier id(String name) {
		return Identifier.fromNamespaceAndPath("matcha", name);
	}
}
