package monster.east.matchaff.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeHolderType;
import monster.east.matchaff.recipe.KettleBrewingRecipe;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.PotionIngredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.Nullable;

public final class KettleJeiCategory implements IRecipeCategory<RecipeHolder<KettleBrewingRecipe>> {
	private Object stepSource;
	private final Map<Identifier, Integer> brewingSteps = new HashMap<>();

	private int steps(RecipeHolder<KettleBrewingRecipe> holder) {
		var level = Minecraft.getInstance().level;
		if (level == null) return Integer.MAX_VALUE;
		var source = level.recipeAccess().getSynchronizedRecipes();
		if (source != stepSource) {
			stepSource = source;
			brewingSteps.clear();
			var recipes = List.copyOf(source.getAllOfType(KettleBrewingRecipe.TYPE));
			var outputs = recipes.stream().map(r -> r.value().recipe().getOutput().create()).toList();
			int[] distances = new int[recipes.size()];
			Arrays.fill(distances, Integer.MAX_VALUE);
			List<ItemStack> bases = List.of(
					PotionContents.createItemStack(Items.POTION, Potions.WATER),
					new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("matcha", "milk_bottle"))));
			boolean[] roots = new boolean[recipes.size()];
			for (int i = 0; i < recipes.size(); i++) {
				var input = recipes.get(i).value().recipe().getInput();
				roots[i] = bases.stream().anyMatch(input::test);
			}
			boolean changed;
			do {
				changed = false;
				for (int i = 0; i < recipes.size(); i++) {
					int previous = roots[i] ? 0 : Integer.MAX_VALUE;
					var input = recipes.get(i).value().recipe().getInput();
					for (int j = 0; j < outputs.size(); j++) {
						if (input.test(outputs.get(j))) previous = Math.min(previous, distances[j]);
					}
					if (previous != Integer.MAX_VALUE && previous + 1 < distances[i]) {
						distances[i] = previous + 1;
						changed = true;
					}
				}
			} while (changed);
			for (int i = 0; i < recipes.size(); i++) {
				int shortest = distances[i];
				// Identical products share their shortest route; graph edges use the input predicate above.
				for (int j = 0; j < outputs.size(); j++) {
					if (ItemStack.isSameItemSameComponents(outputs.get(i), outputs.get(j))) {
						shortest = Math.min(shortest, distances[j]);
					}
				}
				brewingSteps.put(recipes.get(i).id().identifier(), shortest);
			}
		}
		return brewingSteps.getOrDefault(holder.id().identifier(), Integer.MAX_VALUE);
	}
	private static final Identifier GUI = Identifier.fromNamespaceAndPath("matcha", "textures/gui/jei/kettle.png");
	private static final Identifier PROGRESS = Identifier.withDefaultNamespace("container/brewing_stand/brew_progress");
	private static final Identifier BUBBLES = Identifier.withDefaultNamespace("container/brewing_stand/bubbles");
	private static final Identifier FUEL = Identifier.withDefaultNamespace("container/brewing_stand/fuel_length");
	private static final int[] BUBBLE_HEIGHTS = {0, 6, 11, 16, 20, 24, 29};
	public static final IRecipeHolderType<KettleBrewingRecipe> TYPE = IRecipeHolderType.create(KettleBrewingRecipe.TYPE);

	@Override
	public IRecipeHolderType<KettleBrewingRecipe> getRecipeType() { return TYPE; }

	@Override
	public Component getTitle() { return Component.translatable("block.matcha.kettle"); }

	@Override
	public int getWidth() { return 114; }

	@Override
	public int getHeight() { return 61; }

	@Override
	public @Nullable IDrawable getIcon() { return null; }

	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<KettleBrewingRecipe> holder, IFocusGroup focuses) {
		var recipe = holder.value().recipe();
		List<ItemStack> inputs = stacks(recipe.getInput());
		IRecipeSlotBuilder[] bottles = new IRecipeSlotBuilder[3];
		for (int i = 0; i < 3; i++) {
			bottles[i] = builder.addInputSlot(1 + i * 23, i == 1 ? 44 : 37).addItemStacks(inputs);
		}
		builder.createFocusLink(bottles);
		builder.addOutputSlot(81, 3).addItemStack(recipe.getOutput().create());
		builder.addInputSlot(24, 3).addItemStacks(stacks(recipe.getReagent()));
	}

	private static List<ItemStack> stacks(PotionIngredient ingredient) {
		List<ItemStack> result = new ArrayList<>();
		ingredient.ingredient().items().forEach(item -> {
			ItemStack stack = item.value().getDefaultInstance();
			if (ingredient.test(stack)) result.add(stack);
			else ingredient.potions().flatMap(predicate -> predicate.potions()).ifPresent(potions -> {
				potions.stream().forEach(potion -> {
					ItemStack candidate = stack.copy();
					candidate.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
					if (ingredient.test(candidate)) result.add(candidate);
				});
			});
		});
		return result;
	}

	@Override
	public void draw(RecipeHolder<KettleBrewingRecipe> recipe, IRecipeSlotsView slots,
			GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, GUI, 0, 0, 0, 0, 114, 61, 114, 61);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FUEL, 18, 4, 0, 0, 5, 30, 18, 4);
		long time = System.currentTimeMillis();
		int progress = (int) ((time % 20000L) * 28 / 20000L);
		if (progress > 0) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PROGRESS, 9, 28, 0, 0, 42, 2, 9, progress);
		}
		int bubbleHeight = BUBBLE_HEIGHTS[(int) ((time / 100L) % BUBBLE_HEIGHTS.length)];
		if (bubbleHeight > 0) {
			int top = 29 - bubbleHeight;
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BUBBLES, 12, 29, 0, top, 8, top, 12, bubbleHeight);
		}
		int count = steps(recipe);
		graphics.text(Minecraft.getInstance().font, Component.translatable("gui.jei.category.brewing.steps",
				count == Integer.MAX_VALUE ? "?" : Integer.toString(count)), 70, 28, 0xFFD8C5A9, false);
	}
}
