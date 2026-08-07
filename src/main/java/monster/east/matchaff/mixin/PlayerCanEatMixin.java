package monster.east.matchaff.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The hunger bar is kept permanently full, so vanilla would refuse to start
 * eating at full hunger. Allow eating anytime: food consumption only matters
 * for the direct-heal effect.
 */
@Mixin(Player.class)
public abstract class PlayerCanEatMixin {
	@Inject(method = "canEat", at = @At("HEAD"), cancellable = true)
	private void matcha$alwaysCanEat(boolean canAlwaysEat, CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(true);
	}
}
