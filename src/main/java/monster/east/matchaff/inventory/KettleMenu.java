package monster.east.matchaff.inventory;

import monster.east.matchaff.recipe.KettleBrewingRecipe;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.Level;

import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;

public final class KettleMenu extends AbstractContainerMenu {
	public static final MenuType<KettleMenu> TYPE = Registry.register(BuiltInRegistries.MENU,
			Identifier.fromNamespaceAndPath("matcha", "kettle"), new MenuType<>(KettleMenu::new, FeatureFlags.VANILLA_SET));

	public static void init() { }

	private static final Identifier EMPTY_SLOT_FUEL = Identifier.withDefaultNamespace("container/slot/brewing_fuel");
	private static final Identifier EMPTY_SLOT_POTION = Identifier.withDefaultNamespace("container/slot/potion");
	private final Container brewingStand;
	private final ContainerData brewingStandData;
	private final Slot ingredientSlot;

	public KettleMenu(final int containerId, final Inventory inventory) {
		this(containerId, inventory, new SimpleContainer(5), new SimpleContainerData(4));
	}

	public KettleMenu(final int containerId, final Inventory inventory, final Container brewingStand, final ContainerData brewingStandData) {
		super(TYPE, containerId);
		checkContainerSize(brewingStand, 5);
		checkContainerDataCount(brewingStandData, 4);
		this.brewingStand = brewingStand;
		this.brewingStandData = brewingStandData;
		Level level = inventory.player.level();
		this.addSlot(new KettleMenu.PotionSlot(level, brewingStand, 0, 56, 51));
		this.addSlot(new KettleMenu.PotionSlot(level, brewingStand, 1, 79, 58));
		this.addSlot(new KettleMenu.PotionSlot(level, brewingStand, 2, 102, 51));
		this.ingredientSlot = this.addSlot(
			new KettleMenu.IngredientsSlot(level, brewingStand, 3, 79, 17)
		);
		this.addSlot(new KettleMenu.FuelSlot(brewingStand, 4, 17, 17));
		this.addDataSlots(brewingStandData);
		this.addStandardInventorySlots(inventory, 8, 84);
	}

	@Override
	public boolean stillValid(final Player player) {
		return this.brewingStand.stillValid(player);
	}

	@Override
	public ItemStack quickMoveStack(final Player player, final int slotIndex) {
		ItemStack clicked = ItemStack.EMPTY;
		Slot slot = this.slots.get(slotIndex);
		if (slot != null && slot.hasItem()) {
			ItemStack stack = slot.getItem();
			clicked = stack.copy();
			if ((slotIndex < 0 || slotIndex > 2) && slotIndex != 3 && slotIndex != 4) {
				if (KettleMenu.FuelSlot.mayPlaceItem(clicked)) {
					if (!this.moveItemStackTo(stack, 4, 5, false)
							&& !(this.ingredientSlot.mayPlace(stack) && this.moveItemStackTo(stack, 3, 4, false))) {
						return ItemStack.EMPTY;
					}
				} else if (this.ingredientSlot.mayPlace(stack)) {
					if (!this.moveItemStackTo(stack, 3, 4, false)) {
						return ItemStack.EMPTY;
					}
				} else if (KettleBrewingRecipe.isInput(player.level(), clicked)) {
					if (!this.moveItemStackTo(stack, 0, 3, false)) {
						return ItemStack.EMPTY;
					}
				} else if (slotIndex >= 5 && slotIndex < 32) {
					if (!this.moveItemStackTo(stack, 32, 41, false)) {
						return ItemStack.EMPTY;
					}
				} else if (slotIndex >= 32 && slotIndex < 41) {
					if (!this.moveItemStackTo(stack, 5, 32, false)) {
						return ItemStack.EMPTY;
					}
				} else if (!this.moveItemStackTo(stack, 5, 41, false)) {
					return ItemStack.EMPTY;
				}
			} else {
				if (!this.moveItemStackTo(stack, 5, 41, true)) {
					return ItemStack.EMPTY;
				}

				slot.onQuickCraft(stack, clicked);
			}

			if (stack.isEmpty()) {
				slot.setByPlayer(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}

			if (stack.getCount() == clicked.getCount()) {
				return ItemStack.EMPTY;
			}

			slot.onTake(player, clicked);
		}

		return clicked;
	}

	public int getFuel() {
		return this.brewingStandData.get(1);
	}

	public int getTotalFuel() {
		return this.brewingStandData.get(3);
	}

	public int getBrewingTicks() {
		return this.brewingStandData.get(0);
	}

	public int getTotalBrewingTicks() {
		return this.brewingStandData.get(2);
	}

	private static class FuelSlot extends Slot {
		public FuelSlot(final Container container, final int slot, final int x, final int y) {
			super(container, slot, x, y);
		}

		@Override
		public boolean mayPlace(final ItemStack itemStack) {
			return mayPlaceItem(itemStack);
		}

		public static boolean mayPlaceItem(final ItemStack itemStack) {
			return itemStack.has(DataComponents.BREWING_FUEL);
		}

		@Override
		public Identifier getNoItemIcon() {
			return KettleMenu.EMPTY_SLOT_FUEL;
		}
	}

	private static class IngredientsSlot extends Slot {
		private final Level level;

		public IngredientsSlot(final Level level, final Container container, final int slot, final int x, final int y) {
			super(container, slot, x, y);
			this.level = level;
		}

		@Override
		public boolean mayPlace(final ItemStack itemStack) {
			return KettleBrewingRecipe.isReagent(this.level, itemStack);
		}
	}

	private static class PotionSlot extends Slot {
		private final Level level;

		public PotionSlot(final Level level, final Container container, final int slot, final int x, final int y) {
			super(container, slot, x, y);
			this.level = level;
		}

		@Override
		public boolean mayPlace(final ItemStack itemStack) {
			return KettleBrewingRecipe.isInput(this.level, itemStack);
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}

		@Override
		public void onTake(final Player player, final ItemStack carried) {
			PotionContents potionContents = carried.get(DataComponents.POTION_CONTENTS);
			if (potionContents != null && player instanceof ServerPlayer serverPlayer) {
				CriteriaTriggers.BREWED_POTION.trigger(serverPlayer, potionContents);
			}

			super.onTake(player, carried);
		}

		@Override
		public Identifier getNoItemIcon() {
			return KettleMenu.EMPTY_SLOT_POTION;
		}
	}
}
