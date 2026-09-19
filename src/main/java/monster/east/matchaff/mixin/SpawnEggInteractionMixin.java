package monster.east.matchaff.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpawnEggItem.class)
public abstract class SpawnEggInteractionMixin {
	@Inject(method = "useOn", at = @At("RETURN"))
	private void matcha$punishSpawnerInteraction(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
		if (!cir.getReturnValue().consumesAction()
				|| !(context.getLevel() instanceof ServerLevel level)
				|| !(context.getPlayer() instanceof ServerPlayer player)
				|| !isSpawner(level.getBlockState(context.getClickedPos()))) {
			return;
		}

		if (!player.isCreative()) {
			clearSpawners(level, player.blockPosition(), 8);
			clearSpawners(level, player.blockPosition(), 16);
		}
		player.hurtServer(level, level.damageSources().generic(), 20.0F);
	}

	@Unique
    private static void clearSpawners(ServerLevel level, BlockPos center, int radius) {
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
				center.offset(radius, radius, radius))) {
			if (isSpawner(level.getBlockState(pos))) {
				level.destroyBlock(pos, true);
			}
		}
	}

	@Unique
    private static boolean isSpawner(BlockState state) {
		return state.is(Blocks.SPAWNER) || state.is(Blocks.TRIAL_SPAWNER);
	}
}
