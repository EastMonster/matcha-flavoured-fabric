package monster.east.matchaff.mixin;

import monster.east.matchaff.mechanic.BirdMechanics;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Animal.class)
public abstract class BirdFeedingMixin extends AgeableMob {
	protected BirdFeedingMixin(EntityType<? extends AgeableMob> type, Level level) { super(type, level); }
	@Shadow protected abstract void playEatingSound();

	@Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
	private void matcha$feedBird(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (!((Object) this instanceof Chicken chicken)) return;
		if (!BirdMechanics.isManaged(chicken)) return;
		ItemStack stack = player.getItemInHand(hand);
		if (!chicken.isFood(stack) || chicken.isBaby()) return;
		if (chicken.level().isClientSide()) cir.setReturnValue(InteractionResult.CONSUME);
		else if (chicken.getAge() == 0 && chicken.canFallInLove() && chicken.level() instanceof ServerLevel level) {
			this.usePlayerItem(player, hand, stack);
			this.playEatingSound();
			BirdMechanics.onFed(chicken, level);
			cir.setReturnValue(InteractionResult.SUCCESS_SERVER);
		}
	}
}
