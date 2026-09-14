package monster.east.matchaff.mixin;

import monster.east.matchaff.mechanic.FishingStatsMechanics;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;
import net.minecraft.resources.Identifier;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AdvancementWidget.class)
public abstract class AdvancementWidgetMixin {
	@Unique
	private static final Identifier ANGLERS_ALMANAC_ROOT =
			Identifier.fromNamespaceAndPath("matcha", "anglers_almanac/root");

	@Shadow @Final private AdvancementNode advancementNode;
	@Shadow @Final private ItemStack icon;
	@Shadow @Final private Minecraft minecraft;
	@Shadow @Final private int x;
	@Shadow @Final private int y;

	@Inject(
			method = "extractRenderState",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fakeItem(Lnet/minecraft/world/item/ItemStack;II)V",
					shift = At.Shift.AFTER
			)
	)
	private void matcha$renderFishCount(GuiGraphicsExtractor graphics, int xo, int yo, CallbackInfo ci) {
		this.matcha$drawFishCount(graphics, xo, yo);
	}

	@Unique
	private void matcha$drawFishCount(GuiGraphicsExtractor graphics, int xo, int yo) {
		if (this.minecraft.player == null
				|| !this.advancementNode.root().holder().id().equals(ANGLERS_ALMANAC_ROOT)) {
			return;
		}

		StatsCounter stats = this.minecraft.player.getStats();
		int count = stats.getValue(FishingStatsMechanics.FISHING_CAUGHT_BY_ITEM, this.icon.getItem());
		if (count <= 0) {
			return;
		}

		String text = count > 999 ? "999+" : Integer.toString(count);
		int width = 0;
		for (int character = 0; character < text.length(); character++) {
			width += this.matcha$pixelGlyphWidth(text.charAt(character));
		}
		int left = xo + this.x + 25 - width;
		int top = yo + this.y + 16;
		this.matcha$drawPixelCount(graphics, text, left + 1, top + 1, -16777216);
		this.matcha$drawPixelCount(graphics, text, left, top, -1);
	}

	@Unique
	private void matcha$drawPixelCount(
			GuiGraphicsExtractor graphics, String text, int left, int top, int color) {
		int characterLeft = left;
		for (int character = 0; character < text.length(); character++) {
			char value = text.charAt(character);
			String glyph = this.matcha$pixelGlyph(value);
			int glyphLeft = 4 - this.matcha$pixelGlyphWidth(value);
			for (int row = 0; row < 5; row++) {
				for (int column = 0; column < 4; column++) {
					if (glyph.charAt(row * 4 + column) == '1') {
						int x = characterLeft + column - glyphLeft;
						int y = top + row;
						graphics.fill(x, y, x + 1, y + 1, color);
					}
				}
			}
			characterLeft += this.matcha$pixelGlyphWidth(value);
		}
	}

	@Unique
	private int matcha$pixelGlyphWidth(char character) {
		return character == '1' || character == '+' ? 3 : 4;
	}

	@Unique
	private String matcha$pixelGlyph(char character) {
		return switch (character) {
			case '0' -> "01101001100110010110";
			case '1' -> "00100110001000100111";
			case '2' -> "01101001001001001111";
			case '3' -> "11100001011000011110";
			case '4' -> "00100110101011110010";
			case '5' -> "11111000111000011110";
			case '6' -> "01101000111010010110";
			case '7' -> "11110001001001000100";
			case '8' -> "01101001011010010110";
			case '9' -> "01101001011100010110";
			case '+' -> "00000010011100100000";
			default -> "00000000000000000000";
		};
	}
}
