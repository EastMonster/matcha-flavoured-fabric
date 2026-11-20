package monster.east.matchaff.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.feature.BlockModelFeatureRenderer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockModelFeatureRenderer.class)
public abstract class LeavesBreakingMixin {
	// FRAPI is disabled in 26.4 snapshot 10; recheck the breaking render path and Sodium compatibility when it returns.
	@Inject(method = "putQuad", at = @At("HEAD"), cancellable = true)
	private static void matcha$dropOutOfBoundsBreakingQuads(
			PoseStack.Pose pose, BakedQuad quad, QuadInstance instance,
			int baseTintColor, int[] tintLayers, VertexConsumer buffer, CallbackInfo ci) {
		// Only breaking decals should omit the decorative panels outside the block.
		if (buffer instanceof SheetedDecalTextureGenerator && matcha$quadOutOfBounds(quad)) {
			ci.cancel();
		}
	}

	@Unique
	private static boolean matcha$quadOutOfBounds(BakedQuad quad) {
		for (int i = 0; i < 4; i++) {
			var position = quad.position(i);
			if (position.x() < 0.0F || position.x() > 1.0F
					|| position.y() < 0.0F || position.y() > 1.0F
					|| position.z() < 0.0F || position.z() > 1.0F) {
				return true;
			}
		}
		return false;
	}
}
