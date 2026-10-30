package monster.east.matchaff.mixin;

import monster.east.matchaff.mechanic.BeaconKindlingJadeClientProvider;
import net.minecraft.world.level.block.CampfireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import snownee.jade.api.IWailaClientRegistration;

@Mixin(targets = "snownee.jade.impl.WailaClientRegistration")
public abstract class JadeBeaconKindlingMixin {
	@Inject(method = "<init>", at = @At("TAIL"), require = 0)
	private void matcha$registerBeaconKindling(CallbackInfo ci) {
		((IWailaClientRegistration) (Object) this).registerBlockComponent(
				BeaconKindlingJadeClientProvider.INSTANCE, CampfireBlock.class);
	}
}
