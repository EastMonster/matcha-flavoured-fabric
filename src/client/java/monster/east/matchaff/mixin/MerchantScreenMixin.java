package monster.east.matchaff.mixin;

import monster.east.matchaff.mechanic.GameplayMechanics;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.gui.screens.inventory.MerchantScreen$TradeOfferButton")
public abstract class MerchantScreenMixin {
	@Shadow @Final private MerchantOffer offer;

	@Inject(method = "extractTooltip", at = @At("TAIL"))
	private void matcha$favoriteFoodTooltip(GuiGraphicsExtractor graphics, CallbackInfo ci) {
		var button = (AbstractWidget) (Object) this;
		if (!button.isHovered() || !GameplayMechanics.isFavoriteFoodOffer(this.offer)) {
			return;
		}
		int x = graphics.mouseX() - button.getX();
		// Leave both item tooltip regions to vanilla; only label the space between them.
		if (x < 20 || x > 65) {
			return;
		}
		Component message = Component.translatable("merchant.matcha.favorite_food", this.offer.getCostA().getStyledHoverName())
				.withStyle(ChatFormatting.GRAY);
		graphics.setTooltipForNextFrame(Minecraft.getInstance().font, message, graphics.mouseX(), graphics.mouseY());
	}
}
