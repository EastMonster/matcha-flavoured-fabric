package monster.east.matchaff.mixin.compat;

import monster.east.matchaff.compat.FarmersDelightCompat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "vectorwing.farmersdelight.common.block.entity.container.CookingPotMenu", remap = false)
public abstract class FarmersDelightCookingPotMenuMixin {
	@Shadow @Final public static int INDEX_OUTPUT;

	@Inject(method = "quickMoveStack", at = @At("RETURN"), require = 0)
	private void matcha$discoverShiftTakenRecipe(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
		if (index == INDEX_OUTPUT) {
			// FD passes the remaining stack to onTake, which can be empty after Shift-clicking.
			FarmersDelightCompat.discoverCookingPotRecipe(player, cir.getReturnValue());
		}
	}
}
