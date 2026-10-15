package monster.east.matchaff.item;

import monster.east.matchaff.mechanic.BeaconKindlingMechanics;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.context.UseOnContext;

public final class BeaconKindlingItem extends SpawnEggItem {
	public BeaconKindlingItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		return BeaconKindlingMechanics.useOn(context);
	}
}
