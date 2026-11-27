package monster.east.matchaff.datafix.fix;

import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Separates the Angler's Almanac and updates legacy models and explorer maps. */
public final class V10Migration extends DataFix {
	public V10Migration(Schema outputSchema) {
		super(outputSchema, false);
	}

	@Override
	protected TypeRewriteRule makeRule() {
		Type<?> root = getInputSchema().getType(MatchaDataFixSupport.ROOT);
		return writeFixAndRead("Matcha V10 almanac, legacy model and map migration", root, root,
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
				migrateMap(compound, components, id);
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

	private static void migrateMap(CompoundTag stack, CompoundTag components, String id) {
		if (!(components.get("minecraft:item_name") instanceof CompoundTag name)) return;
		String target = switch (name.getStringOr("translate", "")) {
			case "filled_map.desert_pyramid" ->
					id.equals("minecraft:filled_map") || id.equals("minecraft:desert_village_map")
						|| id.equals("minecraft:desert_pyramid_map") ? "minecraft:desert_pyramid_map" : "";
			case "filled_map.warm_ocean_ruin" ->
					id.equals("minecraft:filled_map") || id.equals("minecraft:buried_treasure_map")
						|| id.equals("minecraft:warm_ocean_ruins_map") ? "minecraft:warm_ocean_ruins_map" : "";
			default -> "";
		};
		if (!target.isEmpty()) {
			stack.putString("id", target);
			components.remove("minecraft:item_name");
		}
	}
}
