package monster.east.matchaff.enchantment;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Initializes the custom enchantments whose behaviour cannot live entirely in data. */
public final class EnchantmentMechanics {
	private static final List<Function<ServerPlayer, Iterable<ItemStack>>> EXTRA_HEAD_ITEMS = new ArrayList<>();
	private static final AttachmentType<Integer> DIVINITY_COOLDOWN = AttachmentRegistry.create(
			EnchantmentUtil.id("divinity_cooldown"), builder -> builder.persistent(Codec.INT).copyOnDeath());

	private EnchantmentMechanics() {
	}

	public static void registerExtraHeadItems(Function<ServerPlayer, ? extends Iterable<ItemStack>> provider) {
		EXTRA_HEAD_ITEMS.add(player -> provider.apply(player));
	}

	public static void init() {
		AnemosEnchantment.init();
		TraversalEnchantment.init();
		ShakudoRegenEnchantment.init();
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!server.tickRateManager().runsNormally()) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				Registry<Enchantment> enchantments = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
				List<ItemStack> extraHeadItems = extraHeadItems(player);
				WardingEnchantment.tick(player, enchantments);
				AdamantArmourEnchantment.tick(player, enchantments);
				tickDivinity(player, enchantments);
				BloodrageEnchantment.tick(player, enchantments);
				CleanseEnchantment.tick(player, enchantments);
				ShakudoRegenEnchantment.tick(player, enchantments);
				ConduitPowerEnchantment.tick(player, enchantments, extraHeadItems);
				FireProofEnchantment.tick(player, enchantments, extraHeadItems);
				HasteEnchantment.tick(player, enchantments, extraHeadItems);
				RegenerationEnchantment.tick(player, enchantments, extraHeadItems);
				ZephyrEnchantment.tick(player, enchantments);
				SlaughterEnchantment.tick(player, enchantments);
			}
		});
	}

	public static int shakudoWitherResistance(ServerPlayer player) {
		Registry<Enchantment> enchantments = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		return Math.clamp(ShakudoRegenEnchantment.armourPower(player, enchantments), 0, 4);
	}

	private static void tickDivinity(ServerPlayer player, Registry<Enchantment> enchantments) {
		boolean active = EnchantmentUtil.countArmor(player, enchantments, EnchantmentUtil.id("adamant_armour")) == 4
				|| EnchantmentUtil.countArmor(player, enchantments, EnchantmentUtil.id("electrum_armour")) == 4;
		int remaining = player.getAttachedOrElse(DIVINITY_COOLDOWN, -1);
		if (active && remaining == 0) {
			player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 620, 0, false, false));
		}
		player.setAttached(DIVINITY_COOLDOWN, nextDivinityCooldown(remaining, active));
	}

	static int nextDivinityCooldown(int remaining, boolean active) {
		return active && remaining <= 0 ? 599 : Math.max(-1, remaining - 1);
	}

	private static List<ItemStack> extraHeadItems(ServerPlayer player) {
		if (EXTRA_HEAD_ITEMS.isEmpty()) {
			return List.of();
		}
		List<ItemStack> items = new ArrayList<>();
		for (Function<ServerPlayer, Iterable<ItemStack>> provider : EXTRA_HEAD_ITEMS) {
			for (ItemStack stack : provider.apply(player)) {
				items.add(stack);
			}
		}
		return items;
	}
}
