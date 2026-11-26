package monster.east.matchaff.mechanic;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.theme.IThemeHelper;
import snownee.jade.api.ui.JadeUI;

public final class KettleJadeClientProvider implements IBlockComponentProvider {
	public static final KettleJadeClientProvider INSTANCE = new KettleJadeClientProvider();
	private static final Identifier FUEL_ICON = Identifier.withDefaultNamespace("container/furnace/lit_progress");

	private KettleJadeClientProvider() { }

	@Override
	public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
		var data = accessor.getServerData();
		if (!data.contains(MatchaJadePlugin.KettleProvider.FUEL_KEY)) return;
		tooltip.add(JadeUI.sprite(FUEL_ICON, 10, 10));
		tooltip.append(IThemeHelper.get().info(data.getIntOr(MatchaJadePlugin.KettleProvider.FUEL_KEY, 0)));
		int time = data.getIntOr(MatchaJadePlugin.KettleProvider.TIME_KEY, 0);
		if (time > 0) {
			tooltip.append(JadeUI.spacer(5, 0));
			tooltip.append(JadeUI.smallItem(new ItemStack(Items.CLOCK)));
			tooltip.append(IThemeHelper.get().seconds(time, accessor.tickRate()));
		}
	}

	@Override
	public Identifier getUid() { return MatchaJadePlugin.KettleProvider.UID; }
}
