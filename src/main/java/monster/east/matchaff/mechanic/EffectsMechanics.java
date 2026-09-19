package monster.east.matchaff.mechanic;

import net.minecraft.ChatFormatting;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Soul Sight: eating glow jam / glow crumble / glow mash grants a delayed Glowing effect
 * to every entity within 50 blocks. Detection reuses the datapack's own
 * consume_item advancements: once granted, the effect fires and the
 * advancement is revoked so the next bite re-triggers it.
 */
public final class EffectsMechanics {
	private static final Identifier[] SOUL_SIGHT_ADVANCEMENTS = {
			Identifier.fromNamespaceAndPath("matcha", "mechanics/glow_jam_eaten"),       // 30s
			Identifier.fromNamespaceAndPath("matcha", "mechanics/glow_crumble_eaten"),   // 60s
			Identifier.fromNamespaceAndPath("matcha", "mechanics/glow_mash_eaten"),      // 3s
	};
	private static final int[] SOUL_SIGHT_DURATIONS = {600, 1200, 60};
	private static final String[] WITHER_COUNTDOWN = {
			"\uE048\uE046\uE046\uE047",
			"\uE048\uE046\uE046\uE049",
			"\uE048\uE046\uE04B\uE049",
			"\uE048\uE046\uE044\uE049",
			"\uE048\uE04B\uE044\uE049",
			"\uE048\uE044\uE044\uE049",
			"\uE04A\uE044\uE044\uE049",
			"\uE04A\uE044\uE044\uE049"
	};

	/** Player UUID -> one [trigger tick, duration] task for each Soul Sight variant. */
	private static final Map<UUID, int[][]> PENDING_SOUL_SIGHT = new HashMap<>();
	private static final Map<UUID, Integer> WITHER_TICKS = new HashMap<>();

	private EffectsMechanics() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PENDING_SOUL_SIGHT.clear();
			WITHER_TICKS.clear();
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				PENDING_SOUL_SIGHT.remove(player.getUUID());
				WITHER_TICKS.remove(player.getUUID());
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
		{
			PENDING_SOUL_SIGHT.remove(handler.getPlayer().getUUID());
			WITHER_TICKS.remove(handler.getPlayer().getUUID());
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!server.tickRateManager().runsNormally()) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tick(player);
				tickWither(player);
			}
		});
	}

	private static void tickWither(ServerPlayer player) {
		UUID uuid = player.getUUID();
		if (WorldMechanics.cachedDifficulty(player.level().getServer()) == Difficulty.EASY
				|| !player.hasEffect(MobEffects.WITHER)) {
			WITHER_TICKS.remove(uuid);
			return;
		}

		int ticks = WITHER_TICKS.merge(uuid, 1, Integer::sum);
		if (ticks % 20 != 0) {
			return;
		}
		int seconds = ticks / 20;
		if (seconds <= 8) {
			Component bar = Component.literal("\uE010").withStyle(ChatFormatting.RED)
					.append(Component.literal(" " + WITHER_COUNTDOWN[seconds - 1])
							.withStyle(seconds == 8 ? ChatFormatting.RED : ChatFormatting.WHITE));
			player.connection.send(new ClientboundSetActionBarTextPacket(bar));
		}
		if (seconds < 8) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5F, 1.0F);
			return;
		}

		player.removeEffect(MobEffects.WITHER);
		PlayerMechanics.loseHeart(player);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.25F, 1.0F);
		player.hurtServer(player.level(), player.level().damageSources().generic(), 0.1F);
		WITHER_TICKS.remove(uuid);
	}

	private static void tick(ServerPlayer player) {
		var advancements = player.level().getServer().getAdvancements();
		for (int i = 0; i < SOUL_SIGHT_ADVANCEMENTS.length; i++) {
			AdvancementHolder advancement = advancements.get(SOUL_SIGHT_ADVANCEMENTS[i]);
			if (advancement == null || !player.getAdvancements().getOrStartProgress(advancement).isDone()) {
				continue;
			}
			var level = player.level();
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 2.0F, 1.0F);
			PENDING_SOUL_SIGHT.computeIfAbsent(player.getUUID(), uuid ->
					new int[SOUL_SIGHT_ADVANCEMENTS.length][])[i] = new int[] {
					player.level().getServer().getTickCount() + 48, SOUL_SIGHT_DURATIONS[i]
			};
			for (String criterion : advancement.value().criteria().keySet()) {
				player.getAdvancements().revoke(advancement, criterion);
			}
		}

		int[][] pending = PENDING_SOUL_SIGHT.get(player.getUUID());
		if (pending == null) {
			return;
		}
		int now = player.level().getServer().getTickCount();
		boolean hasPending = false;
		for (int i = 0; i < pending.length; i++) {
			int[] task = pending[i];
			if (task == null) {
				continue;
			}
			if (now >= task[0]) {
				pending[i] = null;
				applyGlow(player, task[1]);
			} else {
				hasPending = true;
			}
		}
		if (!hasPending) {
			PENDING_SOUL_SIGHT.remove(player.getUUID());
		}
	}

	private static void applyGlow(ServerPlayer player, int duration) {
		var level = player.level();
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
				player.getBoundingBox().inflate(50.0), target -> {
					double distanceSquared = target.distanceToSqr(player);
					return distanceSquared >= 0.01 && distanceSquared <= 2500.0;
				})) {
			entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, duration, 0, true, false));
		}
	}
}
