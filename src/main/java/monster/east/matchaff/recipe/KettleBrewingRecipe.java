package monster.east.matchaff.recipe;

import java.util.Collection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.recipe.v1.sync.RecipeSynchronization;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.PotionIngredient;
import net.minecraft.world.item.crafting.BrewingInput;
import net.minecraft.world.item.crafting.BrewingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

public record KettleBrewingRecipe(BrewingRecipe recipe) implements Recipe<BrewingInput> {
	private static final Map<Object, Candidates> CANDIDATES = new WeakHashMap<>();
	private static final Identifier ID = Identifier.fromNamespaceAndPath("matcha", "brewing");
	public static final RecipeType<KettleBrewingRecipe> TYPE = Registry.register(BuiltInRegistries.RECIPE_TYPE, ID,
			new RecipeType<KettleBrewingRecipe>() {
				@Override
				public String toString() { return ID.toString(); }
			});
	public static final RecipeSerializer<KettleBrewingRecipe> SERIALIZER = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, ID,
			new RecipeSerializer<>(BrewingRecipe.MAP_CODEC.xmap(KettleBrewingRecipe::new, KettleBrewingRecipe::recipe),
					BrewingRecipe.STREAM_CODEC.map(KettleBrewingRecipe::new, KettleBrewingRecipe::recipe)));

	public static void init() {
		RecipeSynchronization.synchronizeRecipeSerializer(SERIALIZER);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			synchronized (CANDIDATES) { CANDIDATES.clear(); }
		});
	}

	private static Collection<RecipeHolder<KettleBrewingRecipe>> recipes(Level level) {
		return level instanceof ServerLevel serverLevel ? serverLevel.recipeAccess().getAllOfType(TYPE)
				: level.recipeAccess().getSynchronizedRecipes().getAllOfType(TYPE);
	}

	public static boolean isInput(Level level, ItemStack stack) {
		return !stack.isEmpty() && candidates(level).inputs.getOrDefault(stack.getItem(), List.of())
				.stream().anyMatch(ingredient -> ingredient.test(stack));
	}

	public static boolean isReagent(Level level, ItemStack stack) {
		return !stack.isEmpty() && candidates(level).reagents.getOrDefault(stack.getItem(), List.of())
				.stream().anyMatch(ingredient -> ingredient.test(stack));
	}

	private static Candidates candidates(Level level) {
		// Both sources are replaced on recipe reload; never retain a Level in the cache.
		Object source = level instanceof ServerLevel serverLevel ? serverLevel.recipeAccess()
				: level.recipeAccess().getSynchronizedRecipes();
		synchronized (CANDIDATES) {
			return CANDIDATES.computeIfAbsent(source, ignored -> {
				Candidates index = new Candidates(new HashMap<>(), new HashMap<>());
				for (RecipeHolder<KettleBrewingRecipe> holder : recipes(level)) {
					addCandidates(index.inputs, holder.value().recipe.getInput());
					addCandidates(index.reagents, holder.value().recipe.getReagent());
				}
				return index;
			});
		}
	}

	private static void addCandidates(Map<Item, List<PotionIngredient>> index, PotionIngredient ingredient) {
		ingredient.ingredient().items().forEach(item ->
				index.computeIfAbsent(item.value(), ignored -> new ArrayList<>()).add(ingredient));
	}

	private record Candidates(Map<Item, List<PotionIngredient>> inputs, Map<Item, List<PotionIngredient>> reagents) { }

	@Override
	public boolean matches(BrewingInput input, Level level) { return recipe.matches(input, level); }

	@Override
	public ItemStack assemble(BrewingInput input) { return recipe.assemble(input); }

	@Override
	public RecipeType<KettleBrewingRecipe> getType() { return TYPE; }

	@Override
	public RecipeSerializer<KettleBrewingRecipe> getSerializer() { return SERIALIZER; }

	@Override
	public PlacementInfo placementInfo() { return PlacementInfo.NOT_PLACEABLE; }

	@Override
	public boolean isSpecial() { return true; }

	@Override
	public boolean showNotification() { return false; }

	@Override
	public String group() { return ""; }

	@Override
	public RecipeBookCategory recipeBookCategory() { return RecipeBookCategories.CRAFTING_MISC; }
}
