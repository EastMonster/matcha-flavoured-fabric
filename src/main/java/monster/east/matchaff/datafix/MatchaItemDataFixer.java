package monster.east.matchaff.datafix;

import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.DataFixerBuilder;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import monster.east.matchaff.datafix.fix.ItemNamespaceFix;
import monster.east.matchaff.datafix.fix.ItemRenameFix;
import monster.east.matchaff.datafix.fix.NazarItemFix;
import monster.east.matchaff.datafix.fix.References;
import monster.east.matchaff.datafix.fix.WardingEnchantmentFix;
import monster.east.matchaff.datafix.schema.V1;
import monster.east.matchaff.datafix.schema.V2;
import monster.east.matchaff.datafix.schema.V3;
import monster.east.matchaff.datafix.schema.V4;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import java.util.List;
import java.util.Map;

/** Migrates Matcha ItemStacks and banner patterns before their codecs resolve registry IDs. */
public final class MatchaItemDataFixer {
	public static final String DATA_VERSION_KEY = "matcha_data_version";
	private static final int CURRENT_DATA_VERSION = 4;
	private static final Map<String, Integer> LEGACY_ELECTRUM_FORTUNE_LEVELS = Map.of(
			"matcha:electrum_axe", 2,
			"matcha:electrum_dolabra", 2,
			"matcha:electrum_hoe", 3,
			"matcha:electrum_mattock", 2,
			"matcha:electrum_pickaxe", 3,
			"matcha:electrum_shovel", 3
	);
	private static final DataFixer FIXER = createFixer();

	private MatchaItemDataFixer() {
	}

	public static CompoundTag update(CompoundTag tag) {
		return (CompoundTag) update(new Dynamic<>(NbtOps.INSTANCE, tag)).getValue();
	}

	public static CompoundTag updateIfNeeded(CompoundTag tag) {
		if (tag.getIntOr(DATA_VERSION_KEY, 0) >= CURRENT_DATA_VERSION) {
			return migrateCurrentElectrumTools(tag);
		}
		CompoundTag updated = update(tag);
		markCurrent(updated);
		return migrateCurrentElectrumTools(updated);
	}

	public static <T> Dynamic<T> updateIfNeeded(Dynamic<T> data) {
		if (data.getValue() instanceof CompoundTag tag) {
			@SuppressWarnings("unchecked")
			Dynamic<T> updated = (Dynamic<T>) new Dynamic<>(NbtOps.INSTANCE, updateIfNeeded(tag));
			return updated;
		}
		return data.get(DATA_VERSION_KEY).asInt(0) >= CURRENT_DATA_VERSION
			? data
			: update(data).set(DATA_VERSION_KEY, data.createInt(CURRENT_DATA_VERSION));
	}

	public static void markCurrent(CompoundTag tag) {
		if (tag != null) tag.putInt(DATA_VERSION_KEY, CURRENT_DATA_VERSION);
	}

	public static <T> Dynamic<T> update(Dynamic<T> data) {
		return FIXER.update(References.ROOT, data, 0, CURRENT_DATA_VERSION);
	}

	// Compatibility normalization for V4 saves; this intentionally does not create a V5 step.
	private static CompoundTag migrateCurrentElectrumTools(CompoundTag tag) {
		migrateCurrentElectrumTools(tag, null, false);
		return tag;
	}

	private static void migrateCurrentElectrumTools(Tag tag, String itemId, boolean customData) {
		if (tag instanceof CompoundTag compound) {
			String currentItemId = itemId;
			if (!customData && compound.contains("id")) {
				String id = compound.getStringOr("id", "");
				currentItemId = LEGACY_ELECTRUM_FORTUNE_LEVELS.containsKey(id) ? id : null;
			}
			if (!customData && currentItemId != null) {
				CompoundTag components = compound.get("components") instanceof CompoundTag existing
						? existing : compound;
				if (components.get("minecraft:enchantments") instanceof CompoundTag enchantments) {
					int intrinsicLevel = LEGACY_ELECTRUM_FORTUNE_LEVELS.get(currentItemId);
					int fortuneLevel = enchantments.getIntOr("minecraft:fortune", 0);
					if (fortuneLevel == intrinsicLevel) enchantments.remove("minecraft:fortune");
					if (enchantments.getIntOr("matcha:electrum_tool", 0) < intrinsicLevel) {
						enchantments.putInt("matcha:electrum_tool", intrinsicLevel);
					}
				}
			}
			for (String key : List.copyOf(compound.keySet())) {
				Tag child = compound.get(key);
				if (child != null) {
					migrateCurrentElectrumTools(child, currentItemId,
							customData || key.equals("minecraft:custom_data"));
				}
			}
			return;
		}
		if (tag instanceof ListTag list) {
			for (int index = 0; index < list.size(); index++) {
				migrateCurrentElectrumTools(list.get(index), itemId, customData);
			}
		}
	}

	private static DataFixer createFixer() {
		DataFixerBuilder builder = new DataFixerBuilder(CURRENT_DATA_VERSION);
		builder.addSchema(0, V1::new);
		Schema v1 = builder.addSchema(1, V1::new);
		builder.addFixer(new ItemNamespaceFix(v1));
		Schema v2 = builder.addSchema(2, V2::new);
		builder.addFixer(new NazarItemFix(v2));
		Schema v3 = builder.addSchema(3, V3::new);
		builder.addFixer(new ItemRenameFix(v3));
		Schema v4 = builder.addSchema(4, V4::new);
		builder.addFixer(new WardingEnchantmentFix(v4));
		return builder.build().fixer();
	}
}
