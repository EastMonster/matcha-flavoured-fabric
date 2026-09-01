package monster.east.matchaff.datafix;

import net.minecraft.nbt.CompoundTag;

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

		CompoundTag nonItem = new CompoundTag();
		nonItem.putString("id", "matcha-flavoured:abbey_overgrown");
		assert "matcha-flavoured:abbey_overgrown".equals(MatchaItemDataFixer.update(nonItem).getStringOr("id", ""));

		CompoundTag customData = new CompoundTag();
		customData.putString("id", "matcha-flavoured:external_value");
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
		assert "matcha-flavoured:external_value".equals(customData.getStringOr("id", ""));
		assert enchantments.contains("matcha:warding1") && !enchantments.contains("matcha-flavoured:warding1");
		assert storedEnchantments.contains("matcha:anemos") && !storedEnchantments.contains("matcha-flavoured:anemos");

		CompoundTag oneTime = new CompoundTag();
		oneTime.putString("id", "matcha-flavoured:amber");
		assert "matcha:amber".equals(MatchaItemDataFixer.updateIfNeeded(oneTime).getStringOr("id", ""));
		assert oneTime.getIntOr(MatchaItemDataFixer.DATA_VERSION_KEY, 0) == 1;
		oneTime.putString("id", "matcha-flavoured:amber");
		assert "matcha-flavoured:amber".equals(MatchaItemDataFixer.updateIfNeeded(oneTime).getStringOr("id", ""));
	}
}
