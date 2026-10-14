package monster.east.matchaff.enchantment;

import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.effects.EnchantmentEntityEffect;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/** Replaces the upstream buffer function; all activation conditions remain in the enchantment JSON. */
public record TraversalEnchantment() implements EnchantmentEntityEffect {
	private static final String BUFFER = "TraversalBuffer";
	private static final MapCodec<TraversalEnchantment> CODEC = MapCodec.unit(new TraversalEnchantment());

	static void init() {
		Registry.register(BuiltInRegistries.ENCHANTMENT_ENTITY_EFFECT_TYPE,
				EnchantmentUtil.id("traversal_buffer"), CODEC);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			var scoreboard = server.getScoreboard();
			if (scoreboard.getObjective(BUFFER) == null) {
				scoreboard.addObjective(BUFFER, ObjectiveCriteria.DUMMY, Component.literal(BUFFER),
						ObjectiveCriteria.RenderType.INTEGER, true, StyledFormat.NO_STYLE);
			}
		});
		ServerTickEvents.START_SERVER_TICK.register(server -> {
			if (!server.tickRateManager().runsNormally()) {
				return;
			}
			var scoreboard = server.getScoreboard();
			var objective = scoreboard.getObjective(BUFFER);
			if (objective == null) {
				return;
			}
			for (var player : server.getPlayerList().getPlayers()) {
				var score = scoreboard.getPlayerScoreInfo(player, objective);
				if (score != null && score.value() > 0) {
					scoreboard.getOrCreatePlayerScore(player, objective).set(score.value() - 1);
				}
			}
		});
	}

	@Override
	public void apply(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, Entity entity, Vec3 position) {
		var scoreboard = level.getServer().getScoreboard();
		var objective = scoreboard.getObjective(BUFFER);
		if (objective != null) {
			var score = scoreboard.getOrCreatePlayerScore(entity, objective);
			score.set(recharge(score.get()));
		}
	}

	static int recharge(int buffer) {
		return buffer < 20 ? buffer + 2 : buffer;
	}

	@Override
	public MapCodec<TraversalEnchantment> codec() {
		return CODEC;
	}
}
