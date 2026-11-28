package monster.east.matchaff.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.UnaryOperator;
import monster.east.matchaff.compat.EnderscapeCompat;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.penumbra.enderscape.manager.VoidManager", remap = false)
public abstract class EnderscapeVoidManagerMixin {
	@WrapOperation(
			method = "afterLivingEntityDamage",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getActiveEffects()Ljava/util/Collection;"),
			remap = false,
			require = 0
	)
	private static Collection<MobEffectInstance> matcha$snapshotEffects(LivingEntity entity,
			Operation<Collection<MobEffectInstance>> original) {
		// Enderscape removes unsupported effects while iterating this collection.
		return new ArrayList<>(original.call(entity));
	}

	@Inject(method = "modifyVoidedHealth", at = @At("HEAD"), cancellable = true, require = 0)
	private static void matcha$preserveCrystalHeartVoid(LivingEntity entity, UnaryOperator<Float> modifier, CallbackInfo ci) {
		// Crystal Heart uses death protection internally; its self-damage must not purify voided health.
		if (EnderscapeCompat.isUsingCrystalHeart(entity)) {
			ci.cancel();
		}
	}
}
