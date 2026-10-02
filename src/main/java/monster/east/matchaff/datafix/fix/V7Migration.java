package monster.east.matchaff.datafix.fix;

import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Set;

/** Migrates legacy jukebox references and removes conflicts with intrinsic enchantments. */
public final class V7Migration extends DataFix {
	// Frozen migration scope, audited against recipe/registration defaults.
	private static final Set<String> ELECTRUM_TOOLS = Set.of(
			"matcha:electrum_axe", "matcha:electrum_dolabra", "matcha:electrum_hoe",
			"matcha:electrum_mattock", "matcha:electrum_pickaxe", "matcha:electrum_shovel"
	);
	private static final Set<String> SMITE_WEAPONS = Set.of(
			"matcha:electrum_axe", "matcha:electrum_spear", "matcha:electrum_sword", "matcha:silver_sword"
	);
	private static final Set<String> SHAKUDO_TOOLS = Set.of(
			"matcha:shakudo_axe", "matcha:shakudo_dolabra", "matcha:shakudo_hoe",
			"matcha:shakudo_mattock", "matcha:shakudo_pickaxe", "matcha:shakudo_shovel"
	);
	public V7Migration(Schema outputSchema) {
		super(outputSchema, false);
	}

	@Override
	protected TypeRewriteRule makeRule() {
		Type<?> root = getInputSchema().getType(MatchaDataFixSupport.ROOT);
		return writeFixAndRead("Matcha V7 jukebox and intrinsic enchantment migration", root, root,
				data -> MatchaDataFixSupport.apply(data, V7Migration::migrate));
	}

	private static Tag migrate(Tag tag) {
		if (tag instanceof CompoundTag compound) {
			if (compound.contains("id") && compound.get("components") instanceof CompoundTag components) {
				String song = components.getStringOr("minecraft:jukebox_playable", "");
				if (song.equals("main:golden") || song.equals("main:dry_hands") || song.equals("main:labyrinthine")) {
					components.putString("minecraft:jukebox_playable", "matcha:" + song.substring(5));
				}
				if (components.get("minecraft:enchantments") instanceof CompoundTag enchantments) {
					String id = compound.getStringOr("id", "");
					if (ELECTRUM_TOOLS.contains(id)) {
						enchantments.remove("minecraft:silk_touch");
						enchantments.remove("minecraft:fortune");
					}
					if (SMITE_WEAPONS.contains(id)) enchantments.remove("minecraft:sharpness");
					if (SHAKUDO_TOOLS.contains(id)) enchantments.remove("minecraft:fortune");
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
