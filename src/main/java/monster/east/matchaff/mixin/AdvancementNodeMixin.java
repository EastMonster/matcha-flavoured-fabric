package monster.east.matchaff.mixin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.advancements.AdvancementNode;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AdvancementNode.class)
public abstract class AdvancementNodeMixin {
	@Unique
	private static final Identifier ANGLERS_ALMANAC_ROOT =
			Identifier.fromNamespaceAndPath("matcha", "anglers_almanac/root");
	@Unique
	private static final List<Identifier> ANGLERS_ALMANAC_CATEGORY_ORDER = List.of(
			Identifier.fromNamespaceAndPath("matcha", "anglers_almanac/freshwater/cold/root"),
			Identifier.fromNamespaceAndPath("matcha", "anglers_almanac/freshwater/cool/root"),
			Identifier.fromNamespaceAndPath("matcha", "anglers_almanac/freshwater/temperate/root"),
			Identifier.fromNamespaceAndPath("matcha", "anglers_almanac/freshwater/hot_dry/root"),
			Identifier.fromNamespaceAndPath("matcha", "anglers_almanac/freshwater/hot_wet/root"),
			Identifier.fromNamespaceAndPath("matcha", "anglers_almanac/saltwater/cold/root"),
			Identifier.fromNamespaceAndPath("matcha", "anglers_almanac/saltwater/cool/root"),
			Identifier.fromNamespaceAndPath("matcha", "anglers_almanac/saltwater/temperate/root"),
			Identifier.fromNamespaceAndPath("matcha", "anglers_almanac/saltwater/warm/root"),
			Identifier.fromNamespaceAndPath("matcha", "anglers_almanac/saltwater/hot/root"),
			Identifier.fromNamespaceAndPath("matcha", "anglers_almanac/swamps/root"),
			Identifier.fromNamespaceAndPath("matcha", "anglers_almanac/deep_dark/root"),
			Identifier.fromNamespaceAndPath("matcha", "anglers_almanac/pale_garden/root"),
			Identifier.fromNamespaceAndPath("matcha", "anglers_almanac/sulfur_caves/root"),
			Identifier.fromNamespaceAndPath("matcha", "anglers_almanac/junk/root"),
			Identifier.fromNamespaceAndPath("matcha", "anglers_almanac/treasure/root")
	);

	@Shadow
	public abstract AdvancementNode root();

	@Inject(method = "children", at = @At("RETURN"), cancellable = true)
	private void matcha$orderChildren(CallbackInfoReturnable<Iterable<AdvancementNode>> callbackInfo) {
		if (!this.root().holder().id().equals(ANGLERS_ALMANAC_ROOT)) {
			return;
		}

		List<AdvancementNode> children = new ArrayList<>();
		callbackInfo.getReturnValue().forEach(children::add);
		children.sort(Comparator
				.comparingInt(AdvancementNodeMixin::matcha$order)
				.thenComparing(node -> node.holder().id().toString()));
		callbackInfo.setReturnValue(children);
	}

	@Unique
	private static int matcha$order(AdvancementNode node) {
		int order = ANGLERS_ALMANAC_CATEGORY_ORDER.indexOf(node.holder().id());
		return order < 0 ? Integer.MAX_VALUE : order;
	}
}
