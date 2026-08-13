package monster.east.matchaff.mixin;

import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Hud.class)
public abstract class AirBubblesHudMixin {
	@Inject(method = "getAirBubbleYLine", at = @At("RETURN"), cancellable = true)
	private void matcha$alignAirBubblesWithHealth(int vehicleHearts, int yLineAir, CallbackInfoReturnable<Integer> cir) {
		if (vehicleHearts == 0) {
			cir.setReturnValue(yLineAir + 10);
		}
	}
}
