package monster.east.matchaff.mixin;

import monster.east.matchaff.mechanic.FoodHealMechanics;
import net.minecraft.client.gui.Hud;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Hud.class)
public abstract class FoodHealHudMixin {
	@Shadow private int tickCount;

	@ModifyArgs(method = "extractPlayerHealth", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/Hud;extractHearts(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/entity/player/Player;IIIIFIIIZ)V"))
	private void matcha$animateFoodHealing(Args args) {
		Player player = args.get(1);
		int heartOffsetIndex = args.get(5);
		if (heartOffsetIndex < 0 && player.getAttachedOrElse(FoodHealMechanics.HEALING_ANIMATION, false)) {
			float maxHealth = args.get(6);
			args.set(5, this.tickCount % Mth.ceil(maxHealth + 5.0F));
		}
	}
}
