package monster.east.matchaff.mechanic;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import monster.east.matchaff.enchantment.EnchantmentMechanics;
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
	private static final Identifier HEARTBREAKER = Identifier.fromNamespaceAndPath("matcha", "hell/wither_breaks_heart");

	private static final Map<UUID, AuraCharge> PENDING_AURA = new HashMap<>();
	private static final AttachmentType<WitherCountdown> WITHER_TICKS = AttachmentRegistry.create(
			Identifier.fromNamespaceAndPath("matcha", "wither_countdown"),
			builder -> builder.persistent(WitherCountdown.CODEC));
	private static final Set<UUID> HUNGRY_PLAYERS = new HashSet<>();

	private EffectsMechanics() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PENDING_AURA.clear();
			HUNGRY_PLAYERS.clear();
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				PENDING_AURA.remove(player.getUUID());
				player.removeAttached(WITHER_TICKS);
				HUNGRY_PLAYERS.remove(player.getUUID());
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
		{
			PENDING_AURA.remove(handler.getPlayer().getUUID());
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
		Difficulty difficulty = WorldMechanics.cachedDifficulty(player.level().getServer());
		if ((difficulty != Difficulty.NORMAL && difficulty != Difficulty.HARD)
				|| !player.hasEffect(MobEffects.WITHER)) {
			player.removeAttached(WITHER_TICKS);
			return;
		}

		WitherCountdown countdown = player.getAttached(WITHER_TICKS);
		if (countdown == null) {
			countdown = new WitherCountdown(0, witherBudget(EnchantmentMechanics.shakudoWitherResistance(player)));
		}
		countdown = countdown.tick();
		player.setAttached(WITHER_TICKS, countdown);
		if (countdown.ticks() % 20 != 0) {
			return;
		}
		int remaining = countdown.remaining();
		boolean broken = remaining <= witherThreshold(difficulty);
		var bar = Component.literal("\uE010").withStyle(ChatFormatting.RED)
				.append(Component.literal(" " + witherBar(difficulty, remaining))
						.withStyle(broken ? ChatFormatting.RED : ChatFormatting.WHITE));
		if (remaining >= 9) {
			String resistanceBar = switch (remaining) {
				case 13 -> "\uE04F\uE044\uE049";
				case 12 -> "\uE04F\uE044\uE047";
				case 11 -> "\uE04F\uE045\uE047";
				case 10 -> "\uE04F\uE046\uE047";
				default -> "\uE052\uE046\uE047";
			};
			bar.append(Component.literal(" " + resistanceBar)
					.withStyle(remaining == 9 ? ChatFormatting.RED : ChatFormatting.AQUA));
		}
		player.connection.send(new ClientboundSetActionBarTextPacket(bar));
		var sound = remaining >= 10 ? SoundEvents.LODESTONE_COMPASS_LOCK
				: remaining == 9 ? SoundEvents.SHIELD_BREAK.value() : SoundEvents.EXPERIENCE_ORB_PICKUP;
		if (!broken || difficulty == Difficulty.HARD) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
					sound, SoundSource.PLAYERS, remaining == 9 ? 1.0F : 0.5F, remaining >= 9 ? 2.0F : 1.0F);
		}
		if (!broken) {
			return;
		}

		player.removeEffect(MobEffects.WITHER);
		PlayerMechanics.loseHeart(player);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.25F, 0.0F);
		player.hurtServer(player.level(), player.level().damageSources().generic(), 0.1F);
		var advancement = player.level().getServer().getAdvancements().get(HEARTBREAKER);
		if (advancement != null) {
			player.getAdvancements().award(advancement, "get_heart_broken");
		}
		player.removeAttached(WITHER_TICKS);
	}

	static int witherBudget(int resistance) {
		return resistance <= 0 ? 9 : 10 + Math.clamp(resistance, 1, 4);
	}

	static int witherThreshold(Difficulty difficulty) {
		return difficulty == Difficulty.HARD ? 3 : 0;
	}

	static String witherBar(Difficulty difficulty, int remaining) {
		if (difficulty == Difficulty.HARD) {
			return switch (remaining) {
				case 3, 4 -> "\uE04A\uE044\uE049";
				case 5 -> "\uE048\uE044\uE049";
				case 6 -> "\uE048\uE04B\uE049";
				case 7 -> "\uE048\uE046\uE049";
				default -> "\uE048\uE046\uE047";
			};
		}
		return switch (remaining) {
			case 0, 1 -> "\uE04A\uE044\uE044\uE044\uE049";
			case 2 -> "\uE048\uE044\uE044\uE044\uE049";
			case 3 -> "\uE048\uE04B\uE044\uE044\uE049";
			case 4 -> "\uE048\uE046\uE044\uE044\uE049";
			case 5 -> "\uE048\uE046\uE04B\uE044\uE049";
			case 6 -> "\uE048\uE046\uE046\uE044\uE049";
			case 7 -> "\uE048\uE046\uE046\uE04B\uE049";
			case 8 -> "\uE048\uE046\uE046\uE046\uE049";
			default -> "\uE048\uE046\uE046\uE046\uE047";
		};
	}

	// Snapshot resistance at the start; changing armour must not refill an active countdown.
	record WitherCountdown(int ticks, int remaining) {
		static final Codec<WitherCountdown> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.intRange(0, 280).fieldOf("ticks").forGetter(WitherCountdown::ticks),
				Codec.intRange(0, 14).fieldOf("remaining").forGetter(WitherCountdown::remaining)
		).apply(instance, WitherCountdown::new));

		WitherCountdown tick() {
			int next = ticks + 1;
			return new WitherCountdown(next, remaining - (next % 20 == 0 ? 1 : 0));
		}
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
