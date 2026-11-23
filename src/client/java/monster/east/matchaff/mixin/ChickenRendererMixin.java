package monster.east.matchaff.mixin;

import monster.east.matchaff.mechanic.BirdMechanics;
import net.minecraft.client.renderer.entity.ChickenRenderer;
import net.minecraft.client.renderer.entity.state.ChickenRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.chicken.Chicken;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChickenRenderer.class)
public abstract class ChickenRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/animal/chicken/Chicken;Lnet/minecraft/client/renderer/entity/state/ChickenRenderState;F)V", at = @At("TAIL"))
	private void matcha$animate(Chicken chicken, ChickenRenderState state, float partialTicks, CallbackInfo ci) {
		int remaining = BirdMechanics.animationTicks(chicken);
		if (remaining <= 0) return;
		state.walkAnimationSpeed = 0;
		state.yRot = 0;
		boolean nesting = BirdMechanics.nesting(chicken);
		state.xRot = Mth.lerp(partialTicks, BirdMechanics.actionPitch(remaining + 1, nesting), BirdMechanics.actionPitch(remaining, nesting));
		state.flap = (60 - remaining + partialTicks) * 2;
		state.flapSpeed = 0.5F;
		if (nesting) state.bodyRot += Mth.lerp(partialTicks, BirdMechanics.nestingTurn(remaining + 1), BirdMechanics.nestingTurn(remaining));
	}
}
