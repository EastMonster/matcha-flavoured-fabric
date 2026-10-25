package monster.east.matchaff.mechanic;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.theme.IThemeHelper;

public final class PigForagingJadeClientProvider implements IEntityComponentProvider {
	public static final PigForagingJadeClientProvider INSTANCE = new PigForagingJadeClientProvider();

	private PigForagingJadeClientProvider() {
	}

	@Override
	public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
		var data = accessor.getServerData();
		if (!data.contains(MatchaJadePlugin.PigForagingProvider.DATA_KEY)) return;
		int remaining = data.getIntOr(MatchaJadePlugin.PigForagingProvider.DATA_KEY, 0);
		int animation = data.getIntOr(MatchaJadePlugin.PigForagingProvider.ANIMATION_KEY, 0);
		MutableComponent line;
		if (animation != 0) {
			line = Component.translatable(animation > 0 ? "tooltip.matcha.pig.digging" : "tooltip.matcha.pig.mud_play");
		} else if (remaining > 0) {
			line = Component.translatable("tooltip.matcha.pig.foraging_cooldown",
					IThemeHelper.get().seconds(remaining, accessor.tickRate()));
		} else {
			line = Component.translatable("tooltip.matcha.pig.foraging_ready");
		}
		tooltip.add(line.withStyle(ChatFormatting.GRAY));
	}

	@Override
	public Identifier getUid() {
		return MatchaJadePlugin.PigForagingProvider.UID;
	}
}
