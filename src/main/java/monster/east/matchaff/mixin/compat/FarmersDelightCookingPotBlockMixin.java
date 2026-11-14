package monster.east.matchaff.mixin.compat;

import monster.east.matchaff.compat.FarmersDelightCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "vectorwing.farmersdelight.common.block.CookingPotBlock", remap = false)
public abstract class FarmersDelightCookingPotBlockMixin {
	@ModifyVariable(method = "useItemOn", at = @At("STORE"), ordinal = 1, require = 0)
	private ItemStack matcha$discoverBowlServedRecipe(ItemStack serving, ItemStack heldStack, BlockState state,
			Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		FarmersDelightCompat.discoverCookingPotRecipe(player, serving);
		return serving;
	}
}
