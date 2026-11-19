package monster.east.matchaff.datafix.fix;

import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Separates the Angler's Almanac and updates legacy porkchop and Lava Kit models. */
public final class V10Migration extends DataFix {
	public V10Migration(Schema outputSchema) {
		super(outputSchema, false);
	}

	@Override
	protected TypeRewriteRule makeRule() {
		Type<?> root = getInputSchema().getType(MatchaDataFixSupport.ROOT);
		return writeFixAndRead("Matcha V10 almanac and legacy item model migration", root, root,
				data -> MatchaDataFixSupport.apply(data, V10Migration::migrate));
	}

	private static Tag migrate(Tag tag) {
		if (tag instanceof CompoundTag compound) {
			String id = compound.getStringOr("id", "");
			if (compound.get("components") instanceof CompoundTag components) {
				String model = components.getStringOr("minecraft:item_model", "");
				if (id.equals("minecraft:book")
						&& (model.equals("minecraft:anglers_almanac") || model.equals("matcha:anglers_almanac"))) {
					compound.putString("id", "matcha:anglers_almanac");
					components.putString("minecraft:item_model", "matcha:anglers_almanac");
				} else if (id.equals("minecraft:porkchop") && model.equals("minecraft:porkchop_classic")) {
					components.putString("minecraft:item_model", "matcha:porkchop_classic");
				} else if (id.equals("minecraft:red_bundle") && model.equals("matcha:lava_kit")) {
					components.putString("minecraft:item_model", "minecraft:red_bundle");
				}
			}
			for (String key : compound.keySet()) {
				if (!key.equals("minecraft:custom_data")) {
					Tag child = compound.get(key);
					if (child != null) migrate(child);
				}
			}
		} else if (tag instanceof ListTag list) {
			for (Tag child : list) migrate(child);
		}
		return tag;
	}
}
