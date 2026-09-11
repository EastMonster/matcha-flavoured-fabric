package monster.east.matchaff.mixin;

import monster.east.matchaff.compat.TrinketsCompat;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityEquipmentMixin {
	@Unique
	private ItemStack matcha$lastHeadEquipment = ItemStack.EMPTY;
	@Unique
	private boolean matcha$headEquipmentInitialized;

	@Inject(method = "detectEquipmentUpdates", at = @At("HEAD"))
	private void matcha$refreshEarringArmor(CallbackInfo ci) {
		if (!((Object) this instanceof Player player)
				|| !FabricLoader.getInstance().isModLoaded("trinkets_updated")) {
			return;
		}

		ItemStack current = player.getItemBySlot(EquipmentSlot.HEAD);
		if (this.matcha$headEquipmentInitialized
				&& ItemStack.matches(current, this.matcha$lastHeadEquipment)) {
			return;
		}

		TrinketsCompat.refreshEarringArmor(player);
		this.matcha$lastHeadEquipment = current.copy();
		this.matcha$headEquipmentInitialized = true;
	}
}
