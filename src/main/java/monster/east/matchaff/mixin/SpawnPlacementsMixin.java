package monster.east.matchaff.mixin;

import monster.east.matchaff.mechanic.MobMechanics;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cancels forbidden natural spawns before creation to avoid a visible flash.
 * MobSpawnMixin marks other forbidden spawn reasons for removal after creation,
 * preserving their normal spawn-side timing. Spawn eggs and commands are
 * intentionally exempt for creative-mode testing.
 */
@Mixin(SpawnPlacements.class)
public abstract class SpawnPlacementsMixin {
	@Inject(method = "checkSpawnRules", at = @At("HEAD"), cancellable = true)
	private static <T extends Entity> void matcha$forbidOpenFieldSpawns(
			EntityType<T> type,
			ServerLevelAccessor level,
			EntitySpawnReason spawnReason,
			BlockPos pos,
			RandomSource random,
			CallbackInfoReturnable<Boolean> cir) {
		if (spawnReason == EntitySpawnReason.NATURAL && MobMechanics.isForbiddenSpawn(type, level.getLevel(), pos)) {
			cir.setReturnValue(false);
		}
	}
}
