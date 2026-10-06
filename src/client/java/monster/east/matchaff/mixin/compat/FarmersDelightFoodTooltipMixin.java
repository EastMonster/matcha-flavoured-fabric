package monster.east.matchaff.mixin.compat;

import monster.east.matchaff.compat.FarmersDelightCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;
import java.util.List;

@Pseudo
@Mixin(targets = {
		"vectorwing.farmersdelight.common.item.ConsumableItem",
		"vectorwing.farmersdelight.common.item.DogFoodItem",
		"vectorwing.farmersdelight.common.item.HorseFeedItem",
		"vectorwing.farmersdelight.common.item.SkilletItem",
		"vectorwing.farmersdelight.client.event.TooltipEvents"
}, remap = false)
public abstract class FarmersDelightFoodTooltipMixin {
	@Inject(method = "addTooltipToVanillaSoups", at = @At("HEAD"), cancellable = true, require = 0)
	private static void matcha$replaceVanillaSoupTooltip(ItemStack stack, Item.TooltipContext context,
			TooltipFlag flag, List<Component> lines, CallbackInfo ci) {
		if (FarmersDelightCompat.hasCustomLore(stack)) {
			ci.cancel();
		}
	}

	@Inject(method = "appendHoverText", at = @At("HEAD"), cancellable = true, require = 0)
	private void matcha$replaceFoodTooltip(ItemStack stack, Item.TooltipContext context,
			TooltipDisplay display, Consumer<Component> tooltipAdder, TooltipFlag flag, CallbackInfo ci) {
		if (FarmersDelightCompat.hasCustomLore(stack)) {
			ci.cancel();
		}
	}
}
