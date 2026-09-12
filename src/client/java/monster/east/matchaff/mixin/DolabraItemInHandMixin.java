package monster.east.matchaff.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import monster.east.matchaff.client.DolabraVisuals;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class DolabraItemInHandMixin {
	@Inject(
			method = "submitArmWithItem",
			at = @At("HEAD")
	)
	private void matcha$beginDolabraRender(
			PlayerRenderState playerRenderState,
			FirstPersonHandsAndItemsRenderState handsRenderState,
			float frameInterp,
			float xRot,
			InteractionHand hand,
			float attack,
			ItemStack itemStack,
			float inverseArmHeight,
			PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector,
			int lightCoords,
			CallbackInfo ci
	) {
		DolabraVisuals.beginFirstPersonRender(hand, itemStack, frameInterp);
	}

	@Inject(method = "submitArmWithItem", at = @At("RETURN"))
	private void matcha$endDolabraRender(
			PlayerRenderState playerRenderState,
			FirstPersonHandsAndItemsRenderState handsRenderState,
			float frameInterp,
			float xRot,
			InteractionHand hand,
			float attack,
			ItemStack itemStack,
			float inverseArmHeight,
			PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector,
			int lightCoords,
			CallbackInfo ci
	) {
		DolabraVisuals.endFirstPersonRender();
	}
}
