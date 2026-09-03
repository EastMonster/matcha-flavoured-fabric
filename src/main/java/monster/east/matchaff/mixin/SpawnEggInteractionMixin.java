package monster.east.matchaff.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
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
				|| !level.getBlockState(context.getClickedPos()).is(Blocks.SPAWNER)) {
			return;
		}

		if (!player.isCreative()) {
			clearSpawners(level, player.blockPosition(), 8);
			clearSpawners(level, player.blockPosition(), 16);
		}
		player.hurtServer(level, level.damageSources().generic(), 20.0F);
	}

	private static void clearSpawners(ServerLevel level, BlockPos center, int radius) {
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
				center.offset(radius, radius, radius))) {
			if (level.getBlockState(pos).is(Blocks.SPAWNER)) {
				level.destroyBlock(pos, true);
			}
		}
	}
}
