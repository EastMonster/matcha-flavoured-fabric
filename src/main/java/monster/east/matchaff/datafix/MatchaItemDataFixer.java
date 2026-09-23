package monster.east.matchaff.datafix;

import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.DataFixerBuilder;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import monster.east.matchaff.datafix.fix.V1Migration;
import monster.east.matchaff.datafix.fix.V2Migration;
import monster.east.matchaff.datafix.fix.MatchaDataFixSupport;
import monster.east.matchaff.datafix.fix.V3Migration;
import monster.east.matchaff.datafix.fix.V4Migration;
import monster.east.matchaff.datafix.fix.V5Migration;
import monster.east.matchaff.datafix.schema.V1;
import monster.east.matchaff.datafix.schema.V2;
import monster.east.matchaff.datafix.schema.V3;
import monster.east.matchaff.datafix.schema.V4;
import monster.east.matchaff.datafix.schema.V5;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;


/** Migrates Matcha ItemStacks and banner patterns before their codecs resolve registry IDs. */
public final class MatchaItemDataFixer {
	public static final String DATA_VERSION_KEY = "matcha_data_version";
	private static final int CURRENT_DATA_VERSION = 5;
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
		return FIXER.update(MatchaDataFixSupport.ROOT, data, 0, CURRENT_DATA_VERSION);
	}

	private static DataFixer createFixer() {
		DataFixerBuilder builder = new DataFixerBuilder(CURRENT_DATA_VERSION);
		builder.addSchema(0, V1::new);
		Schema v1 = builder.addSchema(1, V1::new);
		builder.addFixer(new V1Migration(v1));
		Schema v2 = builder.addSchema(2, V2::new);
		builder.addFixer(new V2Migration(v2));
		Schema v3 = builder.addSchema(3, V3::new);
		builder.addFixer(new V3Migration(v3));
		Schema v4 = builder.addSchema(4, V4::new);
		builder.addFixer(new V4Migration(v4));
		Schema v5 = builder.addSchema(5, V5::new);
		builder.addFixer(new V5Migration(v5));
		return builder.build().fixer();
	}
}
