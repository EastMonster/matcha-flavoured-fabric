package monster.east.matchaff.client;

import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class PauseMenuSettings {
	private static final Logger LOGGER = LoggerFactory.getLogger("matcha");

	private PauseMenuSettings() {
	}

	static void afterInit(Minecraft client, Screen screen, int width, int height) {
		if (!(screen instanceof PauseScreen pauseScreen) || !pauseScreen.showsPauseMenu()) {
			return;
		}
		var widgets = Screens.getWidgets(screen);
		var reportingButton = widgets.stream()
				.filter(widget -> widget.getMessage().getContents() instanceof TranslatableContents contents
						&& contents.getKey().equals("menu.playerReporting"))
				.findFirst().orElse(null);
		if (reportingButton == null) {
			LOGGER.warn("Could not locate pause-menu reporting button; widgets: {}",
					widgets.stream().map(widget -> widget.getClass().getName() + ": " + widget.getMessage()).toList());
			return;
		}
		int anchorX = reportingButton.getX();
		int rowY = reportingButton.getY();
		for (var widget : widgets) {
			if (widget.getY() == rowY && widget.getWidth() == 20 && widget.getHeight() == 20) {
				widget.setX(widget.getX() + (widget.getX() <= anchorX ? -12 : 12));
			}
		}
		var button = SpriteIconButton.builder(Component.translatable("matcha.config.title"),
				ignored -> client.setScreenAndShow(new MatchaConfigScreen(screen)), true)
				.width(20)
				.sprite(Identifier.fromNamespaceAndPath("matcha", "pause_menu/settings"), 15, 15)
				.withTootip()
				.build();
		button.setPosition(anchorX + 12, rowY);
		widgets.add(widgets.indexOf(reportingButton) + 1, button);
	}
}
