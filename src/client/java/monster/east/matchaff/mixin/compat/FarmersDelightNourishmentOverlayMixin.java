package monster.east.matchaff.mixin.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "vectorwing.farmersdelight.client.gui.HUDOverlays$NourishmentOverlay", remap = false)
public abstract class FarmersDelightNourishmentOverlayMixin {
	@Inject(method = "shouldRenderOverlay", at = @At("HEAD"), cancellable = true, require = 0)
	private void matcha$hideHungerOverlay(Minecraft minecraft, Player player, GuiGraphicsExtractor graphics,
			int guiTicks, CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(false);
	}
}
