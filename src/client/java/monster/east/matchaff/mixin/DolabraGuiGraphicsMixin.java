package monster.east.matchaff.mixin;

import monster.east.matchaff.client.DolabraVisuals;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphicsExtractor.class)
public abstract class DolabraGuiGraphicsMixin {
	@Inject(method = "item(Lnet/minecraft/world/item/ItemStack;III)V", at = @At("HEAD"))
	private void matcha$beginPlayerInventoryItem(ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
		if (DolabraVisuals.renderingPlayerInventorySlot()) {
			DolabraVisuals.beginGuiItemRender(stack);
		}
	}

	@Inject(method = "item(Lnet/minecraft/world/item/ItemStack;III)V", at = @At("RETURN"))
	private void matcha$endPlayerInventoryItem(ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
		if (DolabraVisuals.renderingPlayerInventorySlot()) {
			DolabraVisuals.endGuiItemRender();
		}
	}
}
