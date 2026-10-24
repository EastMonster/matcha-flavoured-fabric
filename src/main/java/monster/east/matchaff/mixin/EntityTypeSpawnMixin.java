package monster.east.matchaff.mixin;

import monster.east.matchaff.mechanic.GameplayMechanics;
import monster.east.matchaff.mechanic.MobMechanics;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityType.class)
public abstract class EntityTypeSpawnMixin {
	@Inject(method = "spawn(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/EntitySpawnReason;ZZ)Lnet/minecraft/world/entity/Entity;", at = @At("RETURN"))
	private void matcha$asylumSeekerParticles(
			ServerLevel level, @Nullable ItemStack stack, @Nullable LivingEntity user, BlockPos pos,
			EntitySpawnReason reason, boolean tryMoveDown, boolean movedUp, CallbackInfoReturnable<Entity> cir
	) {
		Entity entity = cir.getReturnValue();
		if (stack != null && entity != null) {
			GameplayMechanics.onAsylumSeekerSpawn(level, stack, entity);
		}
	}

	@Inject(method = "create(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/EntitySpawnRequest;)Lnet/minecraft/world/entity/Entity;", at = @At("RETURN"))
	private void matcha$rememberSpawnExemption(Level level, EntitySpawnRequest request, CallbackInfoReturnable<Entity> cir) {
		if (!level.isClientSide() && cir.getReturnValue() instanceof Mob mob) {
			((MobMechanics.SpawnExemption) mob).matcha$setSpawnExempt(MobMechanics.isExemptSpawnReason(request.reason()));
		}
	}
}
