package monster.east.matchaff.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.PackRepository;

public final class MatchaConfigScreen extends Screen {
	private final Screen parent;

	public MatchaConfigScreen(Screen parent) {
		super(Component.translatable("matcha.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		addRenderableWidget(new StringWidget(
				(width - font.width(title)) / 2, 25, font.width(title), 20, title, font));

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

		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
				.bounds(width / 2 - 100, height / 2 + 26, 200, 20)
				.build());
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

	private static Component trueDarknessLabel() {
		return CommonComponents.optionStatus(
				Component.translatable("matcha.config.true_darkness"), MatchaClientConfig.trueDarkness());
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreenAndShow(parent);
	}
}
