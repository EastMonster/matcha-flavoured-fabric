package monster.east.matchaff.mixin;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.class)
public abstract class BlockSoundMixin {
	@Inject(method = "getSoundType", at = @At("RETURN"), cancellable = true)
	private void matcha$restoreMaterialSound(BlockState state, CallbackInfoReturnable<SoundType> cir) {
		Block block = (Block) (Object) this;
		if (block == Blocks.TARGET) {
			cir.setReturnValue(SoundType.STONE);
		} else if (block == Blocks.PETRIFIED_OAK_SLAB) {
			cir.setReturnValue(SoundType.GRAVEL);
		}
	}
}
