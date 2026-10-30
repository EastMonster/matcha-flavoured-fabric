package monster.east.matchaff.mechanic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public final class MatchaJadePlugin implements IWailaPlugin {
	@Override
	public void register(IWailaCommonRegistration registration) {
		registration.registerBlockDataProvider(BeaconKindlingProvider.INSTANCE, CampfireBlock.class);
	}

	static final class BeaconKindlingProvider implements IServerDataProvider<BlockAccessor> {
		static final BeaconKindlingProvider INSTANCE = new BeaconKindlingProvider();
		static final Identifier UID = Identifier.fromNamespaceAndPath(
				"matcha-flavoured", "beacon_kindling"
		);
		static final String DATA_KEY = UID.toString();
		static final String PHASE_KEY = DATA_KEY + ".phase_end";

		@Override
		public void appendServerData(CompoundTag data, BlockAccessor accessor) {
			if (!isActiveCampfire(accessor.getBlockState())
					|| !(accessor.getLevel() instanceof ServerLevel level)) {
				return;
			}
			BeaconKindlingMechanics.BeaconStatus status = BeaconKindlingMechanics.status(
					level.getServer(), level.dimension(), accessor.getPosition());
			if (status.remainingTicks() > 0) {
				data.putInt(DATA_KEY, status.remainingTicks());
				data.putBoolean(PHASE_KEY, status.traderSummoned());
			}
		}

		@Override
		public boolean shouldRequestData(BlockAccessor accessor) {
			return isActiveCampfire(accessor.getBlockState());
		}

		@Override
		public Identifier getUid() {
			return UID;
		}

		private static boolean isActiveCampfire(BlockState state) {
			return state.is(Blocks.CAMPFIRE)
					&& state.getValue(CampfireBlock.SIGNAL_FIRE)
					&& state.getValue(CampfireBlock.LIT);
		}
	}
}
