package monster.east.matchaff.datafix;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Run with assertions enabled to check that upgrades start at the saved version. */
public final class MatchaVersionedMigrationCheck {
	private MatchaVersionedMigrationCheck() {
	}

	public static void main(String[] args) {
		CompoundTag current = MatchaItemDataFixer.updateIfNeeded(playerData(5));
		assert itemId(current).equals("minecraft:resin_brick");
		assert current.getIntOr(MatchaItemDataFixer.DATA_VERSION_KEY, 0) > 5;

		CompoundTag legacy = MatchaItemDataFixer.updateIfNeeded(playerData(0));
		assert itemId(legacy).equals("matcha:steel");

		for (String song : new String[] {"golden", "dry_hands", "labyrinthine"}) {
			CompoundTag data = playerData(6);
			CompoundTag components = new CompoundTag();
			components.putString("minecraft:jukebox_playable", "main:" + song);
			CompoundTag stack = data.getList("Inventory").orElseThrow().getCompound(0).orElseThrow();
			stack.put("components", components);
			CompoundTag custom = stack.copy();
			components.put("minecraft:custom_data", custom);
			CompoundTag migrated = MatchaItemDataFixer.updateIfNeeded(data);
			CompoundTag fixedComponents = migrated.getList("Inventory").orElseThrow()
					.getCompound(0).orElseThrow().getCompound("components").orElseThrow();
			assert fixedComponents.getStringOr("minecraft:jukebox_playable", "").equals("matcha:" + song);
			assert fixedComponents.getCompound("minecraft:custom_data").orElseThrow().equals(custom);
			assert itemId(migrated).equals("minecraft:resin_brick");
			assert migrated.getIntOr(MatchaItemDataFixer.DATA_VERSION_KEY, 0) == 8;
			assert MatchaItemDataFixer.updateIfNeeded(migrated).equals(migrated);
		}
		CompoundTag unrelated = playerData(6);
		CompoundTag components = new CompoundTag();
		components.putString("minecraft:jukebox_playable", "main:other_song");
		unrelated.getList("Inventory").orElseThrow().getCompound(0).orElseThrow().put("components", components);
		CompoundTag fixed = MatchaItemDataFixer.updateIfNeeded(unrelated);
		assert fixed.getList("Inventory").orElseThrow().getCompound(0).orElseThrow()
				.getCompound("components").orElseThrow().getStringOr("minecraft:jukebox_playable", "")
				.equals("main:other_song");
		checkIntrinsicConflicts();
		checkCookies();
		checkVolatilePotions();
		checkPith();
	}

	private static void checkPith() {
		for (int version : new int[] {0, 7}) {
			for (String id : new String[] {"matcha:divine_fragment", "matcha-flavoured:divine_fragment"}) {
				CompoundTag data = playerData(version);
				CompoundTag stack = data.getList("Inventory").orElseThrow().getCompound(0).orElseThrow();
				stack.putString("id", id);
				stack.putInt("count", 9);
				CompoundTag components = new CompoundTag();
				CompoundTag name = new CompoundTag();
				name.putString("translate", "item.matcha.divine_fragment");
				components.put("minecraft:item_name", name);
				components.putString("minecraft:custom_name", "Keep my name");
				stack.put("components", components);
				CompoundTag custom = stack.copy();
				components.put("minecraft:custom_data", custom);
				ListTag nested = new ListTag();
				nested.add(stack.copy());
				components.put("minecraft:bundle_contents", nested);
				CompoundTag fixed = MatchaItemDataFixer.updateIfNeeded(data);
				CompoundTag actual = fixed.getList("Inventory").orElseThrow().getCompound(0).orElseThrow();
				CompoundTag actualComponents = actual.getCompound("components").orElseThrow();
				assert itemId(fixed).equals("matcha:pith");
				assert actual.getIntOr("count", 0) == 9;
				assert actualComponents.getCompound("minecraft:item_name").orElseThrow()
						.getStringOr("translate", "").equals("item.matcha.pith");
				assert actualComponents.getStringOr("minecraft:custom_name", "").equals("Keep my name");
				assert actualComponents.getCompound("minecraft:custom_data").orElseThrow().equals(custom);
				assert actualComponents.getList("minecraft:bundle_contents").orElseThrow().getCompound(0).orElseThrow()
						.getStringOr("id", "").equals("matcha:pith");
				CompoundTag expected = fixed.copy();
				assert MatchaItemDataFixer.updateIfNeeded(fixed).equals(expected);
			}
		}
		for (int version : new int[] {0, 7}) {
			CompoundTag data = playerData(version);
			data.getList("Inventory").orElseThrow().getCompound(0).orElseThrow()
					.putString("id", "minecraft:turtle_scute");
			assert itemId(MatchaItemDataFixer.updateIfNeeded(data))
					.equals(version == 0 ? "matcha:pith" : "minecraft:turtle_scute");
		}
	}

	private static void checkVolatilePotions() {
		String[] variants = {"infested", "invisibility", "oozing", "pitch", "poison", "slowness",
				"weakness", "weaving", "wind_charged", "wither"};
		int[] durations = {6000, 12000, 6000, 1200, 2400, 6000, 6000, 6000, 6000, 2400};
		for (int index = 0; index < variants.length; index++) {
			for (String namespace : new String[] {"matcha:", "matcha-flavoured:"}) {
				CompoundTag data = playerData(7);
				CompoundTag stack = data.getList("Inventory").orElseThrow().getCompound(0).orElseThrow();
				stack.putString("id", namespace + "volatile_" + variants[index]);
				stack.putInt("count", 16);
				CompoundTag original = stack.copy();
				CompoundTag components = new CompoundTag();
				components.put("minecraft:custom_data", original);
				ListTag nested = new ListTag();
				nested.add(original.copy());
				components.put("minecraft:bundle_contents", nested);
				stack.put("components", components);
				CompoundTag fixed = MatchaItemDataFixer.updateIfNeeded(data);
				CompoundTag actual = fixed.getList("Inventory").orElseThrow().getCompound(0).orElseThrow();
				CompoundTag restored = actual.getCompound("components").orElseThrow();
				assert itemId(fixed).equals("minecraft:splash_potion");
				assert actual.getIntOr("count", 0) == 16;
				assert restored.getIntOr("minecraft:max_stack_size", 0) == 64;
				ListTag effects = restored.getCompound("minecraft:potion_contents").orElseThrow()
						.getList("custom_effects").orElseThrow();
				String effect = variants[index].equals("pitch") ? "darkness" : variants[index];
				assert effects.getCompound(0).orElseThrow().getStringOr("id", "").equals("minecraft:" + effect);
				assert effects.getCompound(0).orElseThrow().getIntOr("duration", 0) == durations[index];
				if (variants[index].equals("pitch")) {
					assert effects.size() == 2;
					assert effects.getCompound(1).orElseThrow().getIntOr("duration", 0) == 600;
				}
				assert restored.getCompound("minecraft:custom_data").orElseThrow().equals(original);
				assert restored.getList("minecraft:bundle_contents").orElseThrow().getCompound(0).orElseThrow()
						.getStringOr("id", "").equals("minecraft:splash_potion");
				CompoundTag expected = fixed.copy();
				assert MatchaItemDataFixer.updateIfNeeded(fixed).equals(expected);
			}
		}
		for (String nameKey : new String[] {"item.matcha.volatile_poison", "My potion"}) {
			CompoundTag data = playerData(7);
			CompoundTag stack = data.getList("Inventory").orElseThrow().getCompound(0).orElseThrow();
			stack.putString("id", "matcha:volatile_poison");
			CompoundTag components = new CompoundTag();
			CompoundTag name = new CompoundTag();
			name.putString("translate", nameKey);
			components.put("minecraft:custom_name", name);
			components.putInt("minecraft:max_stack_size", 32);
			components.put("minecraft:potion_contents", new CompoundTag());
			components.put("!minecraft:lore", new CompoundTag());
			stack.put("components", components);
			CompoundTag fixed = MatchaItemDataFixer.updateIfNeeded(data);
			CompoundTag actual = fixed.getList("Inventory").orElseThrow().getCompound(0).orElseThrow()
					.getCompound("components").orElseThrow();
			assert actual.getIntOr("minecraft:max_stack_size", 0) == 32;
			assert actual.getCompound("minecraft:potion_contents").orElseThrow().isEmpty();
			assert !actual.contains("minecraft:lore") && actual.contains("!minecraft:lore");
			assert actual.getCompound("minecraft:custom_name").orElseThrow().getStringOr("translate", "")
					.equals(nameKey.startsWith("item.matcha.") ? "item.minecraft.splash_potion.effect.poison" : nameKey);
		}
		for (String id : new String[] {"minecraft:splash_potion", "matcha:volatile_unknown", "matcha:cheese"}) {
			CompoundTag data = playerData(7);
			data.getList("Inventory").orElseThrow().getCompound(0).orElseThrow().putString("id", id);
			assert itemId(MatchaItemDataFixer.updateIfNeeded(data)).equals(id);
		}
	}

	private static void checkCookies() {
		for (int version : new int[] {0, 7}) {
			for (String id : new String[] {"matcha:chocolate_chip_cookie", "matcha-flavoured:chocolate_chip_cookie"}) {
				CompoundTag data = playerData(version);
				CompoundTag stack = data.getList("Inventory").orElseThrow().getCompound(0).orElseThrow();
				stack.putString("id", id);
				stack.putInt("count", 12);
				CompoundTag components = new CompoundTag();
				components.putString("minecraft:custom_name", "Keep my cookie");
				CompoundTag custom = stack.copy();
				components.put("minecraft:custom_data", custom);
				ListTag nested = new ListTag();
				nested.add(stack.copy());
				components.put("minecraft:bundle_contents", nested);
				stack.put("components", components);
				CompoundTag fixed = MatchaItemDataFixer.updateIfNeeded(data);
				CompoundTag actual = fixed.getList("Inventory").orElseThrow().getCompound(0).orElseThrow();
				CompoundTag actualComponents = actual.getCompound("components").orElseThrow();
				assert itemId(fixed).equals("minecraft:cookie");
				assert actual.getIntOr("count", 0) == 12;
				assert actualComponents.getStringOr("minecraft:custom_name", "").equals("Keep my cookie");
				assert actualComponents.getCompound("minecraft:custom_data").orElseThrow().equals(custom);
				assert actualComponents.getList("minecraft:bundle_contents").orElseThrow()
						.getCompound(0).orElseThrow().getStringOr("id", "").equals("minecraft:cookie");
				assert fixed.getIntOr(MatchaItemDataFixer.DATA_VERSION_KEY, 0) == 8;
				CompoundTag expected = fixed.copy();
				assert MatchaItemDataFixer.updateIfNeeded(fixed).equals(expected);
			}
		}
		for (String id : new String[] {"minecraft:cookie", "matcha:cheese", "minecraft:resin_brick"}) {
			CompoundTag data = playerData(7);
			data.getList("Inventory").orElseThrow().getCompound(0).orElseThrow().putString("id", id);
			assert itemId(MatchaItemDataFixer.updateIfNeeded(data)).equals(id);
		}
		CompoundTag current = playerData(8);
		current.getList("Inventory").orElseThrow().getCompound(0).orElseThrow()
				.putString("id", "matcha:chocolate_chip_cookie");
		CompoundTag expected = current.copy();
		assert MatchaItemDataFixer.updateIfNeeded(current).equals(expected);
	}

	private static void checkIntrinsicConflicts() {
		for (String id : new String[] {
				"matcha:electrum_axe", "matcha:electrum_dolabra", "matcha:electrum_hoe",
				"matcha:electrum_mattock", "matcha:electrum_pickaxe", "matcha:electrum_shovel",
				"matcha:electrum_spear", "matcha:electrum_sword", "matcha:silver_sword",
				"matcha:shakudo_axe", "matcha:shakudo_dolabra", "matcha:shakudo_hoe",
				"matcha:shakudo_mattock", "matcha:shakudo_pickaxe", "matcha:shakudo_shovel",
				"minecraft:diamond_pickaxe", "minecraft:diamond_sword", "matcha:electrum_helmet"
		}) {
			CompoundTag data = playerData(6);
			CompoundTag stack = data.getList("Inventory").orElseThrow().getCompound(0).orElseThrow();
			stack.putString("id", id);
			CompoundTag components = new CompoundTag();
			CompoundTag enchantments = new CompoundTag();
			for (String enchantment : new String[] {"silk_touch", "sharpness", "fortune", "smite", "unbreaking"}) {
				enchantments.putInt("minecraft:" + enchantment, 3);
			}
			enchantments.putInt("matcha:electrum_tool", 3);
			components.put("minecraft:enchantments", enchantments);
			components.putInt("minecraft:damage", 123);
			components.putString("minecraft:custom_name", "Keep this name");
			stack.put("components", components);
			// Real nested stacks are migrated, opaque custom data must be preserved.
			CompoundTag custom = stack.copy();
			components.put("minecraft:custom_data", custom);
			ListTag nested = new ListTag();
			nested.add(stack.copy());
			components.put("minecraft:bundle_contents", nested);
			CompoundTag expected = components.copy();
			CompoundTag expectedEnchantments = expected.getCompound("minecraft:enchantments").orElseThrow();
			boolean electrumTool = id.startsWith("matcha:electrum_")
					&& !id.endsWith("spear") && !id.endsWith("sword") && !id.endsWith("helmet");
			boolean smiteWeapon = id.equals("matcha:electrum_axe") || id.equals("matcha:electrum_spear")
					|| id.equals("matcha:electrum_sword") || id.equals("matcha:silver_sword");
			if (electrumTool) expectedEnchantments.remove("minecraft:silk_touch");
			if (electrumTool) expectedEnchantments.remove("minecraft:fortune");
			if (smiteWeapon) expectedEnchantments.remove("minecraft:sharpness");
			if (id.startsWith("matcha:shakudo_")) expectedEnchantments.remove("minecraft:fortune");
			CompoundTag current = data.copy();
			current.putInt(MatchaItemDataFixer.DATA_VERSION_KEY, 7);
			CompoundTag currentExpected = current.copy();
			currentExpected.putInt(MatchaItemDataFixer.DATA_VERSION_KEY, 8);
			CompoundTag fixed = MatchaItemDataFixer.updateIfNeeded(data);
			CompoundTag actual = fixed.getList("Inventory").orElseThrow().getCompound(0).orElseThrow()
					.getCompound("components").orElseThrow();
			assert actual.getCompound("minecraft:enchantments").orElseThrow().equals(expectedEnchantments) : id;
			assert actual.getIntOr("minecraft:damage", 0) == 123;
			assert actual.getStringOr("minecraft:custom_name", "").equals("Keep this name");
			assert actual.getCompound("minecraft:custom_data").orElseThrow().equals(custom);
			assert actual.getList("minecraft:bundle_contents").orElseThrow().getCompound(0).orElseThrow()
					.getCompound("components").orElseThrow().getCompound("minecraft:enchantments").orElseThrow()
					.equals(expectedEnchantments);
			CompoundTag fixedExpected = fixed.copy();
			assert MatchaItemDataFixer.updateIfNeeded(fixed).equals(fixedExpected);
			// A save already marked V7 must not silently rerun amended V7 rules.
			assert MatchaItemDataFixer.updateIfNeeded(current).equals(currentExpected);
		}
	}

	private static CompoundTag playerData(int version) {
		CompoundTag data = new CompoundTag();
		data.putInt(MatchaItemDataFixer.DATA_VERSION_KEY, version);
		CompoundTag item = new CompoundTag();
		item.putString("id", "minecraft:resin_brick");
		ListTag inventory = new ListTag();
		inventory.add(item);
		data.put("Inventory", inventory);
		return data;
	}

	private static String itemId(CompoundTag data) {
		return data.getList("Inventory").orElseThrow().getCompound(0).orElseThrow().getStringOr("id", "");
	}
}
