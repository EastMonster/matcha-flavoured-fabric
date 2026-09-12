package monster.east.matchaff.mixin;

import monster.east.matchaff.client.MatchaClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Restores the author's non-True-Darkness ambient colour for this client only. */
@Mixin(LightmapRenderStateExtractor.class)
public abstract class TrueDarknessMixin {
	@Inject(method = "extract", at = @At("TAIL"))
	private void matcha$optionalOverworldAmbientLight(LightmapRenderState renderState, float partialTicks, CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		if (!MatchaClientConfig.trueDarkness()
				&& client.level != null
				&& client.level.dimension().equals(Level.OVERWORLD)) {
			renderState.ambientColor = ARGB.vector3fFromRGB24(0x212631);
		}
	}
}
