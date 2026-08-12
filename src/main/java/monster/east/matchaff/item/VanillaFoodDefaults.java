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
	private static final List<String> LOOT_FILES = List.of(
			"apple", "carrot", "glow_berries", "sweet_berries", "enchanted_golden_apple"
	);

	private VanillaFoodDefaults() {
	}

	public static void init() {
		List<Definition> definitions = new ArrayList<>();
		for (String name : RECIPE_FILES) {
			definitions.add(readRecipe(name));
		}
		definitions.add(readRecipeAt("/data/crafting/recipe/morsel_stew.json"));
		for (String name : LOOT_FILES) {
			definitions.add(readLootTable(name));
		}
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
		return readRecipeAt("/data/food/recipe/" + name + ".json");
	}

	private static Definition readRecipeAt(String path) {
		JsonObject root = readJson(path);
		JsonObject result = root.getAsJsonObject("result");
		return new Definition(result.get("id").getAsString(), result.getAsJsonObject("components"));
	}

	private static Definition readLootTable(String name) {
		JsonObject root = readJson("/data/minecraft/loot_table/food/" + name + ".json");
		JsonObject entry = root.getAsJsonArray("pools").get(0).getAsJsonObject()
				.getAsJsonArray("entries").get(0).getAsJsonObject();
		return definitionFromLootEntry(entry, name);
	}

	private static Definition readNestedLootTable(String path, String itemId) {
		JsonObject entry = findItemEntry(readJson(path), itemId);
		if (entry == null) {
			throw new IllegalStateException("No item entry for " + itemId + " in " + path);
		}
		return definitionFromLootEntry(entry, path);
	}

	private static Definition definitionFromLootEntry(JsonObject entry, String source) {
		for (JsonElement element : entry.getAsJsonArray("functions")) {
			JsonObject function = element.getAsJsonObject();
			if ("minecraft:set_components".equals(function.get("function").getAsString())) {
				return new Definition(entry.get("name").getAsString(), function.getAsJsonObject("components"));
			}
		}
		throw new IllegalStateException("No set_components function in loot table entry: " + source);
	}

	private static JsonObject findItemEntry(JsonElement element, String itemId) {
		if (element.isJsonObject()) {
			JsonObject object = element.getAsJsonObject();
			if (object.has("name") && itemId.equals(object.get("name").getAsString())) {
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
