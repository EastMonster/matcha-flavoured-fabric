package monster.east.matchaff.mixin.compat;

import monster.east.matchaff.compat.EnchantmentDescriptionsCompat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "net.darkhax.enchdesc.common.impl.EnchantmentDescriptionsMod", remap = false)
public abstract class EnchantmentDescriptionsMixin {
	@Redirect(
			method = "canDisplayDescription",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/item/ItemStack;getItem()Lnet/minecraft/world/item/Item;"),
			require = 0)
	private static Item matcha$acceptBlessingBooks(ItemStack stack) {
		Item item = stack.getItem();
		// Only extend EnchDesc's book identity check; its other display conditions still apply.
		return EnchantmentDescriptionsCompat.isBlessingBook(BuiltInRegistries.ITEM.getKey(item))
				? Items.ENCHANTED_BOOK : item;
	}
}
