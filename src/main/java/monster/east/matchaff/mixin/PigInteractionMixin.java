package monster.east.matchaff.mixin;

import monster.east.matchaff.mechanic.PigMechanics;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Pig.class)
public abstract class PigInteractionMixin extends Animal {
	protected PigInteractionMixin(EntityType<? extends Animal> type, Level level) {
		super(type, level);
	}

	@Inject(method = "registerGoals", at = @At("TAIL"))
	private void matcha$registerForagingGoal(CallbackInfo ci) {
		this.goalSelector.addGoal(-1, new PigMechanics.ForagingGoal((Pig) (Object) this));
	}

	@Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
	private void matcha$feedOrWash(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		Pig pig = (Pig) (Object) this;
		InteractionResult result = PigMechanics.wash(pig, player, hand);
		if (result.consumesAction()) {
			cir.setReturnValue(result);
			return;
		}
		ItemStack stack = player.getItemInHand(hand);
		// Baby growth stays vanilla; only the two golden foods may start breeding.
		if (!pig.isFood(stack) || pig.isBaby() || stack.is(Items.GOLDEN_CARROT) || stack.is(Items.GOLDEN_APPLE)) return;
		if (pig.level().isClientSide()) {
			cir.setReturnValue(InteractionResult.CONSUME);
		} else if (pig.getAge() == 0 && pig.canFallInLove() && pig.level() instanceof ServerLevel level) {
			this.usePlayerItem(player, hand, stack);
			PigMechanics.onFed(pig, level);
			cir.setReturnValue(InteractionResult.SUCCESS_SERVER);
		}
	}
}
