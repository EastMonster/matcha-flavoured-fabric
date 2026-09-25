package monster.east.matchaff.datafix.fix;

import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.List;

/** Migrates the Hepatizon elytra rename and the former beetroot-based tomato items and crop. */
public final class V6Migration extends DataFix {
	private static final String OLD_ITEM_ID = "matcha:bronze_elytra";
	private static final String NEW_ITEM_ID = "matcha:hepatizon_elytra";

	public V6Migration(Schema outputSchema) {
		super(outputSchema, false);
	}

	@Override
	protected TypeRewriteRule makeRule() {
		Type<?> root = getInputSchema().getType(MatchaDataFixSupport.ROOT);
		return writeFixAndRead("Matcha V6 item and crop migration", root, root, V6Migration::migrate);
	}

	static Dynamic<?> migrate(Dynamic<?> data) {
		return MatchaDataFixSupport.apply(data, tag -> {
			migrate(tag, false);
			if (tag instanceof CompoundTag chunk) migrateCropBlocks(chunk);
			return tag;
		});
	}

	private static Tag migrate(Tag tag, boolean customData) {
		if (tag instanceof CompoundTag compound) {
			if (!customData) {
				switch (compound.getStringOr("id", "")) {
					case OLD_ITEM_ID -> {
						compound.putString("id", NEW_ITEM_ID);
						renameAttributeModifierId(compound);
					}
					case "minecraft:beetroot" -> compound.putString("id", "matcha:tomato");
					case "minecraft:beetroot_seeds" -> compound.putString("id", "matcha:tomato_seeds");
				}
			}
			for (String key : List.copyOf(compound.keySet())) {
				Tag child = compound.get(key);
				if (child != null) compound.put(key, migrate(child, customData || key.equals("minecraft:custom_data")));
			}
		} else if (tag instanceof ListTag list) {
			for (int index = 0; index < list.size(); index++) list.set(index, migrate(list.get(index), customData));
		}
		return tag;
	}

	private static void migrateCropBlocks(CompoundTag chunk) {
		if (!(chunk.get("sections") instanceof ListTag sections)) return;
		for (Tag tag : sections) {
			if (!(tag instanceof CompoundTag section)
					|| !(section.get("block_states") instanceof CompoundTag blockStates)
					|| !(blockStates.get("palette") instanceof ListTag palette)) continue;
			for (Tag paletteTag : palette) {
				if (paletteTag instanceof CompoundTag state
						&& state.getStringOr("Name", "").equals("minecraft:beetroots")) {
					state.putString("Name", "matcha:tomatoes");
				}
			}
		}
	}

	private static void renameAttributeModifierId(CompoundTag stack) {
		if (!(stack.get("components") instanceof CompoundTag components)
				|| !(components.get("minecraft:attribute_modifiers") instanceof ListTag modifiers)) return;
		for (Tag tag : modifiers) {
			if (!(tag instanceof CompoundTag modifier)) continue;
			String id = modifier.getStringOr("id", "");
			int separator = id.indexOf(':');
			String path = separator < 0 ? id : id.substring(separator + 1);
			if (path.equals("bronze_elytra")) {
				modifier.putString("id", (separator < 0 ? "" : id.substring(0, separator + 1)) + "hepatizon_elytra");
			}
		}
	}
}
