package monster.east.matchaff.mixin.compat;

import monster.east.matchaff.compat.FarmersDelightCompat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "vectorwing.farmersdelight.common.block.entity.container.CookingPotResultSlot", remap = false)
public abstract class FarmersDelightCookingPotResultSlotMixin {
	@Inject(method = "onTake", at = @At("TAIL"), require = 0)
	private void matcha$discoverRecipe(Player player, ItemStack serving, CallbackInfo ci) {
		FarmersDelightCompat.discoverCookingPotRecipe(player, serving);
	}
}
