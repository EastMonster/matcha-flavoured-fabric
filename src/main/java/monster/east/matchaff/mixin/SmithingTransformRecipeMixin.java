package monster.east.matchaff.mixin;

import it.unimi.dsi.fastutil.objects.Object2IntMap;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.TransmuteRecipe;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Smithing transform results normally replace the whole enchantments
 * component, dropping enchantments already on the base tool. This merges the
 * base enchantments back in, keeping the highest level where the recipe's own
 * intrinsic enchantments overlap.
 */
@Mixin(SmithingTransformRecipe.class)
public abstract class SmithingTransformRecipeMixin {
	@Redirect(
			method = "assemble",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/item/crafting/TransmuteRecipe;createWithOriginalComponents(Lnet/minecraft/world/item/ItemStackTemplate;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;"
			)
	)
	private ItemStack matcha$mergeBaseEnchantments(final ItemStackTemplate resultTemplate, final ItemStack base) {
		ItemStack result = TransmuteRecipe.createWithOriginalComponents(resultTemplate, base);
		if (result.isEmpty()) {
			return result;
		}

		ItemEnchantments baseEnchantments = base.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		if (baseEnchantments.isEmpty()) {
			return result;
		}

		ItemEnchantments resultEnchantments = result.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		ItemEnchantments.Mutable merged = new ItemEnchantments.Mutable(resultEnchantments);
		for (Object2IntMap.Entry<Holder<Enchantment>> entry : baseEnchantments.entrySet()) {
			merged.upgrade(entry.getKey(), entry.getIntValue());
		}
		result.set(DataComponents.ENCHANTMENTS, merged.toImmutable());
		return result;
	}
}
