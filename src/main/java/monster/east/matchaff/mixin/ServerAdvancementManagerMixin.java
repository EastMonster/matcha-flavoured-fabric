package monster.east.matchaff.mixin;

import net.minecraft.advancements.Advancement;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ServerAdvancementManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Removes the vanilla advancement trees (story/adventure/end/husbandry/nether)
 * that the datapack blocks via pack.mcmeta, so only the datapack's own
 * (main:*) advancement tree remains visible.
 */
@Mixin(ServerAdvancementManager.class)
public abstract class ServerAdvancementManagerMixin {
	@Unique
	private static final Set<String> BLOCKED_PREFIXES = Set.of("story/", "adventure/", "end/", "husbandry/", "nether/");

	@ModifyVariable(
			method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
			at = @At("HEAD"),
			argsOnly = true
	)
	private Map<Identifier, Advancement> matcha$dropVanillaAdvancements(Map<Identifier, Advancement> preparations) {
		return preparations.entrySet().stream()
				.filter(entry -> !isVanillaTreeAdvancement(entry.getKey()))
				.collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
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
