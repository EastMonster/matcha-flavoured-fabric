package monster.east.matchaff.datafix;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.DataFixerBuilder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.types.templates.TypeTemplate;
import com.mojang.serialization.Dynamic;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.schemas.V99;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/** Migrates Matcha ItemStacks before their component codecs resolve the item ID. */
public final class MatchaItemDataFixer {
	public static final String DATA_VERSION_KEY = "matcha_data_version";
	private static final int CURRENT_DATA_VERSION = 1;
	private static final DSL.TypeReference ROOT = () -> "matcha:item_data";
	private static final String OLD_NAMESPACE = "matcha-flavoured:";
	private static final String NEW_NAMESPACE = "matcha:";
	private static final Set<String> ITEM_PATHS = itemPaths();
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
			Dynamic<T> updated = (Dynamic<T>)new Dynamic<>(NbtOps.INSTANCE, updateIfNeeded(tag));
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
		return FIXER.update(ROOT, data, 0, 1);
	}

	private static DataFixer createFixer() {
		DataFixerBuilder builder = new DataFixerBuilder(1);
		builder.addSchema(0, MatchaSchema::new);
		Schema after = builder.addSchema(1, MatchaSchema::new);
		builder.addFixer(new ItemNamespaceFix(after));
		return builder.build().fixer();
	}

	private static Dynamic<?> migrate(Dynamic<?> data) {
		if (!(data.getValue() instanceof Tag tag)) {
			return data;
		}
		// ponytail: raw NBT reaches every vanilla ItemStack location; the creative-order allowlist prevents non-item registry IDs from changing.
		return new Dynamic<>(NbtOps.INSTANCE, migrate(tag, false));
	}

	private static Tag migrate(Tag tag, boolean customData) {
		if (tag instanceof CompoundTag compound) {
			if (!customData) {
				String id = compound.getStringOr("id", "");
				if (id.startsWith(OLD_NAMESPACE)) {
					String path = id.substring(OLD_NAMESPACE.length());
					if (ITEM_PATHS.contains(path)) {
						compound.putString("id", NEW_NAMESPACE + path);
					}
				}
			}
			for (String key : List.copyOf(compound.keySet())) {
				Tag child = compound.get(key);
				if (child != null) {
					compound.put(key, migrate(child, customData || key.equals("minecraft:custom_data")));
				}
			}
			return compound;
		}
		if (tag instanceof ListTag list) {
			for (int index = 0; index < list.size(); index++) {
				list.set(index, migrate(list.get(index), customData));
			}
		}
		return tag;
	}

	private static Set<String> itemPaths() {
		try (var stream = MatchaItemDataFixer.class.getResourceAsStream("/matcha/creative_order.json");
				 var reader = new InputStreamReader(Objects.requireNonNull(stream), StandardCharsets.UTF_8)) {
			Set<String> paths = new HashSet<>();
			collectPaths(JsonParser.parseReader(reader), paths);
			return Set.copyOf(paths);
		} catch (Exception exception) {
			throw new IllegalStateException("Could not read Matcha item migration paths", exception);
		}
	}

	private static void collectPaths(JsonElement element, Set<String> paths) {
		if (element.isJsonPrimitive()) {
			paths.add(element.getAsString());
		} else if (element.isJsonArray()) {
			for (JsonElement child : element.getAsJsonArray()) collectPaths(child, paths);
		} else {
			for (JsonElement child : element.getAsJsonObject().asMap().values()) collectPaths(child, paths);
		}
	}

	private static final class MatchaSchema extends V99 {
		private MatchaSchema(int versionKey, Schema parent) {
			super(versionKey, parent);
		}

		@Override
		public void registerTypes(Schema schema, Map<String, Supplier<TypeTemplate>> entityTypes, Map<String, Supplier<TypeTemplate>> blockEntityTypes) {
			super.registerTypes(schema, entityTypes, blockEntityTypes);
			schema.registerType(false, ROOT, DSL::remainder);
		}
	}

	private static final class ItemNamespaceFix extends DataFix {
		private ItemNamespaceFix(Schema outputSchema) {
			super(outputSchema, false);
		}

		@Override
		protected TypeRewriteRule makeRule() {
			Type<?> root = getInputSchema().getType(ROOT);
			return writeFixAndRead("Matcha item namespace migration", root, root, MatchaItemDataFixer::migrate);
		}
	}
}
