package monster.east.matchaff.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import monster.east.matchaff.MatchaFlavouredFabric;

public final class MatchaFlavouredClient implements ClientModInitializer {
	public static final String NO_LEAF_EXTENSIONS_PACK = "matcha-flavoured:no_leaf_extensions";
	public static final String VANILLA_PREVIEW_PACK = "matcha-flavoured:vanilla_preview";

	@Override
	public void onInitializeClient() {
		MatchaClientConfig.load();
		ClientTickEvents.END_CLIENT_TICK.register(client ->
				MatchaFlavouredFabric.setVanillaPreviewActive(client.getResourcePackRepository().getSelectedIds()
						.contains(VANILLA_PREVIEW_PACK)));
		ResourceLoader.registerBuiltinPack(
				Identifier.parse(NO_LEAF_EXTENSIONS_PACK),
				FabricLoader.getInstance().getModContainer("matcha-flavoured").orElseThrow(),
				Component.translatable("matcha.config.no_leaf_extensions.pack"),
				PackActivationType.NORMAL
		);
		ResourceLoader.registerBuiltinPack(
				Identifier.parse(VANILLA_PREVIEW_PACK),
				FabricLoader.getInstance().getModContainer("matcha-flavoured").orElseThrow(),
				Component.translatable("matcha.config.vanilla_preview.pack"),
				PackActivationType.NORMAL
		);
	}
}
