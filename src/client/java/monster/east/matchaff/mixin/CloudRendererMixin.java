package monster.east.matchaff.mixin;

import monster.east.matchaff.client.MatchaFlavouredClient;
import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(CloudRenderer.class)
public abstract class CloudRendererMixin {
	@ModifyVariable(
			method = "render(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V",
			at = @At("HEAD"),
			argsOnly = true
	)
	private long matcha$accelerateCloudTime(long gameTime) {
		return MatchaFlavouredClient.acceleratedCloudTime(gameTime);
	}
}
