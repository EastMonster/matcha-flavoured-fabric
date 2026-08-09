package monster.east.matchaff;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Replaces the datapack's regeneration-based food healing with an independent
 * per-food tick schedule. Regeneration refreshes in place (duration is never
 * stacked), so eating two steaks in a row silently loses part of the second
 * steak's heal; scheduling each consumed food on its own timer reproduces the
 * intended totals exactly (same interval, same amount per tick).
 */
public final class FoodHealMechanics {
	private static final class HealTask {
		int ticksLeft;
		final int interval;
		final MobEffectInstance visibleEffect;
		boolean started;

		HealTask(MobEffectInstance segment, int duration) {
			this.ticksLeft = duration;
			if (isHealingSimulation(segment)) {
				this.interval = Math.max(1, 50 >> segment.getAmplifier());
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
	private static final class HealChain {
		final Deque<HealTask> tasks = new ArrayDeque<>();

		HealChain(List<MobEffectInstance> chain) {
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
	}

	private static final Map<UUID, List<HealChain>> PENDING = new HashMap<>();

	private FoodHealMechanics() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING.clear());
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				PENDING.remove(player.getUUID());
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				List<HealChain> chains = PENDING.get(player.getUUID());
				if (chains == null || chains.isEmpty()) {
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
					if (task.visibleEffect == null && task.ticksLeft % task.interval == 0
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
					PENDING.remove(player.getUUID());
				}
			}
		});
	}

	/**
	 * Hooks from FoodHealMixin: schedules the hidden healing-simulation segments
	 * and applies real regeneration segments only when their place in the vanilla
	 * hidden-effect chain begins.
	 */
	public static boolean onConsumed(ServerPlayer player, List<MobEffectInstance> regens) {
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
		PENDING.computeIfAbsent(player.getUUID(), key -> new ArrayList<>()).add(healChain);
		return applied;
	}

	private static boolean isHealingSimulation(MobEffectInstance segment) {
		// Matcha's direct food-heal segments are hidden Regeneration III/IV.
		// Lower amplifiers are genuine timed effects even when their icon is hidden,
		// as on the enchanted golden apple's Regeneration II.
		return !segment.showIcon() && segment.getAmplifier() >= 2;
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
			MobEffectInstance main = chain.get(0);
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
