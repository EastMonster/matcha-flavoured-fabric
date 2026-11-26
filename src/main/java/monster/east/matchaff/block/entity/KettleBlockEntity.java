package monster.east.matchaff.block.entity;

import monster.east.matchaff.inventory.KettleMenu;
import monster.east.matchaff.recipe.KettleBrewingRecipe;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import java.util.Set;

import java.util.Arrays;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BrewingFuel;
import net.minecraft.world.item.crafting.BrewingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public final class KettleBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
	public static BlockEntityType<KettleBlockEntity> TYPE;

	public static void register(Block block) {
		TYPE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Identifier.fromNamespaceAndPath("matcha", "kettle"),
				new BlockEntityType<>(KettleBlockEntity::new, Set.of(block)));
	}

	public void replenishHeatFuel() {
		fuel = 20;
		totalFuel = 20;
		setChanged();
	}

	public int fuelUses() { return fuel; }
	public int brewingTicks() { return brewTime; }
	private static final int[] SLOTS_FOR_UP = new int[]{3};
	private static final int[] SLOTS_FOR_DOWN = new int[]{0, 1, 2, 3};
	private static final int[] SLOTS_FOR_SIDES = new int[]{0, 1, 2, 4};
	private static final Component DEFAULT_NAME = Component.translatable("block.matcha.kettle");
	private NonNullList<ItemStack> items = NonNullList.withSize(5, ItemStack.EMPTY);
	private int brewTime;
	private int totalBrewTime;
	private boolean[] lastPotionCount;
	private Item ingredient;
	private int fuel;
	private int totalFuel;
	private float speedMultiplier = 1.0F;
	protected final ContainerData dataAccess = new ContainerData() {
		@Override
		public int get(final int dataId) {
			return switch (dataId) {
				case 0 -> KettleBlockEntity.this.brewTime;
				case 1 -> KettleBlockEntity.this.fuel;
				case 2 -> KettleBlockEntity.this.totalBrewTime;
				case 3 -> KettleBlockEntity.this.totalFuel;
				default -> 0;
			};
		}

		@Override
		public void set(final int dataId, final int value) {
			switch (dataId) {
				case 0:
					KettleBlockEntity.this.brewTime = value;
					break;
				case 1:
					KettleBlockEntity.this.fuel = value;
					break;
				case 2:
					KettleBlockEntity.this.totalBrewTime = value;
					break;
				case 3:
					KettleBlockEntity.this.totalFuel = value;
			}
		}

		@Override
		public int getCount() {
			return 4;
		}
	};
	private final RecipeManager.CachedCheck<BrewingInput, KettleBrewingRecipe> quickCheck = RecipeManager.createCheck(KettleBrewingRecipe.TYPE);

	public KettleBlockEntity(final BlockPos worldPosition, final BlockState blockState) {
		super(TYPE, worldPosition, blockState);
	}

	@Override
	protected Component getDefaultName() {
		return DEFAULT_NAME;
	}

	@Override
	public int getContainerSize() {
		return this.items.size();
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return this.items;
	}

	@Override
	protected void setItems(final NonNullList<ItemStack> items) {
		this.items = items;
	}

	protected int getUses(final ServerLevel level, final BrewingFuel brewingFuel) {
		return brewingFuel.uses().get(this.getLootContext(level), 0);
	}

	protected float getSpeedMultiplier(final ServerLevel level, final BrewingFuel brewingFuel) {
		return brewingFuel.speedMultiplier().get(this.getLootContext(level), 1.0F);
	}

	public static void serverTick(final ServerLevel level, final BlockPos pos, final BlockState selfState, final KettleBlockEntity entity) {
		NonNullList<ItemStack> items = entity.getItems();
		ItemStack fuel = items.get(4);
		BrewingFuel brewingFuel = fuel.get(DataComponents.BREWING_FUEL);
		if (entity.fuel <= 0 && brewingFuel != null) {
			entity.fuel = entity.getUses(level, brewingFuel);
			entity.totalFuel = entity.fuel;
			entity.speedMultiplier = entity.getSpeedMultiplier(level, brewingFuel);
			ItemStackTemplate fuelRemainder = fuel.getItem().getCraftingRemainder();
			ItemStack newFuel = fuel;
			fuel.shrink(1);
			if (fuelRemainder != null) {
				if (fuel.isEmpty()) {
					newFuel = fuelRemainder.create();
				} else {
					Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), fuelRemainder.create());
				}
			}

			items.set(4, newFuel);
			setChanged(level, pos, selfState);
		}

		boolean brewable = isBrewable(level, entity);
		boolean isBrewing = entity.brewTime > 0;
		ItemStack ingredient = entity.items.get(3);
		if (isBrewing) {
			entity.brewTime--;
			boolean isDoneBrewing = entity.brewTime == 0;
			if (isDoneBrewing && brewable) {
				doBrew(level, pos, entity);
			} else if (!brewable || !ingredient.is(entity.ingredient)) {
				entity.brewTime = 0;
			}

			setChanged(level, pos, selfState);
		} else if (brewable && entity.fuel > 0) {
			float speedMutliplier = entity.speedMultiplier > 0.0F ? entity.speedMultiplier : 1.0F;
			entity.fuel--;
			entity.brewTime = (int)Math.ceil(400.0F / speedMutliplier);
			entity.totalBrewTime = entity.brewTime;
			entity.ingredient = ingredient.getItem();
			setChanged(level, pos, selfState);
		}

		boolean[] newCount = entity.getPotionBits();
		if (!Arrays.equals(newCount, entity.lastPotionCount)) {
			entity.lastPotionCount = newCount;
			BlockState state = selfState;
			if (!(state.getBlock() instanceof BrewingStandBlock)) {
				return;
			}

			for (int i = 0; i < BrewingStandBlock.HAS_BOTTLE.length; i++) {
				state = state.setValue(BrewingStandBlock.HAS_BOTTLE[i], newCount[i]);
			}

			level.setBlock(pos, state, 2);
		}
	}

	private boolean[] getPotionBits() {
		boolean[] result = new boolean[3];

		for (int potion = 0; potion < 3; potion++) {
			if (!this.items.get(potion).isEmpty()) {
				result[potion] = true;
			}
		}

		return result;
	}

	private static boolean isBrewable(final ServerLevel serverLevel, final KettleBlockEntity entity) {
		NonNullList<ItemStack> items = entity.getItems();
		ItemStack ingredient = items.get(3);
		if (ingredient.isEmpty()) {
			return false;
		}

		for (int dest = 0; dest < 3; dest++) {
			ItemStack itemStack = items.get(dest);
			if (!itemStack.isEmpty()) {
				Optional<RecipeHolder<KettleBrewingRecipe>> recipe = entity.quickCheck.getRecipeFor(new BrewingInput(itemStack, ingredient), serverLevel);
				if (recipe.isPresent()) {
					return true;
				}
			}
		}

		return false;
	}

	private static void doBrew(final ServerLevel level, final BlockPos pos, final KettleBlockEntity entity) {
		NonNullList<ItemStack> items = entity.getItems();
		ItemStack ingredient = items.get(3);

		for (int dest = 0; dest < 3; dest++) {
			ItemStack container = items.get(dest);
			BrewingInput input = new BrewingInput(container, ingredient);
			Optional<RecipeHolder<KettleBrewingRecipe>> recipe = entity.quickCheck.getRecipeFor(input, level);
			items.set(dest, recipe.isPresent() ? recipe.get().value().assemble(input) : container);
		}

		ItemStackTemplate remainder = ingredient.getItem().getCraftingRemainder();
		ingredient.shrink(1);
		if (remainder != null) {
			if (ingredient.isEmpty()) {
				ingredient = remainder.create();
			} else {
				Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), remainder.create());
			}
		}

		items.set(3, ingredient);
		level.levelEvent(1035, pos, 0);
	}

	@Override
	protected void loadAdditional(final ValueInput input) {
		super.loadAdditional(input);
		this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, this.items);
		this.brewTime = input.getIntOr("BrewTime", 0);
		this.totalBrewTime = input.getIntOr("total_brew_time", 400);
		if (this.brewTime > 0) {
			this.ingredient = this.items.get(3).getItem();
		}

		this.fuel = input.getIntOr("Fuel", 0);
		this.totalFuel = input.getIntOr("total_fuel", 20);
		this.speedMultiplier = input.getFloatOr("speed_multiplier", 1.0F);
	}

	@Override
	protected void saveAdditional(final ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("BrewTime", this.brewTime);
		output.putInt("total_brew_time", this.totalBrewTime);
		ContainerHelper.saveAllItems(output, this.items);
		output.putInt("Fuel", this.fuel);
		output.putInt("total_fuel", this.totalFuel);
		output.putFloat("speed_multiplier", this.speedMultiplier);
	}

	@Override
	public boolean canPlaceItem(final int slot, final ItemStack itemStack) {
		if (slot == 4) {
			return itemStack.has(DataComponents.BREWING_FUEL);
		}

		if (this.level == null) {
			return false;
		}

		return slot == 3
			? KettleBrewingRecipe.isReagent(this.level, itemStack)
			: KettleBrewingRecipe.isInput(this.level, itemStack) && this.getItem(slot).isEmpty();
	}

	@Override
	public int[] getSlotsForFace(final Direction direction) {
		if (direction == Direction.UP) {
			return SLOTS_FOR_UP;
		} else {
			return direction == Direction.DOWN ? SLOTS_FOR_DOWN : SLOTS_FOR_SIDES;
		}
	}

	@Override
	public boolean canPlaceItemThroughFace(final int slot, final ItemStack itemStack, final @Nullable Direction direction) {
		return this.canPlaceItem(slot, itemStack);
	}

	@Override
	public boolean canTakeItemThroughFace(final int slot, final ItemStack itemStack, final Direction direction) {
		return slot == 3 ? itemStack.is(Items.GLASS_BOTTLE) : true;
	}

	@Override
	protected AbstractContainerMenu createMenu(final int containerId, final Inventory inventory) {
		return new KettleMenu(containerId, inventory, this, this.dataAccess);
	}
}
