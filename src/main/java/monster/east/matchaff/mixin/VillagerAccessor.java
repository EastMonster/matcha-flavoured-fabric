package monster.east.matchaff.mixin;

import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Villager.class)
public interface VillagerAccessor {
	@Accessor("lastRestockGameTime")
	void matcha$setLastRestockGameTime(long value);
}
