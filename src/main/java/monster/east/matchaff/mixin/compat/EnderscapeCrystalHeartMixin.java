package monster.east.matchaff.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import monster.east.matchaff.compat.EnderscapeCompat;
import monster.east.matchaff.item.CrystalHeartItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DeathProtection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class EnderscapeCrystalHeartMixin {
	@WrapOperation(
			method = "checkTotemDeathProtection",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/component/DeathProtection;applyEffects(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)V"),
			require = 0
	)
	private void matcha$applyCrystalHeartVoidResistance(DeathProtection protection, ItemStack stack,
			LivingEntity entity, Operation<Void> original) {
		original.call(protection, stack, entity);
		if (!entity.level().isClientSide() && stack.getItem() instanceof CrystalHeartItem
				&& !EnderscapeCompat.isUsingCrystalHeart(entity)) {
			BuiltInRegistries.MOB_EFFECT.get(Identifier.fromNamespaceAndPath("enderscape", "void_resistance"))
					.ifPresent(effect -> entity.addEffect(new MobEffectInstance(effect, 200)));
		}
	}
}
