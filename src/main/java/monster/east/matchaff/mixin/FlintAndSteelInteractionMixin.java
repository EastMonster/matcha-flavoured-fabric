package monster.east.matchaff.mixin;

import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.TntBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({TntBlock.class, SulfurCube.class, CandleCakeBlock.class})
public abstract class FlintAndSteelInteractionMixin {
	@Redirect(
			method = {"useItemOn", "mobInteract"},
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z")
	)
	private boolean matcha$acceptCustomFlintAndSteel(ItemStack stack, Object item) {
		// SulfurCube checks ignition items and shears at the same call site, so one redirect must handle both.
		return stack.is((Item) item)
				|| item == Items.FLINT_AND_STEEL && stack.getItem() instanceof FlintAndSteelItem
				|| item == Items.SHEARS && stack.getItem() instanceof ShearsItem;
	}
}
