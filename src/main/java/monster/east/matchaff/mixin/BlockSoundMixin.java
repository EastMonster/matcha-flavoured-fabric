package monster.east.matchaff.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.sounds.BlockSoundSet;
import net.minecraft.world.level.block.sounds.BlockSoundSets;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(BlockBehaviour.class)
public abstract class BlockSoundMixin {
	@Inject(method = "getSounds", at = @At("RETURN"), cancellable = true)
	private void matcha$restoreMaterialSound(BlockState state, CallbackInfoReturnable<Optional<ResourceKey<BlockSoundSet>>> cir) {
		Block block = (Block) (Object) this;
		if (block == Blocks.TARGET) {
			cir.setReturnValue(Optional.of(BlockSoundSets.STONE));
		} else if (block == Blocks.PETRIFIED_OAK_SLAB) {
			cir.setReturnValue(Optional.of(BlockSoundSets.GRAVEL));
		}
	}
}
