package monster.east.matchaff.mixin;

import monster.east.matchaff.mechanic.PigMechanics;
import net.minecraft.client.renderer.entity.PigRenderer;
import net.minecraft.client.renderer.entity.state.PigRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.pig.Pig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PigRenderer.class)
public abstract class PigRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/animal/pig/Pig;Lnet/minecraft/client/renderer/entity/state/PigRenderState;F)V", at = @At("TAIL"))
	private void matcha$animatePig(Pig pig, PigRenderState state, float partialTicks, CallbackInfo ci) {
		int animation = PigMechanics.animationTicks(pig);
		if (animation == 0) return;
		state.walkAnimationSpeed = 0;
		state.yRot = 0;
		state.xRot = animation > 0
				? Mth.lerp(partialTicks, PigMechanics.diggingPitch(animation + 1), PigMechanics.diggingPitch(animation)) : 0;
	}
}
