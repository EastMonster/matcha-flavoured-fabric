package monster.east.matchaff.mixin.compat;

import java.util.List;
import monster.east.matchaff.mechanic.FoodHealMechanics;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.penumbra.enderscape.block.ChorusCakeRollBlock", remap = false)
public abstract class EnderscapeChorusCakeRollMixin {
	@Inject(method = "eat", at = @At("RETURN"), require = 0)
	private static void matcha$healCakeSlice(LevelAccessor level, BlockPos pos, BlockState state, Player player,
			CallbackInfoReturnable<InteractionResult> cir) {
		if (player instanceof ServerPlayer serverPlayer && cir.getReturnValue().consumesAction()) {
			// A slice has two nutrition, equivalent to one point under third-party food rules.
			FoodHealMechanics.onConsumed(serverPlayer, List.of(
					new MobEffectInstance(MobEffects.REGENERATION, 12, 2, false, false, false)));
		}
	}
}
