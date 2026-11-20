package monster.east.matchaff.mixin;

import monster.east.matchaff.mechanic.SheepMechanics;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Sheep.class)
public abstract class SheepInteractionMixin extends Animal {
	protected SheepInteractionMixin(EntityType<? extends Animal> type, Level level) {
		super(type, level);
	}

	@Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
	private void matcha$feed(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		Sheep sheep = (Sheep) (Object) this;
		ItemStack stack = player.getItemInHand(hand);
		// Babies retain vanilla growth; adult wheat feeding must not start breeding.
		if (!stack.is(SheepMechanics.EDIBLE) || !sheep.isFood(stack) || sheep.isBaby()) return;
		if (sheep.level().isClientSide()) {
			cir.setReturnValue(InteractionResult.CONSUME);
		} else if (sheep.getAge() == 0 && sheep.canFallInLove() && sheep.level() instanceof ServerLevel level) {
			this.usePlayerItem(player, hand, stack);
			level.broadcastEntityEvent(sheep, (byte) 18);
			this.playEatingSound();
			SheepMechanics.onFed(sheep, level);
			cir.setReturnValue(InteractionResult.SUCCESS_SERVER);
		}
	}
}
