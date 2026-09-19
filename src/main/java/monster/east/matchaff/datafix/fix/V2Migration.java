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
import java.util.Map;

/**
 * V2 migrates Matcha ItemStacks that still use vanilla carrier item IDs to their dedicated Matcha items.
 * It covers Steel, Shakudo, Hepatizon, Electrum, Divine Fragment, and Nazar with legacy Warding.
 */
public final class V2Migration extends DataFix {
	public V2Migration(Schema outputSchema) {
		super(outputSchema, false);
	}

	@Override
	protected TypeRewriteRule makeRule() {
		Type<?> root = getInputSchema().getType(MatchaDataFixSupport.ROOT);
		return writeFixAndRead("Matcha carrier item migration", root, root, V2Migration::migrate);
	}

	private static final Map<String, String> CARRIERS = Map.of(
			"minecraft:resin_brick", "matcha:steel", "minecraft:shulker_shell", "matcha:shakudo",
			"minecraft:phantom_membrane", "matcha:hepatizon", "minecraft:heart_of_the_sea", "matcha:electrum",
			"minecraft:turtle_scute", "matcha:divine_fragment"
	);

	private static Dynamic<?> migrate(Dynamic<?> data) {
		return MatchaDataFixSupport.apply(data, tag -> migrate(tag, false));
	}

	private static Tag migrate(Tag tag, boolean customData) {
		if (tag instanceof CompoundTag compound) {
			if (!customData) {
				String id = compound.getStringOr("id", "");
				String replacement = CARRIERS.get(id);
				if (replacement != null) compound.putString("id", replacement);
				else if (id.equals("minecraft:glistering_melon_slice") && carriesNazarWarding(compound)) compound.putString("id", "matcha:nazar");
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

	private static boolean carriesNazarWarding(CompoundTag stack) {
		CompoundTag components = stack.get("components") instanceof CompoundTag child ? child : stack;
		for (String component : MatchaDataFixSupport.enchantmentComponents()) {
			if (components.get(component) instanceof CompoundTag enchantments
					&& (enchantments.contains("matcha:warding1") || enchantments.contains("matcha-flavoured:warding1")
							|| enchantments.contains("matcha:warding_2"))) return true;
		}
		return false;
	}
}
