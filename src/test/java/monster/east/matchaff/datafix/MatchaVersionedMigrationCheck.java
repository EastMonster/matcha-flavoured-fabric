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
			assert migrated.getIntOr(MatchaItemDataFixer.DATA_VERSION_KEY, 0) == 7;
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
