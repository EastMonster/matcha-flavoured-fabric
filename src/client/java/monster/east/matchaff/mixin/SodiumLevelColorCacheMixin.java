package monster.east.matchaff.mixin;

import monster.east.matchaff.MatchaFlavouredFabric;
import monster.east.matchaff.client.VanillaWaterColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.IdentityHashMap;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.world.biome.LevelColorCache")
public abstract class SodiumLevelColorCacheMixin {
	@Unique
	private final IdentityHashMap<Biome, Integer> matcha$waterColors = new IdentityHashMap<>();

	@Redirect(
			method = "updateColorBuffers",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/ColorResolver;getColor(Lnet/minecraft/world/level/biome/Biome;DD)I"),
			require = 0)
	private int matcha$restoreVanillaWaterColor(
			ColorResolver resolver, Biome biome, double x, double z) {
		if (!MatchaFlavouredFabric.vanillaPreviewActive() || resolver != BiomeColors.WATER_COLOR_RESOLVER) {
			return resolver.getColor(biome, x, z);
		}

		Minecraft client = Minecraft.getInstance();
		return client.level == null
				? resolver.getColor(biome, x, z)
				: this.matcha$waterColor(client, biome);
	}

	@Unique
	private int matcha$waterColor(Minecraft client, Biome biome) {
		Integer cached = this.matcha$waterColors.get(biome);
		if (cached != null) {
			return cached;
		}

		int color = VanillaWaterColor.get(client.level, biome);
		this.matcha$waterColors.put(biome, color);
		return color;
	}
}
