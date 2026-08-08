package monster.east.matchaff.mixin;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Hides the cross_leaves extension panels that point at another leaf block, so
 * the out-of-bounds decoration disappears inside a canopy while the leaf
 * blocks' own cube faces keep rendering (they carry no cullface in the model
 * and are therefore never tested here). The extension segments are marked with
 * cullfaces, and the vanilla face-culling path consults
 * {@link #skipRendering} before rendering a cullface'd face; returning true
 * for a leaf neighbor culls them. Only leaf blocks are affected; a neighboring
 * leaf block itself still renders normally.
 */
@Mixin(value = LeavesBlock.class, priority = 2000)
public abstract class LeavesCullMixin extends Block {
	protected LeavesCullMixin(Properties properties) {
		super(properties);
	}

	@Override
	public boolean skipRendering(BlockState state, BlockState neighborState, Direction direction) {
		return neighborState.getBlock() instanceof LeavesBlock;
	}
}
