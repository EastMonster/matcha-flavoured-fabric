package monster.east.matchaff.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.InstrumentItem;
import net.minecraft.world.item.component.UseCooldown;
import net.minecraft.world.level.Level;
import net.minecraft.resources.Identifier;
import monster.east.matchaff.mechanic.GudiMechanics;
import java.util.Optional;

public final class GudiItem extends InstrumentItem {
	public GudiItem(Properties properties) {
		super(properties.component(DataComponents.USE_COOLDOWN,
				new UseCooldown(5, Optional.of(Identifier.fromNamespaceAndPath("matcha", "gudi")))));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		InteractionResult result = super.use(level, player, hand);
		if (!level.isClientSide() && result.consumesAction()) GudiMechanics.recall(player, player.getItemInHand(hand));
		return result;
	}
}
