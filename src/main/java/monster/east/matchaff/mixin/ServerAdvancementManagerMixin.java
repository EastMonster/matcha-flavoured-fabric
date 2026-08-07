package monster.east.matchaff.mixin;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
	@Shadow
	private Map<Identifier, AdvancementHolder> advancements;

	@Unique
	private static final Set<String> BLOCKED_PREFIXES = Set.of("story/", "adventure/", "end/", "husbandry/", "nether/");

	@Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("TAIL"))
	private void matcha$dropVanillaAdvancementTrees(Map<Identifier, Advancement> preparations,
	                                                ResourceManager manager, ProfilerFiller profiler, CallbackInfo ci) {
		var tree = ((ServerAdvancementManager) (Object) this).tree();
		Set<Identifier> blocked = tree.nodes().stream()
				.map(AdvancementNode::holder)
				.map(AdvancementHolder::id)
				.filter(ServerAdvancementManagerMixin::isVanillaTreeAdvancement)
				.collect(Collectors.toSet());
		tree.remove(blocked);
		// Criterion triggers resolve advancements through this map, not the tree, so
		// vanilla advancements must be removed here too or they would still trigger
		// (e.g. "Getting an Upgrade" when obtaining a stone pickaxe).
		this.advancements = this.advancements.entrySet().stream()
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
