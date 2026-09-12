package monster.east.matchaff.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import monster.east.matchaff.client.DolabraVisuals;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.renderer.item.ItemStackRenderState$LayerRenderState")
public abstract class DolabraItemLayerRenderStateMixin {
	@Inject(method = "applyTransform", at = @At("RETURN"))
	private void matcha$rotateDolabra(PoseStack.Pose pose, CallbackInfo ci) {
		DolabraVisuals.applyItemRotation(pose);
	}
}
