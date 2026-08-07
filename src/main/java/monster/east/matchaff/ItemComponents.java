package monster.east.matchaff;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.Repairable;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.food.FoodProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies raw Minecraft component JSON to {@link Item.Properties}, shared by the
 * equipment registrar and the fish/simple batch registrar.
 *
 * <p>Components that reference datapack registries (enchantments, attributes,
 * block tags, bundle contents, potions) are resolved lazily through
 * {@link Item.Properties#delayedComponent} so the datapack's own definitions are
 * available when default components are baked.</p>
 */
final class ItemComponents {
	private static final Logger LOGGER = LoggerFactory.getLogger("matcha");

	private ItemComponents() {
	}

	static void apply(Item.Properties properties, String id, JsonElement json) {
		switch (id) {
			case "minecraft:max_damage" -> properties.stacksTo(1).durability(json.getAsInt());
			case "minecraft:max_stack_size" -> properties.stacksTo(json.getAsInt());
			case "minecraft:item_name" ->
					properties.component(DataComponents.ITEM_NAME, decode(ComponentSerialization.CODEC, json));
			case "minecraft:custom_name" ->
					properties.component(DataComponents.CUSTOM_NAME, decode(ComponentSerialization.CODEC, json));
			case "minecraft:lore" -> properties.component(DataComponents.LORE, decode(ItemLore.CODEC, json));
			case "minecraft:rarity" -> properties.component(DataComponents.RARITY, decode(Rarity.CODEC, json));
			case "minecraft:enchantment_glint_override" ->
					properties.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, json.getAsBoolean());
			case "minecraft:unbreakable" -> properties.component(DataComponents.UNBREAKABLE, Unit.INSTANCE);
			case "minecraft:damage" -> properties.stacksTo(1).component(DataComponents.DAMAGE, json.getAsInt());
			case "minecraft:tooltip_display" ->
					properties.component(DataComponents.TOOLTIP_DISPLAY, decode(TooltipDisplay.CODEC, json));
			case "minecraft:enchantments" ->
					properties.delayedComponent(DataComponents.ENCHANTMENTS, provider -> decode(ItemEnchantments.CODEC, provider, json));
			case "minecraft:stored_enchantments" ->
					properties.delayedComponent(DataComponents.STORED_ENCHANTMENTS, provider -> decode(ItemEnchantments.CODEC, provider, json));
			case "minecraft:attribute_modifiers" ->
					properties.delayedComponent(DataComponents.ATTRIBUTE_MODIFIERS, provider -> decode(ItemAttributeModifiers.CODEC, provider, json));
			case "minecraft:tool" ->
					properties.delayedComponent(DataComponents.TOOL, provider -> decode(Tool.CODEC, provider, json));
			case "minecraft:equippable" ->
					properties.delayedComponent(DataComponents.EQUIPPABLE, provider -> decode(Equippable.CODEC, provider, json));
			case "minecraft:repairable" ->
					properties.delayedComponent(DataComponents.REPAIRABLE, provider -> decode(Repairable.CODEC, provider, json));
			case "minecraft:blocks_attacks" ->
					properties.delayedComponent(DataComponents.BLOCKS_ATTACKS, provider -> decode(BlocksAttacks.CODEC, provider, json));
			case "minecraft:bundle_contents" ->
					properties.delayedComponent(DataComponents.BUNDLE_CONTENTS, provider -> decode(BundleContents.CODEC, provider, json));
			case "minecraft:potion_contents" ->
					properties.delayedComponent(DataComponents.POTION_CONTENTS, provider -> decode(PotionContents.CODEC, provider, json));
			case "minecraft:entity_data" ->
					properties.delayedComponent(DataComponents.ENTITY_DATA, provider -> decode(DataComponents.ENTITY_DATA.codec(), provider, json));
			case "minecraft:instrument" ->
					properties.delayedComponent(DataComponents.INSTRUMENT, provider -> decode(DataComponents.INSTRUMENT.codec(), provider, json));
			case "minecraft:jukebox_playable" ->
					properties.delayedComponent(DataComponents.JUKEBOX_PLAYABLE, provider -> decode(DataComponents.JUKEBOX_PLAYABLE.codec(), provider, json));
			case "minecraft:consumable" ->
					properties.delayedComponent(DataComponents.CONSUMABLE, provider -> decode(DataComponents.CONSUMABLE.codec(), provider, json));
			case "minecraft:food" ->
					properties.component(DataComponents.FOOD, decode(FoodProperties.DIRECT_CODEC, json));
			case "minecraft:writable_book_content" ->
					properties.delayedComponent(DataComponents.WRITABLE_BOOK_CONTENT, provider -> decode(DataComponents.WRITABLE_BOOK_CONTENT.codec(), provider, json));
			case "minecraft:banner_patterns" ->
					properties.delayedComponent(DataComponents.BANNER_PATTERNS, provider -> decode(DataComponents.BANNER_PATTERNS.codec(), provider, json));
			case "minecraft:trim" ->
					properties.delayedComponent(DataComponents.TRIM, provider -> decode(DataComponents.TRIM.codec(), provider, json));
			default -> throw new IllegalArgumentException("Unsupported item component: " + id);
		}
	}

	static <T> T decode(Codec<T> codec, JsonElement json) {
		return codec.parse(JsonOps.INSTANCE, json)
				.resultOrPartial(error -> LOGGER.error("Could not decode item component: {}", error))
				.orElseThrow(() -> new IllegalStateException("Invalid item component JSON: " + json));
	}

	static <T> T decode(Codec<T> codec, HolderLookup.Provider provider, JsonElement json) {
		return codec.parse(RegistryOps.create(JsonOps.INSTANCE, provider), json)
				.resultOrPartial(error -> LOGGER.error("Could not decode item component: {}", error))
				.orElseThrow(() -> new IllegalStateException("Invalid item component JSON: " + json));
	}
}
