package monster.east.matchaff.block;

import monster.east.matchaff.block.entity.KettleBlockEntity;
import monster.east.matchaff.mixin.PoiTypeAccessor;
import monster.east.matchaff.mixin.PoiTypesAccessor;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import org.jspecify.annotations.Nullable;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class KettleBlock extends BrewingStandBlock {
	private static final VoxelShape SPOUT = Shapes.or(
			Block.box(12, 2, 7, 14, 5, 9),
			Block.box(12, 5, 7, 15, 7, 9));
	private static final VoxelShape CLOSED_SHAPE = Shapes.or(SPOUT,
			Block.box(4, 0, 4, 12, 6, 12),
			Block.box(5, 6, 5, 11, 7, 11),
			Block.box(7, 6, 7, 9, 8, 9),
			Block.box(4, 6, 7, 12, 11.5, 9));
	private static final VoxelShape OPEN_SHAPE = Shapes.or(SPOUT,
			Block.box(4, 0, 4, 12, 1, 12),
			Block.box(4, 1, 4, 12, 6, 5),
			Block.box(4, 1, 11, 12, 6, 12),
			Block.box(4, 1, 5, 5, 6, 11),
			Block.box(11, 1, 5, 12, 6, 11),
			Block.box(4, 5.517, 7.8706, 12, 8.3894, 13.5714));
	private static final TagKey<Block> HEAT = TagKey.create(Registries.BLOCK,
			Identifier.fromNamespaceAndPath("matcha", "is_kettle_heat_source"));

	private KettleBlock(BlockBehaviour.Properties properties) { super(properties); }

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new KettleBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level instanceof ServerLevel serverLevel ? createTickerHelper(type, KettleBlockEntity.TYPE,
				(ignored, pos, blockState, entity) -> KettleBlockEntity.serverTick(serverLevel, pos, blockState, entity)) : null;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		// Kettles do not emit the brewing stand's ambient smoke.
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(HAS_BOTTLE[0]) || state.getValue(HAS_BOTTLE[1]) || state.getValue(HAS_BOTTLE[2])
				? CLOSED_SHAPE : OPEN_SHAPE;
	}

	public static Item register() {
		Identifier id = Identifier.fromNamespaceAndPath("matcha", "kettle");
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
		Block block = Registry.register(BuiltInRegistries.BLOCK, key,
				new KettleBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.BREWING_STAND).sound(SoundType.LANTERN).setId(key)));
		KettleBlockEntity.register(block);
		var cleric = BuiltInRegistries.POINT_OF_INTEREST_TYPE.getOrThrow(PoiTypes.CLERIC);
		var states = Set.copyOf(block.getStateDefinition().getPossibleStates());
		var poiStates = PoiTypesAccessor.matcha$typesByState();
		cleric.value().matchingStates().forEach(poiStates::remove);
		((PoiTypeAccessor) (Object) cleric.value()).matcha$setMatchingStates(states);
		states.forEach(state -> poiStates.put(state, cleric));
		ResourceKey<Item> item = ResourceKey.create(Registries.ITEM, id);
		return Registry.register(BuiltInRegistries.ITEM, item, new BlockItem(block, new Item.Properties().setId(item)));
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof KettleBlockEntity entity) {
			if (level.getBlockState(pos.below()).is(HEAT)) {
				entity.replenishHeatFuel();
			}
			player.openMenu(entity);
			player.awardStat(Stats.INTERACT_WITH_BREWINGSTAND);
		}
		return InteractionResult.SUCCESS;
	}
}
