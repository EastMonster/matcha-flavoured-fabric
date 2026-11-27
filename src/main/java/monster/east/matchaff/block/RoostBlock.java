package monster.east.matchaff.block;

import monster.east.matchaff.mechanic.BirdMechanics;
import monster.east.matchaff.mixin.PoiTypesAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.Set;

public final class RoostBlock extends VegetationBlock {
	public static final ResourceKey<PoiType> POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE,
			Identifier.fromNamespaceAndPath("matcha", "roost"));
	public static RoostBlock TWIG;
	private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 3, 14);

	private RoostBlock(BlockBehaviour.Properties properties) { super(properties); }

	public static List<Item> register() {
		Item hay = register("roost_hay");
		Item twig = register("roost_twig");
		TWIG = (RoostBlock) ((BlockItem) twig).getBlock();
		var states = Set.of(((BlockItem) hay).getBlock().defaultBlockState(), TWIG.defaultBlockState());
		var poi = Registry.registerForHolder(BuiltInRegistries.POINT_OF_INTEREST_TYPE, POI, new PoiType(states, 0, 1));
		states.forEach(state -> PoiTypesAccessor.matcha$typesByState().put(state, poi));
		return List.of(hay, twig);
	}

	private static Item register(String path) {
		Identifier id = Identifier.fromNamespaceAndPath("matcha", path);
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, new RoostBlock(
				BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).noCollision().instabreak()
						.sound(SoundType.LEAF_LITTER).pushReaction(PushReaction.POPPED).setId(key)));
		ResourceKey<Item> item = ResourceKey.create(Registries.ITEM, id);
		return Registry.register(BuiltInRegistries.ITEM, item, new BlockItem(block, new Item.Properties().setId(item)));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if (level instanceof ServerLevel serverLevel && !oldState.is(this)) BirdMechanics.onRoostPlaced(serverLevel, pos);
	}
}
