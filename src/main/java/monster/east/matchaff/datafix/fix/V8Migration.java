package monster.east.matchaff.datafix.fix;

import com.google.gson.JsonParser;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtOps;
import com.mojang.serialization.JsonOps;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Returns cookies and volatile flasks to vanilla IDs and renames Divine Fragment to Pith. */
public final class V8Migration extends DataFix {
	private static final Map<String, CompoundTag> VOLATILE_POTIONS = readVolatilePotions();

	public V8Migration(Schema outputSchema) {
		super(outputSchema, false);
	}

	@Override
	protected TypeRewriteRule makeRule() {
		Type<?> root = getInputSchema().getType(MatchaDataFixSupport.ROOT);
		return writeFixAndRead("Matcha V8 cookie, volatile potion and Pith migration", root, root,
				data -> MatchaDataFixSupport.apply(data, V8Migration::migrate));
	}

	private static Tag migrate(Tag tag) {
		if (tag instanceof CompoundTag compound) {
			String id = compound.getStringOr("id", "");
			if (id.equals("matcha:chocolate_chip_cookie") || id.equals("matcha-flavoured:chocolate_chip_cookie")) {
				compound.putString("id", "minecraft:cookie");
			}
			if (id.equals("matcha:divine_fragment") || id.equals("matcha-flavoured:divine_fragment")) {
				compound.putString("id", "matcha:pith");
			}
			String translation = compound.getStringOr("translate", "");
			if (translation.equals("item.matcha.divine_fragment") || translation.equals("item.matcha-flavoured.divine_fragment")) {
				compound.putString("translate", "item.matcha.pith");
			}
			String path = id.startsWith("matcha:") ? id.substring(7)
					: id.startsWith("matcha-flavoured:") ? id.substring(17) : "";
			CompoundTag defaults = VOLATILE_POTIONS.get(path);
			if (defaults != null) {
				CompoundTag components = compound.get("components") instanceof CompoundTag existing
						? existing : new CompoundTag();
				for (String key : defaults.keySet()) {
					if (!components.contains(key) && !components.contains("!" + key)) {
						components.put(key, Objects.requireNonNull(defaults.get(key)).copy());
					}
				}
				if (components.get("minecraft:custom_name") instanceof CompoundTag name) {
					String nameTranslation = name.getStringOr("translate", "");
					if (nameTranslation.equals("item.matcha." + path) || nameTranslation.equals("item.matcha-flavoured." + path)) {
						name.putString("translate", defaults.getCompound("minecraft:custom_name").orElseThrow()
								.getStringOr("translate", ""));
					}
				}
				compound.put("components", components);
				compound.putString("id", "minecraft:splash_potion");
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

	private static Map<String, CompoundTag> readVolatilePotions() {
		// Freeze the removed items' defaults so later trade edits cannot change old-save upgrades.
		try (var stream = V8Migration.class.getResourceAsStream("/matcha/datafix/v8_volatile_potions.json");
			 var reader = new InputStreamReader(Objects.requireNonNull(stream), StandardCharsets.UTF_8)) {
			Map<String, CompoundTag> defaults = new HashMap<>();
			JsonParser.parseReader(reader).getAsJsonObject().entrySet().forEach(entry ->
					defaults.put(entry.getKey(), (CompoundTag) JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, entry.getValue())));
			return Map.copyOf(defaults);
		} catch (Exception exception) {
			throw new IllegalStateException("Could not load V8 volatile potion defaults", exception);
		}
	}
}
