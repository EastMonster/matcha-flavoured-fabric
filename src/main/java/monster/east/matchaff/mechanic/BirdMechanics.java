package monster.east.matchaff.mechanic;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Set;
import monster.east.matchaff.entity.Bird;
import monster.east.matchaff.block.RoostBlock;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.phys.AABB;
import monster.east.matchaff.entity.BirdEntities;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class BirdMechanics {
	private static final AttachmentType<String> PERSONALITY = AttachmentRegistry.create(
			Identifier.fromNamespaceAndPath("matcha", "bird_personality"), builder -> builder.persistent(Codec.STRING));
	private static final AttachmentType<Boolean> HOME_INITIALIZED = AttachmentRegistry.create(
			Identifier.fromNamespaceAndPath("matcha", "bird_home_initialized"), builder -> builder.persistent(Codec.BOOL));

	private static final int PECK = 1, EAT = 2, DIVE = 3, NEST = 4;
	private static final TagKey<Block> PECKABLE = blockTag("bird_peckable");
	private static final TagKey<Block> CHICKEN_PECKABLE = blockTag("chicken_peckable");
	private static final TagKey<Block> SEAGULL_PECKABLE = blockTag("seagull_peckable");
	private static final TagKey<Block> EDIBLE = blockTag("chicken_edible");
	private static final TagKey<Block> DIVABLE = blockTag("duck_divable");
	private static final TagKey<Block> NESTABLE = blockTag("chicken_nestable");
	private static final TagKey<Block> ROOST_REPLACEABLE = TagKey.create(Registries.BLOCK, id("custom_block_placeable"));
	private static final TagKey<Block> PLANTABLE = blockTag("chicken_plantable");
	private static final AttachmentType<Integer> GIFT_TIMER = timer("bird_gift_timer");
	private static final AttachmentType<Integer> PLANT_TIMER = timer("bird_plant_timer");
	private static final AttachmentType<Integer> NEST_TIMER = timer("bird_nest_timer");
	private static final AttachmentType<Integer> ACTION = AttachmentRegistry.create(id("bird_action"),
			builder -> builder.persistent(Codec.INT).syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	private static final AttachmentType<Integer> ACTION_TICKS = timer("bird_action_ticks");
	private static final AttachmentType<Long> ACTION_END = AttachmentRegistry.create(id("bird_action_end"),
			builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));
	private static final Set<Chicken> BIRDS = Collections.newSetFromMap(new IdentityHashMap<>());
	private static final ResourceKey<LootTable> GIFTS = ResourceKey.create(Registries.LOOT_TABLE, id("gameplay/chicken_lay/totipotent"));

	private BirdMechanics() {
	}

	public static void init() {
		Registry.register(BuiltInRegistries.LOOT_CONDITION_TYPE, id("bird_state"), StateCondition.CODEC);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Chicken chicken && isManaged(chicken)) {
				BIRDS.add(chicken);
				if (!chicken.hasAttached(GIFT_TIMER)) chicken.setAttached(GIFT_TIMER, delay(chicken, 300, 600));
				if (!chicken.hasAttached(PLANT_TIMER)) chicken.setAttached(PLANT_TIMER, delay(chicken, 400, 600));
				if (!chicken.hasAttached(HOME_INITIALIZED) && level.dimension().equals(Level.OVERWORLD)) {
					initializeHome(chicken);
				}
				if (chicken instanceof Bird) applySpecies(chicken);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> BIRDS.remove(entity));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> BIRDS.clear());
		ServerTickEvents.START_SERVER_TICK.register(BirdMechanics::tick);
	}

	public static boolean isManaged(Chicken chicken) {
		return chicken.getType() == EntityTypes.CHICKEN || chicken instanceof Bird;
	}

	private static void initializeHome(Chicken chicken) {
		String personality = chicken.getAttachedOrElse(PERSONALITY, "normal");
		chicken.getAttribute(Attributes.TEMPT_RANGE).setBaseValue(20.0);
		switch (personality) {
			case "mama" -> chicken.setHomeTo(chicken.blockPosition(), 2);
			case "curious" -> chicken.setHomeTo(chicken.blockPosition(), 21);
			case "rascal" -> {
				chicken.setHomeTo(chicken.blockPosition(), 13);
				chicken.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3);
			}
			case "nest_maker" -> chicken.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.28);
			default -> chicken.setHomeTo(chicken.blockPosition(), 11);
		}
		applySpecies(chicken);
		chicken.setAttached(HOME_INITIALIZED, true);
	}

	private static void applySpecies(Chicken chicken) {
		String species;
		if (chicken instanceof Bird) {
			species = BuiltInRegistries.ENTITY_TYPE.getKey(chicken.getType()).getPath();
			chicken.setVariant(chicken.registryAccess().lookupOrThrow(Registries.CHICKEN_VARIANT)
					.getOrThrow(ResourceKey.create(Registries.CHICKEN_VARIANT, Identifier.fromNamespaceAndPath("matcha", species))));
		} else {
			String variant = chicken.getVariant().unwrapKey().map(key -> key.identifier().toString()).orElse("");
			species = switch (variant) {
				case "minecraft:temperate" -> "classic";
				case "minecraft:warm", "minecraft:cold" -> "picky";
				default -> null;
			};
			if (species == null) return;
		}
		Identifier sound = Identifier.fromNamespaceAndPath(chicken instanceof Bird ? "matcha" : "minecraft", species);
		chicken.setComponent(DataComponents.CHICKEN_SOUND_VARIANT, chicken.registryAccess()
				.lookupOrThrow(Registries.CHICKEN_SOUND_VARIANT).getOrThrow(ResourceKey.create(Registries.CHICKEN_SOUND_VARIANT, sound)));
	}


	public static void onFed(Chicken chicken, ServerLevel level) {
		chicken.setAttached(GIFT_TIMER, Math.max(0, chicken.getAttachedOrThrow(GIFT_TIMER) - 200 * 20));
		level.broadcastEntityEvent(chicken, (byte) 18);
		String variant = chicken.getVariant().unwrapKey().map(key -> key.identifier().toString()).orElse("");
		if (chicken instanceof Bird.Bobwhite || variant.equals("minecraft:temperate")) {
			var sounds = chicken.get(DataComponents.CHICKEN_SOUND_VARIANT).value();
			sound(chicken, level, (chicken.isBaby() ? sounds.babySounds() : sounds.adultSounds()).ambientSound().value(), 1.0F);
		}
	}

	private static void tick(MinecraftServer server) {
		if (!server.tickRateManager().runsNormally()) return;
		for (Chicken chicken : BIRDS) {
			ServerLevel level = (ServerLevel) chicken.level();
			if (!chicken.isAlive() || chicken.isRemoved() || !level.isPositionEntityTicking(chicken.blockPosition())) {
				chicken.removeAttached(ACTION_END);
				continue;
			}
			int giftTimer = chicken.getAttachedOrThrow(GIFT_TIMER);
			if (giftTimer > 0) chicken.setAttached(GIFT_TIMER, giftTimer - 1);
			int plantTimer = chicken.getAttachedOrThrow(PLANT_TIMER) - 1;
			if (plantTimer <= 0) {
				plant(chicken, level);
				plantTimer = delay(chicken, 400, 600);
			}
			chicken.setAttached(PLANT_TIMER, plantTimer);
			if (personality(chicken).equals("nest_maker")) {
				if (!chicken.hasAttached(NEST_TIMER)) chicken.setAttached(NEST_TIMER, delay(chicken, 600, 1200));
				int nestTimer = chicken.getAttachedOrThrow(NEST_TIMER);
				if (nestTimer > 0) chicken.setAttached(NEST_TIMER, nestTimer - 1);
			}
			if (chicken.isNoAi() || chicken.getFirstPassenger() instanceof Player) chicken.removeAttached(ACTION_END);
		}
	}

	private static void plant(Chicken chicken, ServerLevel level) {
		if (chicken instanceof Bird.Duck && level.getBlockState(at(chicken, 0)).is(Blocks.WATER)
				&& !level.getBlockState(at(chicken, -1)).is(Blocks.WATER)) {
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, chicken.getX(), chicken.getY(), chicken.getZ(), 5, 0.3, 0.1, 0.3, 0);
			level.setBlockAndUpdate(at(chicken, 0), Blocks.SEAGRASS.defaultBlockState());
		}
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, chicken.getX(), chicken.getY(), chicken.getZ(), 5, 0.3, 0.1, 0.3, 0);
		int dx = chicken.getRandom().nextInt(3) - 1;
		int dz = chicken.getRandom().nextInt(3) - 1;
		BlockPos pos = at(chicken, 0.5).offset(dx, 0, dz);
		if (level.getBlockState(pos).is(Blocks.SHORT_GRASS) && level.getBlockState(pos.above()).is(Blocks.AIR)) {
			DoublePlantBlock.placeAt(level, Blocks.TALL_GRASS.defaultBlockState(), pos, Block.UPDATE_ALL);
		}
		if (level.getBlockState(pos).is(Blocks.AIR)) {
			BlockPos ground = pos.below();
			if (level.getBlockState(ground).is(Blocks.FARMLAND)) {
				level.setBlockAndUpdate(ground, Block.pushEntitiesUp(
						level.getBlockState(ground), Blocks.DIRT.defaultBlockState(), level, ground));
			}
			if (level.getBlockState(ground).is(PLANTABLE)) level.setBlockAndUpdate(pos, Blocks.SHORT_GRASS.defaultBlockState());
		}
	}

	public static final class ForagingGoal extends Goal {
		private final Chicken chicken;
		private int selected;
		private boolean starting;

		public ForagingGoal(Chicken chicken) {
			this.chicken = chicken;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			if (!chicken.isAlive() || chicken.getFirstPassenger() instanceof Player) return false;
			if (chicken.getAttachedOrElse(ACTION_TICKS, 0) > 0) return true;
			if (personality(chicken).equals("nest_maker") && chicken.getAttachedOrElse(NEST_TIMER, 1) == 0
					&& chicken.level().getBlockState(at(chicken, -0.8)).is(NESTABLE) && !(chicken instanceof Bird.Seagull)) {
				selected = NEST;
				return true;
			}
			if (chicken.getAttachedOrElse(GIFT_TIMER, 1) > 0) return false;
			var ground = chicken.level().getBlockState(at(chicken, -0.5));
			if (!ground.is(PECKABLE)) return false;
			boolean crop = chicken.level().getBlockState(at(chicken, 0.5)).is(EDIBLE);
			selected = 0;
			if (!crop) {
				if (chicken instanceof Bird.Duck && chicken.level().getBlockState(at(chicken, 0)).is(DIVABLE)) selected = DIVE;
				else if (chicken instanceof Bird.Seagull ? ground.is(SEAGULL_PECKABLE) : ground.is(CHICKEN_PECKABLE)) selected = PECK;
			} else if (!(chicken instanceof Bird.Seagull) && !(chicken instanceof Bird.Duck)
					&& !chicken.isBaby() && !personality(chicken).equals("mama") && ground.is(CHICKEN_PECKABLE)) selected = EAT;
			// Upstream also resets the cooldown when the ground is valid but no action applies.
			if (selected == 0) chicken.setAttached(GIFT_TIMER, delay(chicken, 300, 600));
			return selected != 0;
		}

		@Override
		public boolean canContinueToUse() {
			return chicken.isAlive() && !(chicken.getFirstPassenger() instanceof Player) && chicken.getAttachedOrElse(ACTION_TICKS, 0) > 0;
		}

		@Override
		public boolean isInterruptable() { return false; }

		@Override
		public boolean requiresUpdateEveryTick() { return true; }

		@Override
		public void start() {
			starting = chicken.getAttachedOrElse(ACTION_TICKS, 0) == 0;
			if (starting) {
				chicken.setAttached(ACTION, selected);
				chicken.setAttached(ACTION_TICKS, selected == NEST ? 60 : 30);
				if (selected == NEST) {
					chicken.removeAttached(NEST_TIMER);
					chicken.setAttached(PERSONALITY, "mama");
					initializeHome(chicken);
				} else chicken.setAttached(GIFT_TIMER, delay(chicken, 300, 600));
			}
			chicken.getNavigation().stop();
			chicken.getMoveControl().setWantedPosition(chicken.getX(), chicken.getY(), chicken.getZ(), 0);
			chicken.setXxa(0);
			chicken.setSpeed(0);
			syncAnimation(chicken);
		}

		@Override
		public void tick() {
			if (!canContinueToUse()) return;
			if (starting) starting = false;
			else tickAction(chicken, (ServerLevel) chicken.level());
			syncAnimation(chicken);
		}

		@Override
		public void stop() { chicken.removeAttached(ACTION_END); }
	}

	private static void tickAction(Chicken chicken, ServerLevel level) {
		int action = chicken.getAttachedOrElse(ACTION, 0);
		int remaining = chicken.getAttachedOrThrow(ACTION_TICKS) - 1;
		chicken.setAttached(ACTION_TICKS, remaining);
		boolean pulse = action == NEST ? remaining == 56 || remaining == 46 || remaining == 36
				|| remaining == 26 || remaining == 16 || remaining == 6 || remaining == 1
				: remaining == 26 || remaining == 16 || remaining == 10 || remaining == 4;
		if (pulse) {
			float yaw = chicken.getYRot() + (action == NEST ? nestingTurn(remaining) : 0);
			Vec3 front = Vec3.directionFromRotation(0, yaw).scale(0.5).add(chicken.position());
			if (action == DIVE) {
				level.sendParticles(ParticleTypes.SPLASH, chicken.getX(), chicken.getY() + 0.2, chicken.getZ(), 20, 0.4, 0.1, 0.4, 0);
				// Normal movement respects the bottom instead of teleporting through blocks.
				chicken.setDeltaMovement(chicken.getDeltaMovement().add(0, -0.35, 0));
				chicken.needsSync = true;
				sound(chicken, level, SoundEvents.GENERIC_SPLASH, 0.5F);
			} else {
				Block particle = action == NEST ? Blocks.SHORT_GRASS : action == EAT ? Blocks.WHEAT : peckParticle(chicken, level);
				if (particle != null) {
					particles(level, front, particle, action == EAT ? 15 : 5);
					sound(chicken, level, action == PECK && (particle == Blocks.SAND || particle == Blocks.RED_SAND) ? SoundEvents.SAND_HIT
							: action == PECK && particle == Blocks.GRAVEL ? SoundEvents.GRAVEL_HIT : SoundEvents.CROP_BREAK, 1.0F);
				}
				if (action == NEST) {
					particles(level, chicken.position().add(0, 0.1, 0), Blocks.SHORT_GRASS, 15);
					level.sendParticles(ParticleTypes.HAPPY_VILLAGER, chicken.getX(), chicken.getY() + 0.1, chicken.getZ(), 3, 0.2, 0.1, 0.2, 0.1);
					sound(chicken, level, SoundEvents.GRASS_PLACE, 1.0F);
				}
			}
			if (action == EAT) level.sendParticles(ParticleTypes.HEART, chicken.getX(), chicken.getY() + 1, chicken.getZ(), 1, 0.3, 0.1, 0.3, 0.1);
		}
		if (action == EAT && level.getBlockState(at(chicken, 0.5)).is(EDIBLE)) {
			BlockPos crop = at(chicken, 0.5);
			level.levelEvent(2001, crop, Block.getId(level.getBlockState(crop)));
			level.setBlockAndUpdate(crop, Blocks.AIR.defaultBlockState());
			// Upstream changes the personality here without moving the existing home.
			chicken.setAttached(PERSONALITY, "nest_maker");
			chicken.setAttached(NEST_TIMER, delay(chicken, 10, 30));
		}
		if (remaining == 1) {
			double spread = action == NEST ? 0.5 : 0.3;
			level.sendParticles(ParticleTypes.HEART, chicken.getX(), chicken.getY() + 1, chicken.getZ(), action == NEST ? 5 : 1, spread, 0.1, spread, 0.1);
			chicken.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 2, true, false));
			if (action == NEST) makeNest(chicken, level);
			else if (action == PECK) gift(chicken, 0.15);
			else if (action == DIVE) {
				chicken.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 2400, 0, true, false));
				if (level.getBlockState(at(chicken, 0)).is(DIVABLE)) {
					gift(chicken, 0.3);
					if (level.getBlockState(at(chicken, 0)).is(Blocks.SEAGRASS)) {
						sound(chicken, level, SoundEvents.CROP_BREAK, 1.0F);
						level.setBlockAndUpdate(at(chicken, 0), Blocks.WATER.defaultBlockState());
					}
				}
			}
			chicken.removeAttached(ACTION);
			chicken.removeAttached(ACTION_TICKS);
			chicken.removeAttached(ACTION_END);
		}
	}

	private static void makeNest(Chicken chicken, ServerLevel level) {
		BlockPos ground = at(chicken, -0.5);
		level.setBlockAndUpdate(ground, Block.pushEntitiesUp(level.getBlockState(ground), Blocks.COARSE_DIRT.defaultBlockState(), level, ground));
		BlockPos nest = chicken.blockPosition();
		if (level.getBlockState(nest).is(ROOST_REPLACEABLE)
				&& RoostBlock.TWIG.defaultBlockState().canSurvive(level, nest)) {
			level.setBlockAndUpdate(nest, RoostBlock.TWIG.defaultBlockState());
		}
		if (chicken instanceof Bird.Seagull) return;
		for (int i = 0; i < 2; i++) {
			Chicken baby = (Chicken) chicken.getType().create(level, EntitySpawnReason.BREEDING);
			if (baby == null) continue;
			baby.setVariant(chicken.getVariant());
			baby.setAge(-24000);
			baby.snapTo(chicken.getX(), chicken.getY(), chicken.getZ(), chicken.getYRot(), 0);
			level.addFreshEntity(baby);
		}
	}

	public static void onRoostPlaced(ServerLevel level, BlockPos pos) {
		Vec3 center = Vec3.atBottomCenterOf(pos);
		for (Chicken chicken : level.getEntitiesOfClass(Chicken.class, new AABB(pos).inflate(15), BirdMechanics::isManaged)) {
			double distance = chicken.position().distanceToSqr(center);
			if (distance > 15 * 15) continue;
			if (personality(chicken).equals("mama") && distance > 2 * 2) {
				if (distance <= 10 * 10) level.sendParticles(ParticleTypes.ANGRY_VILLAGER,
						chicken.getX(), chicken.getY() + 0.5, chicken.getZ(), 1, 0.1, 0.1, 0.1, 0.1);
				continue;
			}
			// The new block's POI may still be queued; keep it as a candidate explicitly.
			BlockPos nearest = level.getPoiManager().findAll(holder -> holder.is(RoostBlock.POI), candidate -> {
				var chunk = level.getChunkSource().getChunkNow(candidate.getX() >> 4, candidate.getZ() >> 4);
				return chunk != null && chunk.getBlockState(candidate).getBlock() instanceof RoostBlock;
			}, chicken.blockPosition(), 16, PoiManager.Occupancy.ANY)
					.min(Comparator.comparingDouble(candidate -> chicken.position().distanceToSqr(Vec3.atBottomCenterOf(candidate))))
					.orElse(pos);
			if (chicken.position().distanceToSqr(Vec3.atBottomCenterOf(nearest)) >= distance) nearest = pos;
			// Moving a home keeps the personality's existing home radius.
			chicken.setHomeTo(nearest, chicken.getHomeRadius());
			level.sendParticles(ParticleTypes.HEART, chicken.getX(), chicken.getY() + 0.5, chicken.getZ(), 1, 0.1, 0.1, 0.1, 0.1);
		}
	}

	private static void gift(Chicken chicken, double velocity) {
		chicken.dropFromGiftLootTable((ServerLevel) chicken.level(), GIFTS, (world, stack) -> {
			ItemEntity item = new ItemEntity(world, chicken.getX(), chicken.getY() + 0.1, chicken.getZ(), stack);
			item.setDeltaMovement(0, velocity, 0);
			item.setDefaultPickUpDelay();
			world.addFreshEntity(item);
		});
	}

	private record StateCondition(String state) implements LootItemCondition {
		private static final MapCodec<StateCondition> CODEC = Codec.STRING.fieldOf("state").xmap(StateCondition::new, StateCondition::state);
		@Override
		public MapCodec<StateCondition> codec() { return CODEC; }
		@Override
		public Set<ContextKey<?>> getReferencedContextParams() { return Set.of(LootContextParams.THIS_ENTITY); }
		@Override
		public boolean test(LootContext context) {
			if (!(context.getOptional(LootContextParams.THIS_ENTITY) instanceof Chicken chicken)) return false;
			return switch (state) {
				case "diving" -> chicken.getAttachedOrElse(ACTION, 0) == DIVE;
				case "seagrass" -> chicken.getAttachedOrElse(ACTION, 0) == DIVE && chicken.level().getBlockState(at(chicken, 0)).is(Blocks.SEAGRASS);
				default -> personality(chicken).equals(state);
			};
		}
	}

	public static int animationTicks(Chicken chicken) {
		return chicken.level().isClientSide()
				? (int) Math.clamp(chicken.getAttachedOrElse(ACTION_END, 0L) - chicken.level().getGameTime(), 0L, 60L)
				: chicken.getAttachedOrElse(ACTION_TICKS, 0);
	}

	public static boolean nesting(Chicken chicken) { return chicken.getAttachedOrElse(ACTION, 0) == NEST; }

	static int foragingCooldown(Chicken chicken) { return Math.max(0, chicken.getAttachedOrElse(GIFT_TIMER, 0)); }

	static String foragingAction(Chicken chicken) {
		if (chicken.getAttachedOrElse(ACTION_TICKS, 0) <= 0) return "";
		return switch (chicken.getAttachedOrElse(ACTION, 0)) {
			case PECK -> "pecking";
			case EAT -> "eating";
			case DIVE -> "diving";
			case NEST -> "nesting";
			default -> "";
		};
	}

	private static void syncAnimation(Chicken chicken) {
		int ticks = chicken.getAttachedOrElse(ACTION_TICKS, 0);
		if (ticks <= 0) chicken.removeAttached(ACTION_END);
		else {
			long end = chicken.level().getGameTime() + ticks;
			if (chicken.getAttachedOrElse(ACTION_END, 0L) != end) chicken.setAttached(ACTION_END, end);
		}
	}

	public static int actionPitch(int remaining, boolean nest) {
		if (nest) return remaining % 10 == 1 || remaining % 10 == 2 ? 20 : 90;
		return remaining >= 23 || remaining >= 13 && remaining <= 20
				|| remaining >= 6 && remaining <= 10 || remaining <= 3 ? 90 : 20;
	}

	public static int nestingTurn(int remaining) {
		int elapsed = Math.clamp(60 - remaining, 0, 59);
		int phase = elapsed % 10;
		return (elapsed / 10 * 7 + Math.min(phase, 4) + Math.max(0, phase - 7)) * 22;
	}

	private static Block peckParticle(Chicken chicken, ServerLevel level) {
		var ground = level.getBlockState(at(chicken, -1));
		if (ground.is(Blocks.GRASS_BLOCK)) return Blocks.SHORT_GRASS;
		if (ground.is(Blocks.DIRT)) return Blocks.DIRT;
		if (ground.is(Blocks.MYCELIUM)) return Blocks.MYCELIUM;
		if (ground.is(Blocks.SAND)) return Blocks.SAND;
		if (ground.is(Blocks.RED_SAND)) return Blocks.RED_SAND;
		return ground.is(Blocks.GRAVEL) ? Blocks.GRAVEL : null;
	}

	private static void particles(ServerLevel level, Vec3 pos, Block block, int count) {
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, block.defaultBlockState()), pos.x, pos.y, pos.z, count, 0.1, 0.1, 0.1, 0.1);
	}

	private static void sound(Chicken chicken, ServerLevel level, SoundEvent sound, float volume) {
		level.playSound(null, chicken.getX(), chicken.getY(), chicken.getZ(), sound, SoundSource.NEUTRAL, volume, 1.0F);
	}

	static String personality(Chicken chicken) { return chicken.getAttachedOrElse(PERSONALITY, "normal"); }
	private static int delay(Chicken chicken, int min, int max) { return (min + chicken.getRandom().nextInt(max - min + 1)) * 20; }
	private static BlockPos at(Chicken chicken, double offset) { return BlockPos.containing(chicken.getX(), chicken.getY() + offset, chicken.getZ()); }
	private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("matcha", path); }
	private static TagKey<Block> blockTag(String path) { return TagKey.create(Registries.BLOCK, id("animal_affects/" + path)); }
	private static AttachmentType<Integer> timer(String path) { return AttachmentRegistry.create(id(path), builder -> builder.persistent(Codec.INT)); }

	public static EntityType<? extends Chicken> hatchType(ItemStack egg) {
		var variant = egg.get(DataComponents.CHICKEN_VARIANT);
		Identifier id = variant == null ? null : variant.unwrapKey().map(ResourceKey::identifier).orElse(null);
		if (id == null || !id.getNamespace().equals("matcha")) return EntityTypes.CHICKEN;
		return switch (id.getPath()) {
			case "duck" -> BirdEntities.DUCK;
			case "seagull" -> BirdEntities.SEAGULL;
			case "bobwhite" -> BirdEntities.BOBWHITE;
			default -> EntityTypes.CHICKEN;
		};
	}
}
