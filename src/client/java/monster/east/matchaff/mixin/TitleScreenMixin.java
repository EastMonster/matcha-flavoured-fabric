package monster.east.matchaff.mixin;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {
	@Unique
	private static final Component MATCHA$VERSION = Component.literal("Matcha Flavoured v" + FabricLoader.getInstance()
			.getModContainer("matcha-flavoured").orElseThrow().getMetadata().getVersion().getFriendlyString()
			.replaceFirst("\\+.*$", "")
			.replace("-alpha.", " Alpha ")
			.replace("-beta.", " Beta ")
			.replace("-r.", " Release "));
	@Unique
	private int matcha$versionColor = 0xFFFFFFFF;

	@ModifyArg(
		method = "extractRenderState",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"),
		index = 3
	)
	private int matcha$moveVanillaVersion(int y) {
		return y - 10;
	}

	@ModifyArg(
		method = "extractRenderState",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"),
		index = 4
	)
	private int matcha$captureVersionColor(int color) {
		matcha$versionColor = color;
		return color;
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void matcha$renderVersion(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
			CallbackInfo ci) {
		graphics.text(((TitleScreen) (Object) this).getFont(), MATCHA$VERSION, 2, graphics.guiHeight() - 10,
				matcha$versionColor);
	}
}
