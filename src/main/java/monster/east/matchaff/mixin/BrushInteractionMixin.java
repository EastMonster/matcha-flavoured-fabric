package monster.east.matchaff.mixin;

import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.item.BrushItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Armadillo.class)
public abstract class BrushInteractionMixin {
	@Redirect(
			method = "mobInteract",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z")
	)
	private boolean matcha$acceptCustomBrushes(ItemStack stack, Object item) {
		return stack.is((Item) item)
				|| item == Items.BRUSH && stack.getItem() instanceof BrushItem;
	}
}
