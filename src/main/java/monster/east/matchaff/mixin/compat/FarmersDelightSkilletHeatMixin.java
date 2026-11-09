package monster.east.matchaff.mixin.compat;

import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "vectorwing.farmersdelight.common.item.SkilletItem", remap = false)
public abstract class FarmersDelightSkilletHeatMixin {
	@Redirect(
			method = "isPlayerNearHeatSource",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/tags/TagKey;)Z"),
			require = 0
	)
	private static boolean matcha$requireBurningOven(BlockState state, TagKey<Block> tag) {
		return state.is(tag) && (!state.is(Blocks.FURNACE) || state.getValue(BlockStateProperties.LIT));
	}
}
