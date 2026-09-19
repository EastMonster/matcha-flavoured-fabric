package monster.east.matchaff.datafix.fix;

import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * V4 migrates Warding and intrinsic enchantments, plus legacy equipment lore and resource-model data.
 * It also handles Adamant equipment, Shakudo enchantments, Electrum tools, map models, and classic flowers.
 */
public final class V4Migration extends DataFix {
	private static final String NEW_NAMESPACE = "matcha:";
	private static final String ADAMANT_ARMOUR = "matcha:adamant_armour";
	private static final String ADAMANT_WEAPON = "matcha:adamant_weapon";
	private static final String ADAMANT_TOOL = "matcha:adamant_tool";
	private static final String ADAMANT_DOLABRA = "matcha:adamant_dolabra";
	private static final Set<String> ADAMANT_ARMOUR_ITEMS = Set.of(
			"minecraft:netherite_helmet", "minecraft:netherite_chestplate",
			"minecraft:netherite_leggings", "minecraft:netherite_boots"
	);
	private static final Set<String> ADAMANT_WEAPON_ITEMS = Set.of(
			"minecraft:netherite_sword", "minecraft:netherite_spear", "matcha:adamant_claymore"
	);
	private static final Set<String> ADAMANT_TOOL_ITEMS = Set.of(
			"minecraft:netherite_axe", "minecraft:netherite_pickaxe", "minecraft:netherite_shovel",
			"minecraft:netherite_hoe", ADAMANT_DOLABRA, "matcha:adamant_mattock"
	);
	private static final Map<String, String> WARDING_ENCHANTMENT_IDS = Map.ofEntries(
			Map.entry("matcha:warding0", "matcha:warding_1"),
			Map.entry("matcha:warding1", "matcha:warding_2"),
			Map.entry("matcha:warding2", "matcha:warding_3"),
			Map.entry("matcha:warding3", "matcha:warding_4"),
			Map.entry("matcha:warding_armour", "matcha:electrum_armour")
	);
	private static final String WARDING_SHIELD = "matcha:warding_shield";
	private static final Map<String, String> CLASSIC_FLOWER_MODELS = Map.of(
			"minecraft:rose_classic", "rose_classic",
			"minecraft:cyan_rose_classic", "cyan_rose_classic",
			"minecraft:dandelion_classic", "dandelion_classic",
			"matcha:rose_classic", "rose_classic",
			"matcha:cyan_rose_classic", "cyan_rose_classic",
			"matcha:dandelion_classic", "dandelion_classic"
	);
	private static final Map<String, String> MAP_ITEM_MODELS = Map.of(
			"minecraft:abbey_map", "matcha:abbey_map",
			"minecraft:papal_outpost_map", "matcha:papal_outpost_map"
	);
	private static final Map<String, Integer> LEGACY_FORTUNE = Map.of(
			"matcha:electrum_axe", 2, "matcha:electrum_dolabra", 2,
			"matcha:electrum_hoe", 3, "matcha:electrum_mattock", 2,
			"matcha:electrum_pickaxe", 3, "matcha:electrum_shovel", 3
	);
	private static final Map<String, Integer> TOOL_LEVELS = Map.of(
			"matcha:electrum_axe", 3, "matcha:electrum_dolabra", 3,
			"matcha:electrum_hoe", 3, "matcha:electrum_mattock", 2,
			"matcha:electrum_pickaxe", 3, "matcha:electrum_shovel", 3
	);
	private static final Map<String, Integer> SMITE_LEVELS = Map.of(
			"matcha:electrum_axe", 3, "matcha:electrum_spear", 4, "matcha:electrum_sword", 3
	);
	private static final Map<String, String> SHAKUDO_IDS = Map.of(
			"matcha:sanguine", "matcha:shakudo_weapon", "matcha:shakudo_regen", "matcha:shakudo_armour"
	);

	public V4Migration(Schema outputSchema) {
		super(outputSchema, false);
	}

	@Override
	protected TypeRewriteRule makeRule() {
		Type<?> root = getInputSchema().getType(MatchaDataFixSupport.ROOT);
		return writeFixAndRead("Matcha V4 item migration", root, root, V4Migration::migrate);
	}

	static Dynamic<?> migrate(Dynamic<?> data) {
		return MatchaDataFixSupport.apply(data, tag -> {
			Tag wardingMigrated = migrateWardingEnchantments(tag, false, null);
			Tag adamantMigrated = migrateAdamant(wardingMigrated, false, null, false);
			Tag loreMigrated = migrateLegacyEquipmentLore(adamantMigrated, false, null);
			renameV4TranslationKeys(loreMigrated);
			Tag mapMigrated = migrateMapItemModels(loreMigrated, false);
			Tag flowersMigrated = migrateClassicFlowers(mapMigrated, false);
			return migrateIntrinsic(flowersMigrated, false);
		});
	}

	private static Tag migrateMapItemModels(Tag tag, boolean customData) {
		if (tag instanceof CompoundTag compound) {
			if (!customData && compound.get("components") instanceof CompoundTag components) {
				String model = MAP_ITEM_MODELS.get(components.getStringOr("minecraft:item_model", ""));
				if (model != null) components.putString("minecraft:item_model", model);
			}
			for (String key : List.copyOf(compound.keySet())) {
				Tag child = compound.get(key);
				if (child != null) compound.put(key, migrateMapItemModels(child, customData || key.equals("minecraft:custom_data")));
			}
		} else if (tag instanceof ListTag list) {
			for (int index = 0; index < list.size(); index++) list.set(index, migrateMapItemModels(list.get(index), customData));
		}
		return tag;
	}

	private static Tag migrateClassicFlowers(Tag tag, boolean customData) {
		if (tag instanceof CompoundTag compound) {
			if (!customData && compound.contains("id")) {
				CompoundTag components = compound.get("components") instanceof CompoundTag existing ? existing : null;
				String oldModel = components == null ? "" : components.getStringOr("minecraft:item_model", "");
				String path = CLASSIC_FLOWER_MODELS.get(oldModel);
				if (path != null) {
					compound.putString("id", NEW_NAMESPACE + path);
					if (components == null) components = new CompoundTag();
					components.putString("minecraft:item_model", NEW_NAMESPACE + path);
					CompoundTag itemName = new CompoundTag();
					itemName.putString("translate", "item.matcha." + path);
					components.put("minecraft:item_name", itemName);
					compound.put("components", components);
				}
			}
			for (String key : List.copyOf(compound.keySet())) {
				Tag child = compound.get(key);
				if (child != null) compound.put(key, migrateClassicFlowers(child, customData || key.equals("minecraft:custom_data")));
			}
			return compound;
		}
		if (tag instanceof ListTag list) {
			for (int index = 0; index < list.size(); index++) list.set(index, migrateClassicFlowers(list.get(index), customData));
		}
		return tag;
	}

	private static Tag migrateWardingEnchantments(Tag tag, boolean customData, String itemId) {
		if (tag instanceof CompoundTag compound) {
			String id = compound.getStringOr("id", "");
			String currentItemId = !customData && id.equals(WARDING_SHIELD) ? id : itemId;
			for (String key : List.copyOf(compound.keySet())) {
				Tag child = compound.get(key);
				if (child == null) continue;
				boolean childCustomData = customData || key.equals("minecraft:custom_data");
				Tag migrated = migrateWardingEnchantments(child, childCustomData, currentItemId);
				if (!customData && MatchaDataFixSupport.isEnchantmentComponent(key)
						&& migrated instanceof CompoundTag enchantments) {
					renameWardingEnchantments(enchantments);
					updateIntrinsicWarding(enchantments, currentItemId);
				} else if (!customData && MatchaDataFixSupport.isTranslationComponent(key)) {
					renameV4TranslationKeys(migrated);
				}
				compound.put(key, migrated);
			}
			return compound;
		}
		if (tag instanceof ListTag list) {
			for (int index = 0; index < list.size(); index++) list.set(index, migrateWardingEnchantments(list.get(index), customData, itemId));
		}
		return tag;
	}

	private static Tag migrateAdamant(Tag tag, boolean customData, String itemId, boolean adamant) {
		if (tag instanceof CompoundTag compound) {
			String currentItemId = itemId;
			boolean currentAdamant = adamant;
			if (!customData && compound.contains("id")) {
				currentItemId = compound.getStringOr("id", "");
				currentAdamant = isAdamantStack(compound, currentItemId);
			}
			for (String key : List.copyOf(compound.keySet())) {
				Tag child = compound.get(key);
				if (child == null) continue;
				boolean childCustomData = customData || key.equals("minecraft:custom_data");
				compound.put(key, migrateAdamant(child, childCustomData, currentItemId, currentAdamant));
			}
			if (!customData && currentAdamant && compound.contains("id")) {
				CompoundTag components = compound.get("components") instanceof CompoundTag existing ? existing : new CompoundTag();
				updateAdamantComponents(components, currentItemId);
				compound.put("components", components);
			}
			return compound;
		}
		if (tag instanceof ListTag list) {
			for (int index = 0; index < list.size(); index++) list.set(index, migrateAdamant(list.get(index), customData, itemId, adamant));
		}
		return tag;
	}

	private static boolean isAdamantStack(CompoundTag stack, String itemId) {
		if (!ADAMANT_ARMOUR_ITEMS.contains(itemId) && !ADAMANT_WEAPON_ITEMS.contains(itemId)
				&& !ADAMANT_TOOL_ITEMS.contains(itemId)) return false;
		if ((ADAMANT_WEAPON_ITEMS.contains(itemId) || ADAMANT_TOOL_ITEMS.contains(itemId))
				&& !itemId.startsWith("minecraft:netherite_")) return true;
		if (stack.get("components") instanceof CompoundTag components) {
			if (components.get("minecraft:custom_data") instanceof CompoundTag customData
					&& customData.contains("has_intrinsic_enchants")) return true;
			for (String component : MatchaDataFixSupport.enchantmentComponents()) {
				if (components.get(component) instanceof CompoundTag enchantments
						&& (enchantments.contains("matcha:divinity") || enchantments.contains("matcha-flavoured:divinity"))) return true;
			}
			if (components.get("minecraft:item_name") instanceof CompoundTag itemName
					&& itemName.getStringOr("translate", "").equals("item." + itemId.replace(':', '.'))) return true;
		}
		return false;
	}

	private static void updateAdamantComponents(CompoundTag components, String itemId) {
		CompoundTag enchantments = components.get("minecraft:enchantments") instanceof CompoundTag existing ? existing : new CompoundTag();
		updateAdamantEnchantments(enchantments, itemId);
		components.put("minecraft:enchantments", enchantments);
		if (components.get("minecraft:stored_enchantments") instanceof CompoundTag stored) updateAdamantEnchantments(stored, itemId);
		if (ADAMANT_DOLABRA.equals(itemId)) {
			if (components.get("minecraft:attribute_modifiers") instanceof ListTag modifiers) updateDolabraAttackDamage(modifiers);
			if (components.get("minecraft:lore") instanceof ListTag lore) updateDolabraLore(lore);
		}
	}

	private static void updateAdamantEnchantments(CompoundTag enchantments, String itemId) {
		if (ADAMANT_DOLABRA.equals(itemId)) {
			enchantments.remove("matcha:divinity");
			enchantments.remove("matcha-flavoured:divinity");
			enchantments.putInt(ADAMANT_TOOL, 1);
		} else if (ADAMANT_ARMOUR_ITEMS.contains(itemId)) {
			moveEnchantment(enchantments, "matcha:divinity", ADAMANT_ARMOUR);
			moveEnchantment(enchantments, "matcha-flavoured:divinity", ADAMANT_ARMOUR);
			enchantments.putInt(ADAMANT_ARMOUR, 1);
		} else if (ADAMANT_WEAPON_ITEMS.contains(itemId) || ADAMANT_TOOL_ITEMS.contains(itemId)) {
			enchantments.remove("matcha:divinity");
			enchantments.remove("matcha-flavoured:divinity");
			if (ADAMANT_WEAPON_ITEMS.contains(itemId)) enchantments.putInt(ADAMANT_WEAPON, 1);
			if (ADAMANT_TOOL_ITEMS.contains(itemId)) enchantments.putInt(ADAMANT_TOOL, 1);
			if (itemId.equals("minecraft:netherite_axe")) enchantments.putInt(ADAMANT_WEAPON, 1);
		}
	}

	private static void moveEnchantment(CompoundTag enchantments, String oldId, String newId) {
		Tag level = enchantments.remove(oldId);
		if (level != null && !enchantments.contains(newId)) enchantments.put(newId, level);
	}

	private static void updateDolabraAttackDamage(ListTag modifiers) {
		for (Tag tag : modifiers) {
			if (tag instanceof CompoundTag modifier && modifier.getStringOr("id", "").equals("attack_damage")
					&& Double.compare(modifier.getDoubleOr("amount", Double.NaN), 9.0) == 0) modifier.putDouble("amount", 6.0);
		}
	}

	private static void updateDolabraLore(ListTag lore) {
		for (Tag tag : lore) {
			if (tag instanceof CompoundTag component && component.getStringOr("text", "").equals("🗡 10")) component.putString("text", "🗡 7");
		}
	}

	private static Tag migrateLegacyEquipmentLore(Tag tag, boolean customData, String itemId) {
		if (tag instanceof CompoundTag compound) {
			String currentItemId = !customData && compound.contains("id") ? compound.getStringOr("id", "") : itemId;
			for (String key : List.copyOf(compound.keySet())) {
				Tag child = compound.get(key);
				if (child != null) compound.put(key, migrateLegacyEquipmentLore(child, customData || key.equals("minecraft:custom_data"), currentItemId));
			}
			if (!customData && isLegacyVanillaEquipment(currentItemId)
					&& compound.get("components") instanceof CompoundTag components
					&& components.get("minecraft:lore") instanceof ListTag lore) {
				if (ADAMANT_ARMOUR_ITEMS.contains(currentItemId)) updateAdamantSetBonusLore(lore);
				for (Tag entry : lore) {
					if (!(entry instanceof CompoundTag component)) continue;
					String text = component.getStringOr("text", "");
					String key = loreTranslationKey(text);
					if (key == null) continue;
					component.remove("text");
					component.putString("translate", key);
					ListTag with = new ListTag();
					with.add(StringTag.valueOf(text.substring(text.indexOf(' ') + 1)));
					component.put("with", with);
				}
			}
			return compound;
		}
		if (tag instanceof ListTag list) {
			for (int index = 0; index < list.size(); index++) list.set(index, migrateLegacyEquipmentLore(list.get(index), customData, itemId));
		}
		return tag;
	}

	private static void updateAdamantSetBonusLore(ListTag lore) {
		boolean hasDoom = false;
		for (Tag entry : lore) {
			if (entry instanceof CompoundTag component
					&& component.getStringOr("translate", "").equals("enchantment.matcha.adamant_armour.set_bonus")) {
				hasDoom = true;
				break;
			}
		}
		for (int index = 0; index < lore.size(); index++) {
			if (!(lore.get(index) instanceof CompoundTag component)) continue;
			String translate = component.getStringOr("translate", "");
			if (translate.equals("enchantment.matcha.adamant_armour.set_bonus") && component.getStringOr("color", "").equals("aqua")) {
				component.putString("translate", "enchantment.matcha.divinity.set_bonus");
				CompoundTag doom = component.copy();
				doom.putString("translate", "enchantment.matcha.adamant_armour.set_bonus");
				doom.putString("color", "red");
				lore.add(index, doom);
				return;
			}
			if (!hasDoom && translate.equals("enchantment.matcha.divinity.set_bonus") && component.getStringOr("color", "").equals("aqua")) {
				CompoundTag doom = component.copy();
				doom.putString("translate", "enchantment.matcha.adamant_armour.set_bonus");
				doom.putString("color", "red");
				lore.add(index, doom);
				return;
			}
		}
	}

	private static boolean isLegacyVanillaEquipment(String id) {
		if (id == null || !id.startsWith("minecraft:")) return false;
		String path = id.substring("minecraft:".length());
		return path.equals("mace")
				|| path.matches("(?:wooden|copper|iron|golden|diamond|netherite)_(?:axe|hoe|pickaxe|shovel|spear|sword)")
				|| path.matches("netherite_(?:helmet|chestplate|leggings|boots)");
	}

	private static String loreTranslationKey(String text) {
		if (text.startsWith("🗡 ")) return "desc.matcha.attack_damage";
		if (text.startsWith("🕒 ")) return "desc.matcha.cooldown";
		if (text.startsWith("⛏ ")) return "desc.matcha.mining_speed";
		if (text.startsWith("🛡 ")) return "desc.matcha.armour";
		if (text.startsWith("\uE008 ")) return "desc.matcha.armour_toughness";
		if (text.startsWith("🏃 ")) return "desc.matcha.speed_attribute";
		if (text.startsWith("💥🚫 ")) return "desc.matcha.knockback_resistance";
		return null;
	}

	private static void renameWardingEnchantments(CompoundTag enchantments) {
		for (String oldId : List.copyOf(enchantments.keySet())) {
			String newId = WARDING_ENCHANTMENT_IDS.get(oldId);
			if (newId == null) continue;
			Tag level = enchantments.remove(oldId);
			if (!enchantments.contains(newId)) enchantments.put(newId, level);
		}
	}

	private static void updateIntrinsicWarding(CompoundTag enchantments, String itemId) {
		if (WARDING_SHIELD.equals(itemId)) {
			Tag level = enchantments.remove("matcha:warding_2");
			if (level != null && !enchantments.contains("matcha:warding_1")) enchantments.put("matcha:warding_1", level);
		}
	}

	private static void renameV4TranslationKeys(Tag tag) {
		if (tag instanceof CompoundTag compound) {
			String translate = compound.getStringOr("translate", "");
			String renamed = switch (translate) {
				case "enchantment.matcha.warding0" -> "enchantment.matcha.warding_1";
				case "enchantment.matcha.warding1" -> "enchantment.matcha.warding_2";
				case "enchantment.matcha.warding2" -> "enchantment.matcha.warding_3";
				case "enchantment.matcha.warding3" -> "enchantment.matcha.warding_4";
				case "effect.matcha.regen_ii" -> "effect.matcha.regen_2";
				case "effect.matcha.weakness_ii" -> "effect.matcha.weakness_2";
				case "effect.matcha.poison_ii" -> "effect.matcha.poison_2";
				case "effect.matcha.haste_ii" -> "effect.matcha.haste_2";
				case "effect.matcha.strength_ii" -> "effect.matcha.strength_2";
				case "effect.matcha.levitation_iii" -> "effect.matcha.levitation_3";
				case "effect.matcha.levitation_xxx" -> "effect.matcha.levitation_30";
				default -> null;
			};
			if (renamed != null) compound.putString("translate", renamed);
			for (String key : List.copyOf(compound.keySet())) {
				Tag child = compound.get(key);
				if (child != null) renameV4TranslationKeys(child);
			}
		} else if (tag instanceof ListTag list) {
			for (Tag child : list) renameV4TranslationKeys(child);
		}
	}

	private static Tag migrateIntrinsic(Tag tag, boolean customData) {
		if (tag instanceof CompoundTag compound) {
			String itemId = compound.getStringOr("id", "");
			CompoundTag components = compound.get("components") instanceof CompoundTag existing ? existing : compound;
			if (!customData) {
				migrateShakudo(components, "minecraft:enchantments");
				migrateShakudo(components, "minecraft:stored_enchantments");
			}
			if (!customData && components.get("minecraft:enchantments") instanceof CompoundTag enchantments) {
				Integer fortune = LEGACY_FORTUNE.get(itemId);
				if (fortune != null) {
					if (enchantments.getIntOr("minecraft:fortune", 0) == fortune) enchantments.remove("minecraft:fortune");
					int level = TOOL_LEVELS.get(itemId);
					if (enchantments.getIntOr("matcha:electrum_tool", 0) < level) enchantments.putInt("matcha:electrum_tool", level);
				}
				int smite = SMITE_LEVELS.getOrDefault(itemId, 0);
				if (enchantments.getIntOr("minecraft:smite", 0) < smite) enchantments.putInt("minecraft:smite", smite);
			}
			for (String key : List.copyOf(compound.keySet())) {
				Tag child = compound.get(key);
				if (child != null) compound.put(key, migrateIntrinsic(child, customData || key.equals("minecraft:custom_data")));
			}
		} else if (tag instanceof ListTag list) {
			for (int index = 0; index < list.size(); index++) list.set(index, migrateIntrinsic(list.get(index), customData));
		}
		return tag;
	}

	private static void migrateShakudo(CompoundTag components, String componentId) {
		if (!(components.get(componentId) instanceof CompoundTag enchantments)) return;
		SHAKUDO_IDS.forEach((oldId, newId) -> {
			Tag level = enchantments.remove(oldId);
			if (level != null && !enchantments.contains(newId)) enchantments.put(newId, level);
		});
	}
}
