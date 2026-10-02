package monster.east.matchaff.datafix.fix;

import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Migrates the three legacy main jukebox song references in saved ItemStacks. */
public final class V7Migration extends DataFix {
	public V7Migration(Schema outputSchema) {
		super(outputSchema, false);
	}

	@Override
	protected TypeRewriteRule makeRule() {
		Type<?> root = getInputSchema().getType(MatchaDataFixSupport.ROOT);
		return writeFixAndRead("Matcha V7 jukebox song migration", root, root,
				data -> MatchaDataFixSupport.apply(data, V7Migration::migrate));
	}

	private static Tag migrate(Tag tag) {
		if (tag instanceof CompoundTag compound) {
			if (compound.contains("id") && compound.get("components") instanceof CompoundTag components) {
				String song = components.getStringOr("minecraft:jukebox_playable", "");
				if (song.equals("main:golden") || song.equals("main:dry_hands") || song.equals("main:labyrinthine")) {
					components.putString("minecraft:jukebox_playable", "matcha:" + song.substring(5));
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
