package monster.east.matchaff.mechanic;

import net.minecraft.ChatFormatting;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

/**
 * Aura: consuming an item with an Aura marker charges a nearby Glowing effect.
 * Repeated consumption during the windup changes its duration, not its deadline.
 */
public final class EffectsMechanics {
	private static final TagKey<EntityType<?>> AURA_IMMUNE = TagKey.create(
			Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("matcha", "aura_immune"));
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

	private static final Map<UUID, AuraCharge> PENDING_AURA = new HashMap<>();
	private static final Map<UUID, Integer> WITHER_TICKS = new HashMap<>();
	private static final Set<UUID> HUNGRY_PLAYERS = new HashSet<>();

	private EffectsMechanics() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PENDING_AURA.clear();
			WITHER_TICKS.clear();
			HUNGRY_PLAYERS.clear();
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				PENDING_AURA.remove(player.getUUID());
				WITHER_TICKS.remove(player.getUUID());
				HUNGRY_PLAYERS.remove(player.getUUID());
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
		{
			PENDING_AURA.remove(handler.getPlayer().getUUID());
			WITHER_TICKS.remove(handler.getPlayer().getUUID());
			HUNGRY_PLAYERS.remove(handler.getPlayer().getUUID());
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!server.tickRateManager().runsNormally()) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tick(player);
				tickWither(player);
				tickHunger(player);
			}
		});
	}

	public static boolean isHealingBlocked(ServerPlayer player) {
		return blocksHealing(WorldMechanics.cachedDifficulty(player.level().getServer()),
				player.hasEffect(MobEffects.HUNGER));
	}

	static boolean blocksHealing(Difficulty difficulty, boolean hungry) {
		return hungry && (difficulty == Difficulty.NORMAL || difficulty == Difficulty.HARD);
	}

	static String hungerBar(int duration) {
		int segments = Math.clamp(duration / 20, 0, 10);
		if (segments == 0) {
			return "\uE048\uE046\uE046\uE046\uE046\uE047";
		}
		if (segments == 10) {
			return "\uE04A\uE044\uE044\uE044\uE044\uE049";
		}
		int middle = segments - 1;
		return "\uE04A" + "\uE044".repeat(middle / 2)
				+ (middle % 2 == 1 ? "\uE045" : "")
				+ "\uE046".repeat(4 - (middle + 1) / 2) + "\uE047";
	}

	private static void tickHunger(ServerPlayer player) {
		UUID uuid = player.getUUID();
		if (!isHealingBlocked(player)) {
			if (HUNGRY_PLAYERS.remove(uuid)) {
				player.connection.send(new ClientboundSetActionBarTextPacket(
						Component.literal("\uE007 " + hungerBar(0)).withStyle(ChatFormatting.WHITE)));
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5F, 1.0F);
			}
			return;
		}
		player.removeEffect(MobEffects.REGENERATION);
		FoodHealMechanics.cancelHealing(player);
		boolean first = HUNGRY_PLAYERS.add(uuid);
		if (!first && player.level().getServer().getTickCount() % 20 != 0) {
			return;
		}
		MobEffectInstance hunger = player.getEffect(MobEffects.HUNGER);
		int duration = hunger.isInfiniteDuration() ? Integer.MAX_VALUE : hunger.getDuration();
		player.connection.send(new ClientboundSetActionBarTextPacket(
				Component.literal("\uE04C " + hungerBar(duration)).withColor(0x73aa55)));
		if (duration < 1000) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 0.35F, 2.0F);
		}
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

	public static void onConsumed(ServerPlayer player, ItemStack stack) {
		var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		for (int seconds : new int[] {3, 30, 45, 60}) {
			if (data.getCompound("matcha:aura_" + seconds + "s").isEmpty()) {
				continue;
			}
			UUID uuid = player.getUUID();
			AuraCharge pending = PENDING_AURA.get(uuid);
			if (pending == null) {
				player.connection.send(new ClientboundSoundPacket(Holder.direct(SoundEvents.BELL_RESONATE),
						SoundSource.PLAYERS, player.getX(), player.getY(), player.getZ(),
						2.0F, 1.0F, player.getRandom().nextLong()));
			}
			PENDING_AURA.put(uuid, AuraCharge.startOrUpdate(pending,
					player.level().getServer().getTickCount(), seconds * 20));
		}
	}

	private static void tick(ServerPlayer player) {
		AuraCharge pending = PENDING_AURA.get(player.getUUID());
		if (pending == null) {
			return;
		}
		if (pending.ready(player.level().getServer().getTickCount())) {
			PENDING_AURA.remove(player.getUUID());
			applyGlow(player, pending.duration());
		}
	}

	private static void applyGlow(ServerPlayer player, int duration) {
		var level = player.level();
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
				player.getBoundingBox().inflate(50.0), target -> {
					double distanceSquared = target.distanceToSqr(player);
					return distanceSquared >= 0.01 && distanceSquared <= 2500.0
							&& !target.getType().builtInRegistryHolder().is(AURA_IMMUNE);
				})) {
			entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, duration, 0, false, false), player);
		}
	}
}
