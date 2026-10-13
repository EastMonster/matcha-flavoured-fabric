package monster.east.matchaff.mixin;

import monster.east.matchaff.mechanic.EffectsMechanics;
import monster.east.matchaff.mechanic.FoodHealMechanics;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class HungerRegenerationMixin {
	@Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true)
	private void matcha$blockRegeneration(MobEffectInstance effect, CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof ServerPlayer player
				&& effect.getEffect() == MobEffects.REGENERATION && EffectsMechanics.isHealingBlocked(player)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
			at = @At("RETURN"))
	private void matcha$cancelHealing(MobEffectInstance effect, Entity source, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && effect.getEffect() == MobEffects.HUNGER
				&& (Object) this instanceof ServerPlayer player && EffectsMechanics.isHealingBlocked(player)) {
			player.removeEffect(MobEffects.REGENERATION);
			FoodHealMechanics.cancelHealing(player);
		}
	}
}
