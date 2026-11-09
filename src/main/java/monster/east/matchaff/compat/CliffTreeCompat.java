package monster.east.matchaff.compat;

import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

public final class CliffTreeCompat {
	private CliffTreeCompat() {
	}

	public static void init() {
		if (FabricLoader.getInstance().isModLoaded("clifftree")) {
			ResourceLoader.registerBuiltinPack(
					Identifier.fromNamespaceAndPath("matcha", "clifftree_compat"),
					FabricLoader.getInstance().getModContainer("matcha-flavoured").orElseThrow(),
					PackActivationType.ALWAYS_ENABLED
			);
		}
	}
}
