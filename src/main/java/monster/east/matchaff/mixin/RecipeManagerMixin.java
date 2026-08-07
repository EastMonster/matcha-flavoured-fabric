package monster.east.matchaff.mixin;

import com.google.gson.Gson;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Removes the vanilla recipes listed in the datapack's pack.mcmeta filter
 * (stone/iron/golden/diamond/copper/wooden tools, smelting, foods, etc.),
 * so the mod matches the datapack's crafting behaviour. The blocked item
 * definitions still exist, they just have no vanilla crafting recipe anymore.
 */
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {
	@Unique
	private static final Set<String> BLOCKED_RECIPES = loadBlockedRecipes();

	@Inject(method = "prepare", at = @At("RETURN"), cancellable = true)
	private void matcha$dropBlockedRecipes(ResourceManager manager, ProfilerFiller profiler,
	                                       CallbackInfoReturnable<RecipeMap> cir) {
		RecipeMap map = cir.getReturnValue();
		List<RecipeHolder<?>> filtered = map.values().stream()
				.filter(holder -> !isBlocked(holder.id().identifier()))
				.toList();
		cir.setReturnValue(RecipeMap.create(filtered));
	}

	@Unique
	private static boolean isBlocked(Identifier id) {
		return id.getNamespace().equals("minecraft") && BLOCKED_RECIPES.contains(id.getPath());
	}

	@Unique
	private static Set<String> loadBlockedRecipes() {
		try (var stream = RecipeManagerMixin.class.getResourceAsStream("/matcha/blocked_recipes.json")) {
			String[] recipes = new Gson().fromJson(
					new InputStreamReader(Objects.requireNonNull(stream), StandardCharsets.UTF_8),
					String[].class
			);
			return Set.of(recipes);
		} catch (Exception exception) {
			throw new IllegalStateException("Could not load blocked recipes", exception);
		}
	}
}
