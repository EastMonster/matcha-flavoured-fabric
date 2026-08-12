package monster.east.matchaff.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(Villager.class)
public abstract class VillagerFoodMixin {
	@Unique
	private static final Identifier NAAN = Identifier.fromNamespaceAndPath("matcha-flavoured", "naan");

	@Redirect(
			method = "eatUntilFull",
			at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;")
	)
	private Object matcha$naanFeedsVillagers(Map<Item, Integer> foodPoints, Object item) {
		return item == BuiltInRegistries.ITEM.getValue(NAAN) ? 4 : foodPoints.get(item);
	}

	@Inject(method = "countFoodPointsInInventory", at = @At("RETURN"), cancellable = true)
	private void matcha$countNaanFoodPoints(CallbackInfoReturnable<Integer> cir) {
		Villager villager = (Villager) (Object) this;
		cir.setReturnValue(cir.getReturnValue()
				+ villager.getInventory().countItem(BuiltInRegistries.ITEM.getValue(NAAN)) * 4);
	}
}
