package monster.east.matchaff.client;

import monster.east.matchaff.network.BlazeRodDropsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.permissions.Permissions;

public final class MatchaConfigScreen extends Screen {
	private final Screen parent;
	private Button blazeRodDropsButton;
	private boolean blazeRodDrops;
	private boolean blazeRodDropsSynced;

	public MatchaConfigScreen(Screen parent) {
		super(Component.translatable("matcha.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		blazeRodDropsSynced = false;
		addRenderableWidget(new StringWidget(
				(width - font.width(title)) / 2, 25, font.width(title), 20, title, font));

		addRenderableWidget(Button.builder(vanillaPreviewLabel(), this::toggleVanillaPreview)
				.bounds(width / 2 - 100, height / 2 - 60, 200, 20)
				.tooltip(Tooltip.create(Component.translatable("matcha.config.vanilla_preview.tooltip")))
				.build());

		addRenderableWidget(Button.builder(leafExtensionsLabel(), this::toggleLeafExtensions)
				.bounds(width / 2 - 100, height / 2 - 34, 200, 20)
				.tooltip(Tooltip.create(Component.translatable("matcha.config.leaf_extensions.tooltip")))
				.build());

		addRenderableWidget(Button.builder(trueDarknessLabel(), button -> {
					MatchaClientConfig.toggleTrueDarkness();
					button.setMessage(trueDarknessLabel());
				})
				.bounds(width / 2 - 100, height / 2 - 8, 200, 20)
				.tooltip(Tooltip.create(Component.translatable("matcha.config.true_darkness.tooltip")))
				.build());

		addRenderableWidget(Button.builder(dolabraVisualsLabel(), button -> {
					MatchaClientConfig.toggleDolabraVisuals();
					button.setMessage(dolabraVisualsLabel());
				})
				.bounds(width / 2 - 100, height / 2 + 18, 200, 20)
				.tooltip(Tooltip.create(Component.translatable("matcha.config.dolabra_visuals.tooltip")))
				.build());

		blazeRodDropsButton = addRenderableWidget(Button.builder(blazeRodDropsLabel(), button -> {
					blazeRodDropsSynced = false;
					button.active = false;
					ClientPlayNetworking.send(new BlazeRodDropsPayload.Request(true, !blazeRodDrops));
				})
				.bounds(width / 2 - 100, height / 2 + 44, 200, 20)
				.tooltip(Tooltip.create(Component.translatable("matcha.config.blaze_rod_drops.tooltip")))
				.build());
		blazeRodDropsButton.active = false;
		if (ClientPlayNetworking.canSend(BlazeRodDropsPayload.Request.TYPE)) {
			ClientPlayNetworking.send(new BlazeRodDropsPayload.Request(false, false));
		}

		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
				.bounds(width / 2 - 100, height / 2 + 78, 200, 20)
				.build());
	}

	void updateBlazeRodDrops(boolean enabled) {
		blazeRodDrops = enabled;
		blazeRodDropsSynced = true;
		blazeRodDropsButton.setMessage(blazeRodDropsLabel());
		updateBlazeRodDropsPermission();
	}

	@Override
	public void tick() {
		super.tick();
		updateBlazeRodDropsPermission();
	}

	private void updateBlazeRodDropsPermission() {
		Minecraft client = Minecraft.getInstance();
		blazeRodDropsButton.active = blazeRodDropsSynced && client.player != null
				&& ClientPlayNetworking.canSend(BlazeRodDropsPayload.Request.TYPE)
				&& client.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}

	private Component blazeRodDropsLabel() {
		return CommonComponents.optionStatus(Component.translatable("matcha.config.blaze_rod_drops"), blazeRodDrops);
	}

	private void toggleVanillaPreview(Button button) {
		Minecraft client = Minecraft.getInstance();
		PackRepository packs = client.getResourcePackRepository();
		String packId = MatchaFlavouredClient.VANILLA_PREVIEW_PACK;
		boolean changed = packs.getSelectedIds().contains(packId)
				? packs.removePack(packId)
				: packs.addPack(packId);
		if (changed) {
			client.options.updateResourcePacks(packs);
			button.setMessage(vanillaPreviewLabel());
		}
	}

	private void toggleLeafExtensions(Button button) {
		Minecraft client = Minecraft.getInstance();
		PackRepository packs = client.getResourcePackRepository();
		String packId = MatchaFlavouredClient.NO_LEAF_EXTENSIONS_PACK;
		boolean changed = packs.getSelectedIds().contains(packId)
				? packs.removePack(packId)
				: packs.addPack(packId);
		if (changed) {
			client.options.updateResourcePacks(packs);
			button.setMessage(leafExtensionsLabel());
		}
	}

	private static Component leafExtensionsLabel() {
		boolean enabled = !Minecraft.getInstance().getResourcePackRepository().getSelectedIds()
				.contains(MatchaFlavouredClient.NO_LEAF_EXTENSIONS_PACK);
		return CommonComponents.optionStatus(Component.translatable("matcha.config.leaf_extensions"), enabled);
	}

	private static Component vanillaPreviewLabel() {
		return CommonComponents.optionStatus(
				Component.translatable("matcha.config.vanilla_preview"),
				Minecraft.getInstance().getResourcePackRepository().getSelectedIds()
						.contains(MatchaFlavouredClient.VANILLA_PREVIEW_PACK));
	}

	private static Component trueDarknessLabel() {
		return CommonComponents.optionStatus(
				Component.translatable("matcha.config.true_darkness"), MatchaClientConfig.trueDarkness());
	}

	private static Component dolabraVisualsLabel() {
		return CommonComponents.optionStatus(
				Component.translatable("matcha.config.dolabra_visuals"),
				MatchaClientConfig.dolabraVisuals());
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreenAndShow(parent);
	}
}
