package monster.east.matchaff.mixin;

import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.WeatheringCopperGolemStatueBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({CopperGolem.class, WeatheringCopperGolemStatueBlock.class})
public abstract class HoneycombInteractionMixin {
	@Redirect(
		method = {"mobInteract", "useItemOn"},
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z"),
		require = 0
	)
	private boolean matcha$acceptCustomHoneycomb(ItemStack stack, Object item) {
		return stack.is((Item) item)
			|| item == Items.HONEYCOMB && stack.getItem() instanceof HoneycombItem;
	}
}
