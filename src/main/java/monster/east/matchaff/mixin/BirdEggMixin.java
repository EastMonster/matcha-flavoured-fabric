package monster.east.matchaff.mixin;

import monster.east.matchaff.mechanic.BirdMechanics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ThrownEgg.class)
public abstract class BirdEggMixin {
	@Redirect(method = "onHit", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/EntityType;create(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/EntitySpawnReason;)Lnet/minecraft/world/entity/Entity;"))
	private Entity matcha$hatchBird(EntityType<?> type, Level level, EntitySpawnReason reason) {
		return BirdMechanics.hatchType(((ThrownEgg) (Object) this).getItem()).create(level, reason);
	}
}
