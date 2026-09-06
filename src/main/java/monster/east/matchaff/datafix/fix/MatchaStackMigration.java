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

/** Shared ItemStack migration walker used by {@link ItemNamespaceFix} and {@link NazarItemFix}. */
final class MatchaStackMigration {
	private static final String OLD_NAMESPACE = "matcha-flavoured:";
	private static final String NEW_NAMESPACE = "matcha:";
	private static final Set<String> ENCHANTMENT_COMPONENTS = Set.of("minecraft:enchantments", "minecraft:stored_enchantments");
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
	private static final Map<String, String> RENAMED_ITEM_PATHS = Map.of(
			"heart_container", "crystal_heart",
			"application", "asylum_seeker"
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
