package monster.east.matchaff.mixin;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CropBlock.class)
public abstract class WheatShapeMixin {
	@Unique
	private static final VoxelShape MATCHA_TALL_WHEAT_SHAPE = Block.column(16, 0, 32);

	@Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
	private void matcha$tallMatureWheat(
			BlockState state, BlockGetter level, BlockPos pos, CollisionContext context,
			CallbackInfoReturnable<VoxelShape> cir) {
		if (state.is(Blocks.WHEAT) && state.getValue(CropBlock.AGE) == CropBlock.MAX_AGE) {
			cir.setReturnValue(MATCHA_TALL_WHEAT_SHAPE);
		}
	}
}
