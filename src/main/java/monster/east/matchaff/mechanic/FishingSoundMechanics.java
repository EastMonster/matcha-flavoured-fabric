package monster.east.matchaff.mechanic;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Ports the datapack's per-catch fishing sounds. Upstream detects the caught
 * stack through "fishing_rod_hooked" advancements that each run a function
 * using the record sound source; this class performs the same classification
 * directly and keeps the volume on the Records/Music-disc slider.
 */
public final class FishingSoundMechanics {
	private static final Set<Item> STAR_1 = Set.of(
			item("matcha", "alaska_blackfish"),
			item("matcha", "anchovy"),
			item("matcha", "bluegill"),
			item("matcha", "bujurqui"),
			item("matcha", "crappie"),
			item("matcha", "freshwater_pufferfish"),
			item("matcha", "guppy"),
			item("matcha", "humpback_whitefish"),
			item("matcha", "mediterranean_killifish"),
			item("matcha", "rainbow_wrasse"),
			item("matcha", "shad"),
			item("matcha", "striped_perch"),
			item("minecraft", "cod"),
			item("minecraft", "pufferfish"),
			item("minecraft", "salmon"),
			item("minecraft", "tropical_fish"));
	private static final Set<Item> STAR_2 = Set.of(
			item("matcha", "black_seabass"),
			item("matcha", "carp"),
			item("matcha", "flying_fish"),
			item("matcha", "gurnard"),
			item("matcha", "herring"),
			item("matcha", "lamprey"),
			item("matcha", "mahi_mahi"),
			item("matcha", "piranha"),
			item("matcha", "spoonhead_sculpin"),
			item("matcha", "walleye"));
	private static final Set<Item> STAR_3 = Set.of(
			item("matcha", "armoured_catfish"),
			item("matcha", "catfish"),
			item("matcha", "flounder"),
			item("matcha", "gar"),
			item("matcha", "monkfish"),
			item("matcha", "northern_pike"),
			item("matcha", "painted_moray"),
			item("matcha", "sturgeon"),
			item("matcha", "tunisian_barb"),
			item("matcha", "wolffish"));
	private static final Set<Item> STAR_4 = Set.of(
			item("matcha", "arapaima"),
			item("matcha", "bass"),
			item("matcha", "echo_fish"),
			item("matcha", "european_eel"),
			item("matcha", "muskellunge"),
			item("matcha", "oarfish"),
			item("matcha", "opah"),
			item("matcha", "pale_fish"),
			item("matcha", "siberian_sturgeon"),
			item("matcha", "skate"),
			item("matcha", "swordfish"));

	private static final Map<Item, String> SPECIAL = Map.of(
			item("matcha", "axolotl"), "fishing.axolotl",
			item("matcha", "fish_bones"), "fishing.fish_bones",
			item("minecraft", "enchanted_book"), "fishing.enchanted_book",
			item("minecraft", "frog_spawn_egg"), "fishing.frog",
			item("minecraft", "nautilus_shell"), "fishing.nautilus_shell",
			item("minecraft", "tadpole_spawn_egg"), "fishing.frog");

	private static final Set<Item> TREASURE = Set.of(
			item("matcha", "crystal_heart"),
			item("minecraft", "angler_pottery_sherd"),
			item("minecraft", "chest"),
			item("minecraft", "emerald"),
			item("minecraft", "fishing_rod"),
			item("minecraft", "goat_horn"));

	private static final Set<Item> JUNK = Set.of(
			item("minecraft", "bamboo"),
			item("minecraft", "echo_shard"),
			item("minecraft", "glowstone_dust"),
			item("minecraft", "ink_sac"),
			item("minecraft", "leather"),
			item("minecraft", "lily_pad"),
			item("minecraft", "pale_hanging_moss"),
			item("minecraft", "potion"),
			item("minecraft", "resin_clump"),
			item("minecraft", "sculk_vein"),
			item("minecraft", "stick"),
			item("minecraft", "string"));

	private FishingSoundMechanics() {
	}

	public static void onCaught(final ServerPlayer player, final Collection<ItemStack> caught) {
		if (caught.isEmpty()) {
			return;
		}
		for (ItemStack stack : caught) {
			String event = eventFor(stack.getItem());
			if (event != null) {
				Holder<SoundEvent> sound = Holder.direct(SoundEvent.createVariableRangeEvent(
						Identifier.fromNamespaceAndPath("matcha", event)));
				player.level().playSound(
						null, player.getX(), player.getY(), player.getZ(),
						sound, SoundSource.RECORDS, 1.0F, 1.0F);
			}
		}
	}

	private static String eventFor(final Item item) {
		String special = SPECIAL.get(item);
		if (special != null) {
			return special;
		}
		if (STAR_1.contains(item) || STAR_2.contains(item) || STAR_3.contains(item) || STAR_4.contains(item)) {
			return STAR_1.contains(item) ? "fishing.1_star"
					: STAR_2.contains(item) ? "fishing.2_star"
					: STAR_3.contains(item) ? "fishing.3_star"
					: "fishing.4_star";
		}
		if (TREASURE.contains(item)) {
			return "fishing.treasure";
		}
		if (JUNK.contains(item)) {
			return "fishing.junk";
		}
		return null;
	}

	private static Item item(final String namespace, final String path) {
		return java.util.Objects.requireNonNull(
				BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(namespace, path)),
				namespace + ":" + path);
	}
}
