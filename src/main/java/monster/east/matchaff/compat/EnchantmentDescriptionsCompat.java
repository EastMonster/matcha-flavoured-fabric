package monster.east.matchaff.compat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class EnchantmentDescriptionsCompat {
	public static final String WITHER_RESISTANCE_DESCRIPTION = "tooltip.matcha.wither_resistance.desc";

	private EnchantmentDescriptionsCompat() {
	}

	public static boolean isBlessingBook(Identifier id) {
		return id.getNamespace().equals("matcha")
				&& (id.getPath().startsWith("blessing_") || id.getPath().equals("ofuda"));
	}

	public static Component witherResistanceDescription(List<Component> lines) {
		// Reuse an already displayed description so EnchDesc owns visibility, style, prefix and suffix.
		for (Component line : lines) {
			List<Component> siblings = line.getSiblings();
			for (int i = 0; i < siblings.size(); i++) {
				Component sibling = siblings.get(i);
				if (sibling.getContents() instanceof TranslatableContents contents
						&& (contents.getKey().equals("enchantment.matcha.shakudo_armour.desc")
						|| contents.getKey().equals("enchantment.matcha.shakudo_elytra.desc"))) {
					Component description = line.copy();
					description.getSiblings().set(i, Component.translatable(WITHER_RESISTANCE_DESCRIPTION)
							.withStyle(sibling.getStyle()));
					return description;
				}
			}
		}
		return null;
	}

	public static boolean isWitherResistanceDescription(Component line) {
		return line.getSiblings().stream().anyMatch(sibling ->
				sibling.getContents() instanceof TranslatableContents contents
						&& contents.getKey().equals(WITHER_RESISTANCE_DESCRIPTION));
	}
}
