package monster.east.matchaff.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.BeetrootBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.Objects;

public final class TomatoCropBlock extends BeetrootBlock {
	public static final MapCodec<BeetrootBlock> CODEC = simpleCodec(TomatoCropBlock::new);
	private static Item seed;

	public TomatoCropBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	public MapCodec<BeetrootBlock> codec() {
		return CODEC;
	}

	@Override
	protected ItemLike getBaseSeedId() {
		return Objects.requireNonNull(seed, "Tomato seeds are not registered");
	}

	public static Item register() {
		ResourceKey<Block> blockKey = ResourceKey.create(
				Registries.BLOCK, Identifier.fromNamespaceAndPath("matcha", "tomatoes"));
		Block crop = Registry.register(BuiltInRegistries.BLOCK, blockKey,
				new TomatoCropBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.BEETROOTS).setId(blockKey)));
		ResourceKey<Item> itemKey = ResourceKey.create(
				Registries.ITEM, Identifier.fromNamespaceAndPath("matcha", "tomato_seeds"));
		seed = Registry.register(BuiltInRegistries.ITEM, itemKey,
				new BlockItem(crop, new Item.Properties().useItemDescriptionPrefix().setId(itemKey)));
		return seed;
	}
}
