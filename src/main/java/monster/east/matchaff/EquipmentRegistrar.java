package monster.east.matchaff;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.context.UseOnContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Registers the Matcha equipment batch from matcha/equipment.json.
 *
 * <p>Components that reference datapack registries (enchantments, attributes,
 * block tags, repair materials) are resolved lazily through
 * {@link Item.Properties#delayedComponent} so the datapack's own enchantment
 * definitions are available when default components are baked.</p>
 */
public final class EquipmentRegistrar {
	private static final Logger LOGGER = LoggerFactory.getLogger("matcha");

	private EquipmentRegistrar() {
	}

	public static List<EquipmentItem> registerAll() {
		try (var stream = EquipmentRegistrar.class.getResourceAsStream("/matcha/equipment.json")) {
			EquipmentDefinition[] definitions = new Gson().fromJson(
					new InputStreamReader(Objects.requireNonNull(stream), StandardCharsets.UTF_8),
					EquipmentDefinition[].class
			);
			List<EquipmentItem> items = Arrays.stream(definitions).map(EquipmentRegistrar::register).toList();
			LOGGER.info("Registered {} Matcha equipment items", items.size());
			return items;
		} catch (Exception exception) {
			throw new IllegalStateException("Could not load Matcha equipment definitions", exception);
		}
	}

	private static EquipmentItem register(EquipmentDefinition definition) {
		Item carrier = BuiltInRegistries.ITEM.getValue(Identifier.parse(definition.carrier));
		Item.Properties properties = new Item.Properties();
		CarrierDefaults.apply(properties, definition.carrier);
		for (Map.Entry<String, JsonElement> entry : definition.components.entrySet()) {
			ItemComponents.apply(properties, entry.getKey(), entry.getValue());
		}
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("matcha-flavoured", definition.id));
		Item item = Registry.register(BuiltInRegistries.ITEM, key, createItem(carrier, properties.setId(key)));
		return new EquipmentItem(item, tab(definition));
	}

	private static Item createItem(Item carrier, Item.Properties properties) {
		if (carrier instanceof AxeItem || carrier instanceof HoeItem || carrier instanceof ShovelItem) {
			return new UseOnCarrierItem(carrier, properties);
		}
		return new Item(properties);
	}

	private static ResourceKey<CreativeModeTab> tab(EquipmentDefinition definition) {
		if (definition.components.containsKey("minecraft:equippable")) {
			return CreativeModeTabs.COMBAT;
		}
		if (definition.components.containsKey("minecraft:tool")) {
			return CreativeModeTabs.TOOLS_AND_UTILITIES;
		}
		return CreativeModeTabs.COMBAT;
	}

	public record EquipmentItem(Item item, ResourceKey<CreativeModeTab> tab) {
	}

	private static final class EquipmentDefinition {
		private String id;
		private String carrier;
		private String source;
		private Map<String, JsonElement> components;
	}

	/**
	 * Axe, hoe and shovel interactions live in their Item subclasses rather
	 * than data components. The vanilla carrier operates on the stack stored in
	 * the context, so delegating preserves the exact interaction while damage is
	 * still applied to the migrated Matcha item.
	 */
	private static final class UseOnCarrierItem extends Item {
		private final Item carrier;

		private UseOnCarrierItem(Item carrier, Item.Properties properties) {
			super(properties);
			this.carrier = carrier;
		}

		@Override
		public InteractionResult useOn(UseOnContext context) {
			return carrier.useOn(context);
		}
	}
}
