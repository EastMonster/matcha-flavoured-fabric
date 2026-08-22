package monster.east.matchaff.mixin;

import monster.east.matchaff.mechanic.MobMechanics;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class MobSpawnMixin {
	@Inject(method = "finalizeSpawn", at = @At("TAIL"))
	private void matcha$markForbiddenSpawn(
			ServerLevelAccessor level,
			DifficultyInstance difficulty,
			EntitySpawnReason spawnReason,
			SpawnGroupData spawnData,
			CallbackInfoReturnable<SpawnGroupData> cir) {
		if (spawnReason == EntitySpawnReason.SPAWN_ITEM_USE
				|| spawnReason == EntitySpawnReason.DISPENSER
				|| spawnReason == EntitySpawnReason.COMMAND) {
			return;
		}
		Mob mob = (Mob) (Object) this;
		if (MobMechanics.isForbiddenSpawn(mob.getType(), level.getLevel(), mob.blockPosition())) {
			mob.addTag("SpawnForbidden");
		}
	}
}
