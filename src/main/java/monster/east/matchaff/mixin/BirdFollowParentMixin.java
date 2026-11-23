package monster.east.matchaff.mixin;

import monster.east.matchaff.mechanic.BirdMechanics;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

@Mixin(FollowParentGoal.class)
public abstract class BirdFollowParentMixin {
	@Shadow @Final private Animal animal;

	@ModifyVariable(method = "canUse", at = @At("STORE"), ordinal = 0)
	private List<? extends Animal> matcha$sameSpecies(List<? extends Animal> parents) {
		if (!(animal instanceof Chicken chicken) || !BirdMechanics.isManaged(chicken)) return parents;
		return parents.stream().filter(parent -> parent.getType() == animal.getType()).toList();
	}
}
