package monster.east.matchaff.datafix.fix;

import com.mojang.serialization.Dynamic;
import com.mojang.datafixers.DSL;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import java.util.Set;
import java.util.function.UnaryOperator;

/** Common DataFix support shared by the versioned Matcha item migrations. */
public final class MatchaDataFixSupport {
	public static final DSL.TypeReference ROOT = () -> "matcha:item_data";
	private static final Set<String> ENCHANTMENT_COMPONENTS = Set.of(
			"minecraft:enchantments", "minecraft:stored_enchantments"
	);
	private static final Set<String> TRANSLATION_COMPONENTS = Set.of(
			"minecraft:item_name", "minecraft:custom_name", "minecraft:lore"
	);

	private MatchaDataFixSupport() {
	}

	static Dynamic<?> apply(Dynamic<?> data, UnaryOperator<Tag> migration) {
		if (!(data.getValue() instanceof Tag tag)) return data;
		return new Dynamic<>(NbtOps.INSTANCE, migration.apply(tag));
	}

	static boolean isEnchantmentComponent(String key) {
		return ENCHANTMENT_COMPONENTS.contains(key);
	}

	static Set<String> enchantmentComponents() {
		return ENCHANTMENT_COMPONENTS;
	}

	static boolean isTranslationComponent(String key) {
		return TRANSLATION_COMPONENTS.contains(key);
	}
}
