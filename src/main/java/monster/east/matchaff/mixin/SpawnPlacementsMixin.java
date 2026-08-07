package monster.east.matchaff.mixin;

import monster.east.matchaff.WorldMechanics;
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
 * Cancels natural spawns of mundane hostiles in open overworld areas before
 * the mob is even created, so the datapack's "no non-undead mobs on open
 * fields" rule no longer spawns-then-kills (no flash). Player spawn eggs use
 * EntitySpawnReason.SPAWN_ITEM_USE and are unaffected.
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
		if (spawnReason == EntitySpawnReason.NATURAL && WorldMechanics.isForbiddenSpawn(type, level.getLevel(), pos)) {
			cir.setReturnValue(false);
		}
	}
}
