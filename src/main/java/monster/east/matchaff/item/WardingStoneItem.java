package monster.east.matchaff.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.Spawner;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Spawn-egg behaviour with a pre-check for the Warding Stone's lodestone anchor. */
public final class WardingStoneItem extends SpawnEggItem {
	public WardingStoneItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (context.getLevel().getBlockEntity(context.getClickedPos()) instanceof Spawner) {
			return super.useOn(context);
		}
		BlockPos pos = spawnPos(context.getLevel(), context.getClickedPos(), context.getClickedFace());
		return canPlaceAnchor(context.getLevel(), context.getPlayer(), pos)
				? super.useOn(context) : InteractionResult.FAIL;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
		if (hit.getType() == HitResult.Type.BLOCK
				&& level.getBlockState(hit.getBlockPos()).getBlock() instanceof LiquidBlock
				&& !canPlaceAnchor(level, player, hit.getBlockPos())) {
			return InteractionResult.FAIL;
		}
		return super.use(level, player, hand);
	}

	private static BlockPos spawnPos(Level level, BlockPos clicked, Direction face) {
		BlockState state = level.getBlockState(clicked);
		return state.getCollisionShape(level, clicked).isEmpty() ? clicked : clicked.relative(face);
	}

	private static boolean canPlaceAnchor(Level level, Player player, BlockPos pos) {
		if (!level.isInWorldBounds(pos)) {
			return false;
		}
		BlockState lodestone = Blocks.LODESTONE.defaultBlockState();
		return level.getBlockState(pos).canBeReplaced()
				&& lodestone.canSurvive(level, pos)
				&& level.isUnobstructed(lodestone, pos, CollisionContext.placementContext(player));
	}
}
