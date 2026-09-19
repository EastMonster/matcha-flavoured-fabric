package monster.east.matchaff.datafix;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.Map;

/** Run with assertions enabled to check the isolated ItemStack DataFixer rule. */
public final class MatchaItemDataFixerCheck {
	private MatchaItemDataFixerCheck() {
	}

	public static void main(String[] args) {
		MatchaItemDataFixer.markCurrent(null);
		CompoundTag oldStack = new CompoundTag();
		oldStack.putString("id", "matcha-flavoured:nazar");
		oldStack.putInt("count", 1);
		assert "matcha:nazar".equals(MatchaItemDataFixer.update(oldStack).getStringOr("id", ""));

		CompoundTag vanillaStack = new CompoundTag();
		vanillaStack.putString("id", "minecraft:apple");
		assert "minecraft:apple".equals(MatchaItemDataFixer.update(vanillaStack).getStringOr("id", ""));

		CompoundTag renamedStack = new CompoundTag();
		renamedStack.putString("id", "matcha-flavoured:heart_container");
		assert "matcha:crystal_heart".equals(MatchaItemDataFixer.update(renamedStack).getStringOr("id", ""));
		renamedStack.putString("id", "matcha-flavoured:application");
		assert "matcha:asylum_seeker".equals(MatchaItemDataFixer.update(renamedStack).getStringOr("id", ""));

		CompoundTag oldAxolotl = new CompoundTag();
		oldAxolotl.putString("id", "matcha:axolotl");
		CompoundTag oldAxolotlComponents = new CompoundTag();
		oldAxolotlComponents.putInt("minecraft:max_stack_size", 1);
		oldAxolotl.put("components", oldAxolotlComponents);
		CompoundTag fixedAxolotl = MatchaItemDataFixer.update(oldAxolotl);
		assert "minecraft:axolotl_spawn_egg".equals(fixedAxolotl.getStringOr("id", ""));
		CompoundTag fixedAxolotlComponents = fixedAxolotl.getCompound("components").orElseThrow();
		assert "item.matcha.axolotl".equals(fixedAxolotlComponents.getCompound("minecraft:item_name").orElseThrow().getStringOr("translate", ""));
		assert "matcha:axolotl".equals(fixedAxolotlComponents.getStringOr("minecraft:item_model", ""));
		assert "minecraft:axolotl".equals(fixedAxolotlComponents.getCompound("minecraft:entity_data").orElseThrow().getStringOr("id", ""));
		assert fixedAxolotlComponents.getIntOr("minecraft:max_stack_size", 0) == 1;

		CompoundTag oldNamespaceAxolotl = new CompoundTag();
		oldNamespaceAxolotl.putString("id", "matcha-flavoured:axolotl");
		assert "minecraft:axolotl_spawn_egg".equals(MatchaItemDataFixer.update(oldNamespaceAxolotl).getStringOr("id", ""));

		CompoundTag nazarCarrier = new CompoundTag();
		nazarCarrier.putString("id", "minecraft:glistering_melon_slice");
		CompoundTag nazarComponents = new CompoundTag();
		CompoundTag nazarEnchantments = new CompoundTag();
		nazarEnchantments.putInt("matcha:warding1", 1);
		nazarComponents.put("minecraft:enchantments", nazarEnchantments);
		nazarCarrier.put("components", nazarComponents);
		assert "matcha:nazar".equals(MatchaItemDataFixer.update(nazarCarrier).getStringOr("id", ""));

		CompoundTag plainCarrier = new CompoundTag();
		plainCarrier.putString("id", "minecraft:glistering_melon_slice");
		assert "minecraft:glistering_melon_slice".equals(MatchaItemDataFixer.update(plainCarrier).getStringOr("id", ""));

		Map.of(
				"minecraft:rose_classic", "rose_classic",
				"minecraft:cyan_rose_classic", "cyan_rose_classic",
				"minecraft:dandelion_classic", "dandelion_classic"
		).forEach((oldModel, path) -> {
			CompoundTag classicFlower = new CompoundTag();
			classicFlower.putString("id", "minecraft:poppy");
			CompoundTag classicFlowerComponents = new CompoundTag();
			classicFlowerComponents.putString("minecraft:item_model", oldModel);
			classicFlower.put("components", classicFlowerComponents);
			CompoundTag fixedClassicFlower = MatchaItemDataFixer.update(classicFlower);
			assert ("matcha:" + path).equals(fixedClassicFlower.getStringOr("id", ""));
			CompoundTag fixedComponents = fixedClassicFlower.getCompound("components").orElseThrow();
			assert ("matcha:" + path).equals(fixedComponents.getStringOr("minecraft:item_model", ""));
			assert ("item.matcha." + path).equals(
					fixedComponents.getCompound("minecraft:item_name").orElseThrow().getStringOr("translate", ""));
		});

		// Old-build Nazar used the pre-migration enchantment namespace.
		CompoundTag nazarLegacy = new CompoundTag();
		nazarLegacy.putString("id", "minecraft:glistering_melon_slice");
		CompoundTag nazarLegacyComponents = new CompoundTag();
		CompoundTag nazarLegacyEnchantments = new CompoundTag();
		nazarLegacyEnchantments.putInt("matcha-flavoured:warding1", 1);
		nazarLegacyComponents.put("minecraft:enchantments", nazarLegacyEnchantments);
		nazarLegacy.put("components", nazarLegacyComponents);
		assert "matcha:nazar".equals(MatchaItemDataFixer.update(nazarLegacy).getStringOr("id", ""));
		assert nazarLegacyEnchantments.contains("matcha:warding_2") && !nazarLegacyEnchantments.contains("matcha-flavoured:warding1");

		CompoundTag steelCarrier = new CompoundTag();
		steelCarrier.putString("id", "minecraft:resin_brick");
		assert "matcha:steel".equals(MatchaItemDataFixer.update(steelCarrier).getStringOr("id", ""));

		CompoundTag shakudoCarrier = new CompoundTag();
		shakudoCarrier.putString("id", "minecraft:shulker_shell");
		assert "matcha:shakudo".equals(MatchaItemDataFixer.update(shakudoCarrier).getStringOr("id", ""));

		CompoundTag hepatizonCarrier = new CompoundTag();
		hepatizonCarrier.putString("id", "minecraft:phantom_membrane");
		assert "matcha:hepatizon".equals(MatchaItemDataFixer.update(hepatizonCarrier).getStringOr("id", ""));

		CompoundTag electrumCarrier = new CompoundTag();
		electrumCarrier.putString("id", "minecraft:heart_of_the_sea");
		assert "matcha:electrum".equals(MatchaItemDataFixer.update(electrumCarrier).getStringOr("id", ""));

		CompoundTag divineFragmentCarrier = new CompoundTag();
		divineFragmentCarrier.putString("id", "minecraft:turtle_scute");
		assert "matcha:divine_fragment".equals(MatchaItemDataFixer.update(divineFragmentCarrier).getStringOr("id", ""));

		CompoundTag nonItem = new CompoundTag();
		nonItem.putString("id", "matcha-flavoured:abbey_overgrown");
		assert "matcha-flavoured:abbey_overgrown".equals(MatchaItemDataFixer.update(nonItem).getStringOr("id", ""));

		Map.ofEntries(
				Map.entry("bronze_axe", "hepatizon_axe"),
				Map.entry("bronze_boots", "hepatizon_boots"),
				Map.entry("bronze_chestplate", "hepatizon_chestplate"),
				Map.entry("bronze_dolabra", "hepatizon_dolabra"),
				Map.entry("bronze_helmet", "hepatizon_helmet"),
				Map.entry("bronze_hoe", "hepatizon_hoe"),
				Map.entry("bronze_laurel", "hepatizon_laurel"),
				Map.entry("bronze_leggings", "hepatizon_leggings"),
				Map.entry("bronze_mattock", "hepatizon_mattock"),
				Map.entry("bronze_pickaxe", "hepatizon_pickaxe"),
				Map.entry("bronze_shovel", "hepatizon_shovel"),
				Map.entry("bronze_spear", "hepatizon_spear"),
				Map.entry("bronze_sword", "hepatizon_sword"),
				Map.entry("bronze_shears", "shepherds_shears"),
				Map.entry("palatinate_sword", "shakudo_sword")
		).forEach((oldPath, newPath) -> {
			CompoundTag stack = new CompoundTag();
			stack.putString("id", "matcha:" + oldPath);
			CompoundTag components = new CompoundTag();
			CompoundTag itemName = new CompoundTag();
			itemName.putString("translate", "item.matcha." + oldPath);
			components.put("minecraft:item_name", itemName);
			stack.put("components", components);
			CompoundTag fixedStack = MatchaItemDataFixer.update(stack);
			assert ("matcha:" + newPath).equals(fixedStack.getStringOr("id", ""));
			assert ("item.matcha." + newPath).equals(itemName.getStringOr("translate", ""));
		});

		CompoundTag oldNamespaceEquipment = new CompoundTag();
		oldNamespaceEquipment.putString("id", "matcha-flavoured:bronze_sword");
		assert "matcha:hepatizon_sword".equals(MatchaItemDataFixer.update(oldNamespaceEquipment).getStringOr("id", ""));

		CompoundTag translatedStack = new CompoundTag();
		translatedStack.putString("id", "matcha:blessing_aeolus");
		CompoundTag translatedComponents = new CompoundTag();
		CompoundTag translatedItemName = new CompoundTag();
		translatedItemName.putString("translate", "item.kleispack.blessing");
		translatedComponents.put("minecraft:item_name", translatedItemName);
		ListTag translatedLore = new ListTag();
		CompoundTag blessingLore = new CompoundTag();
		blessingLore.putString("translate", "item.kleispack.blessing.aeolus");
		translatedLore.add(blessingLore);
		for (String key : new String[] {
				"adv.kleispack.fishing.rarity.4",
				"adv.kleispack.anglers_almanac",
				"adv.kleispack.anglers_almanac.desc",
				"desc.kleispack.repaired_with",
				"desc.kleispack.cleanses_maleffect",
				"item.kleispack.frog",
				"item.kleispack.tadpole",
				"matcha.lore.regen",
				"matcha.lore.blocking_colon",
				"matcha.lore.melee_blocking",
				"matcha.lore.projectile_blocking",
				"matcha.lore.throwable",
				"matcha.lore.warping",
				"matcha.asylum.cause_political",
				"matcha.amnestic.place_hint",
				"matcha.lore.full",
				"matcha.lore.gills"
		}) {
			CompoundTag legacyLore = new CompoundTag();
			legacyLore.putString("translate", key);
			translatedLore.add(legacyLore);
		}
		translatedComponents.put("minecraft:lore", translatedLore);
		CompoundTag translatedEnchantment = new CompoundTag();
		translatedEnchantment.putString("translate", "enchantment.kleispack.warding1");
		translatedComponents.put("minecraft:custom_name", translatedEnchantment);
		translatedStack.put("components", translatedComponents);
		CompoundTag fixedTranslations = MatchaItemDataFixer.update(translatedStack);
		CompoundTag fixedTranslationComponents = fixedTranslations.getCompound("components").orElseThrow();
		ListTag fixedLore = fixedTranslationComponents.getList("minecraft:lore").orElseThrow();
		assert "item.matcha.blessing_aeolus".equals(fixedTranslationComponents.getCompound("minecraft:item_name").orElseThrow().getStringOr("translate", ""));
		assert "item.matcha.blessing.aeolus".equals(fixedLore.getCompound(0).orElseThrow().getStringOr("translate", ""));
		assert "lore.matcha.fish_rarity.4".equals(fixedLore.getCompound(1).orElseThrow().getStringOr("translate", ""));
		assert "advancements.matcha.anglers_almanac.title".equals(fixedLore.getCompound(2).orElseThrow().getStringOr("translate", ""));
		assert "advancements.matcha.anglers_almanac.desc".equals(fixedLore.getCompound(3).orElseThrow().getStringOr("translate", ""));
		assert "tooltip.matcha.repaired_with".equals(fixedLore.getCompound(4).orElseThrow().getStringOr("translate", ""));
		assert "effect.matcha.cleanse_maleffect".equals(fixedLore.getCompound(5).orElseThrow().getStringOr("translate", ""));
		assert "item.matcha.frog".equals(fixedLore.getCompound(6).orElseThrow().getStringOr("translate", ""));
		assert "item.matcha.tadpole".equals(fixedLore.getCompound(7).orElseThrow().getStringOr("translate", ""));
		assert "effect.matcha.regen".equals(fixedLore.getCompound(8).orElseThrow().getStringOr("translate", ""));
		assert "desc.matcha.blocking_colon".equals(fixedLore.getCompound(9).orElseThrow().getStringOr("translate", ""));
		assert "desc.matcha.melee_blocking".equals(fixedLore.getCompound(10).orElseThrow().getStringOr("translate", ""));
		assert "desc.matcha.projectile_blocking".equals(fixedLore.getCompound(11).orElseThrow().getStringOr("translate", ""));
		assert "desc.matcha.throwable".equals(fixedLore.getCompound(12).orElseThrow().getStringOr("translate", ""));
		assert "effect.matcha.warping".equals(fixedLore.getCompound(13).orElseThrow().getStringOr("translate", ""));
		assert "item.matcha.asylum_seeker.cause.1".equals(fixedLore.getCompound(14).orElseThrow().getStringOr("translate", ""));
		assert "item.matcha.amnestic.place_hint".equals(fixedLore.getCompound(15).orElseThrow().getStringOr("translate", ""));
		assert "desc.matcha.full".equals(fixedLore.getCompound(16).orElseThrow().getStringOr("translate", ""));
		assert "effect.matcha.water_breathing".equals(fixedLore.getCompound(17).orElseThrow().getStringOr("translate", ""));
		assert "enchantment.matcha.warding_2".equals(fixedTranslationComponents.getCompound("minecraft:custom_name").orElseThrow().getStringOr("translate", ""));

		CompoundTag customTranslationData = new CompoundTag();
		customTranslationData.putString("translate", "item.kleispack.blessing.aeolus");
		CompoundTag customTranslationStack = new CompoundTag();
		customTranslationStack.putString("id", "matcha:blessing_aeolus");
		CompoundTag customTranslationComponents = new CompoundTag();
		customTranslationComponents.put("minecraft:custom_data", customTranslationData);
		customTranslationStack.put("components", customTranslationComponents);
		CompoundTag fixedCustomTranslation = MatchaItemDataFixer.update(customTranslationStack);
		assert "item.kleispack.blessing.aeolus".equals(fixedCustomTranslation.getCompound("components").orElseThrow()
				.getCompound("minecraft:custom_data").orElseThrow().getStringOr("translate", ""));

		CompoundTag elytra = new CompoundTag();
		elytra.putString("id", "matcha:bronze_elytra");
		assert "matcha:bronze_elytra".equals(MatchaItemDataFixer.update(elytra).getStringOr("id", ""));

		CompoundTag customData = new CompoundTag();
		customData.putString("id", "matcha-flavoured:bronze_sword");
		CompoundTag stackWithCustomData = new CompoundTag();
		stackWithCustomData.putString("id", "matcha-flavoured:amber");
		CompoundTag components = new CompoundTag();
		components.put("minecraft:custom_data", customData);
		CompoundTag enchantments = new CompoundTag();
		enchantments.putInt("matcha-flavoured:warding1", 1);
		components.put("minecraft:enchantments", enchantments);
		CompoundTag storedEnchantments = new CompoundTag();
		storedEnchantments.putInt("matcha-flavoured:anemos", 1);
		components.put("minecraft:stored_enchantments", storedEnchantments);
		stackWithCustomData.put("components", components);
		CompoundTag fixed = MatchaItemDataFixer.update(stackWithCustomData);
		assert "matcha:amber".equals(fixed.getStringOr("id", ""));
		assert "matcha-flavoured:bronze_sword".equals(customData.getStringOr("id", ""));
		assert enchantments.contains("matcha:warding_2") && !enchantments.contains("matcha-flavoured:warding1");
		assert storedEnchantments.contains("matcha:anemos") && !storedEnchantments.contains("matcha-flavoured:anemos");

		CompoundTag oneTime = new CompoundTag();
		oneTime.putString("id", "matcha-flavoured:amber");
		assert "matcha:amber".equals(MatchaItemDataFixer.updateIfNeeded(oneTime).getStringOr("id", ""));
		assert oneTime.getIntOr(MatchaItemDataFixer.DATA_VERSION_KEY, 0) == 4;
		oneTime.putString("id", "matcha-flavoured:amber");
		assert "matcha-flavoured:amber".equals(MatchaItemDataFixer.updateIfNeeded(oneTime).getStringOr("id", ""));

		CompoundTag v2 = new CompoundTag();
		v2.putString("id", "matcha:bronze_sword");
		v2.putInt(MatchaItemDataFixer.DATA_VERSION_KEY, 2);
		assert "matcha:hepatizon_sword".equals(MatchaItemDataFixer.updateIfNeeded(v2).getStringOr("id", ""));
		assert v2.getIntOr(MatchaItemDataFixer.DATA_VERSION_KEY, 0) == 4;

		CompoundTag v3Warding = new CompoundTag();
		v3Warding.putString("id", "matcha:warding_sword");
		v3Warding.putInt(MatchaItemDataFixer.DATA_VERSION_KEY, 3);
		CompoundTag v3Components = new CompoundTag();
		CompoundTag v3Enchantments = new CompoundTag();
		v3Enchantments.putInt("matcha:warding0", 1);
		v3Enchantments.putInt("matcha:warding1", 1);
		v3Enchantments.putInt("matcha:warding2", 1);
		v3Enchantments.putInt("matcha:warding3", 1);
		v3Enchantments.putInt("matcha:warding_armour", 1);
		v3Components.put("minecraft:enchantments", v3Enchantments);
		CompoundTag v3StoredEnchantments = new CompoundTag();
		v3StoredEnchantments.putInt("matcha:warding1", 1);
		v3Components.put("minecraft:stored_enchantments", v3StoredEnchantments);
		CompoundTag v3Name = new CompoundTag();
		v3Name.putString("translate", "enchantment.matcha.warding3");
		v3Components.put("minecraft:custom_name", v3Name);
		v3Warding.put("components", v3Components);
		CompoundTag fixedV3Warding = MatchaItemDataFixer.updateIfNeeded(v3Warding);
		assert fixedV3Warding.getIntOr(MatchaItemDataFixer.DATA_VERSION_KEY, 0) == 4;
		assert v3Enchantments.contains("matcha:warding_1") && !v3Enchantments.contains("matcha:warding0");
		assert v3Enchantments.contains("matcha:warding_2") && !v3Enchantments.contains("matcha:warding1");
		assert v3Enchantments.contains("matcha:warding_3") && !v3Enchantments.contains("matcha:warding2");
		assert v3Enchantments.contains("matcha:warding_4") && !v3Enchantments.contains("matcha:warding3");
		assert v3Enchantments.contains("matcha:electrum_armour") && !v3Enchantments.contains("matcha:warding_armour");
		assert v3StoredEnchantments.contains("matcha:warding_2") && !v3StoredEnchantments.contains("matcha:warding1");
		assert "enchantment.matcha.warding_4".equals(v3Name.getStringOr("translate", ""));

		CompoundTag electrumTool = stackWithEnchantments("matcha:electrum_pickaxe", "matcha:warding2");
		electrumTool.putInt(MatchaItemDataFixer.DATA_VERSION_KEY, 3);
		CompoundTag toolEnchantments = electrumTool.getCompound("components").orElseThrow()
				.getCompound("minecraft:enchantments").orElseThrow();
		toolEnchantments.putInt("minecraft:fortune", 3);
		MatchaItemDataFixer.updateIfNeeded(electrumTool);
		assert !toolEnchantments.contains("matcha:warding_3");
		assert toolEnchantments.getIntOr("minecraft:fortune", 0) == 3;

		CompoundTag wardingShield = stackWithEnchantments("matcha:warding_shield", "matcha:warding1");
		wardingShield.putInt(MatchaItemDataFixer.DATA_VERSION_KEY, 3);
		CompoundTag shieldEnchantments = wardingShield.getCompound("components").orElseThrow()
				.getCompound("minecraft:enchantments").orElseThrow();
		MatchaItemDataFixer.updateIfNeeded(wardingShield);
		assert shieldEnchantments.contains("matcha:warding_1");
		assert !shieldEnchantments.contains("matcha:warding_2");

		CompoundTag adamantHelmet = namedAdamantStack("minecraft:netherite_helmet");
		CompoundTag adamantHelmetEnchantments = adamantHelmet.getCompound("components").orElseThrow()
				.getCompound("minecraft:enchantments").orElseThrow();
		adamantHelmetEnchantments.putInt("matcha:divinity", 1);
		MatchaItemDataFixer.updateIfNeeded(adamantHelmet);
		assert adamantHelmetEnchantments.contains("matcha:adamant_armour");
		assert !adamantHelmetEnchantments.contains("matcha:divinity");

		CompoundTag adamantAxe = namedAdamantStack("minecraft:netherite_axe");
		CompoundTag adamantAxeEnchantments = adamantAxe.getCompound("components").orElseThrow()
				.getCompound("minecraft:enchantments").orElseThrow();
		MatchaItemDataFixer.updateIfNeeded(adamantAxe);
		assert adamantAxeEnchantments.contains("matcha:adamant_tool");
		assert adamantAxeEnchantments.contains("matcha:adamant_weapon");

		CompoundTag adamantPickaxe = namedAdamantStack("minecraft:netherite_pickaxe");
		CompoundTag adamantPickaxeEnchantments = adamantPickaxe.getCompound("components").orElseThrow()
				.getCompound("minecraft:enchantments").orElseThrow();
		adamantPickaxeEnchantments.putInt("matcha:divinity", 1);
		adamantPickaxeEnchantments.putInt("matcha-flavoured:divinity", 1);
		MatchaItemDataFixer.updateIfNeeded(adamantPickaxe);
		assert adamantPickaxeEnchantments.contains("matcha:adamant_tool");
		assert !adamantPickaxeEnchantments.contains("matcha:divinity");
		assert !adamantPickaxeEnchantments.contains("matcha-flavoured:divinity");

		CompoundTag adamantSword = namedAdamantStack("minecraft:netherite_sword");
		CompoundTag adamantSwordEnchantments = adamantSword.getCompound("components").orElseThrow()
				.getCompound("minecraft:enchantments").orElseThrow();
		MatchaItemDataFixer.updateIfNeeded(adamantSword);
		assert adamantSwordEnchantments.contains("matcha:adamant_weapon");

		CompoundTag ordinaryEnchantedItem = stackWithEnchantments("minecraft:diamond_pickaxe", "minecraft:unbreaking");
		ordinaryEnchantedItem.putInt(MatchaItemDataFixer.DATA_VERSION_KEY, 3);
		MatchaItemDataFixer.updateIfNeeded(ordinaryEnchantedItem);
		assert "minecraft:diamond_pickaxe".equals(ordinaryEnchantedItem.getStringOr("id", ""));

		CompoundTag bareAdamantClaymore = new CompoundTag();
		bareAdamantClaymore.putString("id", "matcha:adamant_claymore");
		CompoundTag fixedBareAdamantClaymore = MatchaItemDataFixer.updateIfNeeded(bareAdamantClaymore);
		assert fixedBareAdamantClaymore.getCompound("components").orElseThrow()
				.getCompound("minecraft:enchantments").orElseThrow().contains("matcha:adamant_weapon");

		CompoundTag dolabra = stackWithEnchantments("matcha:adamant_dolabra", "matcha:divinity");
		dolabra.putInt(MatchaItemDataFixer.DATA_VERSION_KEY, 3);
		CompoundTag dolabraComponents = dolabra.getCompound("components").orElseThrow();
		ListTag modifiers = new ListTag();
		CompoundTag attackDamage = new CompoundTag();
		attackDamage.putString("id", "attack_damage");
		attackDamage.putDouble("amount", 9.0);
		modifiers.add(attackDamage);
		dolabraComponents.put("minecraft:attribute_modifiers", modifiers);
		ListTag lore = new ListTag();
		CompoundTag attackLore = new CompoundTag();
		attackLore.putString("text", "🗡 10");
		lore.add(attackLore);
		dolabraComponents.put("minecraft:lore", lore);
		MatchaItemDataFixer.updateIfNeeded(dolabra);
		assert dolabra.getIntOr(MatchaItemDataFixer.DATA_VERSION_KEY, 0) == 4;
		CompoundTag migratedEnchantments = dolabraComponents.getCompound("minecraft:enchantments").orElseThrow();
		assert !migratedEnchantments.contains("matcha:divinity");
		assert !migratedEnchantments.contains("components");
		assert modifiers.getCompound(0).orElseThrow().getDoubleOr("amount", 0.0) == 6.0;
		assert "🗡 7".equals(lore.getCompound(0).orElseThrow().getStringOr("text", ""));
	}

	private static CompoundTag stackWithEnchantments(String id, String enchantment) {
		CompoundTag stack = new CompoundTag();
		stack.putString("id", id);
		CompoundTag components = new CompoundTag();
		CompoundTag enchantments = new CompoundTag();
		enchantments.putInt(enchantment, 1);
		components.put("minecraft:enchantments", enchantments);
		stack.put("components", components);
		return stack;
	}

	private static CompoundTag namedAdamantStack(String id) {
		CompoundTag stack = stackWithEnchantments(id, "minecraft:unbreaking");
		stack.putInt(MatchaItemDataFixer.DATA_VERSION_KEY, 3);
		CompoundTag itemName = new CompoundTag();
		itemName.putString("translate", "item." + id.replace(':', '.'));
		stack.getCompound("components").orElseThrow().put("minecraft:item_name", itemName);
		return stack;
	}
}
