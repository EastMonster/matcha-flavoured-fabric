package monster.east.matchaff.item;

import monster.east.matchaff.mechanic.PlayerMechanics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;

/** Uses one heart container about once per second while the held use continues. */
public final class CrystalHeartItem extends Item {
	public CrystalHeartItem(Properties properties) {
		super(properties);
	}

	@Override
	public void onUseTick(Level level, LivingEntity user, ItemStack stack, int ticksRemaining) {
		if (!level.isClientSide() && user instanceof ServerPlayer player && ticksRemaining % 20 == 0) {
			PlayerMechanics.useCrystalHeart(player);
		}
	}
}
