package monster.east.matchaff.datafix;

import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.DataFixerBuilder;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import monster.east.matchaff.datafix.fix.ItemNamespaceFix;
import monster.east.matchaff.datafix.fix.ItemRenameFix;
import monster.east.matchaff.datafix.fix.NazarItemFix;
import monster.east.matchaff.datafix.fix.References;
import monster.east.matchaff.datafix.schema.V1;
import monster.east.matchaff.datafix.schema.V2;
import monster.east.matchaff.datafix.schema.V3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;

/** Migrates Matcha ItemStacks before their component codecs resolve the item ID. */
public final class MatchaItemDataFixer {
	public static final String DATA_VERSION_KEY = "matcha_data_version";
	private static final int CURRENT_DATA_VERSION = 3;
	private static final DataFixer FIXER = createFixer();

	private MatchaItemDataFixer() {
	}

	public static CompoundTag update(CompoundTag tag) {
		return (CompoundTag) update(new Dynamic<>(NbtOps.INSTANCE, tag)).getValue();
	}

	public static CompoundTag updateIfNeeded(CompoundTag tag) {
		if (tag.getIntOr(DATA_VERSION_KEY, 0) >= CURRENT_DATA_VERSION) {
			return tag;
		}
		CompoundTag updated = update(tag);
		markCurrent(updated);
		return updated;
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

	private static DataFixer createFixer() {
		DataFixerBuilder builder = new DataFixerBuilder(CURRENT_DATA_VERSION);
		builder.addSchema(0, V1::new);
		Schema v1 = builder.addSchema(1, V1::new);
		builder.addFixer(new ItemNamespaceFix(v1));
		Schema v2 = builder.addSchema(2, V2::new);
		builder.addFixer(new NazarItemFix(v2));
		Schema v3 = builder.addSchema(3, V3::new);
		builder.addFixer(new ItemRenameFix(v3));
		return builder.build().fixer();
	}
}
