package monster.east.matchaff.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiPipeline;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

final class AlmanacSilhouettes {
	private static final Identifier HIDDEN_LOOT = Identifier.fromNamespaceAndPath("matcha", "textures/item/secret_ingredient.png");
	private AlmanacSilhouettes() {
	}

	static void draw(GuiGraphicsExtractor graphics, ItemStack icon, int x, int y) {
		Identifier model = icon.getOrDefault(DataComponents.ITEM_MODEL, BuiltInRegistries.ITEM.getKey(icon.getItem()));
		Identifier item = BuiltInRegistries.ITEM.getKey(icon.getItem());
		Identifier fishModel = Identifier.fromNamespaceAndPath("matcha",
				"items/almanac_silhouette/" + item.getNamespace() + "/" + item.getPath() + ".json");
		if (Minecraft.getInstance().getResourceManager().getResource(fishModel).isEmpty()) {
			graphics.blit(GuiPipeline.TEXTURED, HIDDEN_LOOT, x, y, 0.0F, 0.0F, 16, 16, 16, 16);
			return;
		}
		ItemStack silhouette = icon.copy();
		silhouette.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("matcha",
				"almanac_silhouette/" + model.getNamespace() + "/" + model.getPath()));
		silhouette.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
		graphics.fakeItem(silhouette, x, y);
	}
}
