package monster.east.matchaff.mixin;

import monster.east.matchaff.mechanic.GameplayMechanics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.Set;

@Mixin(SpawnEggItem.class)
public abstract class SpawnEggInteractionMixin {
	@Unique
	private static final Set<String> MATCHA_NBT_SPAWN_EGGS = Set.of(
			"asylum_seeker",
			"crate_cold_cow", "crate_temperate_cow", "crate_warm_cow",
			"crate_cold_pig", "crate_temperate_pig", "crate_warm_pig",
			"crate_sheep_black", "crate_sheep_brown", "crate_sheep_white",
			"fertilised_egg", "fertilised_blue_egg", "fertilised_brown_egg"
	);

	@Inject(method = "spawnOffspringFromSpawnEgg", at = @At("HEAD"), cancellable = true)
	private static void matcha$spawnStoredEntity(
			Player player, Mob parent, EntityType<? extends Mob> type, ServerLevel level, Vec3 pos,
			ItemStack stack, CallbackInfoReturnable<Optional<Mob>> cir
	) {
		var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (!id.getNamespace().equals("matcha") || !MATCHA_NBT_SPAWN_EGGS.contains(id.getPath())
				|| !SpawnEggItem.spawnsEntity(stack, type)) {
			return;
		}
		// These items contain a particular animal or villager, not a request to breed the target.
		cir.setReturnValue(Optional.empty());
		if (!type.canSpawn(level)) {
			return;
		}
		Mob spawned = type.create(level, EntityType.createDefaultStackConfig(level, stack, player),
				BlockPos.containing(pos), EntitySpawnReason.SPAWN_ITEM_USE, false, false);
		if (spawned == null) {
			return;
		}
		spawned.snapTo(pos.x(), pos.y(), pos.z(), 0.0F, 0.0F);
		if (level.addFreshEntity(spawned)) {
			GameplayMechanics.onAsylumSeekerSpawn(level, stack, spawned);
			stack.consume(1, player);
			cir.setReturnValue(Optional.of(spawned));
		}
	}

	@Inject(method = "useOn", at = @At("RETURN"))
	private void matcha$punishSpawnerInteraction(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
		if (!cir.getReturnValue().consumesAction()
				|| !(context.getLevel() instanceof ServerLevel level)
				|| !(context.getPlayer() instanceof ServerPlayer player)
				|| !isSpawner(level.getBlockState(context.getClickedPos()))) {
			return;
		}

		if (!player.isCreative()) {
			clearSpawners(level, player.blockPosition(), 8);
			clearSpawners(level, player.blockPosition(), 16);
		}
		player.hurtServer(level, level.damageSources().generic(), 1.0F);
	}

	@Unique
    private static void clearSpawners(ServerLevel level, BlockPos center, int radius) {
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
				center.offset(radius, radius, radius))) {
			if (isSpawner(level.getBlockState(pos))) {
				level.destroyBlock(pos, true);
			}
		}
	}

	@Unique
    private static boolean isSpawner(BlockState state) {
		return state.is(Blocks.SPAWNER) || state.is(Blocks.TRIAL_SPAWNER);
	}
}
