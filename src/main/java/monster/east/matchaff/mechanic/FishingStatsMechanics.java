package monster.east.matchaff.mechanic;

import java.util.Collection;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.stats.StatType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Per-item fishing counts, stored and synced through vanilla statistics. */
public final class FishingStatsMechanics {
	public static final StatType<Item> FISHING_CAUGHT_BY_ITEM = Registry.register(
			BuiltInRegistries.STAT_TYPE,
			Identifier.fromNamespaceAndPath("matcha", "fish_caught"),
			new StatType<>(BuiltInRegistries.ITEM, Component.translatable("stat_type.matcha.fish_caught"))
	);

	private FishingStatsMechanics() {
	}

	public static void init() {
		// Force registration during common mod initialization.
	}

	public static void onCaught(final ServerPlayer player, final Collection<ItemStack> caught) {
		for (ItemStack stack : caught) {
			if (!stack.isEmpty()) {
				player.awardStat(FISHING_CAUGHT_BY_ITEM.get(stack.getItem()), stack.getCount());
			}
		}
	}
}
