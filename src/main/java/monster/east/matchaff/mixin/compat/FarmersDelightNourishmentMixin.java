package monster.east.matchaff.mixin.compat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "vectorwing.farmersdelight.common.effect.NourishmentEffect", remap = false)
public abstract class FarmersDelightNourishmentMixin {
	@Inject(method = "shouldApplyEffectTickThisTick", at = @At("HEAD"), cancellable = true, require = 0)
	private void matcha$comfortInterval(int duration, int amplifier, CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(duration % 80 == 0);
	}

	@Inject(method = "applyEffectTick", at = @At("HEAD"), cancellable = true, require = 0)
	private void matcha$comfortHealing(ServerLevel level, LivingEntity entity, int amplifier,
			CallbackInfoReturnable<Boolean> cir) {
		if (!entity.hasEffect(MobEffects.REGENERATION) && entity.getHealth() < entity.getMaxHealth()) {
			entity.heal(1.0F);
		}
		cir.setReturnValue(true);
	}
}
