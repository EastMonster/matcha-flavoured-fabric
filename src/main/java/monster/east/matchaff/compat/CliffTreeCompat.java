package monster.east.matchaff.compat;

import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

public final class CliffTreeCompat {
	private CliffTreeCompat() {
	}

	public static void init() {
		FabricLoader loader = FabricLoader.getInstance();
		if (loader.isModLoaded("clifftree")) {
			var matcha = loader.getModContainer("matcha-flavoured").orElseThrow();
			ResourceLoader.registerBuiltinPack(
					Identifier.fromNamespaceAndPath("matcha", "clifftree_compat"),
					matcha,
					PackActivationType.ALWAYS_ENABLED
			);
			if (loader.isModLoaded("tectonic")) {
				ResourceLoader.registerBuiltinPack(
						Identifier.fromNamespaceAndPath("matcha", "clifftree_tectonic_compat"),
						matcha,
						PackActivationType.ALWAYS_ENABLED
				);
			}
		}
	}
}
