package monster.east.matchaff.mixin;

import monster.east.matchaff.mechanic.BirdMechanics;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Chicken.class)
public abstract class ChickenForagingMixin extends Animal {
	protected ChickenForagingMixin(EntityType<? extends Animal> type, Level level) { super(type, level); }

	@Inject(method = "registerGoals", at = @At("TAIL"))
	private void matcha$foraging(CallbackInfo ci) {
		Chicken chicken = (Chicken) (Object) this;
		if (!BirdMechanics.isManaged(chicken)) return;
		for (var wrapped : this.goalSelector.getAvailableGoals()) {
			if (wrapped.getGoal() instanceof FollowParentGoal followParent) {
				followParent.getFlags().add(Goal.Flag.MOVE);
			}
		}
		this.goalSelector.addGoal(-1, new BirdMechanics.ForagingGoal(chicken));
	}
}
