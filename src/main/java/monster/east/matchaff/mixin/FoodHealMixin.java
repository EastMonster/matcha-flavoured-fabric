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
 * regeneration segments without a status icon (the healing simulation) are not
 * applied as effects, instead an independent per-food heal schedule starts
 * (see {@link FoodHealMechanics}). Icon-bearing regeneration segments (e.g.
 * baked apple's lingering 10s regeneration) stay as real effects: they show
 * their icon and refresh like vanilla potions, stacking never applies.
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
		List<MobEffectInstance> visibleAdjusted = FoodHealMechanics.onConsumed(player, regens);
		if (user.getRandom().nextFloat() >= self.probability()) {
			cir.setReturnValue(false);
			return;
		}
		boolean anyApplied = false;
		for (MobEffectInstance effect : self.effects()) {
			// The healing-simulation segments are scheduled, not applied; the
			// icon-bearing ones are applied below with their adjusted duration.
			if (!effect.getEffect().is(MobEffects.REGENERATION) && user.addEffect(new MobEffectInstance(effect))) {
				anyApplied = true;
			}
		}
		if (visibleAdjusted != null) {
			for (MobEffectInstance effect : visibleAdjusted) {
				if (user.addEffect(new MobEffectInstance(effect))) {
					anyApplied = true;
				}
			}
		}
		cir.setReturnValue(anyApplied);
	}
}
