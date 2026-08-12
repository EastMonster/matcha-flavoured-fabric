package monster.east.matchaff.client;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.extensions.vanilla.crafting.ICraftingCategoryExtension;
import mezz.jei.api.registration.IVanillaCategoryExtensionRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.List;

@JeiPlugin
public final class MatchaJeiPlugin implements IModPlugin, ICraftingCategoryExtension<ShapedRecipe> {
    private static final Identifier UID = Identifier.fromNamespaceAndPath("matcha-flavoured", "jei");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerVanillaCategoryExtensions(IVanillaCategoryExtensionRegistration registration) {
        registration.getCraftingCategory().addExtension(ShapedRecipe.class, this);
    }

    @Override
    public boolean isHandled(RecipeHolder<ShapedRecipe> recipe) {
        Identifier id = recipe.id().identifier();
        return id.getNamespace().equals("blessings") && !id.getPath().equals("hell_bound_book");
    }

    @Override
    public List<SlotDisplay> getIngredients(RecipeHolder<ShapedRecipe> recipe) {
        return recipe.value().getIngredients().stream()
                .map(Ingredient::optionalIngredientToDisplay)
                .toList();
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
}
