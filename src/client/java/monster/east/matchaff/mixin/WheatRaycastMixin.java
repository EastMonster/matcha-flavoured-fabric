package monster.east.matchaff.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class WheatRaycastMixin {
	@Unique
	private static final VoxelShape MATCHA_TALL_WHEAT_SHAPE = Block.column(16, 0, 32);

	@Inject(method = "raycastHitResult", at = @At("RETURN"), cancellable = true)
	private void matcha$pickTallWheat(
			float partialTick, Entity cameraEntity, CallbackInfoReturnable<HitResult> cir) {
		LocalPlayer player = (LocalPlayer) (Object) this;
		Vec3 from = cameraEntity.getEyePosition(partialTick);
		double distance = player.blockInteractionRange();
		Vec3 to = from.add(cameraEntity.getViewVector(partialTick).scale(distance));
		BlockHitResult wheatHit = BlockGetter.traverseBlocks(
				from, to, cameraEntity.level(),
				(level, pos) -> {
					BlockPos wheatPos = matcha$matureWheatAt(level, pos);
					return wheatPos == null ? null : MATCHA_TALL_WHEAT_SHAPE.clip(from, to, wheatPos);
				},
				ignored -> null
		);
		if (wheatHit != null
				&& wheatHit.getLocation().distanceToSqr(from) < distance * distance
				&& wheatHit.getLocation().distanceToSqr(from)
						< cir.getReturnValue().getLocation().distanceToSqr(from)) {
			cir.setReturnValue(wheatHit);
		}
	}

	@Unique
	private static BlockPos matcha$matureWheatAt(BlockGetter level, BlockPos pos) {
		if (matcha$isMatureWheat(level.getBlockState(pos))) {
			return pos;
		}
		BlockPos below = pos.below();
		return matcha$isMatureWheat(level.getBlockState(below)) ? below : null;
	}

	@Unique
	private static boolean matcha$isMatureWheat(BlockState state) {
		return state.is(Blocks.WHEAT) && state.getValue(CropBlock.AGE) == CropBlock.MAX_AGE;
	}
}
