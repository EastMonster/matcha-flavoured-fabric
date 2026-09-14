package monster.east.matchaff.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AdvancementsScreen.class)
public abstract class AdvancementsScreenMixin {
	@Inject(method = "init", at = @At("TAIL"))
	private void matcha$requestStats(CallbackInfo ci) {
		if (Minecraft.getInstance().getConnection() != null) {
			Minecraft.getInstance().getConnection().send(
					new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.REQUEST_STATS)
			);
		}
	}
}
