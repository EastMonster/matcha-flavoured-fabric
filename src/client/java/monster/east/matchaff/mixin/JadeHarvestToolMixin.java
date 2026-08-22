package monster.east.matchaff.mixin;

import com.google.common.collect.ImmutableList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Removes the repurposed blocks' old vanilla tools from Jade's harvest hint. */
@Mixin(targets = "snownee.jade.addon.harvest.HarvestToolProvider")
public abstract class JadeHarvestToolMixin {
	@Inject(method = "getTool", at = @At("RETURN"), cancellable = true, require = 0)
	private static void matcha$removeWrongTools(BlockState state, Level level, BlockPos pos,
			CallbackInfoReturnable<ImmutableList<ItemStack>> cir) {
		if (!state.is(Blocks.TARGET) && !state.is(Blocks.PETRIFIED_OAK_SLAB)) {
			return;
		}
		cir.setReturnValue(ImmutableList.copyOf(cir.getReturnValue().stream()
				.filter(stack -> !matcha$isWrongTool(stack, state))
				.toList()));
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
