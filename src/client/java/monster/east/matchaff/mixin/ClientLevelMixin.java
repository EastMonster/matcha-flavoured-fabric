package monster.east.matchaff.mixin;

import monster.east.matchaff.MatchaFlavouredFabric;
import monster.east.matchaff.client.VanillaWaterColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Cursor3D;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
	@Inject(method = "calculateBlockTint", at = @At("HEAD"), cancellable = true)
	private void matcha$restoreVanillaWaterColor(
			BlockPos pos, ColorResolver colorResolver, CallbackInfoReturnable<Integer> cir) {
		if (!MatchaFlavouredFabric.vanillaPreviewActive() || colorResolver != BiomeColors.WATER_COLOR_RESOLVER) {
			return;
		}

		ClientLevel level = (ClientLevel) (Object) this;
		int distance = Minecraft.getInstance().options.biomeBlendRadius().get();
		if (distance == 0) {
			cir.setReturnValue(VanillaWaterColor.get(level.getBiome(pos)));
			return;
		}

		int count = (distance * 2 + 1) * (distance * 2 + 1);
		int red = 0;
		int green = 0;
		int blue = 0;
		Cursor3D cursor = new Cursor3D(
				pos.getX() - distance, pos.getY(), pos.getZ() - distance,
				pos.getX() + distance, pos.getY(), pos.getZ() + distance);
		BlockPos.MutableBlockPos nextPos = new BlockPos.MutableBlockPos();
		while (cursor.advance()) {
			nextPos.set(cursor.nextX(), cursor.nextY(), cursor.nextZ());
			int color = VanillaWaterColor.get(level.getBiome(nextPos));
			red += ARGB.red(color);
			green += ARGB.green(color);
			blue += ARGB.blue(color);
		}
		cir.setReturnValue(ARGB.color(red / count, green / count, blue / count));
	}

}
