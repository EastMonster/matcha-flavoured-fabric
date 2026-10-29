package monster.east.matchaff.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CakeBlock.class)
public abstract class CakeBlockMixin {
	@Inject(method = "eat", at = @At("RETURN"))
	private static void matcha$applyCakeEffects(LevelAccessor level, BlockPos pos, BlockState state, Player player,
			CallbackInfoReturnable<InteractionResult> cir) {
		if (player instanceof ServerPlayer serverPlayer && cir.getReturnValue().consumesAction()) {
			// Apply immediately so multiple slices in one tick each heal.
			MobEffects.INSTANT_HEALTH.value().applyInstantaneousEffect(serverPlayer.level(), null, null, serverPlayer, 0, 1.0);
			serverPlayer.addEffect(new MobEffectInstance(MobEffects.HEALTH_BOOST, 3600, 1, false, false));
		}
	}
}
