package monster.east.matchaff;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Adds every Matcha item to its creative tab in the semantic order defined by creative_order.json. */
final class CreativeOrder {
	private CreativeOrder() {
	}

	static void register(List<Entry> entries) {
		Map<String, Entry> remaining = new LinkedHashMap<>();
		for (Entry entry : entries) {
			String id = BuiltInRegistries.ITEM.getKey(entry.item()).getPath();
			if (remaining.put(id, entry) != null) {
				throw new IllegalStateException("Duplicate creative item: " + id);
			}
		}

		JsonObject root = readOrder();
		for (Map.Entry<String, JsonElement> tabEntry : root.entrySet()) {
			ResourceKey<CreativeModeTab> tab = tab(tabEntry.getKey());
			List<Item> ordered = new ArrayList<>();
			for (Map.Entry<String, JsonElement> groupEntry : tabEntry.getValue().getAsJsonObject().entrySet()) {
				JsonArray group = groupEntry.getValue().getAsJsonArray();
				for (JsonElement idElement : group) {
					String id = idElement.getAsString();
					Entry entry = remaining.remove(id);
					if (entry == null) {
						throw new IllegalStateException("Unknown or duplicate creative order item: " + id);
					}
					if (!entry.tab().equals(tab)) {
						throw new IllegalStateException("Creative tab mismatch for " + id + ": " + tabEntry.getKey());
					}
					ordered.add(entry.item());
				}
			}
			CreativeModeTabEvents.modifyOutputEvent(tab).register(output -> ordered.forEach(output::accept));
		}
		if (!remaining.isEmpty()) {
			throw new IllegalStateException("Items missing from creative_order.json: " + remaining.keySet());
		}
	}

	private static JsonObject readOrder() {
		try (var stream = CreativeOrder.class.getResourceAsStream("/matcha/creative_order.json");
			 var reader = new InputStreamReader(Objects.requireNonNull(stream), StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		} catch (Exception exception) {
			throw new IllegalStateException("Could not load Matcha creative order", exception);
		}
	}

	private static ResourceKey<CreativeModeTab> tab(String tab) {
		return switch (tab) {
			case "combat" -> CreativeModeTabs.COMBAT;
			case "foods" -> CreativeModeTabs.FOOD_AND_DRINKS;
			case "functional_blocks" -> CreativeModeTabs.FUNCTIONAL_BLOCKS;
			case "ingredients" -> CreativeModeTabs.INGREDIENTS;
			case "spawn_eggs" -> CreativeModeTabs.SPAWN_EGGS;
			case "tools" -> CreativeModeTabs.TOOLS_AND_UTILITIES;
			default -> throw new IllegalArgumentException("Unknown creative tab: " + tab);
		};
	}

	record Entry(Item item, ResourceKey<CreativeModeTab> tab) {
	}
}
