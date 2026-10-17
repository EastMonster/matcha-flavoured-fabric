package monster.east.matchaff.mixin;

import monster.east.matchaff.mechanic.FoodHealMechanics;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class FoodHealClearEffectsMixin {
	// Pending regeneration is absent from activeEffects, so clear it even when vanilla finds nothing.
	@Inject(method = "removeAllEffects", at = @At("HEAD"))
	private void matcha$clearPendingRegeneration(CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof ServerPlayer player) {
			FoodHealMechanics.cancelPendingRegeneration(player);
		}
	}

	@Inject(method = "removeEffect", at = @At("HEAD"))
	private void matcha$removePendingRegeneration(Holder<MobEffect> effect, CallbackInfoReturnable<Boolean> cir) {
		if (effect == MobEffects.REGENERATION && (Object) this instanceof ServerPlayer player) {
			FoodHealMechanics.cancelPendingRegeneration(player);
		}
	}
}
