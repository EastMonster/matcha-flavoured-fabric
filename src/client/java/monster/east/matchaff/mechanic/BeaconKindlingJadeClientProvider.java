package monster.east.matchaff.mechanic;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.theme.IThemeHelper;

public final class BeaconKindlingJadeClientProvider implements IBlockComponentProvider {
	public static final BeaconKindlingJadeClientProvider INSTANCE = new BeaconKindlingJadeClientProvider();

	private BeaconKindlingJadeClientProvider() {
	}

	@Override
	public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
		int remaining = accessor.getServerData().getIntOr(MatchaJadePlugin.BeaconKindlingProvider.DATA_KEY, 0);
		if (remaining <= 0) {
			return;
		}
		String translationKey = accessor.getServerData().getBooleanOr(
				MatchaJadePlugin.BeaconKindlingProvider.PHASE_KEY, false)
				? "tooltip.matcha.beacon_kindling.departure"
				: "tooltip.matcha.beacon_kindling.arrival";
		tooltip.add(Component.translatable(
				translationKey,
				IThemeHelper.get().seconds(remaining, accessor.tickRate())
		).withStyle(ChatFormatting.GRAY));
	}

	@Override
	public Identifier getUid() {
		return MatchaJadePlugin.BeaconKindlingProvider.UID;
	}
}
