package monster.east.matchaff.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import monster.east.matchaff.client.MatchaClientConfig;
import monster.east.matchaff.mechanic.EffectsMechanics;
import monster.east.matchaff.mechanic.FoodHealMechanics;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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

	@Inject(method = "extractHearts", at = @At("HEAD"))
	private void matcha$calculateHeldFoodPreview(GuiGraphicsExtractor graphics, Player player, int xLeft, int yLineBase,
			int rowHeight, int heartOffsetIndex, float maxHealth, int health, int oldHealth, int absorption,
			boolean blink, CallbackInfo ci, @Share("foodHealingPoints") LocalIntRef previewPoints) {
		if (!MatchaClientConfig.foodHealingPreview()) {
			return;
		}
		int missingHealth = Mth.ceil(maxHealth) - health;
		if (missingHealth <= 0 || EffectsMechanics.blocksHealing(player.level().getDifficulty(), player.hasEffect(MobEffects.HUNGER))) {
			return;
		}

		Consumable food = matcha$usableConsumable(player, player.getMainHandItem());
		if (food == null) {
			food = matcha$usableConsumable(player, player.getOffhandItem());
		}
		if (food != null) {
			previewPoints.set(Math.min(missingHealth, FoodHealMechanics.foodHealingPoints(food)));
		}
	}

	@Unique
	private static Consumable matcha$usableConsumable(Player player, ItemStack stack) {
		Consumable consumable = stack.get(DataComponents.CONSUMABLE);
		return consumable != null && consumable.canConsume(player, stack) ? consumable : null;
	}

	@Inject(method = "extractHearts", at = @At(value = "INVOKE", ordinal = 0, shift = At.Shift.AFTER,
			target = "Lnet/minecraft/client/gui/Hud;extractHeart(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Hud$HeartType;IIZZZ)V"))
	private void matcha$previewFoodHealing(GuiGraphicsExtractor graphics, Player player, int xLeft, int yLineBase,
			int rowHeight, int heartOffsetIndex, float maxHealth, int health, int oldHealth, int absorption,
			boolean blink, CallbackInfo ci, @Local(ordinal = 10) int containerIndex,
			@Local(ordinal = 13) int x, @Local(ordinal = 14) int y,
			@Share("foodHealingPoints") LocalIntRef previewPoints) {
		int previewHealth = health + previewPoints.get();
		int halves = containerIndex * 2;
		if (previewHealth <= health || previewHealth <= halves || health >= halves + 2) {
			return;
		}

		// Draw below vanilla's current health so an existing half heart stays opaque.
		boolean half = previewHealth == halves + 1;
		boolean hardcore = player.level().getLevelData().isHardcore();
		Identifier sprite = Identifier.withDefaultNamespace("hud/heart/" + (hardcore ? "hardcore_" : "")
				+ (half ? "half" : "full"));
		float alpha = 0.35F + 0.25F * Mth.cos(this.tickCount * Mth.TWO_PI / 24.0F);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, 9, 9, alpha);
	}
}
