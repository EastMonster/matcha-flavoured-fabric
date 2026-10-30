package monster.east.matchaff.mixin;

import net.fabricmc.fabric.api.item.v1.FabricItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import org.spongepowered.asm.mixin.Mixin;

/** The recipes accept potion bottles, including water and custom potion contents. */
@Mixin(PotionItem.class)
public abstract class PotionCraftingRemainderMixin implements FabricItem {
	@Override
	public ItemStackTemplate getCraftingRemainder(ItemStack stack) {
		return new ItemStackTemplate(Items.GLASS_BOTTLE);
	}
}
