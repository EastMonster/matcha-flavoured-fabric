package monster.east.matchaff.datafix.fix;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Dynamic;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Shared ItemStack migration walker used by the Matcha item data fixes. */
final class MatchaStackMigration {
	private static final String OLD_NAMESPACE = "matcha-flavoured:";
	private static final String NEW_NAMESPACE = "matcha:";
	private static final String OLD_BANNER_PATTERN_NAMESPACE = "main:";
	private static final String NEW_BANNER_PATTERN_NAMESPACE = "matcha:";
	private static final Set<String> ENCHANTMENT_COMPONENTS = Set.of("minecraft:enchantments", "minecraft:stored_enchantments");
	private static final Set<String> TRANSLATION_COMPONENTS = Set.of(
			"minecraft:item_name", "minecraft:custom_name", "minecraft:lore"
	);
	private static final Set<String> BANNER_PATTERN_PATHS = Set.of(
			"ace_pride", "actup", "bi_pride", "classic_pride", "common_pride",
			"inclusive_pride", "lesbian_pride", "nb_pride", "new_pride", "trans_pride"
	);
	private static final String NAZAR_CARRIER = "minecraft:glistering_melon_slice";
	private static final String NAZAR_PATH = "nazar";
	private static final String STEEL_CARRIER = "minecraft:resin_brick";
	private static final String STEEL_PATH = "steel";
	private static final String SHAKUDO_CARRIER = "minecraft:shulker_shell";
	private static final String SHAKUDO_PATH = "shakudo";
	private static final String HEPATIZON_CARRIER = "minecraft:phantom_membrane";
	private static final String HEPATIZON_PATH = "hepatizon";
	private static final String ELECTRUM_CARRIER = "minecraft:heart_of_the_sea";
	private static final String ELECTRUM_PATH = "electrum";
	private static final String DIVINE_FRAGMENT_CARRIER = "minecraft:turtle_scute";
	private static final String DIVINE_FRAGMENT_PATH = "divine_fragment";
	private static final Map<String, String> ITEM_RENAME_PATHS = Map.ofEntries(
			Map.entry("bronze_axe", "hepatizon_axe"),
			Map.entry("bronze_boots", "hepatizon_boots"),
			Map.entry("bronze_chestplate", "hepatizon_chestplate"),
			Map.entry("bronze_dolabra", "hepatizon_dolabra"),
			Map.entry("bronze_helmet", "hepatizon_helmet"),
			Map.entry("bronze_hoe", "hepatizon_hoe"),
			Map.entry("bronze_laurel", "hepatizon_laurel"),
			Map.entry("bronze_leggings", "hepatizon_leggings"),
			Map.entry("bronze_mattock", "hepatizon_mattock"),
			Map.entry("bronze_pickaxe", "hepatizon_pickaxe"),
			Map.entry("bronze_shovel", "hepatizon_shovel"),
			Map.entry("bronze_spear", "hepatizon_spear"),
			Map.entry("bronze_sword", "hepatizon_sword"),
			Map.entry("bronze_shears", "shepherds_shears"),
			Map.entry("palatinate_sword", "shakudo_sword")
	);
	private static final Set<String> LEGACY_ITEM_PATHS = Set.copyOf(ITEM_RENAME_PATHS.keySet());
	private static final Map<String, String> RENAMED_ITEM_PATHS = Map.of(
			"heart_container", "crystal_heart",
			"application", "asylum_seeker"
	);
	private static final Map<String, String> ITEM_TRANSLATION_PATHS = Map.ofEntries(
			Map.entry("hatchet", "bloodrage_hatchet"),
			Map.entry("glow_berry_jam", "glow_jam"),
			Map.entry("golden_empanada", "golden_apple_empanada"),
			Map.entry("raw_paneer_makhani", "uncooked_paneer_makhani"),
			Map.entry("raw_ramen", "uncooked_ramen"),
			Map.entry("raw_curry", "uncooked_curry"),
			Map.entry("raw_green_curry", "uncooked_green_curry"),
			Map.entry("key_of_solomon", "solomon"),
			Map.entry("baby_cold_sheep", "crate_sheep_black"),
			Map.entry("baby_temperate_sheep", "crate_sheep_white"),
			Map.entry("baby_warm_sheep", "crate_sheep_brown"),
			Map.entry("baby_cold_cow", "crate_cold_cow"),
			Map.entry("baby_temperate_cow", "crate_temperate_cow"),
			Map.entry("baby_warm_cow", "crate_warm_cow"),
			Map.entry("baby_cold_pig", "crate_cold_pig"),
			Map.entry("baby_temperate_pig", "crate_temperate_pig"),
			Map.entry("baby_warm_pig", "crate_warm_pig"),
			Map.entry("bundle.seed", "seed_bundle"),
			Map.entry("bundle.exotic_seed", "exotic_seed_bundle"),
			Map.entry("bundle.floral", "floral_bundle"),
			Map.entry("bundle.mushroom", "mushy_bundle"),
			Map.entry("bundle.lava_kit", "lava_kit")
	);
	private static final Set<String> BLESSING_LORE_PATHS = Set.of(
			"aeolus", "ahura_mazda", "apollo", "arachnae", "ares", "artemis", "clement",
			"cronus", "daedalus", "demeter", "eros", "glaucus", "god_king", "hyacinthus",
			"icarus", "lu_ban", "man", "midas", "paris", "prometheus", "talos", "will",
			"yama", "yamm", "muhammad", "moshe", "samyaza", "belial"
	);
	private static final Set<String> EFFECT_LORE_PATHS = Set.of(
			"aura", "blindness", "cleanse", "cleanse_maleffect", "conduit_power", "darkness",
			"dolphins_grace", "fire_resistance", "glowing", "haste", "haste_ii",
			"infested", "invisibility", "jump_boost", "levitation_iii", "levitation_xxx", "luck",
			"mining_fatigue", "nausea", "night_vision", "oozing", "poison", "poison_ii", "regen",
			"regen_ii", "resistance", "slow_falling", "slowness", "strength", "strength_ii", "unluck",
		"weakness", "weakness_ii", "warping", "weaving", "wind_charged", "wither"
	);
	private static final Set<String> ITEM_PATHS = itemPaths();

	private MatchaStackMigration() {
	}

	static Dynamic<?> migrate(Dynamic<?> data) {
		if (!(data.getValue() instanceof Tag tag)) {
			return data;
		}
		// ponytail: raw NBT reaches every vanilla ItemStack location; the creative-order allowlist prevents non-item registry IDs from changing.
		return new Dynamic<>(NbtOps.INSTANCE, migrate(tag, false));
	}

	static Dynamic<?> migrateItemRenames(Dynamic<?> data) {
		if (!(data.getValue() instanceof Tag tag)) {
			return data;
		}
		return new Dynamic<>(NbtOps.INSTANCE, migrateItemRenames(tag, false));
	}

	static Dynamic<?> migrateV3(Dynamic<?> data) {
		return migrateTranslationKeys(migrateAxolotl(migrateBannerPatternIds(migrateItemRenames(data))));
	}

	static Dynamic<?> migrateTranslationKeys(Dynamic<?> data) {
		if (!(data.getValue() instanceof Tag tag)) {
			return data;
		}
		return new Dynamic<>(NbtOps.INSTANCE, migrateTranslationKeys(tag, false, null));
	}

	static Dynamic<?> migrateBannerPatternIds(Dynamic<?> data) {
		if (!(data.getValue() instanceof Tag tag)) {
			return data;
		}
		return new Dynamic<>(NbtOps.INSTANCE, migrateBannerPatternIds(tag, false));
	}

	private static Dynamic<?> migrateAxolotl(Dynamic<?> data) {
		if (!(data.getValue() instanceof Tag tag)) {
			return data;
		}
		return new Dynamic<>(NbtOps.INSTANCE, migrateAxolotl(tag, false));
	}

	private static Tag migrateAxolotl(Tag tag, boolean customData) {
		if (tag instanceof CompoundTag compound) {
			if (!customData && compound.getStringOr("id", "").equals("matcha:axolotl")) {
				compound.putString("id", "minecraft:axolotl_spawn_egg");
				CompoundTag components = compound.get("components") instanceof CompoundTag existing
						? existing : new CompoundTag();
				if (!components.contains("minecraft:item_name")) {
					CompoundTag itemName = new CompoundTag();
					itemName.putString("translate", "item.matcha.axolotl");
					components.put("minecraft:item_name", itemName);
				}
				String itemModel = components.getStringOr("minecraft:item_model", "");
				if (itemModel.isEmpty() || itemModel.equals("axolotl") || itemModel.equals("minecraft:axolotl")) {
					components.putString("minecraft:item_model", "matcha:axolotl");
				}
				if (!components.contains("minecraft:entity_data")) {
					CompoundTag entityData = new CompoundTag();
					entityData.putString("id", "minecraft:axolotl");
					components.put("minecraft:entity_data", entityData);
				}
				compound.put("components", components);
			}
			for (String key : List.copyOf(compound.keySet())) {
				Tag child = compound.get(key);
				if (child != null) {
					compound.put(key, migrateAxolotl(child, customData || key.equals("minecraft:custom_data")));
				}
			}
			return compound;
		}
		if (tag instanceof ListTag list) {
			for (int index = 0; index < list.size(); index++) {
				list.set(index, migrateAxolotl(list.get(index), customData));
			}
		}
		return tag;
	}

	private static Tag migrateItemRenames(Tag tag, boolean customData) {
		if (tag instanceof CompoundTag compound) {
			if (!customData) {
				String id = compound.getStringOr("id", "");
				String path = id.startsWith(NEW_NAMESPACE) ? id.substring(NEW_NAMESPACE.length()) : null;
				String newPath = path == null ? null : ITEM_RENAME_PATHS.get(path);
				if (newPath != null) {
					compound.putString("id", NEW_NAMESPACE + newPath);
				}
			}
			for (String key : List.copyOf(compound.keySet())) {
				Tag child = compound.get(key);
				if (child != null) {
					Tag migrated = migrateItemRenames(child, customData || key.equals("minecraft:custom_data"));
					if (!customData && key.equals("minecraft:item_name") && migrated instanceof CompoundTag itemName) {
						String translate = itemName.getStringOr("translate", "");
						String newTranslate = renamedItemName(translate);
						if (newTranslate != null) itemName.putString("translate", newTranslate);
					}
					compound.put(key, migrated);
				}
			}
			return compound;
		}
		if (tag instanceof ListTag list) {
			for (int index = 0; index < list.size(); index++) {
				list.set(index, migrateItemRenames(list.get(index), customData));
			}
		}
		return tag;
	}

	private static Tag migrateBannerPatternIds(Tag tag, boolean customData) {
		if (tag instanceof CompoundTag compound) {
			if (!customData) {
				String id = compound.getStringOr("pattern", "");
				if (id.startsWith(OLD_BANNER_PATTERN_NAMESPACE)
						&& BANNER_PATTERN_PATHS.contains(id.substring(OLD_BANNER_PATTERN_NAMESPACE.length()))) {
					compound.putString("pattern", NEW_BANNER_PATTERN_NAMESPACE + id.substring(OLD_BANNER_PATTERN_NAMESPACE.length()));
				}
			}
			for (String key : List.copyOf(compound.keySet())) {
				Tag child = compound.get(key);
				if (child != null) {
					compound.put(key, migrateBannerPatternIds(child, customData || key.equals("minecraft:custom_data")));
				}
			}
			return compound;
		}
		if (tag instanceof ListTag list) {
			for (int index = 0; index < list.size(); index++) {
				list.set(index, migrateBannerPatternIds(list.get(index), customData));
			}
		}
		return tag;
	}

	private static String renamedItemName(String translate) {
		String prefix = "item.matcha.";
		if (!translate.startsWith(prefix)) return null;
		String newPath = ITEM_RENAME_PATHS.get(translate.substring(prefix.length()));
		return newPath == null ? null : prefix + newPath;
	}

	private static Tag migrateTranslationKeys(Tag tag, boolean customData, String itemPath) {
		if (tag instanceof CompoundTag compound) {
			String currentItemPath = itemPath;
			String id = compound.getStringOr("id", "");
			if (!customData && id.startsWith(NEW_NAMESPACE)) {
				currentItemPath = id.substring(NEW_NAMESPACE.length());
			}
			for (String key : List.copyOf(compound.keySet())) {
				Tag child = compound.get(key);
				if (child == null) continue;
				boolean childCustomData = customData || key.equals("minecraft:custom_data");
				Tag migrated = TRANSLATION_COMPONENTS.contains(key) && !customData
						? migrateTextComponent(child, currentItemPath)
						: migrateTranslationKeys(child, childCustomData, currentItemPath);
				compound.put(key, migrated);
			}
			return compound;
		}
		if (tag instanceof ListTag list) {
			for (int index = 0; index < list.size(); index++) {
				list.set(index, migrateTranslationKeys(list.get(index), customData, itemPath));
			}
		}
		return tag;
	}

	private static Tag migrateTextComponent(Tag tag, String itemPath) {
		if (tag instanceof CompoundTag compound) {
			String translate = compound.getStringOr("translate", "");
			String newTranslate = renamedTranslationKey(translate, itemPath);
			if (newTranslate != null) compound.putString("translate", newTranslate);
			for (String key : List.copyOf(compound.keySet())) {
				Tag child = compound.get(key);
				if (child != null) compound.put(key, migrateTextComponent(child, itemPath));
			}
			return compound;
		}
		if (tag instanceof ListTag list) {
			for (int index = 0; index < list.size(); index++) {
				list.set(index, migrateTextComponent(list.get(index), itemPath));
			}
		}
		return tag;
	}

	private static String renamedTranslationKey(String translate, String itemPath) {
		if (translate.startsWith("enchantment.kleispack.")) {
			return "enchantment.matcha." + translate.substring("enchantment.kleispack.".length());
		}
		if (translate.startsWith("matcha.lore.")) {
			String path = translate.substring("matcha.lore.".length());
			if (path.equals("gills")) return "effect.matcha.water_breathing";
			if (EFFECT_LORE_PATHS.contains(path)) return "effect.matcha." + path;
			return switch (path) {
				case "blocking_colon", "full", "melee_blocking", "projectile_blocking", "throwable" -> "desc.matcha." + path;
				default -> null;
			};
		}
		if (translate.startsWith("matcha.asylum.")) {
			String path = switch (translate.substring("matcha.asylum.".length())) {
				case "summon_hint" -> "desc";
				case "age_adult" -> "adult";
				case "age_child" -> "child";
				case "culture_none" -> "culture.none";
				case "culture_desert" -> "culture.desert";
				case "culture_swamp" -> "culture.swamp";
				case "culture_plains" -> "culture.plains";
				case "culture_taiga" -> "culture.taiga";
				case "culture_jungle" -> "culture.jungle";
				case "cause_political" -> "cause.1";
				case "cause_religious" -> "cause.2";
				case "cause_war" -> "cause.3";
				case "cause_ethnic" -> "cause.4";
				case "cause_natural_disaster" -> "cause.5";
				case "cause_refused_disclose" -> "cause.6";
				case "cause_dead_god" -> "cause.7";
				case "cause_famine" -> "cause.8";
				case "cause_orphaned" -> "cause.9";
				default -> null;
			};
			return path == null ? null : "item.matcha.asylum_seeker." + path;
		}
		if (translate.startsWith("matcha.amnestic.")) {
			return switch (translate.substring("matcha.amnestic.".length())) {
				case "forget" -> "item.matcha.amnestic.desc";
				case "place_hint" -> "item.matcha.amnestic.instructions";
				default -> null;
			};
		}
		if (translate.startsWith("adv.kleispack.fishing.rarity.")) {
			return "lore.matcha.fish_rarity." + translate.substring("adv.kleispack.fishing.rarity.".length());
		}
		if (translate.startsWith("item.kleispack.trade.")) {
			return "trade.matcha." + translate.substring("item.kleispack.trade.".length());
		}
		if (translate.startsWith("item.kleispack.blessing.")) {
			String path = translate.substring("item.kleispack.blessing.".length());
			return BLESSING_LORE_PATHS.contains(path) ? "item.matcha.blessing." + path : null;
		}
		if (translate.startsWith("item.kleispack.fish.")) {
			return "item.matcha." + translate.substring("item.kleispack.fish.".length());
		}
		return switch (translate) {
			case "adv.kleispack.anglers_almanac" -> "advancements.matcha.anglers_almanac.title";
			case "adv.kleispack.anglers_almanac.desc" -> "advancements.matcha.anglers_almanac.desc";
			case "desc.kleispack.cleanse" -> "effect.matcha.cleanse";
			case "desc.kleispack.cleanses_maleffect" -> "effect.matcha.cleanse_maleffect";
			case "desc.kleispack.repaired_with" -> "tooltip.matcha.repaired_with";
			case "desc.kleispack.elytra_bonus" -> "tooltip.matcha.elytra_bonus";
			case "desc.kleispack.per_equipment_bonus" -> "tooltip.matcha.per_equipment_bonus";
			case "desc.kleispack.set_bonus" -> "tooltip.matcha.set_bonus";
			case "desc.kleispack.when_blocking" -> "tooltip.matcha.when_blocking";
			case "item.kleispack.frog" -> "item.matcha.frog";
			case "item.kleispack.tadpole" -> "item.matcha.tadpole";
			default -> renameItemTranslationKey(translate, itemPath);
		};
	}

	private static String renameItemTranslationKey(String translate, String itemPath) {
		String prefix = "item.kleispack.";
		if (!translate.startsWith(prefix)) return null;
		String path = translate.substring(prefix.length());
		if (path.equals("blessing") && itemPath != null && itemPath.startsWith("blessing_")) {
			return "item.matcha." + itemPath;
		}
		if (path.equals("cooking_recipe") && itemPath != null && itemPath.endsWith("_recipe")) {
			return "item.matcha." + itemPath;
		}
		String newPath = ITEM_TRANSLATION_PATHS.getOrDefault(path, ITEM_RENAME_PATHS.getOrDefault(path, path));
		if (path.endsWith(".desc")) {
			String basePath = path.substring(0, path.length() - ".desc".length());
			String newBasePath = ITEM_TRANSLATION_PATHS.getOrDefault(basePath,
					ITEM_RENAME_PATHS.getOrDefault(basePath, basePath));
			if (ITEM_PATHS.contains(newBasePath)) return "item.matcha." + newBasePath + ".desc";
		}
		return ITEM_PATHS.contains(newPath) ? "item.matcha." + newPath : null;
	}

	private static Tag migrate(Tag tag, boolean customData) {
		if (tag instanceof CompoundTag compound) {
			if (!customData) {
				String id = compound.getStringOr("id", "");
				if (id.startsWith(OLD_NAMESPACE)) {
					String path = id.substring(OLD_NAMESPACE.length());
					String newPath = RENAMED_ITEM_PATHS.getOrDefault(path, path);
					if (ITEM_PATHS.contains(newPath)) {
						compound.putString("id", NEW_NAMESPACE + newPath);
					}
				} else if (id.equals(NAZAR_CARRIER) && carriesNazarWarding(compound)) {
					compound.putString("id", NEW_NAMESPACE + NAZAR_PATH);
				} else if (id.equals(STEEL_CARRIER)) {
					compound.putString("id", NEW_NAMESPACE + STEEL_PATH);
				} else if (id.equals(SHAKUDO_CARRIER)) {
					compound.putString("id", NEW_NAMESPACE + SHAKUDO_PATH);
				} else if (id.equals(HEPATIZON_CARRIER)) {
					compound.putString("id", NEW_NAMESPACE + HEPATIZON_PATH);
				} else if (id.equals(ELECTRUM_CARRIER)) {
					compound.putString("id", NEW_NAMESPACE + ELECTRUM_PATH);
				} else if (id.equals(DIVINE_FRAGMENT_CARRIER)) {
					compound.putString("id", NEW_NAMESPACE + DIVINE_FRAGMENT_PATH);
				}
			}
			for (String key : List.copyOf(compound.keySet())) {
				Tag child = compound.get(key);
				if (child != null) {
					Tag migrated = migrate(child, customData || key.equals("minecraft:custom_data"));
					if (!customData && ENCHANTMENT_COMPONENTS.contains(key) && migrated instanceof CompoundTag enchantments) {
						migrateEnchantmentIds(enchantments);
					}
					compound.put(key, migrated);
				}
			}
			return compound;
		}
		if (tag instanceof ListTag list) {
			for (int index = 0; index < list.size(); index++) {
				list.set(index, migrate(list.get(index), customData));
			}
		}
		return tag;
	}

	private static boolean carriesNazarWarding(CompoundTag compound) {
		// 26.2 item stacks keep components under `components`; fall back to the
		// carrier itself for any non-component (legacy) represenation.
		CompoundTag components = compound.get("components") instanceof CompoundTag child ? child : compound;
		for (String component : ENCHANTMENT_COMPONENTS) {
			if (components.get(component) instanceof CompoundTag enchantments
					&& (enchantments.contains("matcha:warding1") || enchantments.contains("matcha-flavoured:warding1"))) {
				return true;
			}
		}
		return false;
	}

	private static void migrateEnchantmentIds(CompoundTag enchantments) {
		for (String id : List.copyOf(enchantments.keySet())) {
			if (id.startsWith(OLD_NAMESPACE)) {
				Tag level = enchantments.remove(id);
				enchantments.put(NEW_NAMESPACE + id.substring(OLD_NAMESPACE.length()), level);
			}
		}
	}

	private static Set<String> itemPaths() {
		try (var stream = MatchaStackMigration.class.getResourceAsStream("/matcha/creative_order.json");
					var reader = new InputStreamReader(Objects.requireNonNull(stream), StandardCharsets.UTF_8)) {
			Set<String> paths = new HashSet<>();
			collectPaths(JsonParser.parseReader(reader), paths);
			paths.addAll(LEGACY_ITEM_PATHS);
			// The old custom axolotl is no longer a creative item, but still needs the 0 -> 1 namespace migration.
			paths.add("axolotl");
			return Set.copyOf(paths);
		} catch (Exception exception) {
			throw new IllegalStateException("Could not read Matcha item migration paths", exception);
		}
	}

	private static void collectPaths(JsonElement element, Set<String> paths) {
		if (element.isJsonPrimitive()) {
			paths.add(element.getAsString());
		} else if (element.isJsonArray()) {
			for (JsonElement child : element.getAsJsonArray()) collectPaths(child, paths);
		} else {
			for (JsonElement child : element.getAsJsonObject().asMap().values()) collectPaths(child, paths);
		}
	}
}
