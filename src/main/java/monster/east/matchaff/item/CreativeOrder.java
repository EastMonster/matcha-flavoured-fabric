package monster.east.matchaff.item;

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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Adds every Matcha item to its creative tab in the semantic order defined by creative_order.json. */
public class CreativeOrder {
	private CreativeOrder() {
	}

	public static void register(List<Entry> entries) {
		Map<String, Entry> byId = new LinkedHashMap<>();
		for (Entry entry : entries) {
			String id = BuiltInRegistries.ITEM.getKey(entry.item()).getPath();
			if (byId.put(id, entry) != null) {
				throw new IllegalStateException("Duplicate creative item: " + id);
			}
		}

		Map<String, List<ResourceKey<CreativeModeTab>>> placedIn = new LinkedHashMap<>();
		JsonObject root = readOrder();
		for (Map.Entry<String, JsonElement> tabEntry : root.entrySet()) {
			ResourceKey<CreativeModeTab> tab = tab(tabEntry.getKey());
			List<Item> ordered = new ArrayList<>();
			for (Map.Entry<String, JsonElement> groupEntry : tabEntry.getValue().getAsJsonObject().entrySet()) {
				JsonArray group = groupEntry.getValue().getAsJsonArray();
				for (JsonElement idElement : group) {
					String id = idElement.getAsString();
					Entry entry = byId.get(id);
					if (entry == null) {
						throw new IllegalStateException("Unknown creative order item: " + id);
					}
					placedIn.computeIfAbsent(id, k -> new ArrayList<>()).add(tab);
					ordered.add(entry.item());
				}
			}
			CreativeModeTabEvents.modifyOutputEvent(tab).register(output -> ordered.forEach(output::accept));
		}
		Set<String> missing = new HashSet<>();
		for (Entry entry : entries) {
			String id = BuiltInRegistries.ITEM.getKey(entry.item()).getPath();
			List<ResourceKey<CreativeModeTab>> tabs = placedIn.get(id);
			if (tabs == null) {
				missing.add(id);
			} else if (!tabs.contains(entry.tab())) {
				throw new IllegalStateException("Creative item " + id + " not in its primary tab: " + entry.tab());
			}
		}
		if (!missing.isEmpty()) {
			throw new IllegalStateException("Items missing from creative_order.json: " + missing);
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

	public record Entry(Item item, ResourceKey<CreativeModeTab> tab) {
	}
}
