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

public final class BirdForagingJadeClientProvider implements IEntityComponentProvider {
	public static final BirdForagingJadeClientProvider INSTANCE = new BirdForagingJadeClientProvider();

	private BirdForagingJadeClientProvider() {
	}

	@Override
	public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
		var data = accessor.getServerData();
		if (!data.contains(MatchaJadePlugin.BirdForagingProvider.DATA_KEY)) return;
		String personality = data.getStringOr(MatchaJadePlugin.BirdForagingProvider.PERSONALITY_KEY, "normal");
		tooltip.add(Component.translatable("tooltip.matcha.bird.personality",
				Component.translatable("desc.matcha.chicken_type." + personality)).withStyle(ChatFormatting.GRAY));
		int remaining = data.getIntOr(MatchaJadePlugin.BirdForagingProvider.DATA_KEY, 0);
		String action = data.getStringOr(MatchaJadePlugin.BirdForagingProvider.ACTION_KEY, "");
		MutableComponent line;
		if (!action.isEmpty()) {
			line = Component.translatable("tooltip.matcha.bird." + action);
		} else if (remaining > 0) {
			line = Component.translatable("tooltip.matcha.bird.foraging_cooldown",
					IThemeHelper.get().seconds(remaining, accessor.tickRate()));
		} else {
			line = Component.translatable("tooltip.matcha.bird.foraging_ready");
		}
		tooltip.add(line.withStyle(ChatFormatting.GRAY));
	}

	@Override
	public Identifier getUid() { return MatchaJadePlugin.BirdForagingProvider.UID; }
}
