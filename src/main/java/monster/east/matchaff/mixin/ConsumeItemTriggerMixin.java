package monster.east.matchaff.mixin;

import monster.east.matchaff.mechanic.EffectsMechanics;
import monster.east.matchaff.mechanic.GameplayMechanics;
import net.minecraft.advancements.triggers.ConsumeItemTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shares vanilla's consume-item entry point, before the stack is consumed. */
@Mixin(ConsumeItemTrigger.class)
public abstract class ConsumeItemTriggerMixin {
	@Inject(method = "trigger(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"))
	private void matcha$startAura(ServerPlayer player, ItemStack stack, CallbackInfo ci) {
		EffectsMechanics.onConsumed(player, stack);
		GameplayMechanics.onAmnesticConsumed(player, stack);
	}
}
