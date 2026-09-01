package monster.east.matchaff.mixin;

import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Dynamic;
import monster.east.matchaff.datafix.MatchaItemDataFixer;
import net.minecraft.util.datafix.DataFixTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Runs Matcha's migration even when Minecraft's DataVersion is unchanged. */
@Mixin(DataFixTypes.class)
public abstract class DataFixTypesMixin {
	@Inject(method = "update", at = @At("RETURN"), cancellable = true)
	private void matcha$migratePlayerItems(DataFixer fixerUpper, Dynamic<?> input, int fromVersion, int toVersion,
			CallbackInfoReturnable<Dynamic<?>> cir) {
		if ((Object) this == DataFixTypes.PLAYER) {
			cir.setReturnValue(MatchaItemDataFixer.updateIfNeeded(cir.getReturnValue()));
		}
	}
}
