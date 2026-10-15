package monster.east.matchaff.mixin;

import monster.east.matchaff.mechanic.MobMechanics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityType.class)
public abstract class EntityTypeSpawnMixin {
	@Inject(method = "create(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/EntitySpawnRequest;)Lnet/minecraft/world/entity/Entity;", at = @At("RETURN"))
	private void matcha$rememberSpawnExemption(Level level, EntitySpawnRequest request, CallbackInfoReturnable<Entity> cir) {
		if (!level.isClientSide() && cir.getReturnValue() instanceof Mob mob) {
			((MobMechanics.SpawnExemption) mob).matcha$setSpawnExempt(MobMechanics.isExemptSpawnReason(request.reason()));
		}
	}
}
