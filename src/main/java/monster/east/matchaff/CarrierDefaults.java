package monster.east.matchaff;

import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.food.Foods;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.trim.TrimMaterials;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

import java.util.List;
import java.util.Optional;

/**
 * Recreates the vanilla defaults inherited by a datapack item's carrier.
 *
 * <p>Default components are baked only during the server resource reload, so
 * they cannot be copied from {@link Item#components()} while Fabric items are
 * being registered. These calls mirror the corresponding registrations in
 * {@code Items}; the migrated stack's explicit JSON components are applied
 * afterwards and therefore override them just as they did in the datapack.</p>
 */
final class CarrierDefaults {
	private CarrierDefaults() {
	}

	static void apply(Item.Properties properties, String carrier) {
		switch (carrier) {
			case "minecraft:cod" -> properties.food(Foods.COD);
			case "minecraft:salmon" -> properties.food(Foods.SALMON);
			case "minecraft:pufferfish" -> properties.food(Foods.PUFFERFISH, Consumables.PUFFERFISH);
			case "minecraft:poisonous_potato" -> properties.component(DataComponents.FOOD, Foods.POISONOUS_POTATO);
			case "minecraft:rotten_flesh" -> properties.food(Foods.ROTTEN_FLESH, Consumables.ROTTEN_FLESH);

			case "minecraft:wooden_sword" -> properties.sword(ToolMaterial.WOOD, 3.0F, -2.4F);
			case "minecraft:stone_sword" -> properties.sword(ToolMaterial.STONE, 3.0F, -2.4F);
			case "minecraft:netherite_sword" -> properties.sword(ToolMaterial.NETHERITE, 3.0F, -2.4F).fireResistant();

			case "minecraft:copper_axe" -> properties.axe(ToolMaterial.COPPER, 7.0F, -3.2F);
			case "minecraft:stone_axe" -> properties.axe(ToolMaterial.STONE, 7.0F, -3.2F);
			case "minecraft:iron_axe" -> properties.axe(ToolMaterial.IRON, 6.0F, -3.1F);
			case "minecraft:diamond_axe" -> properties.axe(ToolMaterial.DIAMOND, 5.0F, -3.0F);
			case "minecraft:netherite_axe" -> properties.axe(ToolMaterial.NETHERITE, 5.0F, -3.0F).fireResistant();

			case "minecraft:copper_hoe" -> properties.hoe(ToolMaterial.COPPER, -1.0F, -2.0F);
			case "minecraft:stone_hoe" -> properties.hoe(ToolMaterial.STONE, -1.0F, -2.0F);
			case "minecraft:iron_hoe" -> properties.hoe(ToolMaterial.IRON, -2.0F, -1.0F);
			case "minecraft:diamond_hoe" -> properties.hoe(ToolMaterial.DIAMOND, -3.0F, 0.0F);
			case "minecraft:netherite_hoe" -> properties.hoe(ToolMaterial.NETHERITE, -4.0F, 0.0F).fireResistant();

			case "minecraft:stone_pickaxe" -> properties.pickaxe(ToolMaterial.STONE, 1.0F, -2.8F);
			case "minecraft:stone_shovel" -> properties.shovel(ToolMaterial.STONE, 1.5F, -3.0F);
			case "minecraft:iron_shovel" -> properties.shovel(ToolMaterial.IRON, 1.5F, -3.0F);
			case "minecraft:stone_spear" -> properties.spear(
					ToolMaterial.STONE, 0.75F, 0.82F, 0.7F, 4.5F, 13.0F, 9.0F, 5.1F, 13.75F, 4.6F
			);

			case "minecraft:leather_helmet" -> armor(properties, ArmorMaterials.LEATHER, ArmorType.HELMET);
			case "minecraft:leather_chestplate" -> armor(properties, ArmorMaterials.LEATHER, ArmorType.CHESTPLATE);
			case "minecraft:leather_leggings" -> armor(properties, ArmorMaterials.LEATHER, ArmorType.LEGGINGS);
			case "minecraft:leather_boots" -> armor(properties, ArmorMaterials.LEATHER, ArmorType.BOOTS);
			case "minecraft:copper_helmet" -> armor(properties, ArmorMaterials.COPPER, ArmorType.HELMET);
			case "minecraft:copper_chestplate" -> armor(properties, ArmorMaterials.COPPER, ArmorType.CHESTPLATE);
			case "minecraft:copper_leggings" -> armor(properties, ArmorMaterials.COPPER, ArmorType.LEGGINGS);
			case "minecraft:copper_boots" -> armor(properties, ArmorMaterials.COPPER, ArmorType.BOOTS);
			case "minecraft:iron_helmet" -> armor(properties, ArmorMaterials.IRON, ArmorType.HELMET);
			case "minecraft:iron_chestplate" -> armor(properties, ArmorMaterials.IRON, ArmorType.CHESTPLATE);
			case "minecraft:iron_leggings" -> armor(properties, ArmorMaterials.IRON, ArmorType.LEGGINGS);
			case "minecraft:iron_boots" -> armor(properties, ArmorMaterials.IRON, ArmorType.BOOTS);
			case "minecraft:diamond_helmet" -> armor(properties, ArmorMaterials.DIAMOND, ArmorType.HELMET);
			case "minecraft:diamond_chestplate" -> armor(properties, ArmorMaterials.DIAMOND, ArmorType.CHESTPLATE);
			case "minecraft:diamond_leggings" -> armor(properties, ArmorMaterials.DIAMOND, ArmorType.LEGGINGS);
			case "minecraft:diamond_boots" -> armor(properties, ArmorMaterials.DIAMOND, ArmorType.BOOTS);

			case "minecraft:elytra" -> properties
					.durability(432)
					.rarity(Rarity.EPIC)
					.component(DataComponents.GLIDER, Unit.INSTANCE)
					.component(
							DataComponents.EQUIPPABLE,
							Equippable.builder(EquipmentSlot.CHEST)
									.setEquipSound(SoundEvents.ARMOR_EQUIP_ELYTRA)
									.setAsset(EquipmentAssets.ELYTRA)
									.setDamageOnHurt(false)
									.build()
					)
					.repairable(Items.PHANTOM_MEMBRANE);
			case "minecraft:shears" -> properties
					.durability(238)
					.component(DataComponents.TOOL, ShearsItem.createToolProperties());
			case "minecraft:bundle", "minecraft:red_bundle" -> properties
					.stacksTo(1)
					.component(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
			case "minecraft:bow" -> properties.durability(384).enchantable(1);
			case "minecraft:brush" -> properties.durability(64);
			case "minecraft:flint_and_steel" -> properties.durability(64);
			case "minecraft:potion" -> properties
					.stacksTo(1)
					.component(DataComponents.POTION_CONTENTS, PotionContents.EMPTY)
					.component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK)
					.usingConvertsTo(Items.GLASS_BOTTLE);
			case "minecraft:splash_potion" -> properties
					.stacksTo(1)
					.component(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
			case "minecraft:tipped_arrow" -> properties
					.component(DataComponents.POTION_CONTENTS, PotionContents.EMPTY)
					.component(DataComponents.POTION_DURATION_SCALE, 0.125F);
			case "minecraft:enchanted_book" -> properties
					.stacksTo(1)
					.rarity(Rarity.RARE)
					.component(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY)
					.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			case "minecraft:writable_book" -> properties
					.stacksTo(1)
					.component(DataComponents.WRITABLE_BOOK_CONTENT, WritableBookContent.EMPTY);
			case "minecraft:goat_horn" -> properties.rarity(Rarity.UNCOMMON).stacksTo(1);
			case "minecraft:music_disc_11", "minecraft:music_disc_cat" -> properties
					.stacksTo(1)
					.rarity(Rarity.UNCOMMON);
			case "minecraft:music_disc_otherside" -> properties.stacksTo(1).rarity(Rarity.RARE);
			case "minecraft:rabbit_stew" -> properties
					.stacksTo(1)
					.food(Foods.RABBIT_STEW)
					.usingConvertsTo(Items.BOWL);
			case "minecraft:pumpkin_pie" -> properties.food(Foods.PUMPKIN_PIE);
			case "minecraft:baked_potato" -> properties.food(Foods.BAKED_POTATO);
			case "minecraft:shield" -> shield(properties);
			case "minecraft:emerald" -> properties.trimMaterial(TrimMaterials.EMERALD);

			// These carriers have no defaults beyond components explicitly present
			// in the migrated definitions.
			case "minecraft:paper",
					"minecraft:compass",
					"minecraft:item_frame",
					"minecraft:villager_spawn_egg",
					"minecraft:chicken_spawn_egg",
					"minecraft:cow_spawn_egg",
					"minecraft:pig_spawn_egg",
					"minecraft:sheep_spawn_egg" -> {
			}
			default -> throw new IllegalArgumentException("Unsupported vanilla carrier defaults: " + carrier);
		}
	}

	private static void armor(Item.Properties properties, net.minecraft.world.item.equipment.ArmorMaterial material, ArmorType type) {
		properties.humanoidArmor(material, type);
	}

	private static void shield(Item.Properties properties) {
		properties
				.durability(336)
				.component(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY)
				.repairable(ItemTags.WOODEN_TOOL_MATERIALS)
				.equippableUnswappable(EquipmentSlot.OFFHAND)
				.delayedComponent(
						DataComponents.BLOCKS_ATTACKS,
						context -> new BlocksAttacks(
								0.25F,
								1.0F,
								List.of(new BlocksAttacks.DamageReduction(90.0F, Optional.empty(), 0.0F, 1.0F)),
								new BlocksAttacks.ItemDamageFunction(3.0F, 1.0F, 1.0F),
								Optional.of(context.getOrThrow(DamageTypeTags.BYPASSES_SHIELD)),
								Optional.of(SoundEvents.SHIELD_BLOCK),
								Optional.of(SoundEvents.SHIELD_BREAK)
						)
				)
				.component(DataComponents.BREAK_SOUND, SoundEvents.SHIELD_BREAK);
	}
}
