package monster.east.matchaff.mechanic;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * Replaces the datapack's regeneration-based food healing with an independent
 * per-food tick schedule. Regeneration refreshes in place (duration is never
 * stacked), so eating two steaks in a row silently loses part of the second
 * steak's heal; scheduling each consumed food on its own timer reproduces the
 * intended totals exactly (same interval, same amount per tick).
 */
public final class FoodHealMechanics {
	public static final AttachmentType<Boolean> HEALING_ANIMATION = AttachmentRegistry.create(
			Identifier.fromNamespaceAndPath("matcha", "food_healing_animation"),
			builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly()));

	static final class HealTask {
		private static final Codec<HealTask> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.intRange(1, Integer.MAX_VALUE).fieldOf("ticks_left").forGetter(task -> task.ticksLeft),
				Codec.intRange(0, Integer.MAX_VALUE).fieldOf("interval").forGetter(task -> task.interval),
				MobEffectInstance.CODEC.optionalFieldOf("regeneration").forGetter(task -> Optional.ofNullable(task.visibleEffect)),
				Codec.BOOL.optionalFieldOf("started", false).forGetter(task -> task.started)
		).apply(instance, HealTask::new));

		int ticksLeft;
		final int interval;
		final MobEffectInstance visibleEffect;
		boolean started;

		private HealTask(int ticksLeft, int interval, Optional<MobEffectInstance> visibleEffect, boolean started) {
			this.ticksLeft = ticksLeft;
			this.interval = interval;
			this.visibleEffect = visibleEffect.orElse(null);
			this.started = started;
		}

		HealTask(MobEffectInstance segment, int duration) {
			this.ticksLeft = duration;
			if (isHealingSimulation(segment)) {
				this.interval = healingInterval(segment);
				this.visibleEffect = null;
			} else {
				this.interval = 0;
				this.visibleEffect = new MobEffectInstance(segment.getEffect(), duration, segment.getAmplifier(),
						segment.isAmbient(), segment.isVisible(), segment.showIcon());
			}
		}

		boolean start(ServerPlayer player) {
			if (started) {
				return false;
			}
			started = true;
			return visibleEffect != null && player.addEffect(new MobEffectInstance(visibleEffect));
		}
	}

	/** One food's regeneration chain (fast segment then lingering segment), run in sequence. */
	static final class HealChain {
		private static final Codec<HealChain> CODEC = HealTask.CODEC.listOf().xmap(
				tasks -> new HealChain(new ArrayDeque<>(tasks)), chain -> List.copyOf(chain.tasks));
		final Deque<HealTask> tasks;

		private HealChain(Deque<HealTask> tasks) {
			this.tasks = tasks;
		}

		HealChain(List<MobEffectInstance> chain) {
			this(new ArrayDeque<HealTask>());
			// Vanilla hides the lower-amplitude segment but ticks its duration down
			// while the main effect runs, so each following segment only has
			// (original - elapsed) ticks left once it takes over.
			int elapsed = 0;
			for (MobEffectInstance segment : chain) {
				int effective = segment.getDuration() - elapsed;
				if (effective <= 0) {
					break;
				}
				tasks.add(new HealTask(segment, effective));
				elapsed += effective;
			}
		}

		boolean cancelRegeneration() {
			tasks.removeIf(task -> task.visibleEffect != null);
			return tasks.isEmpty();
		}

		boolean isHealing() {
			HealTask task = tasks.peek();
			return task != null && task.interval > 0 && task.visibleEffect == null && task.ticksLeft > 0;
		}
	}

	private static final AttachmentType<List<HealChain>> PENDING = AttachmentRegistry.createPersistent(
			Identifier.fromNamespaceAndPath("matcha", "pending_food_healing"),
			HealChain.CODEC.listOf().<List<HealChain>>xmap(ArrayList::new, chains -> chains));

	private FoodHealMechanics() {
	}

	public static void init() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				cancelHealing(player);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!server.tickRateManager().runsNormally()) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (EffectsMechanics.isHealingBlocked(player)) {
					cancelHealing(player);
					continue;
				}
				List<HealChain> chains = player.getAttached(PENDING);
				if (chains == null || chains.isEmpty()) {
					syncAnimation(player);
					continue;
				}
				chains.removeIf(chain -> {
					HealTask task = chain.tasks.peek();
					if (task == null) {
						return true;
					}
					task.start(player);
					// Mirrors RegenerationMobEffect: heal when duration % interval == 0,
					// then decrement; real regeneration segments are applied normally.
					if (task.visibleEffect == null && task.interval > 0 && task.ticksLeft % task.interval == 0
							&& player.getHealth() < player.getMaxHealth()) {
						player.heal(1.0F);
					}
					task.ticksLeft--;
					if (task.ticksLeft <= 0) {
						chain.tasks.remove();
					}
					return chain.tasks.isEmpty();
				});
				if (chains.isEmpty()) {
					player.removeAttached(PENDING);
				}
				syncAnimation(player);
			}
		});
	}

	/**
	 * Hooks from FoodHealMixin: schedules the hidden healing-simulation segments
	 * and applies real regeneration segments only when their place in the vanilla
	 * hidden-effect chain begins.
	 */
	public static boolean onConsumed(ServerPlayer player, List<MobEffectInstance> regens) {
		if (EffectsMechanics.isHealingBlocked(player)) {
			return false;
		}
		List<MobEffectInstance> chain = buildChain(regens);
		if (chain.isEmpty()) {
			return false;
		}
		HealChain healChain = new HealChain(chain);
		HealTask first = healChain.tasks.peek();
		if (first == null) {
			return false;
		}
		boolean applied = first.start(player);
		player.getAttachedOrCreate(PENDING, ArrayList::new).add(healChain);
		syncAnimation(player);
		return applied;
	}

	public static void cancelHealing(ServerPlayer player) {
		player.removeAttached(PENDING);
		player.removeAttached(HEALING_ANIMATION);
	}

	public static void cancelPendingRegeneration(ServerPlayer player) {
		List<HealChain> chains = player.getAttached(PENDING);
		if (chains != null) {
			chains.removeIf(HealChain::cancelRegeneration);
			if (chains.isEmpty()) {
				player.removeAttached(PENDING);
			}
		}
		syncAnimation(player);
	}

	private static void syncAnimation(ServerPlayer player) {
		List<HealChain> chains = player.getAttached(PENDING);
		boolean active = chains != null && chains.stream().anyMatch(HealChain::isHealing);
		if (active != player.getAttachedOrElse(HEALING_ANIMATION, false)) {
			if (active) {
				player.setAttached(HEALING_ANIMATION, true);
			} else {
				player.removeAttached(HEALING_ANIMATION);
			}
		}
	}

	public static int foodHealingPoints(Consumable consumable) {
		int healingPoints = 0;
		for (var consumeEffect : consumable.onConsumeEffects()) {
			if (consumeEffect instanceof ApplyStatusEffectsConsumeEffect apply && apply.probability() > 0.0F) {
				List<MobEffectInstance> regens = apply.effects().stream()
						.filter(effect -> effect.getEffect() == MobEffects.REGENERATION)
						.toList();
				healingPoints += firstHealingPoints(regens);
			}
		}
		return healingPoints;
	}

	private static int firstHealingPoints(List<MobEffectInstance> regens) {
		int elapsed = 0;
		for (MobEffectInstance segment : buildChain(regens)) {
			int effectiveDuration = segment.getDuration() - elapsed;
			if (effectiveDuration <= 0) {
				break;
			}
			if (isHealingSimulation(segment)) {
				return effectiveDuration / healingInterval(segment);
			}
			elapsed += effectiveDuration;
		}
		return 0;
	}

	private static boolean isHealingSimulation(MobEffectInstance segment) {
		// Matcha's direct food-heal segments are hidden Regeneration III/IV.
		// Lower amplifiers are genuine timed effects even when their icon is hidden,
		// as on the enchanted golden apple's Regeneration II.
		return !segment.showIcon() && segment.getAmplifier() >= 2;
	}

	private static int healingInterval(MobEffectInstance segment) {
		return Math.max(1, 50 >> segment.getAmplifier());
	}

	/**
	 * Rebuilds the effect chain vanilla would produce when applying several
	 * regeneration instances: the highest amplifier leads, lower-amplitude
	 * longer effects follow as the hidden chain (e.g. baked apple heals fast
	 * from amp 2, then keeps regenerating from amp 0).
	 */
	private static List<MobEffectInstance> buildChain(List<MobEffectInstance> regens) {
		List<MobEffectInstance> chain = new ArrayList<>();
		for (MobEffectInstance next : regens) {
			if (chain.isEmpty()) {
				chain.add(next);
				continue;
			}
			MobEffectInstance main = chain.getFirst();
			if (next.getAmplifier() > main.getAmplifier()) {
				if (next.getDuration() < main.getDuration()) {
					chain.add(1, main);
				}
				chain.set(0, next);
			} else if (main.getDuration() < next.getDuration()) {
				if (next.getAmplifier() == main.getAmplifier()) {
					chain.set(0, next);
				} else {
					chain.add(next);
				}
			}
		}
		return chain;
	}
}
