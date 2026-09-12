package monster.east.matchaff.mixin;

import net.minecraft.advancements.Advancement;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ServerAdvancementManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Set;
import java.util.stream.Stream;

/**
 * Removes the vanilla advancement trees (story/adventure/end/husbandry/nether)
 * that the datapack blocks via pack.mcmeta, so only the datapack's own
 * (matcha:*) advancement tree remains visible.
 */
@Mixin(ServerAdvancementManager.class)
public abstract class ServerAdvancementManagerMixin {
	@Unique
	private static final Set<String> BLOCKED_PREFIXES = Set.of("story/", "adventure/", "end/", "husbandry/", "nether/");

	@Redirect(
			method = "<init>",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/core/HolderLookup$RegistryLookup;listElements()Ljava/util/stream/Stream;"
			)
	)
	private Stream<Holder.Reference<Advancement>> matcha$dropVanillaAdvancements(
			HolderLookup.RegistryLookup<Advancement> advancements
	) {
		return advancements.listElements()
				.filter(advancement -> !isVanillaTreeAdvancement(advancement.key().identifier()));
	}

	@Unique
	private static boolean isVanillaTreeAdvancement(Identifier id) {
		if (!id.getNamespace().equals("minecraft")) {
			return false;
		}
		String path = id.getPath();
		return BLOCKED_PREFIXES.stream().anyMatch(path::startsWith);
	}
}
