package monster.east.matchaff.mixin;

import monster.east.matchaff.mechanic.WorldMechanics;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Runs the datapack's crafting reward once for every actual craft operation. */
@Mixin(ServerPlayer.class)
public abstract class RecipeCraftedMixin {
	@Inject(method = "triggerRecipeCrafted", at = @At("TAIL"))
	private void matcha$giveCraftingRewards(RecipeHolder<?> recipe, List<ItemStack> ingredients, CallbackInfo ci) {
		WorldMechanics.glassBottleReward((ServerPlayer) (Object) this);
	}
}
