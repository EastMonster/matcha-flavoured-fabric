package monster.east.matchaff;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.BrushItem;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.InstrumentItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemFrameItem;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.SplashPotionItem;
import net.minecraft.world.item.TippedArrowItem;
import net.minecraft.world.item.WritableBookItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Registers the Matcha fish + remaining simple/review batch from
 * matcha/fish_simple.json.
 *
 * <p>The datapack used vanilla items as carriers (salmon, bundle, tipped
 * arrow, ...), so each migrated item keeps its original carrier's behaviour by
 * registering the same vanilla {@link Item} subclass the carrier used.</p>
 */
public final class SimpleItemRegistrar {
	private static final Logger LOGGER = LoggerFactory.getLogger("matcha");
	private static final List<String> RESOURCES = List.of(
			"/matcha/fish_simple.json", "/matcha/blessings.json", "/matcha/behavior_music.json", "/matcha/second_tier.json"
	);

	private SimpleItemRegistrar() {
	}

	public static List<BatchItem> registerAll() {
		List<BatchItem> items = new ArrayList<>();
		for (String resource : RESOURCES) {
			items.addAll(registerFile(resource));
		}
		return items;
	}

	private static List<BatchItem> registerFile(String resource) {
		try (var stream = SimpleItemRegistrar.class.getResourceAsStream(resource)) {
			BatchDefinition[] definitions = new Gson().fromJson(
					new InputStreamReader(Objects.requireNonNull(stream), StandardCharsets.UTF_8),
					BatchDefinition[].class
			);
			List<BatchItem> items = Arrays.stream(definitions).map(SimpleItemRegistrar::register).toList();
			LOGGER.info("Registered {} Matcha items from {}", items.size(), resource);
			return items;
		} catch (Exception exception) {
			throw new IllegalStateException("Could not load Matcha definitions from " + resource, exception);
		}
	}

	private static BatchItem register(BatchDefinition definition) {
		Item.Properties properties = new Item.Properties();
		if (definition.carrier != null) {
			CarrierDefaults.apply(properties, definition.carrier);
		}
		if (definition.type.equals("bundle")) {
			properties.stacksTo(1);
		}
		for (Map.Entry<String, JsonElement> entry : definition.components.entrySet()) {
			ItemComponents.apply(properties, entry.getKey(), entry.getValue());
		}
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("matcha-flavoured", definition.id));
		Item item = Registry.register(BuiltInRegistries.ITEM, key, createItem(definition, properties.setId(key)));
		return new BatchItem(item, tab(definition.tab));
	}

	private static Item createItem(BatchDefinition definition, Item.Properties properties) {
		return switch (definition.type) {
			case "item" -> new Item(properties);
			case "arrow" -> "minecraft:tipped_arrow".equals(definition.carrier)
					? new TippedArrowItem(properties)
					: new ArrowItem(properties);
			case "bow" -> new BowItem(properties);
			case "bundle" -> new BundleItem(properties);
			case "brush" -> new BrushItem(properties);
			case "compass" -> new CompassItem(properties);
			case "shears" -> new ShearsItem(properties);
			case "flint_and_steel" -> new FlintAndSteelItem(properties);
			case "splash_potion" -> new SplashPotionItem(properties);
			case "spawn_egg" -> new SpawnEggItem(properties);
			case "item_frame" -> new ItemFrameItem(EntityTypes.ITEM_FRAME, properties);
			case "instrument" -> new InstrumentItem(properties);
			case "music_disc" -> new Item(properties);
			case "potion" -> new PotionItem(properties);
			case "book" -> new WritableBookItem(properties);
			case "shield" -> new ShieldItem(properties);
			default -> throw new IllegalArgumentException("Unknown item type: " + definition.type);
		};
	}

	private static ResourceKey<CreativeModeTab> tab(String tab) {
		return switch (tab) {
			case "combat" -> CreativeModeTabs.COMBAT;
			case "functional_blocks" -> CreativeModeTabs.FUNCTIONAL_BLOCKS;
			case "tools" -> CreativeModeTabs.TOOLS_AND_UTILITIES;
			case "ingredients" -> CreativeModeTabs.INGREDIENTS;
			case "foods" -> CreativeModeTabs.FOOD_AND_DRINKS;
			case "spawn_eggs" -> CreativeModeTabs.SPAWN_EGGS;
			default -> throw new IllegalArgumentException("Unknown creative tab: " + tab);
		};
	}

	public record BatchItem(Item item, ResourceKey<CreativeModeTab> tab) {
	}

	private static final class BatchDefinition {
		private String id;
		private String type;
		private String tab;
		private String carrier;
		private String source;
		private Map<String, JsonElement> components;
	}
}
