package monster.east.matchaff.client;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.ICraftingGridHelper;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.extensions.vanilla.crafting.ICraftingCategoryExtension;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IVanillaCategoryExtensionRegistration;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import monster.east.matchaff.mixin.ClientAdvancementsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.CriterionProgress;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@JeiPlugin
public final class MatchaJeiPlugin implements IModPlugin, ICraftingCategoryExtension<ShapedRecipe> {
    private static final Identifier UID = Identifier.fromNamespaceAndPath("matcha-flavoured", "jei");
	private static final List<SecretRecipe> SECRET_RECIPES = List.of(
			new SecretRecipe("food:golden_steamed_carrots", "main:tutorial/cook_secret_food", "golden_steamed_carrots"),
			new SecretRecipe("food:golden_steamed_carrots_campfire", "main:tutorial/cook_secret_food", "golden_steamed_carrots"),
			new SecretRecipe("food:baked_golden_apple", "main:tutorial/cook_secret_food", "baked_golden_apple"),
			new SecretRecipe("food:baked_golden_apple_campfire", "main:tutorial/cook_secret_food", "baked_golden_apple"),
			new SecretRecipe("food:braised_crimson_fungus", "main:tutorial/cook_secret_food", "braised_crimson_fungus"),
			new SecretRecipe("food:braised_crimson_fungus_campfire", "main:tutorial/cook_secret_food", "braised_crimson_fungus"),
			new SecretRecipe("food:brasied_warped_fungus", "main:tutorial/cook_secret_food", "braised_warped_fungus"),
			new SecretRecipe("food:brasied_warped_fungus_campfire", "main:tutorial/cook_secret_food", "braised_warped_fungus"),
			new SecretRecipe("food:golden_carrot_cupcake", "main:tutorial/cook_secret_meal", "golden_carrot_cupcake"),
			new SecretRecipe("food:golden_pickled_carrots", "main:tutorial/cook_secret_meal", "golden_pickled_carrots"),
			new SecretRecipe("food:golden_apple_empanada", "main:tutorial/cook_secret_meal", "golden_apple_empanada"),
			new SecretRecipe("food:canned_golden_apples", "main:tutorial/cook_secret_meal", "canned_golden_apples"),
			new SecretRecipe("food:pickled_warped_fungus", "main:tutorial/cook_secret_meal", "pickled_warped_fungus"),
			new SecretRecipe("food:warped_stroganoff", "main:tutorial/cook_secret_meal", "warped_stroganoff", "main:cooking_recipes/warped_stroganoff_recipe"),
			new SecretRecipe("food:pickled_crimson_fungus", "main:tutorial/cook_secret_meal", "pickled_crimson_fungus"),
			new SecretRecipe("food:crimson_stroganoff", "main:tutorial/cook_secret_meal", "crimson_stroganoff"),
			new SecretRecipe("food:sweet_berry_toast", "main:tutorial/cook_secret_meal", "sweet_berry_toast", "main:cooking_recipes/sweet_berry_toast_recipe"),
			new SecretRecipe("food:warped_pizza", "main:tutorial/cook_secret_meal", "warped_pizza"),
			new SecretRecipe("food:gnocchi", "main:tutorial/cook_secret_meal", "gnocchi", "main:cooking_recipes/gnocchi_recipe")
	);
	private static IJeiRuntime jeiRuntime;
	private static net.minecraft.client.multiplayer.ClientPacketListener lastConnection;
	private static List<RecipeHolder<CraftingRecipe>> craftingRecipes = List.of();
	private static List<RecipeHolder<SmeltingRecipe>> smeltingRecipes = List.of();
	private static List<RecipeHolder<CampfireCookingRecipe>> campfireRecipes = List.of();
	private static long lastUnlockedMask = -1L;

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

	@Override
	public void onRuntimeAvailable(IJeiRuntime runtime) {
		jeiRuntime = runtime;
		ClientTickEvents.END_CLIENT_TICK.register(MatchaJeiPlugin::refreshSecretRecipes);
		ClientPlayConnectionEvents.JOIN.register((connection, sender, client) -> resetRecipeCache());
		ClientPlayConnectionEvents.DISCONNECT.register((connection, client) -> resetRecipeCache());
	}

    @Override
    public void registerVanillaCategoryExtensions(IVanillaCategoryExtensionRegistration registration) {
        registration.getCraftingCategory().addExtension(ShapedRecipe.class, this);
    }

    @Override
    public boolean isHandled(RecipeHolder<ShapedRecipe> recipe) {
        Identifier id = recipe.id().identifier();
        return id.getNamespace().equals("blessings");
    }

    @Override
    public List<SlotDisplay> getIngredients(RecipeHolder<ShapedRecipe> recipe) {
        return recipe.value().getIngredients().stream()
                .map(Ingredient::optionalIngredientToDisplay)
                .toList();
    }

    @Override
    public void setRecipe(RecipeHolder<ShapedRecipe> recipeHolder, IRecipeLayoutBuilder builder,
                          ICraftingGridHelper craftingGridHelper, IFocusGroup focuses) {
        ShapedRecipe recipe = recipeHolder.value();
        craftingGridHelper.createAndSetOutputs(builder, recipe.display().getFirst().result());

        List<List<ItemStack>> visibleIngredients = recipe.getIngredients().stream()
                .map(ingredient -> ingredient.map(MatchaJeiPlugin::visibleStacks).orElse(null))
                .toList();
        craftingGridHelper.createAndSetInputs(
                builder, visibleIngredients, recipe.getWidth(), recipe.getHeight());

        boolean hasEnchantedBook = recipe.getIngredients().stream()
                .anyMatch(ingredient -> ingredient
                        .filter(value -> value.acceptsItem(BuiltInRegistries.ITEM.wrapAsHolder(Items.ENCHANTED_BOOK)))
                        .isPresent());
        if (hasEnchantedBook) {
            builder.addInvisibleIngredients(RecipeIngredientRole.INPUT).add(bindingBookDisplay());
        }
    }

    private static List<ItemStack> visibleStacks(Ingredient ingredient) {
        if (ingredient.acceptsItem(BuiltInRegistries.ITEM.wrapAsHolder(Items.ENCHANTED_BOOK))) {
            Stream<ItemStack> stacks = Stream.of(new ItemStack(Items.ENCHANTED_BOOK));
            if (jeiRuntime != null) {
                stacks = Stream.concat(stacks, jeiRuntime.getIngredientManager().getAllItemStacks().stream()
                        .filter(stack -> stack.getItem() == Items.ENCHANTED_BOOK)
                        .map(ItemStack::copy));
            }
            var player = Minecraft.getInstance().player;
            if (player != null) {
                var inventory = player.getInventory();
                for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                    ItemStack stack = inventory.getItem(slot);
                    if (stack.getItem() == Items.ENCHANTED_BOOK) {
                        stacks = Stream.concat(stacks, Stream.of(stack.copy()));
                    }
                }
            }
            return stacks.toList();
        }
        var connection = Minecraft.getInstance().getConnection();
        var contextBuilder = new ContextMap.Builder();
        if (connection != null) {
            contextBuilder.withParameter(SlotDisplayContext.REGISTRIES, connection.registryAccess());
        }
        return ingredient.display().resolveForStacks(contextBuilder.create(SlotDisplayContext.CONTEXT));
    }

    @Override
    public void onDisplayedIngredientsUpdate(RecipeHolder<ShapedRecipe> recipe,
                                             List<IRecipeSlotDrawable> slots,
                                             IFocusGroup focuses) {
        SlotDisplay bindingBook = bindingBookDisplay();
        for (IRecipeSlotDrawable slot : slots) {
            if (slot.getRole() == RecipeIngredientRole.INPUT
                    && slot.getItemStacks().anyMatch(stack -> stack.getItem() == Items.ENCHANTED_BOOK)) {
                slot.clearDisplayOverrides();
                slot.createDisplayOverrides().add(bindingBook);
            }
        }
    }

    @Override
    public int getWidth(RecipeHolder<ShapedRecipe> recipe) {
        return recipe.value().getWidth();
    }

    @Override
    public int getHeight(RecipeHolder<ShapedRecipe> recipe) {
        return recipe.value().getHeight();
    }

    private static SlotDisplay bindingBookDisplay() {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return new SlotDisplay.ItemSlotDisplay(Items.ENCHANTED_BOOK);
        }
        var bindingCurse = connection.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.BINDING_CURSE);
        var stack = EnchantmentHelper.createBook(new EnchantmentInstance(bindingCurse, 1));
        stack.set(DataComponents.MAX_STACK_SIZE, 64);
        return new SlotDisplay.ItemStackSlotDisplay(ItemStackTemplate.fromNonEmptyStack(stack));
    }

	private static void refreshSecretRecipes(Minecraft client) {
		if (jeiRuntime == null) {
			return;
		}
		var connection = client.getConnection();
		if (connection != lastConnection || (connection != null && craftingRecipes.isEmpty())) {
			lastConnection = connection;
			craftingRecipes = lookup(RecipeTypes.CRAFTING);
			smeltingRecipes = lookup(RecipeTypes.SMELTING);
			campfireRecipes = lookup(RecipeTypes.CAMPFIRE_COOKING);
			lastUnlockedMask = -1L;
		}
		long unlockedMask = getUnlockedMask(connection);
		if (unlockedMask == lastUnlockedMask) {
			return;
		}
		lastUnlockedMask = unlockedMask;
		updateVisibility(RecipeTypes.CRAFTING, craftingRecipes, unlockedMask);
		updateVisibility(RecipeTypes.SMELTING, smeltingRecipes, unlockedMask);
		updateVisibility(RecipeTypes.CAMPFIRE_COOKING, campfireRecipes, unlockedMask);
	}

	private static void resetRecipeCache() {
		lastConnection = null;
		craftingRecipes = List.of();
		smeltingRecipes = List.of();
		campfireRecipes = List.of();
		lastUnlockedMask = -1L;
	}

	private static <R extends Recipe<?>> List<RecipeHolder<R>> lookup(IRecipeType<RecipeHolder<R>> type) {
		return jeiRuntime.getRecipeManager().createRecipeLookup(type).includeHidden().get().toList();
	}

	private static <R extends Recipe<?>> void updateVisibility(IRecipeType<RecipeHolder<R>> type,
	                                                            List<RecipeHolder<R>> recipes,
	                                                            long unlockedMask) {
		var hidden = recipes.stream()
				.filter(recipe -> secretIndex(recipe.id().identifier()) >= 0)
				.filter(recipe -> (unlockedMask & (1L << secretIndex(recipe.id().identifier()))) == 0)
				.toList();
		var visible = recipes.stream()
				.filter(recipe -> secretIndex(recipe.id().identifier()) >= 0)
				.filter(recipe -> (unlockedMask & (1L << secretIndex(recipe.id().identifier()))) != 0)
				.toList();
		jeiRuntime.getRecipeManager().hideRecipes(type, hidden);
		jeiRuntime.getRecipeManager().unhideRecipes(type, visible);
	}

	private static long getUnlockedMask(net.minecraft.client.multiplayer.ClientPacketListener connection) {
		if (connection == null) {
			return 0L;
		}
		Map<net.minecraft.advancements.AdvancementHolder, AdvancementProgress> progress =
				((ClientAdvancementsAccessor) connection.getAdvancements()).matcha$getProgress();
		var advancements = connection.getAdvancements();
		long mask = 0L;
		for (int i = 0; i < SECRET_RECIPES.size(); i++) {
			SecretRecipe secret = SECRET_RECIPES.get(i);
			if (criterionDone(advancements, progress, secret.advancementId(), secret.criterion())
					|| (secret.paperAdvancementId() != null && advancementDone(advancements, progress, secret.paperAdvancementId()))) {
				mask |= 1L << i;
			}
		}
		return mask;
	}

	private static boolean criterionDone(net.minecraft.client.multiplayer.ClientAdvancements advancements,
	                                    Map<net.minecraft.advancements.AdvancementHolder, AdvancementProgress> progress,
	                                     Identifier advancementId, String criterion) {
		var holder = advancements.get(advancementId);
		if (holder == null) {
			return false;
		}
		var advancementProgress = progress.get(holder);
		CriterionProgress criterionProgress = advancementProgress == null ? null : advancementProgress.getCriterion(criterion);
		return criterionProgress != null && criterionProgress.isDone();
	}

	private static boolean advancementDone(net.minecraft.client.multiplayer.ClientAdvancements advancements,
	                                       Map<net.minecraft.advancements.AdvancementHolder, AdvancementProgress> progress,
	                                       Identifier advancementId) {
		var holder = advancements.get(advancementId);
		var advancementProgress = holder == null ? null : progress.get(holder);
		return advancementProgress != null && advancementProgress.isDone();
	}

	private static int secretIndex(Identifier recipeId) {
		for (int i = 0; i < SECRET_RECIPES.size(); i++) {
			if (SECRET_RECIPES.get(i).recipeId().equals(recipeId)) {
				return i;
			}
		}
		return -1;
	}

	private record SecretRecipe(Identifier recipeId, Identifier advancementId, String criterion,
	                            Identifier paperAdvancementId) {
		private SecretRecipe(String recipeId, String advancementId, String criterion) {
			this(recipeId, advancementId, criterion, null);
		}

		private SecretRecipe(String recipeId, String advancementId, String criterion, String paperAdvancementId) {
			this(Identifier.parse(recipeId), Identifier.parse(advancementId), criterion,
					paperAdvancementId == null ? null : Identifier.parse(paperAdvancementId));
		}
	}
}
