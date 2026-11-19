package monster.east.matchaff.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Optional;

@Mixin(LootPoolEntryContainer.class)
public interface LootPoolEntryAccessor {
	@Accessor("condition")
	Optional<Holder<LootItemCondition>> matcha$getCondition();
}
