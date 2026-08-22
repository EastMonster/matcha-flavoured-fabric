package monster.east.matchaff.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Restores the material hardness of the blocks repurposed by the datapack. */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockHardnessMixin {
	@Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
	private void matcha$restoreMaterialHardness(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
		BlockState state = (BlockState) (Object) this;
		if (state.is(Blocks.TARGET)) {
			cir.setReturnValue(Blocks.STONE.defaultBlockState().getDestroySpeed(level, pos));
		} else if (state.is(Blocks.PETRIFIED_OAK_SLAB)) {
			cir.setReturnValue(Blocks.DIRT.defaultBlockState().getDestroySpeed(level, pos));
		}
	}

	@Inject(method = "requiresCorrectToolForDrops", at = @At("RETURN"), cancellable = true)
	private void matcha$restoreMaterialToolRequirement(CallbackInfoReturnable<Boolean> cir) {
		BlockState state = (BlockState) (Object) this;
		if (state.is(Blocks.TARGET)) {
			cir.setReturnValue(true);
		} else if (state.is(Blocks.PETRIFIED_OAK_SLAB)) {
			cir.setReturnValue(false);
		}
	}
}
