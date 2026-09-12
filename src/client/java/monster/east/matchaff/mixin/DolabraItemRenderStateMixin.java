package monster.east.matchaff.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import monster.east.matchaff.client.DolabraVisuals;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStackRenderState.class)
public abstract class DolabraItemRenderStateMixin {
	@Unique
	private boolean matcha$guiMirror;

	@Inject(method = "clear", at = @At("HEAD"))
	private void matcha$clearGuiMirror(CallbackInfo ci) {
		this.matcha$guiMirror = false;
	}

	@Inject(method = "newLayer", at = @At("HEAD"))
	private void matcha$markGuiMirror(CallbackInfoReturnable<ItemStackRenderState.LayerRenderState> cir) {
		if (DolabraVisuals.guiMirrorRequested()) {
			this.matcha$guiMirror = true;
		}
	}

	@Inject(method = "submit", at = @At("HEAD"))
	private void matcha$beginRenderState(
			PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector,
			int lightCoords,
			int overlayCoords,
			int outlineColor,
			CallbackInfo ci
	) {
		DolabraVisuals.beginRenderState(this.matcha$guiMirror);
	}

	@Inject(method = "submit", at = @At("RETURN"))
	private void matcha$endRenderState(
			PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector,
			int lightCoords,
			int overlayCoords,
			int outlineColor,
			CallbackInfo ci
	) {
		DolabraVisuals.endRenderState();
	}
}
