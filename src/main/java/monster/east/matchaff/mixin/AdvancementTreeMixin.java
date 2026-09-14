package monster.east.matchaff.mixin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AdvancementTree.class)
public abstract class AdvancementTreeMixin {
	@Unique
	private static final List<Identifier> ROOT_ORDER = List.of(
			Identifier.fromNamespaceAndPath("matcha", "tutorial/root"),
			Identifier.fromNamespaceAndPath("matcha", "hell/root"),
			Identifier.fromNamespaceAndPath("matcha", "end/root"),
			Identifier.fromNamespaceAndPath("matcha", "anglers_almanac/root")
	);

	@Shadow @Final
	private Set<AdvancementNode> roots;

	@Inject(method = "setListener", at = @At("HEAD"))
	private void matcha$orderRoots(AdvancementTree.Listener listener, CallbackInfo callbackInfo) {
		List<AdvancementNode> orderedRoots = new ArrayList<>(this.roots);
		orderedRoots.sort(Comparator
				.comparingInt(AdvancementTreeMixin::matcha$order)
				.thenComparing(node -> node.holder().id().toString()));
		this.roots.clear();
		this.roots.addAll(orderedRoots);
	}

	@Unique
	private static int matcha$order(AdvancementNode node) {
		int order = ROOT_ORDER.indexOf(node.holder().id());
		return order < 0 ? Integer.MAX_VALUE : order;
	}
}
