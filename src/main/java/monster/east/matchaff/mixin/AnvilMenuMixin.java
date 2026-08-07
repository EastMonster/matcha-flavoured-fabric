package monster.east.matchaff.mixin;

import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The pack removes experience entirely, so the anvil must not cost levels.
 * Zeroing the cost makes every anvil operation free (repairs, enchantment
 * books, renaming) instead of the old "grant 50 levels for 15s" simulation.
 * Vanilla also refuses to pick up the result when the cost is 0, so mayPickup
 * is bypassed as well (otherwise the crafted item could not be taken).
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {
	@Shadow
	@Final
	private DataSlot cost;

	@Inject(method = "createResult", at = @At("TAIL"))
	private void matcha$freeAnvil(CallbackInfo ci) {
		this.cost.set(0);
	}

	@Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
	private void matcha$alwaysPickup(CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(true);
	}
}
