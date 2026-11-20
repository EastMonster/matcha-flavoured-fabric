package monster.east.matchaff.mixin;

import net.minecraft.world.entity.ai.behavior.WorkAtComposter;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Keeps custom seeds compostable by farmers after Fabric's registration API was removed. */
@Mixin(WorkAtComposter.class)
public interface WorkAtComposterAccessor {
	@Accessor("COMPOSTABLE_ITEMS")
	static List<Item> matcha$getCompostableItems() {
		throw new AssertionError();
	}

	@Mutable
	@Accessor("COMPOSTABLE_ITEMS")
	static void matcha$setCompostableItems(List<Item> items) {
		throw new AssertionError();
	}
}
