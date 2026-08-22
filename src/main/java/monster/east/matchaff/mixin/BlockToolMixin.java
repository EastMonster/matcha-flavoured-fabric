package monster.east.matchaff.mixin;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Removes the old vanilla tool identities from the renamed blocks. */
@Mixin(Item.class)
public abstract class BlockToolMixin {
	@Inject(method = "getDestroySpeed", at = @At("HEAD"), cancellable = true)
	private void matcha$removeWrongToolSpeed(ItemStack stack, BlockState state, CallbackInfoReturnable<Float> cir) {
		if (matcha$isWrongTool(stack, state)) {
			cir.setReturnValue(1.0F);
		}
	}

	@Inject(method = "isCorrectToolForDrops", at = @At("HEAD"), cancellable = true)
	private void matcha$removeWrongToolDrops(ItemStack stack, BlockState state, CallbackInfoReturnable<Boolean> cir) {
		if (matcha$isWrongTool(stack, state)) {
			cir.setReturnValue(false);
		}
	}

	private static boolean matcha$isWrongTool(ItemStack stack, BlockState state) {
		Tool tool = stack.get(DataComponents.TOOL);
		if (tool == null) {
			return false;
		}
		boolean worksOnHay = tool.isCorrectForDrops(Blocks.HAY_BLOCK.defaultBlockState());
		boolean worksOnCobblestone = tool.isCorrectForDrops(Blocks.COBBLESTONE.defaultBlockState());
		boolean worksOnDirt = tool.isCorrectForDrops(Blocks.DIRT.defaultBlockState());
		return state.is(Blocks.TARGET) && worksOnHay && !worksOnCobblestone
				|| state.is(Blocks.PETRIFIED_OAK_SLAB) && worksOnCobblestone && !worksOnDirt;
	}
}
