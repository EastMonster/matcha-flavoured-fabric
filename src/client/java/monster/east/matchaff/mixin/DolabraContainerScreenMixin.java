package monster.east.matchaff.mixin;

import monster.east.matchaff.client.DolabraVisuals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class DolabraContainerScreenMixin {
	@Inject(method = "extractSlot", at = @At("HEAD"))
	private void matcha$beginPlayerInventorySlot(
			GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		DolabraVisuals.setRenderingPlayerInventorySlot(
				client.player != null && slot.container == client.player.getInventory());
	}

	@Inject(method = "extractSlot", at = @At("RETURN"))
	private void matcha$endPlayerInventorySlot(
			GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		DolabraVisuals.setRenderingPlayerInventorySlot(false);
	}
}
