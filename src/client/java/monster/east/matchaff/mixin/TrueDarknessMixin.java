package monster.east.matchaff.mixin;

import monster.east.matchaff.client.MatchaClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Restores the author's non-True-Darkness ambient colour for this client only. */
@Mixin(LightmapRenderStateExtractor.class)
public abstract class TrueDarknessMixin {
	@ModifyArg(
			method = "extract",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/util/ARGB;vector3fFromRGB24(I)Lorg/joml/Vector3f;",
					ordinal = 2
			),
			index = 0
	)
	private int matcha$optionalOverworldAmbientLight(int color) {
		Minecraft client = Minecraft.getInstance();
		if (!MatchaClientConfig.trueDarkness()
				&& client.level != null
				&& client.level.dimension().equals(Level.OVERWORLD)) {
			return 0x212631;
		}
		return color;
	}
}
