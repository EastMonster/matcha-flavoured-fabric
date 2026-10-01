package monster.east.matchaff.mixin;

import monster.east.matchaff.mechanic.GameplayMechanics;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MerchantMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MerchantScreen.class)
public abstract class MerchantScreenMixin extends AbstractContainerScreen<MerchantMenu> {
	@Shadow private int scrollOff;

	protected MerchantScreenMixin(MerchantMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 276, 166);
	}

	@Inject(method = "extractContents", at = @At("TAIL"))
	private void matcha$favoriteFoodTooltip(
			GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		int x = mouseX - (this.leftPos + 5);
		int y = mouseY - (this.topPos + 18);
		// Leave both item tooltip regions to vanilla; only label the space between them.
		if (x < 20 || x > 65 || y < 0 || y >= 7 * 20) {
			return;
		}
		int offerIndex = y / 20 + this.scrollOff;
		var offers = this.menu.getOffers();
		if (offerIndex >= offers.size() || !GameplayMechanics.isFavoriteFoodOffer(offers.get(offerIndex))) {
			return;
		}
		var food = offers.get(offerIndex).getCostA();
		Component message = Component.translatable("merchant.matcha.favorite_food", food.getStyledHoverName())
				.withStyle(ChatFormatting.GRAY);
		graphics.setTooltipForNextFrame(this.font, message, mouseX, mouseY);
	}
}
