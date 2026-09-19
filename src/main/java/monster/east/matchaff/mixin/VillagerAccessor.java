package monster.east.matchaff.mixin;

import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Villager.class)
public interface VillagerAccessor {
	@Accessor("lastRestockGameTime")
	void matcha$setLastRestockGameTime(long value);

	@Invoker("updateTrades")
	void matcha$updateTrades(ServerLevel level);
}
