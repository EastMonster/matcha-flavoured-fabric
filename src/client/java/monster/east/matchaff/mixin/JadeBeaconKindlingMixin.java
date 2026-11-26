package monster.east.matchaff.mixin;

import monster.east.matchaff.block.KettleBlock;
import monster.east.matchaff.mechanic.KettleJadeClientProvider;
import monster.east.matchaff.mechanic.BeaconKindlingJadeClientProvider;
import monster.east.matchaff.mechanic.PigForagingJadeClientProvider;
import monster.east.matchaff.mechanic.BirdForagingJadeClientProvider;
import monster.east.matchaff.mechanic.BirdMechanics;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.level.block.CampfireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.JadeIds;

@Mixin(targets = "snownee.jade.impl.WailaClientRegistration")
public abstract class JadeBeaconKindlingMixin {
	@Inject(method = "<init>", at = @At("TAIL"), require = 0)
	private void matcha$registerProviders(CallbackInfo ci) {
		((IWailaClientRegistration) (Object) this).registerBlockComponent(KettleJadeClientProvider.INSTANCE, KettleBlock.class);
		((IWailaClientRegistration) (Object) this).registerBlockComponent(
				BeaconKindlingJadeClientProvider.INSTANCE, CampfireBlock.class);
		((IWailaClientRegistration) (Object) this).registerEntityComponent(
				PigForagingJadeClientProvider.INSTANCE, Pig.class);
		((IWailaClientRegistration) (Object) this).registerEntityComponent(
				BirdForagingJadeClientProvider.INSTANCE, Chicken.class);
		((IWailaClientRegistration) (Object) this).addTooltipCollectedCallback((root, accessor) -> {
			if (accessor instanceof EntityAccessor entityAccessor
					&& entityAccessor.getEntity() instanceof Chicken chicken && BirdMechanics.isManaged(chicken)) {
				root.getTooltip().remove(JadeIds.MC_NEXT_ENTITY_DROP);
			}
		});
	}
}
