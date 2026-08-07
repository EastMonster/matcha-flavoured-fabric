package monster.east.matchaff;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Soul Sight: eating glow jam / glow crumble grants a delayed Glowing effect
 * to every entity within 50 blocks. Detection reuses the datapack's own
 * consume_item advancements: once granted, the effect fires and the
 * advancement is revoked so the next bite re-triggers it.
 */
public final class EffectsMechanics {
	private static final Identifier[] SOUL_SIGHT_ADVANCEMENTS = {
			Identifier.fromNamespaceAndPath("main", "mechanics/glow_jam_eaten"),       // 30s
			Identifier.fromNamespaceAndPath("main", "mechanics/glow_crumble_eaten"),   // 60s
			Identifier.fromNamespaceAndPath("main", "mechanics/glow_mash_eaten"),      // 3s
	};
	private static final int[] SOUL_SIGHT_DURATIONS = {600, 1200, 60};

	/** Player UUID -> [tick the glow triggers, duration in ticks]. */
	private static final Map<UUID, int[]> PENDING_SOUL_SIGHT = new HashMap<>();

	private EffectsMechanics() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tick(player);
			}
		});
	}

	private static void tick(ServerPlayer player) {
		var advancements = player.level().getServer().getAdvancements();
		for (int i = 0; i < SOUL_SIGHT_ADVANCEMENTS.length; i++) {
			AdvancementHolder advancement = advancements.get(SOUL_SIGHT_ADVANCEMENTS[i]);
			if (advancement == null || !player.getAdvancements().getOrStartProgress(advancement).isDone()) {
				continue;
			}
			var level = (ServerLevel) player.level();
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 2.0F, 1.0F);
			PENDING_SOUL_SIGHT.put(player.getUUID(), new int[] {player.tickCount + 48, SOUL_SIGHT_DURATIONS[i]});
			for (String criterion : advancement.value().criteria().keySet()) {
				player.getAdvancements().revoke(advancement, criterion);
			}
		}

		int[] pending = PENDING_SOUL_SIGHT.get(player.getUUID());
		if (pending != null && player.tickCount >= pending[0]) {
			PENDING_SOUL_SIGHT.remove(player.getUUID());
			applyGlow(player, pending[1]);
		}
	}

	private static void applyGlow(ServerPlayer player, int duration) {
		var level = (ServerLevel) player.level();
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
				player.getBoundingBox().inflate(50.0), target -> {
					double distanceSquared = target.distanceToSqr(player);
					return distanceSquared >= 0.01 && distanceSquared <= 2500.0;
				})) {
			entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, duration, 0, true, false));
		}
	}
}
