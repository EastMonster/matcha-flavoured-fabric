package monster.east.matchaff.mixin;

import com.google.gson.Gson;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

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

	@ModifyArg(
			method = "<init>",
			at = @At(value = "INVOKE", target =
					"Lnet/minecraft/world/item/crafting/RecipeMap;create(Lnet/minecraft/core/HolderLookup;)Lnet/minecraft/world/item/crafting/RecipeMap;")
	)
	private static HolderLookup<net.minecraft.world.item.crafting.Recipe<?>> matcha$dropBlockedRecipes(
			HolderLookup<net.minecraft.world.item.crafting.Recipe<?>> lookup) {
		return new HolderLookup<>() {
			@Override
			public Stream<Holder.Reference<net.minecraft.world.item.crafting.Recipe<?>>> listElements() {
				return lookup.listElements().filter(holder -> !isBlocked(holder.key().identifier()));
			}

			@Override
			public Stream<HolderSet.Named<net.minecraft.world.item.crafting.Recipe<?>>> listTags() {
				return lookup.listTags();
			}

			@Override
			public Optional<Holder.Reference<net.minecraft.world.item.crafting.Recipe<?>>> get(
					ResourceKey<net.minecraft.world.item.crafting.Recipe<?>> key) {
				return lookup.get(key);
			}

			@Override
			public Optional<HolderSet.Named<net.minecraft.world.item.crafting.Recipe<?>>> get(
					TagKey<net.minecraft.world.item.crafting.Recipe<?>> key) {
				return lookup.get(key);
			}
		};
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
