package monster.east.matchaff.mixin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AdvancementTree.class)
public abstract class AdvancementTreeMixin {
	@Unique
	private static final List<Identifier> ROOT_ORDER = List.of(
			Identifier.fromNamespaceAndPath("matcha", "tutorial/root"),
			Identifier.fromNamespaceAndPath("matcha", "hell/root"),
			Identifier.fromNamespaceAndPath("matcha", "end/root"),
			Identifier.fromNamespaceAndPath("matcha", "anglers_almanac/root")
	);

	@Inject(method = "roots", at = @At("RETURN"), cancellable = true)
	private void matcha$orderRoots(CallbackInfoReturnable<Iterable<AdvancementNode>> callbackInfo) {
		List<AdvancementNode> orderedRoots = new ArrayList<>();
		callbackInfo.getReturnValue().forEach(orderedRoots::add);
		orderedRoots.sort(Comparator
				.comparingInt(AdvancementTreeMixin::matcha$order)
				.thenComparing(node -> node.holder().id().toString()));
		callbackInfo.setReturnValue(orderedRoots);
	}

	@Unique
	private static int matcha$order(AdvancementNode node) {
		int order = ROOT_ORDER.indexOf(node.holder().id());
		return order < 0 ? Integer.MAX_VALUE : order;
	}
}
