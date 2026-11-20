package monster.east.matchaff.mixin;

import monster.east.matchaff.client.DolabraVisuals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.inventory.SlotWidget;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SlotWidget.class)
public abstract class DolabraContainerScreenMixin {
	@Shadow @Final private Slot slot;

	@Inject(method = "extractWidgetRenderState", at = @At("HEAD"))
	private void matcha$beginPlayerInventorySlot(
			GuiGraphicsExtractor graphics, CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		DolabraVisuals.setRenderingPlayerInventorySlot(
				client.player != null && slot.container == client.player.getInventory());
	}

	@Inject(method = "extractWidgetRenderState", at = @At("RETURN"))
	private void matcha$endPlayerInventorySlot(
			GuiGraphicsExtractor graphics, CallbackInfo ci) {
		DolabraVisuals.setRenderingPlayerInventorySlot(false);
	}
}
