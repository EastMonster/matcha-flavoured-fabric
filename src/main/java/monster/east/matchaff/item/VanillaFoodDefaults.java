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
			"cooked_beef", "cooked_chicken", "cooked_fish", "cooked_mutton",
			"cooked_pork", "cooked_rabbit", "cooked_large_fish",
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
