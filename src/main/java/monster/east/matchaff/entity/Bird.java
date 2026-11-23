package monster.east.matchaff.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.level.Level;

/** Separate runtime classes also keep vanilla breeding and parent-following within each new species. */
public abstract class Bird extends Chicken {
	protected Bird(EntityType<? extends Chicken> type, Level level) {
		super(type, level);
	}

	@Override
	public Chicken getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return partner.getType() == getType() ? (Chicken) getType().create(level, EntitySpawnReason.BREEDING) : null;
	}

	public static final class Duck extends Bird {
		public Duck(EntityType<? extends Chicken> type, Level level) { super(type, level); }
	}

	public static final class Seagull extends Bird {
		public Seagull(EntityType<? extends Chicken> type, Level level) { super(type, level); }
	}

	public static final class Bobwhite extends Bird {
		public Bobwhite(EntityType<? extends Chicken> type, Level level) { super(type, level); }
	}
}
