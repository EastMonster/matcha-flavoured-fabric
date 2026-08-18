package monster.east.matchaff.mixin;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The datapack's blessing books are vanilla enchanted books (stored
 * enchantments). The mod registers them as independent items, but the anvil
 * only treats enchanted_books as stored-enchantment sources; every other item
 * is read from the ENCHANTMENTS component. Without this mixin blessing books
 * can neither be applied on the anvil nor carry their stored enchantments.
 */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperMixin {
	@Inject(method = "getComponentType", at = @At("HEAD"), cancellable = true)
	private static void matcha$blessingBooksUseStoredEnchantments(ItemStack stack,
			CallbackInfoReturnable<DataComponentType<ItemEnchantments>> cir) {
		if (stack.is(Items.ENCHANTED_BOOK) || stack.has(DataComponents.STORED_ENCHANTMENTS)) {
			cir.setReturnValue(DataComponents.STORED_ENCHANTMENTS);
		}
	}
}
