package monster.east.matchaff.item;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Gives the vanilla carrier foods the same default components as their
 * Matcha recipe or loot-table result. This makes creative-tab and plain
 * {@code /give} stacks behave like the versions normally obtained in play.
 */
public class VanillaFoodDefaults {
	private static final List<String> RECIPE_FILES = List.of(
			"baked_potato", "bread",
			"cooked_beef", "cooked_chicken", "cooked_cod", "cooked_mutton",
			"cooked_pork", "cooked_rabbit", "cooked_salmon",
			"dried_kelp", "golden_apple", "golden_carrot",
			"popped_chorus_fruit"
	);
	private VanillaFoodDefaults() {
	}

	public static void init() {
		List<Definition> definitions = new ArrayList<>();
		for (String name : RECIPE_FILES) {
			definitions.add(readRecipe(name));
		}
		definitions.addAll(readDefaults());
		definitions.add(readNestedLootTable(
				"/data/minecraft/loot_table/blocks/beetroots.json", "minecraft:beetroot"));
		definitions.add(readNestedLootTable(
				"/data/minecraft/loot_table/blocks/chorus_plant.json", "minecraft:chorus_fruit"));
		definitions.add(readNestedLootTable(
				"/data/minecraft/loot_table/blocks/melon.json", "minecraft:melon_slice"));

		DefaultItemComponentEvents.MODIFY.register(context -> {
			for (Definition definition : definitions) {
				Item item = Objects.requireNonNull(
						BuiltInRegistries.ITEM.getValue(Identifier.parse(definition.itemId)),
						"Unknown vanilla food carrier: " + definition.itemId
				);
				context.modify(item, (builder, registries, ignored) ->
						definition.components.entrySet().forEach(entry ->
								apply(builder, registries, entry.getKey(), entry.getValue())));
			}
		});
	}

	private static Definition readRecipe(String name) {
		String category = switch (name) {
			case "dried_kelp", "golden_apple", "golden_carrot", "popped_chorus_fruit" -> "crafting";
			default -> "oven";
		};
		return readRecipeAt("/data/matcha/recipe/food/" + category + "/" + name + ".json");
	}

	private static Definition readRecipeAt(String path) {
		JsonObject root = readJson(path);
		JsonObject result = root.getAsJsonObject("result");
		return new Definition(result.get("id").getAsString(), result.getAsJsonObject("components"));
	}

	private static List<Definition> readDefaults() {
		List<Definition> definitions = new ArrayList<>();
		for (Map.Entry<String, JsonElement> entry : readJson("/matcha/vanilla_food_defaults.json").entrySet()) {
			definitions.add(new Definition(entry.getKey(), entry.getValue().getAsJsonObject()));
		}
		return definitions;
	}

	private static Definition readNestedLootTable(String path, String itemId) {
		JsonObject entry = findItemEntry(readJson(path), itemId);
		if (entry == null) entry = findLootTableEntry(readJson(path));
		if (entry == null) {
			throw new IllegalStateException("No item entry for " + itemId + " in " + path);
		}
		return definitionFromLootEntry(entry, path);
	}

	private static Definition definitionFromLootEntry(JsonObject entry, String source) {
		if (entry.has("functions")) {
			for (JsonElement element : entry.getAsJsonArray("functions")) {
				JsonObject function = element.getAsJsonObject();
				if ("minecraft:set_components".equals(function.get("function").getAsString())) {
					return new Definition(entry.get("name").getAsString(), function.getAsJsonObject("components"));
				}
			}
		}
		if ("minecraft:loot_table".equals(entry.get("type").getAsString())) {
			String[] id = entry.get("value").getAsString().split(":", 2);
			JsonObject root = readJson("/data/" + id[0] + "/loot_table/" + id[1] + ".json");
			JsonObject nested = entry.has("name") ? findItemEntry(root, entry.get("name").getAsString()) : findItemEntry(root, null);
			if (nested != null) return definitionFromLootEntry(nested, source);
		}
		throw new IllegalStateException("No set_components function in loot table entry: " + source);
	}

	private static JsonObject findItemEntry(JsonElement element, String itemId) {
		if (element.isJsonObject()) {
			JsonObject object = element.getAsJsonObject();
			if (object.has("name") && (itemId == null || itemId.equals(object.get("name").getAsString()))) {
				return object;
			}
			for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
				JsonObject found = findItemEntry(entry.getValue(), itemId);
				if (found != null) {
					return found;
				}
			}
		} else if (element.isJsonArray()) {
			for (JsonElement child : element.getAsJsonArray()) {
				JsonObject found = findItemEntry(child, itemId);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	private static JsonObject findLootTableEntry(JsonElement element) {
		if (element.isJsonObject()) {
			JsonObject object = element.getAsJsonObject();
			if ("minecraft:loot_table".equals(object.has("type") ? object.get("type").getAsString() : null)) return object;
			for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
				JsonObject found = findLootTableEntry(entry.getValue());
				if (found != null) return found;
			}
		} else if (element.isJsonArray()) {
			for (JsonElement child : element.getAsJsonArray()) {
				JsonObject found = findLootTableEntry(child);
				if (found != null) return found;
			}
		}
		return null;
	}

	private static JsonObject readJson(String path) {
		try (var stream = VanillaFoodDefaults.class.getResourceAsStream(path);
			 var reader = new InputStreamReader(Objects.requireNonNull(stream, path), StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		} catch (Exception exception) {
			throw new IllegalStateException("Could not load vanilla food defaults from " + path, exception);
		}
	}

	@SuppressWarnings("unchecked")
	private static void apply(
			DataComponentMap.Builder builder, HolderLookup.Provider registries,
			String id, JsonElement json
	) {
		boolean remove = id.startsWith("!");
		String componentId = remove ? id.substring(1) : id;
		DataComponentType<Object> type = (DataComponentType<Object>) BuiltInRegistries.DATA_COMPONENT_TYPE
				.getValue(Identifier.parse(componentId));
		if (type == null) {
			throw new IllegalArgumentException("Unknown vanilla food component: " + componentId);
		}
		builder.set(type, remove ? null : ItemComponents.decode(type.codec(), registries, json));
	}

	private record Definition(String itemId, JsonObject components) {
	}
}
