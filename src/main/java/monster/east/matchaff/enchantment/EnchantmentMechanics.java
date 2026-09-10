package monster.east.matchaff.enchantment;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Initializes the custom enchantments whose behaviour cannot live entirely in data. */
public final class EnchantmentMechanics {
	private static final List<Function<ServerPlayer, Iterable<ItemStack>>> EXTRA_HEAD_ITEMS = new ArrayList<>();

	private EnchantmentMechanics() {
	}

	public static void registerExtraHeadItems(Function<ServerPlayer, ? extends Iterable<ItemStack>> provider) {
		EXTRA_HEAD_ITEMS.add(player -> provider.apply(player));
	}

	public static void init() {
		AnemosEnchantment.init();
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!server.tickRateManager().runsNormally()) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				Registry<Enchantment> enchantments = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
				List<ItemStack> extraHeadItems = extraHeadItems(player);
				WardingEnchantment.tick(player, enchantments);
				DivinityEnchantment.tick(player, enchantments);
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
