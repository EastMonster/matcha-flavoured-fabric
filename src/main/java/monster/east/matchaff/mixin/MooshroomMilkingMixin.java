package monster.east.matchaff.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Replace only normal milking output; retain suspicious stew and vanilla bowl handling. */
@Mixin(MushroomCow.class)
public abstract class MooshroomMilkingMixin {
	@ModifyArg(method = "mobInteract", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/ItemUtils;createFilledResult(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;"), index = 2)
	private ItemStack matcha$clearMushroomSoup(ItemStack result) {
		return result.is(Items.MUSHROOM_STEW)
				? new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("matcha", "mushroom_soup")))
				: result;
	}
}
