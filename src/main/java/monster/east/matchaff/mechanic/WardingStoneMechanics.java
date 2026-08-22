package monster.east.matchaff.mechanic;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.Structure;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

final class WardingStoneMechanics {
	private static final TagKey<EntityType<?>> VILLAGER_FRIENDS = TagKey.create(
			Registries.ENTITY_TYPE, id("villager_friends")
	);
	private static final TagKey<EntityType<?>> WARDING_STONE_TARGETS = TagKey.create(
			Registries.ENTITY_TYPE, id("warding_stone_targets")
	);
	private static final ResourceKey<Structure> TRIAL_CHAMBERS = ResourceKey.create(
			Registries.STRUCTURE, Identifier.fromNamespaceAndPath("minecraft", "trial_chambers")
	);
	private static final Set<ArmorStand> WARDING_STONES =
			Collections.newSetFromMap(new IdentityHashMap<>());

	private WardingStoneMechanics() {
	}

	static void init() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> WARDING_STONES.clear());
		ServerEntityEvents.ENTITY_LOAD.register(WardingStoneMechanics::trackEntity);
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> WARDING_STONES.remove(entity));
	}

	static void tick() {
		WARDING_STONES.removeIf(stone ->
				stone.isRemoved() || !stone.entityTags().contains("WardingStone"));
		for (ArmorStand stone : List.copyOf(WARDING_STONES)) {
			if (!(stone.level() instanceof ServerLevel level)) {
				continue;
			}
			BlockPos pos = stone.blockPosition();
			double stoneX = stone.getX();
			double stoneY = stone.getY();
			double stoneZ = stone.getZ();
			boolean setup = stone.entityTags().contains("WardingStoneSetup");

			// Heal friendly villagers nearby.
			var friends = level.getEntitiesOfClass(LivingEntity.class, stone.getBoundingBox().inflate(16.0),
					f -> f.is(VILLAGER_FRIENDS)
							&& f.distanceToSqr(stone) <= 16.0 * 16.0);
			boolean someoneRegenerating = friends.stream()
					.anyMatch(f -> f.hasEffect(MobEffects.REGENERATION));
			if (!someoneRegenerating) {
				for (LivingEntity friend : friends) {
					friend.addEffect(new MobEffectInstance(
							MobEffects.REGENERATION, 60, 0, false, false));
				}
			}

			boolean anchored = level.getBlockState(pos).is(Blocks.LODESTONE);
			if (!setup) {
				if (!anchored) {
					level.setBlockAndUpdate(pos, Blocks.LODESTONE.defaultBlockState());
				}
				level.playSound(null, pos, SoundEvents.WITHER_SPAWN, SoundSource.BLOCKS, 0.25F, 1.0F);
				level.sendParticles(ParticleTypes.SCULK_SOUL, stoneX, stoneY + 0.5, stoneZ,
						10, 0.25, 0.1, 0.25, 0.05);
				level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, stoneX, stoneY + 0.5, stoneZ,
						10, 0.5, 0.1, 0.5, 0.1);
				stone.addTag("WardingStoneSetup");
				setup = true;
			}

			// Anchor destroyed: refund blaze powder and remove the stone.
			if (setup && !level.getBlockState(pos).is(Blocks.LODESTONE)) {
				for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, stone.getBoundingBox().inflate(3.0))) {
					if (item.getItem().is(Items.LODESTONE)) {
						item.discard();
					}
				}
				level.sendParticles(ParticleTypes.LARGE_SMOKE, stoneX, stoneY + 0.25, stoneZ,
						20, 0.25, 0.5, 0.25, 0.01);
				level.addFreshEntity(new ItemEntity(level, stoneX, stoneY, stoneZ,
						new ItemStack(Items.BLAZE_POWDER)));
				stone.discard();
				continue;
			}

			// Placed inside a trial chamber: forbidden, destroy it.
			var structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(TRIAL_CHAMBERS);
			if (level.structureManager().getStructureWithPieceAt(pos, HolderSet.direct(structure)).isValid()) {
				level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
				var tnt = EntityTypes.TNT.create(level, EntitySpawnReason.EVENT);
				tnt.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
				tnt.setFuse(0);
				level.addFreshEntity(tnt);
				level.sendParticles(ParticleTypes.SCULK_SOUL, stoneX, stoneY, stoneZ,
						100, 0.1, 0.1, 0.1, 0.5);
				stone.discard();
				continue;
			}

			// Aura: slow and damage the dedicated 1.10 target set (including pillagers).
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, stoneX, stoneY + 0.5, stoneZ,
					1, 0.5, 0.5, 0.5, 0);
			for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, stone.getBoundingBox().inflate(26.0),
					u -> u.is(WARDING_STONE_TARGETS)
							&& u.distanceToSqr(stone) <= 26.0 * 26.0)) {
				target.addEffect(new MobEffectInstance(
						MobEffects.SLOWNESS, 40, 2, false, false));
			}

			if (level.getServer().getTickCount() % 10 == 0) {
				LivingEntity generalTarget = nearestTarget(level, stone, 24.0, target -> true);
				if (generalTarget != null && nearestTarget(level, generalTarget, 20.0,
						target -> target.getType() == EntityTypes.WITHER) == null) {
					level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
							generalTarget.getX(), generalTarget.getY() + 2.0, generalTarget.getZ(),
							1, 0.25, 0.25, 0.25, 0.025);
					LivingEntity damageTarget = nearestTarget(level, generalTarget, 14.0, target -> true);
					if (damageTarget != null) {
						damageTarget.hurtServer(level, level.damageSources().fellOutOfWorld(), 7.0F);
					}
				}

				LivingEntity witherTarget = nearestTarget(level, stone, 24.0,
						target -> target.getType() == EntityTypes.WITHER);
				if (witherTarget != null) {
					level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
							witherTarget.getX(), witherTarget.getY() + 2.5, witherTarget.getZ(),
							2, 1.0, 1.0, 1.0, 0.5);
					LivingEntity damageTarget = nearestTarget(level, witherTarget, 14.0, target -> true);
					if (damageTarget != null) {
						damageTarget.hurtServer(level, level.damageSources().fellOutOfWorld(), 2.0F);
					}
				}
			}
		}
	}

	private static void trackEntity(Entity entity, ServerLevel level) {
		if (entity instanceof ArmorStand stone && stone.entityTags().contains("WardingStone")) {
			WARDING_STONES.add(stone);
		}
	}

	private static LivingEntity nearestTarget(
			ServerLevel level, LivingEntity center, double radius, Predicate<LivingEntity> predicate
	) {
		double radiusSquared = radius * radius;
		return level.getEntitiesOfClass(LivingEntity.class, center.getBoundingBox().inflate(radius), target ->
				target.is(WARDING_STONE_TARGETS)
						&& predicate.test(target)
						&& target.distanceToSqr(center) <= radiusSquared)
				.stream()
				.min(Comparator.comparingDouble(target -> target.distanceToSqr(center)))
				.orElse(null);
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("main", path);
	}
}
