package monster.east.matchaff.mixin;

import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CompassItem.class)
public abstract class CompassItemMixin {
	@Redirect(
		method = "useOn",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/item/ItemStack;transmuteCopy(Lnet/minecraft/world/level/ItemLike;I)Lnet/minecraft/world/item/ItemStack;"
		)
	)
	private ItemStack matcha$keepCustomCompassItem(ItemStack stack, ItemLike ignoredItem, int count) {
		return stack.transmuteCopy(stack.getItem(), count);
	}
}
