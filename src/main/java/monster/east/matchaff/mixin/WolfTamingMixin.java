package monster.east.matchaff.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.goal.BegGoal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({Wolf.class, BegGoal.class})
public abstract class WolfTamingMixin {
	@Unique
    private static final Identifier FISH_BONES =
			Identifier.fromNamespaceAndPath("matcha-flavoured", "fish_bones");

	@Redirect(
			method = {"mobInteract", "playerHoldingInteresting"},
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z")
	)
	private boolean matcha$fishBonesCountAsBone(ItemStack stack, Object item) {
		return stack.is((Item) item)
				|| item == Items.BONE && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(FISH_BONES);
	}
}
