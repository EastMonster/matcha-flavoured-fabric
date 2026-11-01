package monster.east.matchaff.mixin;

import it.unimi.dsi.fastutil.objects.Object2IntMap;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.TransmuteRecipe;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Start with the result's default enchantments plus recipe overrides, then
 * merge base enchantments at their highest levels. Resolve conflicts against
 * the result's intrinsic enchantments, not enchantments inherited from the base.
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

		// Base stack patches must not override the upgraded item's repair materials.
		result.copyFrom(DataComponents.REPAIRABLE, resultTemplate);

		ItemEnchantments baseEnchantments = base.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		ItemEnchantments intrinsicEnchantments = resultTemplate.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		ItemEnchantments.Mutable merged = new ItemEnchantments.Mutable(intrinsicEnchantments);
		for (Object2IntMap.Entry<Holder<Enchantment>> entry : baseEnchantments.entrySet()) {
			merged.upgrade(entry.getKey(), entry.getIntValue());
		}
		for (Holder<Enchantment> intrinsic : intrinsicEnchantments.keySet()) {
			if (intrinsic.is(Identifier.fromNamespaceAndPath("matcha", "electrum_tool"))) {
				merged.removeIf(enchantment -> enchantment.is(Enchantments.SILK_TOUCH)
						|| enchantment.is(Enchantments.FORTUNE));
			}
			if (intrinsic.is(Enchantments.SMITE)) {
				merged.removeIf(enchantment -> enchantment.is(Enchantments.SHARPNESS));
			}
			if (intrinsic.is(Enchantments.SILK_TOUCH)) {
				merged.removeIf(enchantment -> enchantment.is(Enchantments.FORTUNE));
			}
		}
		result.set(DataComponents.ENCHANTMENTS, merged.toImmutable());
		return result;
	}
}
