package monster.east.matchaff.datafix.fix;

import com.google.gson.JsonParser;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Restores vanilla bundles and separates the Shakudo elytra enchantment. */
public final class V9Migration extends DataFix {
	private static final Map<String, CompoundTag> BUNDLES = readBundles();

	public V9Migration(Schema outputSchema) {
		super(outputSchema, false);
	}

	@Override
	protected TypeRewriteRule makeRule() {
		Type<?> root = getInputSchema().getType(MatchaDataFixSupport.ROOT);
		return writeFixAndRead("Matcha V9 bundle and Shakudo elytra migration", root, root,
				data -> MatchaDataFixSupport.apply(data, V9Migration::migrate));
	}

	private static Tag migrate(Tag tag) {
		if (tag instanceof CompoundTag compound) {
			if (compound.getStringOr("id", "").equals("matcha:shakudo_elytra")
					&& compound.get("components") instanceof CompoundTag components) {
				for (String key : new String[] {"minecraft:enchantments", "minecraft:stored_enchantments"}) {
					if (components.get(key) instanceof CompoundTag enchantments
							&& enchantments.contains("matcha:shakudo_armour")) {
						int level = enchantments.getIntOr("matcha:shakudo_armour", 0);
						enchantments.remove("matcha:shakudo_armour");
						if (level > 0) {
							enchantments.putInt("matcha:shakudo_elytra", Math.max(level,
									enchantments.getIntOr("matcha:shakudo_elytra", 0)));
						}
					}
				}
			}
			CompoundTag defaults = BUNDLES.get(compound.getStringOr("id", ""));
			if (defaults != null) {
				CompoundTag components = compound.get("components") instanceof CompoundTag existing
						? existing : new CompoundTag();
				CompoundTag defaultComponents = defaults.getCompound("components").orElseThrow();
				for (String key : defaultComponents.keySet()) {
					// Saved overrides and removal markers take precedence over the old defaults.
					if (!components.contains(key) && !components.contains("!" + key)) {
						components.put(key, Objects.requireNonNull(defaultComponents.get(key)).copy());
					}
				}
				compound.put("components", components);
				compound.putString("id", defaults.getString("id").orElseThrow());
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

	private static Map<String, CompoundTag> readBundles() {
		// Freeze defaults independently of future changes to the trading bundles.
		try (var stream = V9Migration.class.getResourceAsStream("/matcha/datafix/v9_bundles.json");
			 var reader = new InputStreamReader(Objects.requireNonNull(stream), StandardCharsets.UTF_8)) {
			Map<String, CompoundTag> defaults = new HashMap<>();
			JsonParser.parseReader(reader).getAsJsonObject().entrySet().forEach(entry -> {
				CompoundTag stack = (CompoundTag) JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, entry.getValue());
				defaults.put("matcha:" + entry.getKey(), stack);
				defaults.put("matcha-flavoured:" + entry.getKey(), stack);
			});
			return Map.copyOf(defaults);
		} catch (Exception exception) {
			throw new IllegalStateException("Could not load V9 bundle defaults", exception);
		}
	}
}
