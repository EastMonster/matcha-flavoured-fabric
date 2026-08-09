package monster.east.matchaff.mixin;

import monster.east.matchaff.FoodHealMechanics;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Food healing in the datapack is simulated with regeneration, but the effect
 * refreshes in place (duration never stacks), so eating the same food twice in
 * a row silently loses healing. This mixin intercepts the consume effect:
 * hidden high-amplifier regeneration segments (the healing simulation) are not
 * applied as effects, instead an independent per-food heal schedule starts.
 * Genuine timed regeneration is applied when its place in the vanilla hidden
 * effect chain begins (see {@link FoodHealMechanics}).
 */
@Mixin(ApplyStatusEffectsConsumeEffect.class)
public abstract class FoodHealMixin {
	@Inject(method = "apply", at = @At("HEAD"), cancellable = true)
	private void matcha$redirectRegeneration(Level level, ItemStack stack, LivingEntity user,
			CallbackInfoReturnable<Boolean> cir) {
		if (!(user instanceof ServerPlayer player)) {
			return;
		}
		ApplyStatusEffectsConsumeEffect self = (ApplyStatusEffectsConsumeEffect) (Object) this;
		List<MobEffectInstance> regens = new ArrayList<>();
		for (MobEffectInstance effect : self.effects()) {
			if (effect.getEffect().is(MobEffects.REGENERATION)) {
				regens.add(effect);
			}
		}
		if (regens.isEmpty()) {
			return; // no regeneration, keep the vanilla behaviour
		}
		if (user.getRandom().nextFloat() >= self.probability()) {
			cir.setReturnValue(false);
			return;
		}
		boolean anyApplied = FoodHealMechanics.onConsumed(player, regens);
		for (MobEffectInstance effect : self.effects()) {
			// Regeneration is handled as one ordered chain by FoodHealMechanics.
			if (!effect.getEffect().is(MobEffects.REGENERATION) && user.addEffect(new MobEffectInstance(effect))) {
				anyApplied = true;
			}
		}
		cir.setReturnValue(anyApplied);
	}
}
