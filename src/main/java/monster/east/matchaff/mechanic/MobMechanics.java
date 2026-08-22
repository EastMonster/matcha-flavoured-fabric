package monster.east.matchaff.mechanic;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.List;

public final class MobMechanics {
	private static final TagKey<EntityType<?>> MUNDANE_HOSTILES = TagKey.create(
			Registries.ENTITY_TYPE, id("mundane_hostiles")
	);

	private MobMechanics() {
	}

	static void init() {
		ServerEntityEvents.ENTITY_LOAD.register(MobMechanics::initializeMundaneHostile);
	}

	private static void initializeMundaneHostile(Entity entity, ServerLevel level) {
		if (!(entity instanceof Mob mob)
				|| !mob.is(MUNDANE_HOSTILES)
				|| mob.entityTags().contains("SpawnChecked")) {
			return;
		}
		if (mob.entityTags().contains("SpawnForbidden")) {
			mob.discard();
			return;
		}
		modifyMob(mob, WorldMechanics.cachedDifficulty(level.getServer()));
		mob.addTag("SpawnChecked");
	}

	/**
	 * Datapack spawn rule: newly loaded non-undead mundane hostiles may not remain in
	 * open overworld areas. After the ender dragon dies (safe surface mode) any
	 * mundane hostile at the surface or above sea level is forbidden.
	 */
	public static boolean isForbiddenSpawn(EntityType<?> type, ServerLevel level, BlockPos pos) {
		if (level.dimension() != Level.OVERWORLD
				|| !BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type).is(MUNDANE_HOSTILES)) {
			return false;
		}
		boolean sky = level.canSeeSky(pos);
		if (isSafeSurface(level)) {
			return sky || (pos.getY() >= 63 && pos.getY() <= 350);
		}
		if (type == EntityTypes.CREEPER && (sky || pos.getY() >= 63)) {
			return true;
		}
		return !BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type).is(EntityTypeTags.UNDEAD) && sky;
	}

	private static boolean isSafeSurface(ServerLevel level) {
		var objective = level.getServer().getScoreboard().getObjective("gamerule_safe_surface");
		return objective != null && level.getServer().getScoreboard()
				.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly("gamerule"), objective).get() >= 1;
	}

	private static void modifyMob(Mob mob, Difficulty difficulty) {
		if (difficulty == Difficulty.PEACEFUL) {
			return;
		}
		var type = mob.getType();
		if (mob.is(MUNDANE_HOSTILES)
				|| type == EntityTypes.ZOMBIFIED_PIGLIN || type == EntityTypes.PIGLIN) {
			for (EquipmentSlot slot : List.of(EquipmentSlot.FEET, EquipmentSlot.LEGS,
					EquipmentSlot.CHEST, EquipmentSlot.HEAD, EquipmentSlot.MAINHAND)) {
				mob.setDropChance(slot, 0);
			}
		}
		boolean baby = mob.isBaby();
		if (mob.is(EntityTypeTags.SKELETONS)) {
			setBase(mob, Attributes.MAX_HEALTH, difficulty == Difficulty.HARD ? 12 : 10);
			if (difficulty != Difficulty.EASY) {
				ItemStack bow = new ItemStack(Items.BOW);
				bow.enchant(mob.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
						.getOrThrow(Enchantments.PUNCH), difficulty == Difficulty.HARD ? 2 : 1);
				mob.setItemSlot(EquipmentSlot.MAINHAND, bow);
			}
		}
		if (type == EntityTypes.CREEPER) {
			setBase(mob, Attributes.MAX_HEALTH, switch (difficulty) {
				case EASY -> 14;
				case HARD -> 18;
				default -> 16;
			});
		}
		if (type == EntityTypes.CAVE_SPIDER) {
			setBase(mob, Attributes.MAX_HEALTH, switch (difficulty) {
				case EASY -> 2;
				case HARD -> 6;
				default -> 4;
			});
			setBase(mob, Attributes.MOVEMENT_SPEED, switch (difficulty) {
				case EASY -> 0.35;
				case HARD -> 0.42;
				default -> 0.4;
			});
		}
		if (type == EntityTypes.SILVERFISH) {
			setBase(mob, Attributes.MAX_HEALTH, difficulty == Difficulty.HARD ? 4 : 2);
		}
		if (difficulty == Difficulty.EASY) {
			return;
		}
		if (type == EntityTypes.ZOMBIE) {
			if (baby) {
				setBase(mob, Attributes.MAX_HEALTH, difficulty == Difficulty.HARD ? 5 : 4);
			} else {
				setBase(mob, Attributes.MAX_HEALTH, difficulty == Difficulty.HARD ? 15 : 10);
				setBase(mob, Attributes.MOVEMENT_SPEED, difficulty == Difficulty.HARD ? 0.35 : 0.34);
				setBase(mob, Attributes.STEP_HEIGHT, 1);
				if (difficulty == Difficulty.HARD) {
					setBase(mob, Attributes.ATTACK_DAMAGE, 5);
				}
			}
		}
		if (type == EntityTypes.HUSK && !baby) {
			setBase(mob, Attributes.MAX_HEALTH, 40);
			mob.setHealth(40);
			setBase(mob, Attributes.MOVEMENT_SPEED, difficulty == Difficulty.HARD ? 0.25 : 0.21);
			setBase(mob, Attributes.ATTACK_DAMAGE, difficulty == Difficulty.HARD ? 15 : 10);
			setBase(mob, Attributes.ARMOR, difficulty == Difficulty.HARD ? 14 : 12);
			setBase(mob, Attributes.FOLLOW_RANGE, difficulty == Difficulty.HARD ? 60 : 50);
			setBase(mob, Attributes.KNOCKBACK_RESISTANCE, 1);
			setBase(mob, Attributes.MOVEMENT_EFFICIENCY, 1);
			setBase(mob, Attributes.WATER_MOVEMENT_EFFICIENCY, 1);
			setBase(mob, Attributes.STEP_HEIGHT, 1);
			setBase(mob, Attributes.SPAWN_REINFORCEMENTS_CHANCE, difficulty == Difficulty.HARD ? 0.25 : 0.1);
		} else if (type == EntityTypes.HUSK) {
			setBase(mob, Attributes.MAX_HEALTH, difficulty == Difficulty.HARD ? 5 : 4);
		}
		if (type == EntityTypes.SPIDER) {
			setBase(mob, Attributes.JUMP_STRENGTH, 0.85);
			setBase(mob, Attributes.FALL_DAMAGE_MULTIPLIER, 0);
			mob.addEffect(new MobEffectInstance(MobEffects.WEAVING, MobEffectInstance.INFINITE_DURATION,
					difficulty == Difficulty.HARD ? 1 : 0, false, false));
		}
	}

	private static void setBase(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
		var instance = mob.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("main", path);
	}
}
