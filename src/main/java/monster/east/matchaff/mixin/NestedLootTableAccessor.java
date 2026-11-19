package monster.east.matchaff.mixin;

import net.minecraft.core.HolderSet;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.NestedLootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(NestedLootTable.class)
public interface NestedLootTableAccessor {
	@Accessor("value")
	HolderSet<LootTable> matcha$getTables();
}
