package monster.east.matchaff.datafix.fix;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * V1 migrates ItemStacks from the legacy Matcha Flavoured namespace to the current {@code matcha} namespace.
 * It also updates allowlisted item IDs and legacy enchantment IDs while preserving custom-data payloads.
 */
public final class V1Migration extends DataFix {
	public V1Migration(Schema outputSchema) {
		super(outputSchema, false);
	}

	@Override
	protected TypeRewriteRule makeRule() {
		Type<?> root = getInputSchema().getType(MatchaDataFixSupport.ROOT);
		return writeFixAndRead("Matcha item namespace migration", root, root, V1Migration::migrate);
	}

	private static final String OLD_NAMESPACE = "matcha-flavoured:";
	private static final String NEW_NAMESPACE = "matcha:";
	private static final Map<String, String> RENAMED_ITEM_PATHS = Map.of(
			"heart_container", "crystal_heart", "application", "asylum_seeker"
	);
	private static final Set<String> ITEM_PATHS = itemPaths();

	private static Dynamic<?> migrate(Dynamic<?> data) {
		return MatchaDataFixSupport.apply(data, tag -> migrate(tag, false));
	}

	private static Tag migrate(Tag tag, boolean customData) {
		if (tag instanceof CompoundTag compound) {
			if (!customData) {
				String id = compound.getStringOr("id", "");
				if (id.startsWith(OLD_NAMESPACE)) {
					String path = id.substring(OLD_NAMESPACE.length());
					String replacement = RENAMED_ITEM_PATHS.getOrDefault(path, path);
					if (ITEM_PATHS.contains(replacement)) compound.putString("id", NEW_NAMESPACE + replacement);
				}
			}
			for (String key : List.copyOf(compound.keySet())) {
				Tag child = compound.get(key);
				if (child == null) continue;
				Tag migrated = migrate(child, customData || key.equals("minecraft:custom_data"));
				if (!customData && MatchaDataFixSupport.isEnchantmentComponent(key) && migrated instanceof CompoundTag enchantments) {
					for (String id : List.copyOf(enchantments.keySet())) {
						if (!id.startsWith(OLD_NAMESPACE)) continue;
						Tag level = enchantments.remove(id);
						enchantments.put(NEW_NAMESPACE + id.substring(OLD_NAMESPACE.length()), level);
					}
				}
				compound.put(key, migrated);
			}
		} else if (tag instanceof ListTag list) {
			for (int index = 0; index < list.size(); index++) list.set(index, migrate(list.get(index), customData));
		}
		return tag;
	}

	static Set<String> itemPaths() {
		try (var stream = V1Migration.class.getResourceAsStream("/matcha/creative_order.json");
				 var reader = new InputStreamReader(Objects.requireNonNull(stream), StandardCharsets.UTF_8)) {
			Set<String> paths = new HashSet<>();
			collectPaths(JsonParser.parseReader(reader), paths);
			paths.addAll(Set.of("bronze_axe", "bronze_boots", "bronze_chestplate", "bronze_dolabra", "bronze_elytra", "bronze_helmet", "bronze_hoe", "bronze_laurel", "bronze_leggings", "bronze_mattock", "bronze_pickaxe", "bronze_shovel", "bronze_spear", "bronze_sword", "bronze_shears", "palatinate_sword", "axolotl"));
			return Set.copyOf(paths);
		} catch (Exception exception) {
			throw new IllegalStateException("Could not read Matcha item migration paths", exception);
		}
	}

	private static void collectPaths(JsonElement element, Set<String> paths) {
		if (element.isJsonPrimitive()) paths.add(element.getAsString());
		else if (element.isJsonArray()) for (JsonElement child : element.getAsJsonArray()) collectPaths(child, paths);
		else for (JsonElement child : element.getAsJsonObject().asMap().values()) collectPaths(child, paths);
	}
}
