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

		// Old-build Nazar used the pre-migration enchantment namespace.
		CompoundTag nazarLegacy = new CompoundTag();
		nazarLegacy.putString("id", "minecraft:glistering_melon_slice");
		CompoundTag nazarLegacyComponents = new CompoundTag();
		CompoundTag nazarLegacyEnchantments = new CompoundTag();
		nazarLegacyEnchantments.putInt("matcha-flavoured:warding1", 1);
		nazarLegacyComponents.put("minecraft:enchantments", nazarLegacyEnchantments);
		nazarLegacy.put("components", nazarLegacyComponents);
		assert "matcha:nazar".equals(MatchaItemDataFixer.update(nazarLegacy).getStringOr("id", ""));
		assert nazarLegacyEnchantments.contains("matcha:warding1") && !nazarLegacyEnchantments.contains("matcha-flavoured:warding1");

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
		assert oneTime.getIntOr(MatchaItemDataFixer.DATA_VERSION_KEY, 0) == 2;
		oneTime.putString("id", "matcha-flavoured:amber");
		assert "matcha-flavoured:amber".equals(MatchaItemDataFixer.updateIfNeeded(oneTime).getStringOr("id", ""));
	}
}
