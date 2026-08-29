package monster.east.matchaff.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;

public final class VanillaWaterColor {
	private VanillaWaterColor() {
	}

	public static int get(Holder<Biome> holder) {
		Identifier id = holder.unwrapKey().map(key -> key.identifier()).orElse(null);
		return get(id, holder.value().getWaterColor());
	}

	public static int get(ClientLevel level, Biome biome) {
		Identifier id = level.registryAccess().lookupOrThrow(Registries.BIOME)
				.getResourceKey(biome).map(key -> key.identifier()).orElse(null);
		return get(id, biome.getWaterColor());
	}

	private static int get(Identifier id, int fallback) {
		if (id == null || !id.getNamespace().equals("minecraft")) {
			return fallback;
		}

		return switch (id.getPath()) {
			case "cold_ocean", "deep_cold_ocean", "snowy_taiga", "snowy_beach" -> 4020182;
			case "frozen_ocean", "deep_frozen_ocean", "frozen_river" -> 3750089;
			case "lukewarm_ocean", "deep_lukewarm_ocean" -> 4566514;
			case "warm_ocean" -> 4445678;
			case "pale_garden" -> 7768221;
			case "swamp" -> 6388580;
			case "mangrove_swamp" -> 3832426;
			case "cherry_grove" -> 6141935;
			case "meadow" -> 937679;
			case "badlands", "bamboo_jungle", "basalt_deltas", "beach", "birch_forest",
				"crimson_forest", "dark_forest", "deep_dark", "deep_ocean", "desert",
				"dripstone_caves", "end_barrens", "end_highlands", "end_midlands",
				"eroded_badlands", "flower_forest", "forest", "frozen_peaks", "grove",
				"ice_spikes", "jagged_peaks", "jungle", "lush_caves", "mushroom_fields",
				"nether_wastes", "ocean", "old_growth_birch_forest", "old_growth_pine_taiga",
				"old_growth_spruce_taiga", "plains", "river", "savanna", "savanna_plateau",
				"small_end_islands", "snowy_plains", "snowy_slopes", "soul_sand_valley",
				"sparse_jungle", "stony_peaks", "stony_shore", "sunflower_plains", "taiga",
				"the_end", "the_void", "warped_forest", "windswept_forest", "windswept_gravelly_hills",
				"windswept_hills", "windswept_savanna", "wooded_badlands" -> 4159204;
			default -> fallback;
		};
	}
}
