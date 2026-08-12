package monster.east.matchaff.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.PowderSnowBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Set;

@Mixin(PowderSnowBlock.class)
public abstract class PowderSnowBootsMixin {
	@Unique
	private static final Set<Identifier> MATCHA_LEATHER_BOOTS = Set.of(
			Identifier.fromNamespaceAndPath("matcha-flavoured", "sturdy_leather_boots"),
			Identifier.fromNamespaceAndPath("matcha-flavoured", "gilded_leather_boots")
	);

	@Redirect(
			method = "canEntityWalkOnPowderSnow",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z")
	)
	private static boolean matcha$acceptLeatherBoots(ItemStack stack, Object item) {
		return stack.is((Item) item)
				|| item == Items.LEATHER_BOOTS && MATCHA_LEATHER_BOOTS.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()));
	}
}
