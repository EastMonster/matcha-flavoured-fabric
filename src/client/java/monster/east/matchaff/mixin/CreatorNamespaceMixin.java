package monster.east.matchaff.mixin;

import net.fabricmc.fabric.api.item.v1.FabricItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.Set;

/** Credits Matcha registrations and the vanilla item IDs whose identity Matcha replaces to this mod. */
@Mixin(Item.class)
public abstract class CreatorNamespaceMixin implements FabricItem {
	@Unique
	private static final Set<String> MATCHA$VANILLA_ITEMS = Set.of(
			"beetroot",
			"beetroot_seeds",
			"blaze_powder",
			"blaze_rod",
			"brewing_stand",
			"cod",
			"cooked_cod",
			"cooked_rabbit",
			"cooked_salmon",
			"disc_fragment_5",
			"emerald",
			"enchanted_book",
			"ender_pearl",
			"flint_and_steel",
			"glistering_melon_slice",
			"glow_ink_sac",
			"glowstone_dust",
			"golden_helmet",
			"gunpowder",
			"heart_of_the_sea",
			"honeycomb",
			"leather",
			"leather_chestplate",
			"leather_helmet",
			"nether_brick",
			"nether_star",
			"nether_wart",
			"netherite_axe",
			"netherite_boots",
			"netherite_chestplate",
			"netherite_helmet",
			"netherite_hoe",
			"netherite_horse_armor",
			"netherite_ingot",
			"netherite_leggings",
			"netherite_nautilus_armor",
			"netherite_pickaxe",
			"netherite_scrap",
			"netherite_shovel",
			"netherite_spear",
			"netherite_sword",
			"phantom_membrane",
			"prismarine_crystals",
			"prismarine_shard",
			"quartz",
			"rabbit",
			"rabbit_hide",
			"redstone",
			"resin_brick",
			"salmon",
			"shears",
			"shulker_shell",
			"turtle_scute",
			"wheat",
			"wheat_seeds",

			// Vanilla foods whose default components are replaced by Matcha.
			"apple",
			"baked_potato",
			"bread",
			"carrot",
			"chorus_fruit",
			"cooked_beef",
			"cooked_chicken",
			"cooked_mutton",
			"cooked_porkchop",
			"dried_kelp",
			"enchanted_golden_apple",
			"glow_berries",
			"golden_apple",
			"golden_carrot",
			"melon_slice",
			"popped_chorus_fruit",
			"rabbit_stew",
			"sweet_berries"
	);

	@Override
	public String getCreatorNamespace(ItemStack stack) {
		Identifier id = BuiltInRegistries.ITEM.getKey((Item) (Object) this);
		if (id.getNamespace().equals("matcha-flavoured")) {
			return "matcha-flavoured";
		}
		if (id.getNamespace().equals("minecraft") && MATCHA$VANILLA_ITEMS.contains(id.getPath())) {
			return "matcha-flavoured";
		}
		return FabricItem.super.getCreatorNamespace(stack);
	}
}
