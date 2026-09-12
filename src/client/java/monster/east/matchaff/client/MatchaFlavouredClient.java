package monster.east.matchaff.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.InvalidateRenderStateCallback;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import monster.east.matchaff.compat.TrinketsCompat;
import monster.east.matchaff.MatchaFlavouredFabric;
import monster.east.matchaff.network.SleepFastForwardPayload;

public final class MatchaFlavouredClient implements ClientModInitializer {
	private static final int CLOUD_TIME_SCALE = 120;
	private static boolean sleepFastForwarding;
	private static ClientLevel trackedLevel;
	private static long extraCloudTicks;
	public static final String NO_LEAF_EXTENSIONS_PACK = "matcha-flavoured:no_leaf_extensions";
	public static final String VANILLA_PREVIEW_PACK = "matcha-flavoured:vanilla_preview";
	public static final String TRINKETS_MATCHA_PACK = "matcha-flavoured:trinkets_matcha";

	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(SleepFastForwardPayload.TYPE, (payload, context) ->
				context.client().execute(() -> sleepFastForwarding = payload.active()));
		ClientTickEvents.END_CLIENT_TICK.register(MatchaFlavouredClient::tickCloudTime);
		if (FabricLoader.getInstance().isModLoaded(TrinketsCompat.MOD_ID)) {
			TrinketsCompatClient.init();
			ResourceLoader.registerBuiltinPack(
					Identifier.parse(TRINKETS_MATCHA_PACK),
					FabricLoader.getInstance().getModContainer("matcha-flavoured").orElseThrow(),
					Component.translatable("matcha.config.trinkets_matcha.pack"),
					PackActivationType.ALWAYS_ENABLED
			);
		}
		MatchaClientConfig.load();
		ClientLifecycleEvents.CLIENT_STARTED.register(MatchaFlavouredClient::refreshVanillaPreview);
		InvalidateRenderStateCallback.EVENT.register(() -> refreshVanillaPreview(Minecraft.getInstance()));
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

	public static long acceleratedCloudTime(long gameTime) {
		return gameTime + extraCloudTicks;
	}

	private static void tickCloudTime(Minecraft client) {
		if (client.level != trackedLevel) {
			trackedLevel = client.level;
			extraCloudTicks = 0;
			sleepFastForwarding = false;
		}
		if (client.level != null && sleepFastForwarding) {
			extraCloudTicks += CLOUD_TIME_SCALE - 1L;
		}
	}

	private static void refreshVanillaPreview(Minecraft client) {
		boolean active = client.getResourcePackRepository().getSelectedIds().contains(VANILLA_PREVIEW_PACK);
		boolean changed = active != MatchaFlavouredFabric.vanillaPreviewActive();
		MatchaFlavouredFabric.setVanillaPreviewActive(active);
		if (client.level != null && (active || changed)) {
			client.level.clearTintCaches();
		}
	}
}
