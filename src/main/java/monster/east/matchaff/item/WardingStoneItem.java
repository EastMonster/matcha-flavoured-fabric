package monster.east.matchaff.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

public final class WardingStoneItem extends BlockItem {
	public WardingStoneItem(Item.Properties properties) {
		super(Blocks.LODESTONE, properties);
	}

	@Override
	protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
		if (!(context.getLevel() instanceof ServerLevel level)) {
			return super.placeBlock(context, state);
		}
		var pos = context.getClickedPos();
		var stone = EntityTypes.ARMOR_STAND.create(level,
				EntityType.createDefaultStackConfig(level, context.getItemInHand(), context.getPlayer()),
				pos, EntitySpawnReason.SPAWN_ITEM_USE, false, false);
		if (stone == null) {
			return false;
		}
		BlockState previousState = level.getBlockState(pos);
		if (!super.placeBlock(context, state)) {
			return false;
		}
		if (level.addFreshEntity(stone)) {
			return true;
		}
		// Do not leave a free lodestone behind if the mechanism could not be added.
		level.setBlock(pos, previousState, 3);
		return false;
	}

	@Override
	public void registerBlocks(Map<Block, Item> map, Item item) {
		// Fabric must not replace the lodestone's vanilla item mapping with this item.
	}
}
