package monster.east.matchaff.item;

import monster.east.matchaff.mechanic.BeaconKindlingMechanics;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public final class BeaconKindlingItem extends BlockItem {
	public BeaconKindlingItem(Properties properties) {
		super(Blocks.CAMPFIRE, properties);
	}

	@Override
	protected @Nullable BlockState getPlacementState(BlockPlaceContext context) {
		BlockState state = super.getPlacementState(context);
		// An extinguished underwater beacon cannot summon a trader.
		return state == null || state.getValue(CampfireBlock.WATERLOGGED)
				? null : state.setValue(CampfireBlock.SIGNAL_FIRE, true);
	}

	@Override
	public InteractionResult place(BlockPlaceContext context) {
		InteractionResult result = super.place(context);
		if (result.consumesAction() && context.getLevel() instanceof ServerLevel level
				&& context.getPlayer() instanceof ServerPlayer player) {
			BlockState state = level.getBlockState(context.getClickedPos());
			if (state.is(Blocks.CAMPFIRE) && state.getValue(CampfireBlock.LIT)) {
				BeaconKindlingMechanics.onPlaced(player, level, context.getClickedPos());
			}
		}
		return result;
	}

	@Override
	public void registerBlocks(Map<Block, Item> map, Item item) {
		// Fabric must not replace the campfire's vanilla item mapping with this item.
	}
}
