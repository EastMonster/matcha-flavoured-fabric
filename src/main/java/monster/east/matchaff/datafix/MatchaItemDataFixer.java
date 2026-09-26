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
import monster.east.matchaff.datafix.fix.V6Migration;
import monster.east.matchaff.datafix.schema.V1;
import monster.east.matchaff.datafix.schema.V2;
import monster.east.matchaff.datafix.schema.V3;
import monster.east.matchaff.datafix.schema.V4;
import monster.east.matchaff.datafix.schema.V5;
import monster.east.matchaff.datafix.schema.V6;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;


/** Migrates Matcha ItemStacks, block states, and banner patterns before their codecs resolve registry IDs. */
public final class MatchaItemDataFixer {
	public static final String DATA_VERSION_KEY = "matcha_data_version";
	private static final int CURRENT_DATA_VERSION = 6;
	private static final DataFixer FIXER = createFixer();

	private MatchaItemDataFixer() {
	}

	public static CompoundTag update(CompoundTag tag) {
		return (CompoundTag) update(new Dynamic<>(NbtOps.INSTANCE, tag)).getValue();
	}

	public static CompoundTag updateIfNeeded(CompoundTag tag) {
		int fromVersion = tag.getIntOr(DATA_VERSION_KEY, 0);
		if (fromVersion >= CURRENT_DATA_VERSION) {
			return tag;
		}
		CompoundTag updated = (CompoundTag) update(new Dynamic<>(NbtOps.INSTANCE, tag), fromVersion).getValue();
		markCurrent(updated);
		return updated;
	}

	public static <T> Dynamic<T> updateIfNeeded(Dynamic<T> data) {
		if (data.getValue() instanceof CompoundTag tag) {
			@SuppressWarnings("unchecked")
			Dynamic<T> updated = (Dynamic<T>) new Dynamic<>(NbtOps.INSTANCE, updateIfNeeded(tag));
			return updated;
		}
		int fromVersion = data.get(DATA_VERSION_KEY).asInt(0);
		return fromVersion >= CURRENT_DATA_VERSION
			? data
			: update(data, fromVersion).set(DATA_VERSION_KEY, data.createInt(CURRENT_DATA_VERSION));
	}

	public static void markCurrent(CompoundTag tag) {
		if (tag != null) tag.putInt(DATA_VERSION_KEY, CURRENT_DATA_VERSION);
	}

	public static <T> Dynamic<T> update(Dynamic<T> data) {
		return update(data, 0);
	}

	private static <T> Dynamic<T> update(Dynamic<T> data, int fromVersion) {
		return FIXER.update(MatchaDataFixSupport.ROOT, data, Math.max(0, fromVersion), CURRENT_DATA_VERSION);
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
		Schema v6 = builder.addSchema(6, V6::new);
		builder.addFixer(new V6Migration(v6));
		return builder.build().fixer();
	}
}
