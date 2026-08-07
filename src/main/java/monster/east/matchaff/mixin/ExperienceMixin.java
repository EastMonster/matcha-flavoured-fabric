package monster.east.matchaff.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blocks experience points from ever being granted. The datapack wipes xp
 * every tick (which still plays pickup sounds and briefly flashes the level
 * bar); blocking at the source keeps the level permanently at zero without
 * those side effects. Mending repairs still work because they consume the
 * orb inside ExperienceOrb#playerTouch, before giveExperiencePoints.
 */
@Mixin(ServerPlayer.class)
public abstract class ExperienceMixin {
	@Inject(method = "giveExperiencePoints", at = @At("HEAD"), cancellable = true)
	private void matcha$blockExperiencePoints(int amount, CallbackInfo ci) {
		ci.cancel();
	}
}
