package monster.east.matchaff.mixin;

import net.minecraft.advancements.predicates.ItemPredicate;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShearsItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemPredicate.class)
public abstract class ShearsCompatibilityMixin {
	@Redirect(
			method = "test(Lnet/minecraft/world/item/ItemInstance;)Z",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemInstance;is(Lnet/minecraft/core/HolderSet;)Z")
	)
	private boolean matcha$acceptCustomShears(ItemInstance stack, HolderSet<Item> items) {
		return stack.is(items)
				|| stack.typeHolder().value() instanceof ShearsItem
				&& items.contains(BuiltInRegistries.ITEM.wrapAsHolder(Items.SHEARS));
	}
}
