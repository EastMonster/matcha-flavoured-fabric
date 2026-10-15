package monster.east.matchaff.mixin;

import monster.east.matchaff.mechanic.MobMechanics;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Keeps the creation-time exemption intact when custom entity NBT is loaded. */
@Mixin(Mob.class)
public abstract class MobSpawnMixin implements MobMechanics.SpawnExemption {
	@Unique
	private boolean matcha$spawnExempt;

	@Override
	public boolean matcha$isSpawnExempt() {
		return this.matcha$spawnExempt;
	}

	@Override
	public void matcha$setSpawnExempt(boolean exempt) {
		this.matcha$spawnExempt = exempt;
	}
}
