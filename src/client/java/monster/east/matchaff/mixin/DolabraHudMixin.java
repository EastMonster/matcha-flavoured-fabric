package monster.east.matchaff.mixin;

import monster.east.matchaff.client.DolabraVisuals;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Hud.class)
public abstract class DolabraHudMixin {
	@Redirect(
			method = "extractSlot",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;III)V"
			)
	)
	private void matcha$rotateHotbarItem(
			GuiGraphicsExtractor graphics, LivingEntity owner, ItemStack stack, int x, int y, int seed) {
		DolabraVisuals.renderHotbarItem(graphics, owner, stack, x, y, seed);
	}
}
