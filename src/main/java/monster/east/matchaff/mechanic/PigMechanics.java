package monster.east.matchaff.mechanic;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.pig.PigSoundVariant;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Set;

/** Pig behaviour from upstream c0a9f1e3; timers belong to each loaded, ticking pig. */
public final class PigMechanics {
	private static final TagKey<Block> INTERACTABLE = blockTag("pig_interactable");
	private static final TagKey<Block> DIGGABLE = blockTag("pig_diggable_dirt");
	private static final TagKey<Block> EDIBLE = blockTag("pig_edible");
	private static final TagKey<Block> MUD = blockTag("pig_can_play_on");
	private static final AttachmentType<Integer> GIFT_TIMER = timer("pig_gift_timer");
	private static final AttachmentType<Integer> MUD_TIMER = timer("pig_mud_timer");
	private static final AttachmentType<Integer> INFANT_TIMER = timer("pig_infant_timer");
	private static final AttachmentType<Boolean> MUDDY = AttachmentRegistry.create(id("pig_muddy"),
			builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));
	// Positive values count down digging; negative values count up mud play.
	private static final AttachmentType<Integer> ANIMATION = timer("pig_animation");
	// The signed end time stays constant during an action; late trackers receive its current phase.
	private static final AttachmentType<Long> ANIMATION_END = AttachmentRegistry.create(id("pig_animation_end"),
			builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));
	private static final ResourceKey<LootTable> DEFAULT_GIFT = ResourceKey.create(Registries.LOOT_TABLE, id("gameplay/pig_gift/default"));
	private static final ResourceKey<LootTable> MUDDY_GIFT = ResourceKey.create(Registries.LOOT_TABLE, id("gameplay/pig_gift/muddy"));
	private static final Set<Pig> PIGS = Collections.newSetFromMap(new IdentityHashMap<>());

	private PigMechanics() {
	}

	public static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Pig pig) {
				PIGS.add(pig);
				if (!pig.hasAttached(GIFT_TIMER)) pig.setAttached(GIFT_TIMER, giftDelay(pig));
				var variant = pig.getVariant().unwrapKey().map(ResourceKey::identifier).orElse(null);
				boolean muddyVariant = variant != null && variant.getNamespace().equals("matcha") && variant.getPath().startsWith("muddy_");
				if (muddyVariant && !pig.hasAttached(MUD_TIMER)) {
					pig.setAttached(MUD_TIMER, seconds(pig, 900, 1200));
				}
				if (pig.hasAttached(MUD_TIMER)) pig.setAttached(MUDDY, true);
				syncAnimation(pig);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> PIGS.remove(entity));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> PIGS.clear());
		ServerTickEvents.END_SERVER_TICK.register(PigMechanics::tick);
	}

	public static InteractionResult wash(Pig pig, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (stack.is(Items.POTION) && stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.WATER)
				&& pig.getAttachedOrElse(MUDDY, false)) {
			if (pig.level() instanceof ServerLevel level) {
				pig.setAttached(MUD_TIMER, 0);
				clean(pig, level);
				level.sendParticles(ParticleTypes.FALLING_DRIPSTONE_WATER,
						pig.getX(), pig.getY(), pig.getZ(), 50, 0.5, 0.1, 0.5, 0.1);
				sound(pig, level, pigSounds(pig).ambientSound().value(), 1.0F);
				player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
				player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.GLASS_BOTTLE)));
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	public static void onFed(Pig pig, ServerLevel level) {
		pig.setAttached(GIFT_TIMER, pig.getAttachedOrThrow(GIFT_TIMER) - 150 * 20);
		level.broadcastEntityEvent(pig, (byte) 18);
		sound(pig, level, pigSounds(pig).ambientSound().value(), 1.0F);
	}

	private static void tick(MinecraftServer server) {
		if (!server.tickRateManager().runsNormally()) return;
		for (Pig pig : PIGS) {
			ServerLevel level = (ServerLevel) pig.level();
			if (!pig.isAlive() || pig.isRemoved() || !level.isPositionEntityTicking(pig.blockPosition())) {
				pig.removeAttached(ANIMATION_END);
				continue;
			}
			if (pig.isNoAi() || pig.getFirstPassenger() instanceof Player) pig.removeAttached(ANIMATION_END);
			int cooldown = Math.max(0, pig.getAttachedOrElse(GIFT_TIMER, 0) - 1);
			pig.setAttached(GIFT_TIMER, cooldown);
			if (pig.hasAttached(MUD_TIMER)) {
				pig.setAttached(MUD_TIMER, pig.isInWater() ? 0 : Math.max(0, pig.getAttachedOrElse(MUD_TIMER, 0) - 1));
				clean(pig, level);
			}
			if (pig.hasAttached(INFANT_TIMER) && !pig.isAgeLocked()) {
				int remaining = pig.getAttachedOrElse(INFANT_TIMER, 0) - 1;
				if (remaining <= 0) {
					pig.getAttribute(Attributes.SCALE).setBaseValue(1.0);
					pig.removeAttached(INFANT_TIMER);
				} else pig.setAttached(INFANT_TIMER, remaining);
			}
		}
	}

	public static final class ForagingGoal extends Goal {
		private final Pig pig;
		private boolean starting;

		public ForagingGoal(Pig pig) {
			this.pig = pig;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			if (!pig.isAlive() || pig.getFirstPassenger() instanceof Player) return false;
			if (animationTicks(pig) != 0) return true;
			var level = pig.level();
			return foragingCooldown(pig) == 0 && level.getBlockState(at(pig, -0.8)).is(INTERACTABLE)
					&& (level.getBlockState(at(pig, -0.8)).is(MUD) || level.getBlockState(at(pig, 0.5)).is(EDIBLE)
					|| level.getBlockState(at(pig, -1)).is(DIGGABLE));
		}

		@Override
		public boolean canContinueToUse() {
			return pig.isAlive() && !(pig.getFirstPassenger() instanceof Player) && animationTicks(pig) != 0;
		}

		@Override
		public boolean isInterruptable() {
			// Keep the existing rule that an action completes even if the ground changes or the pig panics.
			return false;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void start() {
			starting = animationTicks(pig) == 0;
			if (starting) {
				pig.setAttached(ANIMATION, pig.level().getBlockState(at(pig, -0.8)).is(MUD) ? -40 : 30);
				pig.setAttached(GIFT_TIMER, giftDelay(pig));
			}
			pig.getNavigation().stop();
			pig.getMoveControl().setWantedPosition(pig.getX(), pig.getY(), pig.getZ(), 0);
			pig.setXxa(0);
			pig.setSpeed(0);
		}

		@Override
		public void tick() {
			if (!canContinueToUse()) return;
			if (starting) starting = false;
			else tickAnimation(pig, (ServerLevel) pig.level(), animationTicks(pig));
			syncAnimation(pig);
		}

		@Override
		public void stop() {
			// Riding or disabled controls pause the saved action instead of cancelling its result.
			pig.removeAttached(ANIMATION_END);
		}
	}

	private static void tickAnimation(Pig pig, ServerLevel level, int animation) {
		int next = animation > 0 ? animation - 1 : animation + 1;
		if (next == 0) {
			pig.removeAttached(ANIMATION);
			return;
		}
		pig.setAttached(ANIMATION, next);
		int remaining = Math.abs(next);
		if (animation > 0) {
			if (remaining == 29 || remaining == 20 || remaining == 10) {
				Vec3 front = Vec3.directionFromRotation(0, pig.getYRot()).scale(0.5).add(pig.position());
				particles(level, front, Blocks.DIRT, remaining == 10 ? 50 : 20, 0.1, 0.1);
				sound(pig, level, SoundEvents.CROP_BREAK, 1.0F);
				if (remaining != 10) sound(pig, level, pigSounds(pig).ambientSound().value(), 1.0F);
			}
			if (remaining == 1) {
				finishDig(pig, level);
				sound(pig, level, pigSounds(pig).deathSound().value(), 1.5F);
			}
		} else {
			if (remaining == 39 || remaining == 25 || remaining == 10) {
				pig.setDeltaMovement(0, 0.3, 0);
				pig.needsSync = true;
				particles(level, pig.position(), Blocks.MUD, 10, 0.2, 0.1);
				sound(pig, level, pigSounds(pig).ambientSound().value(), 1.0F);
				sound(pig, level, SoundEvents.MUD_STEP, 1.0F);
			}
			if (remaining == 30 || remaining == 16 || remaining == 1) {
				particles(level, pig.position(), Blocks.MUD, 50, 0.5, 0.1);
				sound(pig, level, SoundEvents.MUD_PLACE, 1.0F);
			}
			if (remaining == 1 && level.getBlockState(at(pig, -0.8)).is(MUD)) {
				changeMudVariant(pig, true);
				pig.setAttached(MUDDY, true);
				pig.setAttached(MUD_TIMER, seconds(pig, 900, 1200));
			}
		}
		if (remaining == 1) {
			level.sendParticles(ParticleTypes.HEART, pig.getX(), pig.getY() + 1, pig.getZ(), 1, 0.3, 0.1, 0.3, 0.1);
			pig.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 2, true, false));
		}
	}

	private static void finishDig(Pig pig, ServerLevel level) {
		BlockPos crop = at(pig, 0.5);
		if (level.getBlockState(crop).is(EDIBLE)) {
			level.levelEvent(2001, crop, Block.getId(level.getBlockState(crop)));
			level.setBlock(crop, Blocks.AIR.defaultBlockState(), 3);
			if (!pig.isBaby()) {
				for (int i = 0; i < 2; i++) {
					Pig baby = EntityTypes.PIG.create(level, EntitySpawnReason.BREEDING);
					if (baby == null) continue;
					baby.setComponent(DataComponents.PIG_VARIANT, pig.getVariant());
					changeMudVariant(baby, false);
					baby.setAge(-48000);
					baby.getAttribute(Attributes.SCALE).setBaseValue(0.6);
					baby.setAttached(INFANT_TIMER, seconds(pig, 1000, 1200));
					baby.snapTo(pig.getX(), pig.getY(), pig.getZ(), pig.getYRot(), 0);
					level.addFreshEntity(baby);
					level.sendParticles(ParticleTypes.HEART, pig.getX(), pig.getY() + 1, pig.getZ(), 10, 0.3, 0.1, 0.3, 0.1);
				}
			}
		} else if (level.getBlockState(at(pig, -1)).is(DIGGABLE)) {
			level.levelEvent(2001, at(pig, -1), Block.getId(level.getBlockState(at(pig, -1))));
			level.setBlock(at(pig, -1), Blocks.DIRT.defaultBlockState(), 3);
			pig.dropFromGiftLootTable(level, pig.hasAttached(MUD_TIMER) ? MUDDY_GIFT : DEFAULT_GIFT, (world, stack) -> {
				ItemEntity item = new ItemEntity(world, pig.getX(), pig.getY() + 0.1, pig.getZ(), stack);
				item.setDeltaMovement(0, 0.3, 0);
				item.setDefaultPickUpDelay();
				world.addFreshEntity(item);
			});
		}
	}

	private static void clean(Pig pig, ServerLevel level) {
		if (pig.getAttachedOrElse(MUD_TIMER, 0) > 0 || level.getBlockState(at(pig, -0.8)).is(Blocks.MUD)) return;
		changeMudVariant(pig, false);
		pig.removeAttached(MUD_TIMER);
		pig.removeAttached(MUDDY);
		particles(level, pig.position(), Blocks.MUD, 50, 0.25, 0.5);
		sound(pig, level, SoundEvents.GENERIC_SPLASH, 1.0F);
	}

	private static void changeMudVariant(Pig pig, boolean muddy) {
		var variant = pig.getVariant().unwrapKey().map(ResourceKey::identifier).orElse(null);
		if (variant == null || !variant.getNamespace().equals(muddy ? "minecraft" : "matcha")) return;
		String name = variant.getPath();
		if (!muddy) {
			if (!name.startsWith("muddy_")) return;
			name = name.substring("muddy_".length());
		}
		if (!Set.of("temperate", "warm", "cold").contains(name)) return;
		Identifier target = muddy ? id("muddy_" + name) : Identifier.withDefaultNamespace(name);
		pig.registryAccess().lookupOrThrow(Registries.PIG_VARIANT).get(ResourceKey.create(Registries.PIG_VARIANT, target))
				.ifPresent(holder -> pig.setComponent(DataComponents.PIG_VARIANT, holder));
	}

	public static int animationTicks(Pig pig) {
		if (pig.level().isClientSide()) {
			return animationRemaining(pig.getAttachedOrElse(ANIMATION_END, 0L), pig.level().getGameTime());
		}
		return pig.getAttachedOrElse(ANIMATION, 0);
	}

	private static void syncAnimation(Pig pig) {
		int remaining = animationTicks(pig);
		if (remaining == 0) {
			pig.removeAttached(ANIMATION_END);
			return;
		}
		long end = pig.level().getGameTime() + Math.abs(remaining);
		pig.setAttached(ANIMATION_END, remaining > 0 ? end : -end);
	}

	static int animationRemaining(long end, long gameTime) {
		if (end == 0) return 0;
		int remaining = (int) Math.clamp(Math.abs(end) - gameTime, 0L, end > 0 ? 30L : 40L);
		return end > 0 ? remaining : -remaining;
	}

	static int foragingCooldown(Pig pig) {
		return Math.max(0, pig.getAttachedOrElse(GIFT_TIMER, 0));
	}

	public static int diggingPitch(int remaining) {
		if (remaining >= 25 && remaining <= 30 || remaining >= 15 && remaining <= 20 || remaining >= 6 && remaining <= 10) return 90;
		if (remaining == 23 || remaining == 24 || remaining == 13 || remaining == 14) return 40;
		if (remaining == 21 || remaining == 22 || remaining == 11 || remaining == 12 || remaining == 4 || remaining == 5) return 20;
		return remaining >= 1 && remaining <= 3 ? -90 : 0;
	}

	private static int giftDelay(Pig pig) {
		return pig.hasAttached(MUD_TIMER) ? seconds(pig, 80, 150) : seconds(pig, 300, 600);
	}

	private static int seconds(Pig pig, int min, int max) {
		return (min + pig.getRandom().nextInt(max - min + 1)) * 20;
	}

	private static BlockPos at(Pig pig, double yOffset) {
		return BlockPos.containing(pig.getX(), pig.getY() + yOffset, pig.getZ());
	}

	private static void particles(ServerLevel level, Vec3 pos, Block block, int count, double spread, double height) {
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, block.defaultBlockState()),
				pos.x, pos.y, pos.z, count, spread, height, spread, 0.1);
	}

	private static void sound(Pig pig, ServerLevel level, SoundEvent sound, float pitch) {
		level.playSound(null, pig.blockPosition(), sound, SoundSource.NEUTRAL, 1.0F, pitch);
	}

	private static PigSoundVariant.PigSoundSet pigSounds(Pig pig) {
		var variant = pig.get(DataComponents.PIG_SOUND_VARIANT).value();
		return pig.isBaby() ? variant.babySounds() : variant.adultSounds();
	}

	private static AttachmentType<Integer> timer(String name) {
		return AttachmentRegistry.create(id(name), builder -> builder.persistent(Codec.INT));
	}

	private static TagKey<Block> blockTag(String path) {
		return TagKey.create(Registries.BLOCK, id("animal_affects/" + path));
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("matcha", path);
	}
}
