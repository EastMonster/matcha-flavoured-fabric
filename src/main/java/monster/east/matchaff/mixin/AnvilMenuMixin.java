package monster.east.matchaff.mixin;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The pack removes experience entirely, so the anvil must not cost levels.
 * Zeroing the cost makes every anvil operation free (repairs, enchantment
 * books, renaming) instead of the old "grant 50 levels for 15s" simulation.
 * The vanilla "too expensive" cap is bypassed for the same reason.
 * Vanilla also refuses to pick up the result when the cost is 0, so mayPickup
 * is bypassed as well (otherwise the crafted item could not be taken).
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {
	@Shadow
	@Final
	private DataSlot cost;

	@Redirect(
			method = "createResult",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z", ordinal = 1)
	)
	private boolean matcha$acceptBlessingBooks(ItemStack stack, Object item) {
		// The left book must bypass equipment eligibility just like vanilla enchanted books.
		return stack.is((Item) item)
				|| item == Items.ENCHANTED_BOOK && stack.has(DataComponents.STORED_ENCHANTMENTS);
	}

	@Inject(method = "createResult", at = @At("TAIL"))
	private void matcha$freeAnvil(CallbackInfo ci) {
		this.cost.set(0);
	}

	@Redirect(
			method = "createResult",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/DataSlot;get()I", ordinal = 1)
	)
	private int matcha$ignoreTooExpensiveLimit(DataSlot ignored) {
		return 0;
	}

	@Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
	private void matcha$alwaysPickup(CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(true);
	}
}
